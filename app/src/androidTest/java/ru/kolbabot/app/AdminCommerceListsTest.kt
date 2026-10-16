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

    @Test fun cartSearchFindsHiddenItemsAndDetailsOpenOnlyOnRequest() {
        val api = CommerceListsApi(context)
        compose.setContent { KolbaTheme { AdminCommerceScreen(api, "carts") {} } }
        compose.onNodeWithTag("admin-entry-cart-7").assertExists()
        compose.onNodeWithText("reviewer@example.invalid").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("Соль нитритная")
        compose.onNodeWithTag("admin-entry-cart-7").assertExists()
        compose.onNodeWithText("Состав корзины").assertDoesNotExist()
        screenshot("29-admin-cart-list")
        compose.onNodeWithTag("admin-entry-cart-7").performScrollTo().performClick()
        compose.onNodeWithText("reviewer@example.invalid").assertExists()
        val itemText = hasText("Соль нитритная") and !hasSetTextAction()
        compose.onAllNodes(itemText).assertCountEquals(0)
        compose.onNodeWithTag("admin-entry-cart-items-7").performScrollTo().performClick()
        assertTrue(compose.onAllNodes(itemText).fetchSemanticsNodes().isNotEmpty())
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
        "/api/admin/store/carts?limit=500" -> JSONObject("""{"summary":{"carts_count":1,"positions_count":1,"items_quantity":2,"total_kopecks":10000,"average_kopecks":10000},"items":[{"user_id":7,"username":"Покупатель","email":"reviewer@example.invalid","positions_count":1,"items_quantity":2,"total_kopecks":10000,"total_weight_grams":200,"items":[{"product_id":42,"title":"Соль нитритная","quantity":2,"price_kopecks":5000,"amount_kopecks":10000,"weight_grams":100,"is_active":true}]}]}""")
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
