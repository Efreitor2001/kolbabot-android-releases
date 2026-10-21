package ru.kolbabot.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestCartSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before @After fun clear() {
        context.getSharedPreferences("guest_cart", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("store_checkout", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun guestCanBuildCartAndLoginMergesItOnlyOnce() {
        val api = GuestApi(context)
        var askedToLogin = false
        compose.setContent { KolbaTheme { StoreScreen(api, onNavigate = { askedToLogin = it == "account" }) } }
        compose.onNodeWithText("В корзину").performScrollTo().performClick()
        compose.onNodeWithText("Товар добавлен в корзину").assertExists()
        assertEquals(0, api.privateReads)
        compose.onNode(hasText("Корзина") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("1 шт.").assertExists()
        compose.onNodeWithTag("cart-plus-101").performScrollTo().performClick()
        compose.onNodeWithText("2 шт.").assertExists()
        compose.onNodeWithText("Войти для оформления").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(askedToLogin); api.signIn() }
        compose.onNodeWithText("Обновить корзину").performScrollTo().performClick()
        compose.onNodeWithText("5 шт.").assertExists()
        assertEquals(1, api.writes)
        compose.onNodeWithText("Обновить корзину").performScrollTo().performClick()
        compose.onNodeWithText("5 шт.").assertExists()
        assertEquals(1, api.writes)
        compose.onNodeWithTag("storeInlineCart").assertExists()
        compose.onNode(hasSetTextAction() and hasText("ФИО")).performScrollTo().assertExists()
        // Checkout is already in the cart; submitting still requires customer,
        // delivery and consent fields instead of an intermediate navigation step.
        compose.onNodeWithText("Оформить заказ").performScrollTo().assertIsNotEnabled()
    }

    private class GuestApi(context: Context) : ApiClient(context, "https://guest-cart.invalid") {
        override var userId: Long = 0
        fun signIn() { userId = 7 }
        var privateReads = 0; var writes = 0
        private val product = JSONObject("""{"id":101,"title":"Оболочка тестовая","price_kopecks":15000,"product_type":"physical","weight_grams":100} """)
        private var quantity = 3
        private fun cart() = JSONObject().put("items", JSONArray().put(JSONObject(product.toString()).put("product_id", 101).put("quantity", quantity)))
        override suspend fun getCart(expectedUserId: Long): JSONObject {
            check(userId == expectedUserId)
            return get("/api/store/cart").put("user_id", expectedUserId)
        }
        override suspend fun putCart(expectedUserId: Long, body: JSONObject): JSONObject {
            check(userId == expectedUserId)
            return put("/api/store/cart", body).put("user_id", expectedUserId)
        }
        override suspend fun get(path: String): JSONObject = when(path) {
            "/api/store" -> JSONObject().put("products", JSONArray().put(product)).put("banners", JSONArray())
            "/api/store/cart" -> { check(authenticated); privateReads++; cart() }
            else -> error("Unexpected $path")
        }
        override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
            check(authenticated && path == "/api/store/cart")
            writes++; quantity = body.getJSONArray("items").getJSONObject(0).getInt("quantity")
            return cart()
        }
    }
}
