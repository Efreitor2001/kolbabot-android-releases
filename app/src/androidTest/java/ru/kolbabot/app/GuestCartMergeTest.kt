package ru.kolbabot.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class GuestCartMergeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("guest_cart", Context.MODE_PRIVATE)
    @Before @After fun clear() { preferences.edit().clear().commit() }
    private fun row(id: Long, count: Int = 1) = JSONObject().put("product_id", id).put("quantity", count)
    private fun array(rows: List<JSONObject>) = JSONArray().apply { rows.forEach { put(it) } }
    private fun catalog() = JSONObject().put("products", JSONArray().apply {
        (1L..250L).forEach { put(JSONObject().put("id", it).put("title", "Товар $it").put("price_kopecks", 100)) }
    })
    private fun stored(key: String) = JSONObject().put("items", JSONArray(preferences.getString(key, "[]"))).items("items")

    @Test fun guestCannotAddMoreThanOneHundredDistinctProducts() = runBlocking {
        val api = CartApi(context); val cart = StoreCart(context, api)
        cart.save((1L..100L).map { row(it) }, catalog())
        try { cart.add(101, catalog()); fail("Expected cart limit") } catch (_: IllegalArgumentException) { }
        assertEquals(100, stored("items").size)
        assertTrue(api.writes.isEmpty())
    }

    @Test fun overflowKeepsGuestAndReturnsEditableServerCartWithoutCreatingPendingWrite() = runBlocking {
        val api = CartApi(context); val cart = StoreCart(context, api)
        cart.save(listOf(row(200, 2)), catalog())
        api.signIn(); api.server = (1L..100L).map { row(it) }
        val blocked = cart.load(catalog())
        assertEquals(100, blocked.items("items").size)
        assertTrue(blocked.optString("merge_notice").isNotBlank())
        assertEquals(200L, stored("items").single().getLong("product_id"))
        assertFalse(preferences.contains("pending_42"))
        assertTrue(api.writes.isEmpty())

        // The same screen can remove a server item, then explicitly refresh.
        cart.save(api.server.dropLast(1), catalog())
        val merged = cart.load(catalog())
        assertEquals(100, merged.items("items").size)
        assertEquals(2, merged.items("items").single { it.getLong("product_id") == 200L }.getInt("quantity"))
        assertTrue(stored("items").isEmpty())
        assertFalse(preferences.contains("pending_42"))
    }

    @Test fun rejectedMergeRestoresOnlyOriginalGuestSubsetAndKeepsServerItemsPrivate() = runBlocking {
        for (status in listOf(400, 422)) {
            clear()
            val api = CartApi(context); val cart = StoreCart(context, api)
            cart.save(listOf(row(200, 2)), catalog())
            api.signIn(); api.server = listOf(row(7, 3)); api.reject = status
            val result = cart.load(catalog())
            assertEquals(listOf(7L), result.items("items").map { it.getLong("product_id") })
            assertTrue(result.optString("merge_notice").isNotBlank())
            assertEquals(listOf(200L), stored("items").map { it.getLong("product_id") })
            assertEquals(2, stored("items").single().getInt("quantity"))
            assertFalse(preferences.contains("pending_42"))
            assertFalse(preferences.contains("pending_42_guest"))
        }
    }

    @Test fun uncertainWriteReplaysIdenticalSnapshotWithoutAddingGuestTwice() = runBlocking {
        val api = CartApi(context); val cart = StoreCart(context, api)
        cart.save(listOf(row(7, 2)), catalog())
        api.signIn(); api.server = listOf(row(7, 3)); api.failAfterAccepting = true
        try { cart.load(catalog()); fail("Expected network timeout") } catch (_: IOException) { }
        assertEquals(5, api.server.single().getInt("quantity"))
        assertTrue(preferences.contains("pending_42"))
        assertEquals(2, stored("pending_42_guest").single().getInt("quantity"))
        assertTrue(stored("items").isEmpty())
        val result = cart.load(catalog())
        assertEquals(5, result.items("items").single().getInt("quantity"))
        assertEquals(2, api.writes.size)
        assertEquals(api.writes[0], api.writes[1])
        assertFalse(preferences.contains("pending_42"))
        assertFalse(preferences.contains("pending_42_guest"))
    }

    @Test fun corruptPendingSnapshotNeverSendsAnEmptyReplacementCart() = runBlocking {
        val api = CartApi(context).apply { signIn(); server = listOf(row(7, 3)) }
        preferences.edit().putString("pending_42", "broken-json")
            .putString("pending_42_guest", array(listOf(row(200, 2))).toString()).commit()
        val result = StoreCart(context, api).load(catalog())
        assertEquals(7L, result.items("items").single().getLong("product_id"))
        assertTrue(api.writes.isEmpty())
        assertEquals(200L, stored("items").single().getLong("product_id"))
        assertFalse(preferences.contains("pending_42"))
    }

    @Test fun legacyOversizedAttemptIsRetainedUnderOwnerWithoutLeakingIntoGuest() = runBlocking {
        val api = CartApi(context).apply { signIn(); server = listOf(row(7, 3)) }
        val legacy = array((1L..101L).map { row(it) }).toString()
        preferences.edit().putString("pending_42", legacy).commit()
        val result = StoreCart(context, api).load(catalog())
        assertEquals(7L, result.items("items").single().getLong("product_id"))
        assertTrue(api.writes.isEmpty())
        assertTrue(stored("items").isEmpty())
        assertEquals(legacy, preferences.getString("rejected_42", ""))
        assertFalse(preferences.contains("pending_42"))
    }

    @Test fun serverReadFailureLeavesGuestSelectionsUntouched() = runBlocking {
        val api = CartApi(context); val cart = StoreCart(context, api)
        cart.save(listOf(row(200, 2)), catalog())
        api.signIn(); api.failGet = true
        try { cart.load(catalog()); fail("Expected read failure") } catch (_: IOException) { }
        assertEquals(200L, stored("items").single().getLong("product_id"))
        assertFalse(preferences.contains("pending_42"))
        assertTrue(api.writes.isEmpty())
    }

    private class CartApi(context: Context) : ApiClient(context, "https://cart-fixture.invalid") {
        override var userId: Long = 0
        fun signIn() { userId = 42 }
        var server = emptyList<JSONObject>()
        val writes = mutableListOf<String>()
        var reject = 0
        var failGet = false
        var failAfterAccepting = false
        private fun result() = JSONObject().put("user_id", userId).put("items", JSONArray().apply {
            server.forEach { put(JSONObject(it.toString())) }
        })
        override suspend fun get(path: String): JSONObject {
            check(authenticated && path == "/api/store/cart")
            if (failGet) throw IOException("Fixture read failure")
            return result()
        }
        override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
            check(authenticated && path == "/api/store/cart")
            writes += body.toString()
            if (reject != 0) throw ApiException(reject, "Fixture definite rejection")
            server = body.items("items").map { JSONObject(it.toString()) }
            if (failAfterAccepting) { failAfterAccepting = false; throw IOException("Fixture response lost") }
            return result()
        }
    }
}
