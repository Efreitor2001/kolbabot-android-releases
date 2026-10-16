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
class NativeContentAdminTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun catalogSaveRetainsAllThreeDropdownSelectionsThroughReload() {
        val api = CatalogSelectionApi(context)
        compose.setContent { KolbaTheme { AdminContentScreen(api, "catalog") {} } }
        compose.waitUntil { api.finishedLoads >= 1 }
        compose.onNodeWithTag("site-select-Категория").performScrollTo().performClick()
        compose.onNodeWithTag("site-option-second").performClick()
        compose.onNodeWithTag("site-select-Категория для сорта").performScrollTo().performClick()
        compose.onNodeWithTag("site-option-second").performClick()
        compose.onNodeWithTag("site-select-Сорт").performScrollTo().performClick()
        compose.onNodeWithTag("site-option-second-b").performClick()
        compose.onNode(hasSetTextAction() and hasText("Название категории")).performScrollTo().performTextReplacement("Категория после правки")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onAllNodesWithText("Сохранить", substring = false).onFirst().performScrollTo().performClick()
        compose.waitUntil { api.finishedLoads >= 2 }
        compose.onNodeWithTag("site-select-Категория").assertTextContains("Категория после правки")
        compose.onNodeWithTag("site-select-Категория для сорта").assertTextContains("Категория после правки")
        compose.onNodeWithTag("site-select-Сорт").assertTextContains("Второй сорт")
        compose.onNode(hasSetTextAction() and hasText("Название сорта")).performScrollTo().performTextReplacement("Сорт после правки")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onAllNodesWithText("Сохранить", substring = false).onLast().performScrollTo().performClick()
        compose.waitUntil { api.finishedLoads >= 3 }
        compose.onNodeWithTag("site-select-Категория").assertTextContains("Категория после правки")
        compose.onNodeWithTag("site-select-Категория для сорта").assertTextContains("Категория после правки")
        compose.onNodeWithTag("site-select-Сорт").assertTextContains("Сорт после правки")
        assertEquals(listOf("/api/admin/categories/second", "/api/admin/sausages"), api.writes.map { it.first })
        assertEquals("second", api.writes.last().second.getString("category_slug"))
        assertEquals("second-b", api.writes.last().second.getString("old_sausage_slug"))
    }

    @Test fun recipeEditorPreservesAlternativesAndProductBindings() {
        val api = ContentAdminFakeApi(context)
        compose.setContent { KolbaTheme { AdminContentScreen(api, "recipes") {} } }
        compose.onNodeWithTag("site-select-Категория").assertExists()
        compose.onNodeWithTag("site-select-Сорт").assertTextContains("Рецепт с вариантами")
        compose.onNodeWithText("Изменить", substring = false).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction() and hasText("Количество варианта на 1 кг")).onFirst().performScrollTo().performTextReplacement("650")
        compose.onNodeWithText("Применить к рецепту").performScrollTo().performClick()
        compose.onNodeWithText("Сохранить рецепт").performScrollTo().performClick()
        compose.onNodeWithText("Изменения сохранены.").assertExists()
        val body = api.writes.single { it.first == "PUT /api/admin/recipes/test-recipe" }.second
        assertTrue(body.getBoolean("replace_ingredients"))
        assertFalse(body.getBoolean("create_recipe"))
        assertEquals("telegram-photo-42", body.getString("photo_id"))
        val ingredient = body.getJSONArray("ingredients").getJSONObject(0)
        val options = ingredient.getJSONArray("options")
        assertEquals(2, options.length())
        assertEquals("pork", options.getJSONObject(0).getString("option_key"))
        assertEquals("650", options.getJSONObject(0).getString("quantity"))
        assertEquals(101L, options.getJSONObject(0).getLong("product_id"))
        assertEquals("beef", options.getJSONObject(1).getString("option_key"))
        assertEquals("700", options.getJSONObject(1).getString("quantity"))
        assertEquals("Цена говядины", options.getJSONObject(1).getString("short_label"))
    }

    @Test fun articleTitleChangeKeepsExistingFormattedBodyAndPublicationFlags() {
        val api = ContentAdminFakeApi(context)
        compose.setContent { KolbaTheme { AdminContentScreen(api, "articles") {} } }
        compose.onNodeWithTag("site-select-Статья").assertTextContains("Исходный заголовок")
        compose.onNode(hasSetTextAction() and hasText("Заголовок")).performScrollTo().performTextReplacement("Уточнённый заголовок")
        compose.onNodeWithText("Сохранить статью").performScrollTo().performClick()
        compose.onNodeWithText("Изменения сохранены.").assertExists()
        val body = api.writes.single { it.first == "PUT /api/admin/articles/9" }.second
        assertEquals("Уточнённый заголовок", body.getString("title"))
        assertEquals(ContentAdminFakeApi.ARTICLE_BODY, body.getString("content_html"))
        assertTrue(body.getBoolean("is_premium"))
        assertFalse(body.getBoolean("is_published"))
        screenshot("12-admin-articles")
    }

    @Test fun faqEditUsesPatchAndDeleteRequiresConfirmation() {
        val api = ContentAdminFakeApi(context)
        compose.setContent { KolbaTheme { AdminContentScreen(api, "faq") {} } }
        compose.onNodeWithTag("site-select-Запись FAQ").assertTextContains("Как хранить соль?")
        compose.onNode(hasSetTextAction() and hasText("Вопрос *")).performScrollTo().performTextReplacement("Как хранить специи?")
        compose.onNode(hasSetTextAction() and hasText("Ответ *")).performScrollTo().performTextReplacement("В сухом закрытом шкафу.")
        compose.onNodeWithText("Сохранить", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("site-select-Запись FAQ").assertTextContains("Как хранить специи?")
        val body = api.writes.single { it.first == "PATCH /api/admin/faq/4" }.second
        assertEquals("Как хранить специи?", body.getString("question"))
        assertEquals("В сухом закрытом шкафу.", body.getString("answer"))
        compose.onNodeWithText("Удалить", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Подтвердите удаление").assertIsDisplayed()
        assertTrue(api.writes.none { it.first.startsWith("DELETE") })
        compose.onNodeWithText("Отмена").performClick()
        assertTrue(api.writes.none { it.first.startsWith("DELETE") })
        compose.onNodeWithText("Удалить", substring = false).performScrollTo().performClick()
        compose.onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil { api.writes.any { it.first == "DELETE /api/admin/faq/4" } }
        compose.onNodeWithText("Как хранить специи?").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(context.getExternalFilesDir(null), "native-smoke").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}

private class ContentAdminFakeApi(context: Context) : ApiClient(context, "https://native-content.invalid") {
    val writes = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private var question = "Как хранить соль?"
    private var answer = "В сухом месте."
    private var faqExists = true
    init { userId = 7 }

    override suspend fun get(path: String): JSONObject = when (path) {
        "/api/admin/bootstrap" -> JSONObject().put("categories", JSONArray("""[{"slug":"cooked","title":"Варёные","sausages":[{"slug":"test-recipe","display_name":"Рецепт с вариантами"}]}]"""))
            .put("store_products", JSONArray("""[{"id":101,"title":"Свинина","price_kopecks":35000}]"""))
            .put("faq", if (faqExists) JSONArray().put(JSONObject().put("id", 4).put("question", question).put("answer", answer)) else JSONArray())
            .put("articles", JSONArray().put(JSONObject().put("id", 9).put("slug", "test-article").put("title", "Исходный заголовок").put("excerpt", "Краткое описание")
                .put("content_html", ARTICLE_BODY).put("cover_image_path", "").put("card_image_path", "").put("is_premium", true).put("is_published", false).put("comments_count", 0)))
            .put("handbook_sections", JSONArray()).put("equipment", JSONArray())
        "/api/recipes/test-recipe" -> JSONObject("""{
          "slug":"test-recipe","display_name":"Рецепт с вариантами","category_slug":"cooked","category_name":"Варёные","recipe_text":"Подготовить фарш.","recipe_photo_path":"","recipe_photo_url":"","recipe_photo_id":"telegram-photo-42",
          "ingredients":[{"full_name":"Мясо","value":700,"short_key":"Цена мяса","product_id":0,"options":[
            {"option_key":"pork","full_name":"Свинина","value":700,"short_key":"Цена свинины","product_id":101,"link":""},
            {"option_key":"beef","full_name":"Говядина","value":700,"short_key":"Цена говядины","product_id":0,"link":""}
          ]}]
        }""")
        else -> throw AssertionError("Unexpected GET $path")
    }

    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        require(path == "/api/admin/recipes/test-recipe" || path == "/api/admin/articles/9")
        writes += "PUT $path" to JSONObject(body.toString())
        return JSONObject().put("saved", true)
    }

    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        require(path == "/api/admin/faq/4")
        writes += "PATCH $path" to JSONObject(body.toString())
        question = body.getString("question"); answer = body.getString("answer")
        return JSONObject().put("id", 4).put("question", question).put("answer", answer)
    }

    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        require(path == "/api/admin/faq/4")
        writes += "DELETE $path" to JSONObject(body.toString())
        faqExists = false
        return JSONObject().put("deleted", true)
    }

    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected POST $path")

    companion object {
        const val ARTICLE_BODY = "<h2>Подготовка</h2><p>Сохранить <strong>форматирование</strong> и <a href=\"https://example.invalid/guide\">ссылку</a>.</p>"
    }
}

