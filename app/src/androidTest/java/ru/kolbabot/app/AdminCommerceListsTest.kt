package ru.kolbabot.app

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
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class AdminCommerceListsTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun customerCartOpensItsProductListWithoutRawJsonOrANestedAccordion() {
        val api = CommerceListsApi(context)
        compose.setContent { KolbaTheme { AdminCommerceScreen(api, "carts") {} } }
        compose.onNodeWithText("Корзин: 1").assertExists()
        compose.onNodeWithTag("admin-entry-cart-7").assertTextContains("reviewer@example.invalid", substring = true)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Соль нитритная · 100 г").assertDoesNotExist()
        screenshot("29-admin-cart-list")
        compose.onNodeWithTag("admin-entry-cart-7").performScrollTo().performClick()
        compose.onNodeWithTag("admin-entry-cart-items-7").assertDoesNotExist()
        compose.onNodeWithText("Обновлена: 16.10.2026 12:30 МСК").assertExists()
        compose.onNodeWithText("Соль нитритная · 100 г").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Соль нитритная · 500 г").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 шт. × ${money(5000)} — ${money(10000)}").assertExists()
        compose.onNodeWithText("1 шт. × ${money(18000)} — ${money(18000)}").assertExists()
        compose.onNodeWithText("Итого: ${money(28000)}").assertExists()
        compose.onNodeWithText("Товар скрыт").assertExists()
        listOf("product_id", "photo_path", "user_is_active", "variant_group", "debug_metadata", "private-system-sentinel", "updated_at", "price_kopecks", "is_active").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
        screenshot("29b-admin-cart-readable-items")
        assertTrue(api.writes.isEmpty())
    }

    @Test fun reviewModerationKeepsConfirmationAfterOpeningTheRow() {
        val api = CommerceListsApi(context)
        compose.setContent { KolbaTheme { AdminCommerceScreen(api, "reviews") {} } }
        compose.onNodeWithText(CommerceListsApi.REVIEW).assertDoesNotExist()
        compose.onNodeWithText("Удалить отзыв").assertDoesNotExist()
        screenshot("30-admin-review-list")
        compose.onNodeWithTag("admin-entry-review-8").performScrollTo().performClick()
        compose.onNodeWithText(CommerceListsApi.REVIEW).assertExists()
        compose.onNodeWithText("Удалить отзыв").performScrollTo().performClick()
        compose.onNodeWithText("Удалить отзыв?").assertIsDisplayed()
        assertTrue(api.writes.isEmpty())
        compose.onNodeWithText("Отмена").performClick()
        assertTrue(api.writes.isEmpty())
        compose.onNodeWithText("Удалить отзыв").performClick()
        compose.onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil { api.writes.contains("DELETE /api/admin/store/reviews/8") }
        compose.onNodeWithTag("admin-entry-review-8").assertDoesNotExist()
    }

    @Test fun digitalRetryRequiresOpeningTheRecordAndConfirmingTheRecipient() {
        val api = CommerceListsApi(context)
        compose.setContent { KolbaTheme { AdminCommerceScreen(api, "digital") {} } }
        compose.onNodeWithText("Требуют внимания").performScrollTo().performClick()
        compose.onNodeWithTag("admin-entry-sale-9").assertExists()
        compose.onNodeWithText("Повторить отправку").assertDoesNotExist()
        compose.onNodeWithText("Почтовый сервис недоступен").assertDoesNotExist()
        screenshot("31-admin-digital-list")
        compose.onNodeWithTag("admin-entry-sale-9").performScrollTo().performClick()
        compose.onNodeWithText("Почтовый сервис недоступен").assertExists()
        compose.onNodeWithText("Повторить отправку").performScrollTo().performClick()
        compose.onNodeWithText("Повторить отправку книги?").assertIsDisplayed()
        assertTrue(api.writes.isEmpty())
        compose.onNode(hasText("Отправить") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil { api.writes.contains("POST /api/admin/store/digital-sales/9/retry") }
        compose.onNodeWithText("Отправка подтверждена сервером.").assertExists()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("Screenshot unavailable")
        val directory = File(context.getExternalFilesDir(null), "native-smoke").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}

private class CommerceListsApi(context: Context) : ApiClient(context, "https://commerce-lists.invalid") {
    val writes = CopyOnWriteArrayList<String>()
    private var deleted = false
    private var retried = false
    override suspend fun get(path: String): JSONObject = when (path) {
        "/api/admin/store/carts?limit=500" -> JSONObject("""{
          "summary":{"carts_count":1,"positions_count":2,"items_quantity":3,"total_kopecks":28000,"average_kopecks":28000},
          "items":[{"user_id":7,"username":"Покупатель","email":"reviewer@example.invalid","user_is_active":true,
            "updated_at":"2026-10-16 12:30:00","positions_count":2,"items_quantity":3,"total_kopecks":28000,"total_weight_grams":700,
            "debug_metadata":{"source":"private-system-sentinel"},"items":[
              {"product_id":42,"title":"Соль нитритная","variant_label":"100 г","variant_group":"nitrite-salt","quantity":2,"price_kopecks":5000,"amount_kopecks":10000,"weight_grams":100,"is_active":true,"photo_path":"private-system-sentinel"},
              {"product_id":43,"title":"Соль нитритная","variant_label":"500 г","variant_group":"nitrite-salt","quantity":1,"price_kopecks":18000,"amount_kopecks":18000,"weight_grams":500,"is_active":false,"variant":{"label":"500 г","metadata":{"debug":"private-system-sentinel"}}}
            ]}]}""")
        "/api/admin/store/reviews" -> JSONObject().put("items", if (deleted) JSONArray() else JSONArray().put(JSONObject()
            .put("id", 8).put("product_id", 42).put("product_title", "Соль нитритная").put("user_id", 7)
            .put("user_nickname", "Покупатель").put("rating", 5).put("review_text", REVIEW).put("created_at", "2026-10-15")))
        "/api/admin/store/digital-sales?status=delivered&limit=1000" -> JSONObject().put("items", JSONArray()).put("summary", JSONArray()).put("total_quantity", 0)
        "/api/admin/store/digital-sales?status=attention&limit=1000" -> JSONObject().put("summary", JSONArray()).put("total_quantity", 1)
            .put("items", if (retried) JSONArray() else JSONArray("""[{"id":9,"product_title":"Книга рецептов","order_id":"order-9","instance_id":"book-copy-9","customer_name":"Покупатель","delivery_email":"reader@example.invalid","status":"failed","error_text":"Почтовый сервис недоступен"}]"""))
        else -> throw AssertionError("Unexpected GET $path")
    }
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        check(path == "/api/admin/store/reviews/8")
        deleted = true; writes.add("DELETE $path")
        return JSONObject().put("deleted", true)
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        check(path == "/api/admin/store/digital-sales/9/retry")
        retried = true; writes.add("POST $path")
        return JSONObject().put("delivered", true)
    }
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PATCH $path")
    companion object { const val REVIEW = "Полный текст отзыва покупателя: товар получен, упаковка целая, всё соответствует описанию." }
}
