package ru.kolbabot.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class OzonCheckoutTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before @After fun clearDraft() { context.getSharedPreferences("store_checkout", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun mapPickerSearchAndEstimateWorkWhenOzonCheckoutIsDisabled() {
        val api = DeliveryApi(context, checkout = false)
        show(api)
        selectPoint(101)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("ozonDeliveryEstimate").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ozonDeliveryEstimate").performScrollTo().assertTextEquals("Примерный срок доставки: 5 дней")
        assertTrue(api.posts.any { it.first.endsWith("/estimate") && it.second.optLong("delivery_point_id") == 101L })
        assertFalse(api.posts.any { it.first.endsWith("/quote") })
        fillCustomerAndConsents()
        screenshot("01-ozon-estimate-without-checkout")
        compose.onNodeWithText("Оформить заказ").performScrollTo().performClick()
        compose.waitUntil { api.posts.any { it.first == "/api/store/checkout" } }
        val submitted = api.posts.single { it.first == "/api/store/checkout" }.second
        assertEquals(101L, submitted.getLong("ozon_delivery_point_id"))
        assertFalse(submitted.has("expected_ozon_quote"))
        assertEquals("Москва, Тестовая улица, 1", submitted.getString("address"))
    }

    @Test fun changingPhoneInvalidatesQuoteAndLateResponseCannotEnablePayment() {
        val api = DeliveryApi(context, checkout = true, delayedQuotes = true)
        show(api)
        selectPoint(101)
        fillCustomerAndConsents()
        compose.waitUntil(5000) { api.quoteGates.size == 1 }
        compose.onNodeWithText("Оформить заказ").performScrollTo().assertIsNotEnabled()
        input("Телефон", "+79991234568")
        compose.waitUntil(5000) { api.quoteGates.size == 2 }
        api.quoteGates[0].complete(api.quote(101, 30000))
        compose.onNodeWithText("Оформить заказ").performScrollTo().assertIsNotEnabled()
        api.quoteGates[1].complete(api.quote(101, 45000))
        compose.onNodeWithText("Оформить заказ").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Оформить заказ").performClick()
        compose.waitUntil { api.posts.any { it.first == "/api/store/checkout" } }
        val submitted = api.posts.single { it.first == "/api/store/checkout" }.second
        assertEquals("+79991234568", submitted.getString("phone"))
        assertEquals(45000L, submitted.getJSONObject("expected_ozon_quote").getLong("price_kopecks"))
        assertEquals(101L, submitted.getJSONObject("expected_ozon_quote").getJSONObject("delivery_point").getLong("delivery_point_id"))
    }

    @Test fun manualPointUsesTheSameAddressAndNameContractAsTheSite() {
        val api = DeliveryApi(context, checkout = false)
        show(api)
        compose.onNodeWithTag("ozonChoosePoint").performScrollTo().performClick()
        compose.onNodeWithText("Моего ПВЗ нет в списке").performScrollTo().performClick()
        input("Адрес ПВЗ Ozon", "Псковская область, Пустошка, Тестовая улица, 2")
        input("Название или номер ПВЗ (необязательно)", "Пункт Ozon 22")
        compose.onNodeWithText("Использовать этот ПВЗ").performScrollTo().performClick()
        fillCustomerAndConsents()
        compose.onNodeWithText("Оформить заказ").performScrollTo().performClick()
        compose.waitUntil { api.posts.any { it.first == "/api/store/checkout" } }
        val submitted = api.posts.single { it.first == "/api/store/checkout" }.second
        assertEquals("Пункт Ozon 22", submitted.getString("ozon_manual_pvz_name"))
        assertEquals("Псковская область, Пустошка, Тестовая улица, 2", submitted.getString("ozon_manual_pvz_address"))
        assertFalse(submitted.has("ozon_delivery_point_id"))
        assertFalse(api.posts.any { it.first.endsWith("/estimate") || it.first.endsWith("/quote") })
    }

    private fun show(api: DeliveryApi) {
        compose.setContent { KolbaTheme { StoreScreen(api) } }
        compose.onNodeWithTag("storeInlineCart").assertExists()
        compose.onNodeWithTag("site-select-Способ доставки").performScrollTo().assertExists()
    }

    private fun selectPoint(id: Long) {
        compose.onNodeWithTag("ozonChoosePoint").performScrollTo().performClick()
        compose.onNodeWithTag("ozonPickupMap").assertExists()
        // The basemap intentionally has no configured CDN in this deterministic
        // fixture. The native search and point selection must remain usable.
        compose.onNodeWithTag("ozonPointSearch").performScrollTo().performTextInput("Москва")
        compose.onNodeWithTag("ozonPointSearch").performImeAction()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("ozonPoint_$id").fetchSemanticsNodes().isNotEmpty() }
        // The site has a scrollable result list inside the scrolling dialog.
        // Reveal that list in the outer viewport before scrolling its item.
        compose.onNodeWithTag("ozonPointResults").performScrollTo()
        screenshot("00-ozon-map-and-search")
        compose.onNodeWithTag("ozonPoint_$id").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("ozonPickupMap").fetchSemanticsNodes().isEmpty() }
    }

    private fun input(label: String, value: String) = compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)

    private fun fillCustomerAndConsents() {
        input("ФИО", "Тестовый Покупатель")
        input("Телефон", "+79991234567")
        compose.onNodeWithText("Принимаю публичную оферту").performScrollTo().performClick()
        compose.onNodeWithText("Согласен на обработку персональных данных для доставки").performScrollTo().performClick()
        compose.onNodeWithText("Мне исполнилось 18 лет").performScrollTo().performClick()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(context.getExternalFilesDir(null), "ozon-smoke").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private class DeliveryApi(context: Context, checkout: Boolean, private val delayedQuotes: Boolean = false) : ApiClient(context, "https://ozon-fixture.invalid") {
        override var userId = 7L
        val posts = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        val quoteGates = CopyOnWriteArrayList<CompletableDeferred<JSONObject>>()
        private val product = JSONObject("""{"id":1,"product_id":1,"title":"Специи","product_type":"physical","price_kopecks":15000,"quantity":1,"weight_grams":100}""")
        val cart = JSONObject().put("items", JSONArray().put(product))
        val catalog = JSONObject().put("products", JSONArray().put(product)).put("user_id", 7)
            .put("ozon_delivery", JSONObject().put("enabled", true).put("checkout_enabled", checkout))
            .put("delivery_methods", JSONArray().put(JSONObject("""{"code":"ozon","title":"Ozon"}""")))
            .put("delivery_sizes", JSONArray().put(JSONObject("""{"max_weight_grams":1000,"price_kopecks":30000}""")))
            .put("payments", JSONObject().put("enabled", true))

        override suspend fun get(path: String): JSONObject = when {
            path == "/api/store" -> catalog
            path == "/api/store/cart" -> cart
            path == "${OzonDelivery.API}map-config" -> JSONObject().put("pmtiles_url", "").put("enabled", false)
            path.startsWith("${OzonDelivery.API}points?") -> JSONObject().put("items", JSONArray().put(point(101)).put(point(102)))
            else -> error("Unexpected GET $path")
        }
        override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
            posts += path to JSONObject(body.toString())
            return when (path) {
                "${OzonDelivery.API}estimate" -> JSONObject().put("estimate", JSONObject().put("delivery_point_id", body.optLong("delivery_point_id")).put("estimated_delivery_days", 5))
                "${OzonDelivery.API}quote" -> if (delayedQuotes) CompletableDeferred<JSONObject>().also { quoteGates += it }.await() else quote(body.optLong("delivery_point_id"), 30000)
                "/api/store/checkout" -> JSONObject().put("order_id", "test-order").put("payment_url", "https://payments.invalid/test-order")
                else -> error("Unexpected POST $path")
            }
        }
        fun quote(id: Long, price: Long) = JSONObject().put("quote", JSONObject().put("price_kopecks", price).put("delivery_cost_kopecks", price)
            .put("insurance_cost_kopecks", 0).put("estimated_delivery_days", 5).put("delivery_point", point(id)))
        private fun point(id: Long) = JSONObject().put("delivery_point_id", id).put("name", "ПВЗ Ozon $id")
            .put("full_address", "Москва, Тестовая улица, ${if (id == 101L) 1 else 2}")
    }
}
