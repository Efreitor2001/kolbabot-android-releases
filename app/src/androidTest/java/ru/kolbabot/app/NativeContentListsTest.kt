package ru.kolbabot.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Exercises hidden list content and draft edits through the public API contracts, without HTTP. */
@RunWith(AndroidJUnit4::class)
class NativeContentListsTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun recipeSearchFindsFoldedCategoriesAndSlugAndRestoresTheList() {
        val api = ContentListsApi(context)
        compose.setContent { KolbaTheme { RecipesScreen(api) } }
        compose.onNodeWithTag("recipe-doctors").assertExists()
        compose.onNodeWithTag("recipe-smoked-test").assertDoesNotExist()
        screenshot("24a-recipe-categories")
        compose.onNodeWithTag("recipe-category-smoked").performScrollTo().performClick()
        compose.onNodeWithTag("recipe-doctors").assertDoesNotExist()
        compose.onNodeWithTag("recipe-smoked-test").assertExists()
        compose.onNodeWithTag("recipe-category-smoked").performClick()
        compose.onNodeWithTag("recipe-smoked-test").assertDoesNotExist()
        screenshot("24b-recipe-categories-folded")
        input("Поиск по всем рецептам", "  SMOKED-test  ")
        compose.onNodeWithTag("recipe-smoked-test").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recipe-category-cooked").assertDoesNotExist()
        // Search results still have a working +/- control.
        compose.onNodeWithTag("recipe-category-smoked").performClick()
        compose.onNodeWithTag("recipe-smoked-test").assertDoesNotExist()
        compose.onNodeWithTag("recipe-category-smoked").performClick()
        compose.onNodeWithTag("recipe-smoked-test").assertExists()
        input("Поиск по всем рецептам", "")
        compose.onNodeWithTag("recipe-category-cooked").assertExists()
        compose.onNodeWithTag("recipe-smoked-test").assertDoesNotExist()
        input("Поиск по всем рецептам", "краковская")
        screenshot("24-recipe-search")
        compose.onNodeWithTag("recipe-smoked-test").performScrollTo().performClick()
        compose.waitUntil { api.reads.contains("/api/recipes/smoked-test") }
        compose.onNodeWithText("Калькулятор").assertExists()
    }

    @Test fun handbookSearchReadsMaterialsInsideFoldedSections() {
        val api = ContentListsApi(context)
        compose.setContent { KolbaTheme { KnowledgeScreen(api, initialSection = "handbook") } }
        compose.onNodeWithTag("handbook-article-meat").assertExists()
        compose.onNodeWithTag("handbook-article-safe").assertDoesNotExist()
        screenshot("25a-handbook-sections")
        input("Поиск", "  ботулизм  ")
        compose.onNodeWithTag("handbook-section-raw").assertDoesNotExist()
        compose.onNodeWithTag("handbook-article-safe").performScrollTo().assertIsDisplayed()
        screenshot("25-handbook-list")
        compose.onNodeWithTag("handbook-article-safe").performClick()
        compose.waitUntil { api.reads.contains("/api/handbook/safety/safe") }
        compose.onNodeWithText("Безопасность продукта").assertExists()
    }

    @Test fun compactArticleRowsKeepBadgesAndOpenTheCompleteMaterial() {
        val api = ContentListsApi(context)
        compose.setContent { KolbaTheme { KnowledgeScreen(api) } }
        compose.onNodeWithTag("article-10").assertTextContains("PREMIUM").assertTextContains("NEW")
        compose.onNodeWithTag("article-11").assertExists()
        compose.onNodeWithTag("article-12").assertExists()
        screenshot("28-article-list")
        compose.onNodeWithTag("article-10").performScrollTo().performClick()
        compose.waitUntil { api.reads.contains("/api/articles/10?mark_read=1") }
        compose.onNodeWithText("Поделиться").assertExists()
        compose.onNodeWithText("Температура приготовления").assertExists()
    }

    @Test fun faqSearchOpensMatchingAnswersButTheyCanStillBeCollapsed() {
        val api = ContentListsApi(context)
        compose.setContent { KolbaTheme { KnowledgeScreen(api, initialSection = "faq") } }
        compose.onNodeWithTag("faq-answer-1").assertDoesNotExist()
        compose.onNodeWithTag("faq-question-1").performScrollTo().performClick()
        compose.onNodeWithTag("faq-answer-1").assertExists()
        compose.onNodeWithTag("faq-question-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Развёрнуто"))
        input("Поиск", "пункт выдачи")
        compose.onNodeWithTag("faq-question-1").assertDoesNotExist()
        compose.onNodeWithTag("faq-answer-2").assertExists()
        compose.onNodeWithTag("faq-question-2").performScrollTo().performClick()
        compose.onNodeWithTag("faq-answer-2").assertDoesNotExist()
        compose.onNodeWithTag("faq-question-2").performClick()
        compose.onNodeWithTag("faq-answer-2").assertExists()
        screenshot("26-faq-list")
    }

    @Test fun customIngredientRowsKeepValuesWhenCollapsedAndRevealInvalidRows() {
        val api = ContentListsApi(context)
        compose.setContent { KolbaTheme { CustomCalculatorScreen(api) } }
        input("Название", "Свинина")
        input("Количество на 1 кг", "800")
        input("Цена за 1 кг, ₽", "500")
        compose.onNodeWithText("+ Добавить").performScrollTo().performClick()
        input("Название", "Вода")
        input("Количество на 1 кг", "200")
        compose.onNode(hasText("мл") and hasClickAction()).performScrollTo().performClick()
        input("Цена за 1 л, ₽", "0")
        compose.onNodeWithTag("custom-ingredient-1").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Название")).assertTextContains("Свинина")
        compose.onNode(hasSetTextAction() and hasText("Количество на 1 кг")).assertTextContains("800")
        compose.onNode(hasSetTextAction() and hasText("Цена за 1 кг, ₽")).assertTextContains("500")
        input("Количество на 1 кг", "")
        compose.onNodeWithTag("custom-ingredient-2").performScrollTo().performClick()
        compose.onNodeWithText("Рассчитать состав").performScrollTo().performClick()
        compose.onNodeWithTag("custom-ingredient-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Развёрнуто"))
        compose.runOnIdle { assertEquals(0, api.calculations.size) }
        input("Количество на 1 кг", "800")
        compose.onNodeWithTag("custom-ingredient-1").performScrollTo().performClick()
        screenshot("27-custom-ingredient-list")
        compose.onNodeWithText("Рассчитать состав").performScrollTo().performClick()
        compose.waitUntil { api.calculations.size == 1 }
        val request = api.calculations.single()
        assertEquals(1.0, request.getDouble("weight_kg"), 0.0)
        assertEquals(listOf("Свинина | 800.0 г | 500.0", "Вода | 200.0 мл | 0.0"),
            request.getJSONArray("lines").let { lines -> (0 until lines.length()).map(lines::getString) })
        compose.onNodeWithText("Результат на 1 кг").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Сохранить или отправить PDF").assertExists()
    }

    private fun input(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("Screenshot unavailable")
        val directory = File(context.getExternalFilesDir(null), "native-smoke").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}

