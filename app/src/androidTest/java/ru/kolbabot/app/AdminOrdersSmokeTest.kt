package ru.kolbabot.app

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.io.File

/** Synthetic admin data only: no production requests or mutations. */
@RunWith(AndroidJUnit4::class)
class AdminOrdersSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val orderId = "shop-copy-00042"
    private val email = "reader+copy@example.invalid"
    private val phone = "+7 (999) 123-45-67"
    private val address = "г. Москва, ул. Тестовая, д. 7\nПВЗ № 42, вход со двора"

    private fun openOrder(api: AdminOrderFakeApi) {
        compose.setContent { KolbaTheme { AdminOrdersScreen(api) {} } }
        compose.onNodeWithTag("open-order-$orderId").assertDoesNotExist()
        compose.onNodeWithText(address).assertDoesNotExist()
        compose.onNodeWithTag("expand-client-$orderId").performScrollTo().performClick()
        compose.onNodeWithTag("order-status-$orderId").assertTextEquals("Оплачен")
        compose.onNodeWithTag("open-order-$orderId").performScrollTo()
        screenshot("11-admin-orders")
        compose.onNodeWithTag("open-order-$orderId").performClick()
        compose.onNodeWithText("Получатель и доставка").assertExists()
    }

    @Test fun copyOrderEmailPhoneAndMultilineAddressWithoutChangingValues() {
        val api = AdminOrderFakeApi(context, orderId, email, phone, address)
        openOrder(api)
        listOf(orderId, email, phone, address).forEach { expected ->
            compose.onNodeWithText(expected, substring = false).performScrollTo().performClick()
            compose.runOnIdle {
                val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                assertNotNull("Clipboard was not populated", clip)
                assertEquals(expected, clip!!.getItemAt(0).text.toString())
            }
        }
        screenshot("12-admin-order-copy")
        assertTrue(api.mutations.isEmpty())
        assertTrue(api.reads.all { it.startsWith("/api/admin/store/orders?") })
    }

    @Test fun refundRequiresExplicitConfirmationAndDismissDoesNotMutate() {
        val api = AdminOrderFakeApi(context, orderId, email, phone, address)
        openOrder(api)
        compose.onNodeWithText("Отменить и вернуть оплату").performScrollTo().performClick()
        compose.onNodeWithText("Отменить заказ и вернуть оплату?").assertIsDisplayed()
        compose.onNodeWithText("Подтвердить возврат").assertIsDisplayed()
        assertTrue(api.mutations.isEmpty())
        compose.onNodeWithText("Назад", substring = false).performClick()
        compose.onNodeWithText("Подтвердить возврат").assertDoesNotExist()
        assertTrue(api.mutations.isEmpty())
    }

    @Test fun clientGroupsCollapseAndOpeningAnOrderKeepsTheListCompact() {
        val api = AdminOrderFakeApi(context, orderId, email, phone, address)
        compose.setContent { KolbaTheme { AdminOrdersScreen(api) {} } }
        compose.onNodeWithText("Оплачен").assertExists()
        compose.onNodeWithText("Тестовый товар").assertDoesNotExist()
        compose.onNodeWithText(address).assertDoesNotExist()
        compose.onNodeWithTag("expand-client-$orderId").performScrollTo().performClick()
        compose.onNodeWithTag("order-row-$orderId").performScrollTo().assertIsDisplayed()
        screenshot("admin-orders-expanded-list", "visual-review")
        compose.onNodeWithTag("expand-client-$orderId").performScrollTo().performClick()
        compose.onNodeWithTag("order-row-$orderId").assertDoesNotExist()
        screenshot("admin-orders-compact-list", "visual-review")
        assertTrue(api.mutations.isEmpty())
    }

    private fun screenshot(name: String, folder: String = "native-smoke") {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("Could not capture $name")
        val directory = File(context.getExternalFilesDir(null), folder).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}

private class AdminOrderFakeApi(context: Context, orderId: String, email: String, phone: String, address: String) : ApiClient(context, "https://admin-smoke.invalid") {
    val reads = CopyOnWriteArrayList<String>()
    val mutations = CopyOnWriteArrayList<String>()
    init { userId = 7 }
    private val order = JSONObject().put("order_id", orderId).put("user_id", 42).put("customer_name", "Тестовый Покупатель")
        .put("phone", phone).put("digital_delivery_email", email).put("address", address)
        .put("status", "paid").put("tbank_status", "CONFIRMED").put("payment_id", "987654321")
        .put("delivery_method_code", "ozon").put("delivery_method_title", "Ozon ПВЗ").put("delivery_price_kopecks", 30000)
        .put("products_total_kopecks", 120000).put("total_kopecks", 150000).put("created_at", "2026-10-15 12:34:56")
        .put("items", JSONArray().put(JSONObject().put("product_id", 101).put("title", "Тестовый товар").put("quantity", 1).put("amount_kopecks", 120000)))

    override suspend fun get(path: String): JSONObject {
        reads.add(path)
        check(path.startsWith("/api/admin/store/orders?")) { "Unexpected GET $path" }
        val rows = JSONArray().put(JSONObject(order.toString()))
        return JSONObject().put("items", rows).put("clients", JSONArray().put(JSONObject().put("user_id", 42)
            .put("customer_name", "Тестовый Покупатель").put("phone", order.getString("phone")).put("orders", rows)))
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject { mutations.add(path); throw AssertionError("Unexpected POST $path") }
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject { mutations.add(path); throw AssertionError("Unexpected PUT $path") }
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject { mutations.add(path); throw AssertionError("Unexpected PATCH $path") }
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject { mutations.add(path); throw AssertionError("Unexpected DELETE $path") }
}
