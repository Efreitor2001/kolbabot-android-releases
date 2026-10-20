package ru.kolbabot.app

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Synthetic orders only; API writes and external payment navigation are forbidden. */
@RunWith(AndroidJUnit4::class)
class CustomerOrdersSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun historyShowsCompactColoredStatusesAndDetailsCopyExactValuesThenBackReturnsToList() {
        val api = CustomerOrdersFakeApi(context)
        compose.setContent { KolbaTheme { OrdersScreen(api) } }
        compose.onNodeWithTag("order-status-customer-1").assertTextEquals("Ожидает сборки")
        compose.onNodeWithTag("order-status-customer-2").assertTextEquals("Готов")
        compose.onNodeWithTag("order-status-customer-3").assertTextEquals("Возврат")
        compose.onNodeWithText(api.address).assertDoesNotExist()
        compose.onNodeWithText(api.email).assertDoesNotExist()
        compose.onNodeWithText("Товар для проверки подробностей").assertDoesNotExist()
        screenshot("customer-orders-compact-list")
        compose.onNodeWithTag("open-order-customer-1").performScrollTo().performClick()
        compose.onNodeWithText("Товар для проверки подробностей").assertExists()
        listOf("customer-1", api.email, api.phone, api.address).forEach { value ->
            compose.onNodeWithText(value, substring = false).performScrollTo().performClick()
            compose.runOnIdle {
                val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                assertNotNull("Order copy must populate the clipboard", clip)
                assertEquals(value, clip!!.getItemAt(0).text.toString())
            }
        }
        screenshot("customer-order-details-copy")
        Espresso.pressBack()
        compose.onNodeWithTag("order-row-customer-1").assertExists()
        compose.onNodeWithText(api.address).assertDoesNotExist()
        assertTrue(api.reads.all { it == "/api/store/orders" || it == "/api/store/orders/customer-1" })
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("Screenshot failed")
        val directory = File(context.getExternalFilesDir(null), "visual-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}

private class CustomerOrdersFakeApi(context: Context) : ApiClient(context, "https://customer-orders.invalid") {
    val reads = CopyOnWriteArrayList<String>()
    val email = "customer+orders@example.invalid"
    val phone = "+7 (999) 123-45-67"
    val address = "г. Москва, ул. Тестовая, 7\nПВЗ № 42, вход со двора"
    init { userId = 900000323 }
    private fun order(number: Int, status: String) = JSONObject().put("order_id", "customer-$number")
        .put("status", status).put("total_kopecks", 150000).put("products_original_total_kopecks", 120000)
        .put("delivery_price_kopecks", 30000).put("created_at", "2026-10-15 12:34:56")
        .put("customer_name", "Тестовый Покупатель").put("phone", phone).put("digital_delivery_email", email).put("address", address)
        .put("items", JSONArray().put(JSONObject().put("title", "Товар для проверки подробностей").put("quantity", 2).put("amount_kopecks", 120000)))
    override suspend fun get(path: String): JSONObject {
        reads += path
        return when (path) {
            "/api/store/orders" -> JSONObject().put("items", JSONArray().put(order(1, "paid")).put(order(2, "ready")).put(order(3, "refunded")))
            "/api/store/orders/customer-1" -> order(1, "paid")
            else -> throw AssertionError("Unexpected read: $path")
        }
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected mutation: $path")
}