private class ContentListsApi(context: Context) : ApiClient(context, "https://content-lists.invalid") {
    val reads = CopyOnWriteArrayList<String>()
    val calculations = CopyOnWriteArrayList<JSONObject>()
    override suspend fun get(path: String): JSONObject {
        reads.add(path)
        return when (path) {
            "/api/categories" -> JSONObject("""{"items":[
                {"slug":"cooked","title":"Варёные колбасы","sausages_count":1,"items":[{"slug":"doctors","display_name":"Докторская"}]},
                {"slug":"smoked","title":"Полукопчёные колбасы","sausages_count":1,"items":[{"slug":"smoked-test","display_name":"Краковская"}]}
            ]}""")
            "/api/recipes/smoked-test" -> JSONObject("""{"slug":"smoked-test","display_name":"Краковская","category_name":"Полукопчёные колбасы","recipe_text":"Подготовьте ингредиенты.","recipe_photo_url":"","ingredients":[]}""")
            "/api/articles" -> JSONObject("""{"items":[
                {"id":10,"title":"Температура приготовления","excerpt":"Практические рекомендации по технологии приготовления домашней колбасы и контролю температуры внутри продукта.","is_premium":true,"is_new":true,"likes":8,"comments_count":3},
                {"id":11,"title":"Выбор оболочки","excerpt":"Натуральная или коллагеновая: особенности разных оболочек.","likes":5,"comments_count":1},
                {"id":12,"title":"Хранение продукта","excerpt":"Условия хранения готовой колбасы.","likes":3,"comments_count":0}
            ]}""")
            "/api/articles/10?mark_read=1" -> JSONObject("""{"article":{"id":10,"title":"Температура приготовления","content_html":"Полное описание материала.","author_nickname":"Колбабот","is_premium":true},"comments":[],"premium_locked":false,"can_interact":false}""")
            "/api/handbook" -> JSONObject("""{"items":[
                {"key":"raw","title":"Сырьё","articles":[{"key":"meat","title":"Мясное сырьё","text":"Выбор мяса"}]},
                {"key":"safety","title":"Безопасность","articles":[{"key":"safe","title":"Безопасность продукта","text":"Ботулизм и меры предупреждения"}]}
            ]}""")
            "/api/handbook/safety/safe" -> JSONObject("""{"title":"Безопасность продукта","text":"Ботулизм и меры предупреждения","link":""}""")
            "/api/faq" -> JSONObject("""{"items":[
                {"id":1,"question":"Как выбрать рецепт?","answer":"Откройте раздел рецептов."},
                {"id":2,"question":"Как получить заказ?","answer":"Выберите пункт выдачи при оформлении."}
            ]}""")
            else -> throw AssertionError("Unexpected GET $path")
        }
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        check(path == "/api/calc/custom") { "Unexpected POST $path" }
        calculations.add(JSONObject(body.toString()))
        return JSONObject("""{"weight_kg":1,"total_cost":400,"items":[
            {"name":"Свинина","amount_text":"800 г","cost":400,"is_text":false},
            {"name":"Вода","amount_text":"200 мл","cost":0,"is_text":false}
        ]}""")
    }
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PATCH $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")
}