private class CatalogSelectionApi(context: Context) : ApiClient(context, "https://catalog-selection.invalid") {
    @Volatile var finishedLoads = 0
    val writes = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private var categoryTitle = "Вторая категория"
    private var sausageTitle = "Второй сорт"
    override suspend fun get(path: String): JSONObject {
        check(path == "/api/admin/bootstrap")
        // Force an actual loading composition, rather than a synchronous fake masking disposal.
        kotlinx.coroutines.delay(80)
        val categories = JSONArray().put(JSONObject().put("slug", "first").put("title", "Первая категория").put("sausages", JSONArray()))
            .put(JSONObject().put("slug", "second").put("title", categoryTitle).put("sausages", JSONArray()
                .put(JSONObject().put("slug", "second-a").put("display_name", "Первый сорт"))
                .put(JSONObject().put("slug", "second-b").put("display_name", sausageTitle))))
        finishedLoads++
        return JSONObject().put("categories", categories)
    }
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        when (path) {
            "/api/admin/categories/second" -> categoryTitle = body.getString("title")
            "/api/admin/sausages" -> { check(body.getString("old_sausage_slug") == "second-b"); sausageTitle = body.getString("new_display_name") }
            else -> throw AssertionError("Unexpected PATCH $path")
        }
        writes += path to JSONObject(body.toString())
        return JSONObject().put("ok", true)
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected POST $path")
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")
}
