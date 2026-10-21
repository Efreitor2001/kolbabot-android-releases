package ru.kolbabot.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch

class StoreCartMutationTest {
    private fun row(id: Long, quantity: Int) = JSONObject().put("product_id", id).put("quantity", quantity)
    private fun catalog() = JSONObject().put("products", JSONArray().apply {
        (1L..4L).forEach { put(JSONObject().put("id", it).put("title", "SKU $it").put("price_kopecks", 100)) }
    })
    private fun quantities(cart: JSONObject) = cart.items("items").associate {
        it.getLong("product_id") to it.getInt("quantity")
    }

    @Test fun concurrentGuestChangesUseFreshCartAndKeepVariantsSeparate() = runBlocking {
        val context = CartTestContext()
        val api = CartTestApi(context)
        val first = StoreCart(context, api)
        val second = StoreCart(context, api)
        coroutineScope {
            (1..20).map { index -> async {
                (if (index % 2 == 0) first else second).changeQuantity(1, 1, catalog())
            } }.awaitAll()
        }
        assertEquals(mapOf(1L to 20), quantities(first.load(catalog())))
        second.setQuantity(2, 6, catalog())
        assertEquals(mapOf(1L to 47, 2L to 6), quantities(first.setQuantity(1, 47, catalog())))
        assertEquals(mapOf(2L to 6), quantities(first.changeQuantity(1, -47, catalog())))
        first.setQuantity(2, 999, catalog())
        assertEquals(mapOf(2L to 999), quantities(second.changeQuantity(2, Int.MAX_VALUE, catalog())))
        assertEquals(0, api.writes)
    }

    @Test fun signedInChangesMergeGuestOnceAndPreserveOtherServerItems() = runBlocking {
        val context = CartTestContext()
        val api = CartTestApi(context)
        val first = StoreCart(context, api)
        val second = StoreCart(context, api)
        first.setQuantity(1, 2, catalog())
        api.switchOwner(42)
        api.server = listOf(row(1, 3), row(2, 5))
        coroutineScope {
            (1..10).map { index -> async {
                (if (index % 2 == 0) first else second).changeQuantity(1, 1, catalog())
            } }.awaitAll()
        }
        assertEquals(mapOf(1L to 15, 2L to 5), quantities(first.load(catalog())))
        assertEquals(mapOf(1L to 15, 2L to 8), quantities(second.setQuantity(2, 8, catalog())))
        assertFalse(context.preferences("guest_cart").contains("pending_42"))
    }

    @Test fun lostMergeResponseKeepsExactPendingUntilConfirmedWithoutFalseSuccess() = runBlocking {
        val context = CartTestContext()
        val api = CartTestApi(context)
        val cart = StoreCart(context, api)
        cart.setQuantity(1, 2, catalog())
        api.switchOwner(42)
        api.server = listOf(row(1, 3))
        api.failAfterAccepting = true
        assertTrue(runCatching { cart.changeQuantity(1, 1, catalog()) }.exceptionOrNull() is IOException)
        assertTrue(context.preferences("guest_cart").contains("pending_42"))
        assertEquals(5, api.server.single().getInt("quantity"))
        assertEquals(mapOf(1L to 6), quantities(cart.changeQuantity(1, 1, catalog())))
        assertFalse(context.preferences("guest_cart").contains("pending_42"))
    }

    @Test fun ownerChangeAfterReadNeverWritesAccountRowsIntoAnotherAccountOrGuest() = runBlocking {
        for (replacement in listOf(0L, 99L)) {
            val context = CartTestContext()
            val api = CartTestApi(context).apply {
                switchOwner(42)
                server = listOf(row(1, 4), row(2, 3))
                afterRead = { switchOwner(replacement) }
            }
            val result = runCatching { StoreCart(context, api).changeQuantity(1, 1, catalog()) }
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, api.writes)
            assertFalse(context.preferences("guest_cart").contains("items"))
            assertEquals(4, api.server.first().getInt("quantity"))
        }
    }

    @Test fun failedGuestPersistenceDoesNotReportSuccessfulMutation() = runBlocking {
        val context = CartTestContext()
        val api = CartTestApi(context)
        val cart = StoreCart(context, api)
        cart.setQuantity(1, 2, catalog())
        context.preferences("guest_cart").acceptWrites = false
        assertTrue(runCatching { cart.setQuantity(1, 8, catalog()) }.exceptionOrNull() is IllegalStateException)
        assertEquals(mapOf(1L to 2), quantities(cart.load(catalog())))
    }

    @Test fun actualApiChecksExpectedOwnerAfterSessionGateBeforeSendingQueuedPut() = runBlocking {
        val client = SessionOnlyCartApi(CartTestContext()).apply { switchOwner(42) }
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val holding = launch(Dispatchers.IO) {
            client.withCartOwner(42) {
                entered.complete(Unit)
                release.await()
                client.switchOwner(99)
            }
        }
        entered.await()
        val queued = async { runCatching { client.putCart(42, JSONObject().put("items", JSONArray())) } }
        yield()
        release.countDown()
        holding.join()
        assertTrue(queued.await().exceptionOrNull() is IllegalStateException)
        // A mismatch must fail before even attempting the deliberately invalid URL.
        assertTrue(runCatching { client.getCart(42) }.exceptionOrNull() is IllegalStateException)
    }
}

private open class SessionOnlyCartApi(context: Context) : ApiClient(context, "https://cart-never-send.invalid") {
    override var userId: Long = 0
    fun switchOwner(owner: Long) { userId = owner }
}

private class CartTestApi(context: Context) : SessionOnlyCartApi(context) {
    var server = emptyList<JSONObject>()
    var writes = 0
    var afterRead: (() -> Unit)? = null
    var failAfterAccepting = false
    private fun response(owner: Long) = JSONObject().put("user_id", owner).put("items", JSONArray().apply {
        server.forEach { put(JSONObject(it.toString())) }
    })
    override suspend fun getCart(expectedUserId: Long): JSONObject {
        val result = withCartOwner(expectedUserId) { response(expectedUserId) }
        afterRead?.invoke()
        return result
    }
    override suspend fun putCart(expectedUserId: Long, body: JSONObject): JSONObject {
        delay(1)
        return withCartOwner(expectedUserId) {
            writes++
            server = body.items("items").map { JSONObject(it.toString()) }
            if (failAfterAccepting) {
                failAfterAccepting = false
                throw IOException("Response lost after accepted cart write")
            }
            response(expectedUserId)
        }
    }
}

private class CartTestContext : ContextWrapper(null) {
    private val stores = mutableMapOf<String, MemoryCartPreferences>()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences(name)
    fun preferences(name: String) = stores.getOrPut(name) { MemoryCartPreferences() }
}

private class MemoryCartPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    var acceptWrites = true
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearAll = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean {
            if (!acceptWrites) return false
            if (clearAll) values.clear()
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
        override fun apply() { commit() }
    }
}
