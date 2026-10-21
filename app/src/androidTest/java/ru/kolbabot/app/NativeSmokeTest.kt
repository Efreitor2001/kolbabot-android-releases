package ru.kolbabot.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
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

/** Runs native user journeys with the real HTTP contract and no production requests. */
@RunWith(AndroidJUnit4::class)
class NativeSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var api: FakeApi

    @Before fun prepare() {
        context.getSharedPreferences("store_checkout", Context.MODE_PRIVATE).edit().clear().commit()
        api = FakeApi(context)
    }

    @After fun cleanCheckoutDraft() {
        context.getSharedPreferences("store_checkout", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun catalogBannerCartAndCheckoutKeepServerContract() {
        compose.setContent { KolbaTheme { StoreScreen(api) } }
        compose.onNodeWithText("Колбаски на покупки").assertIsDisplayed()
        screenshot("01-store-catalog")
        compose.onNodeWithText("Далее ›").performScrollTo().performClick()
        compose.onNodeWithText("Пригласите друга").assertIsDisplayed()
        compose.onNodeWithText("В корзину").performScrollTo().performClick()
        compose.waitUntil { api.requests.any { it.method == "PUT" && it.path == "/api/store/cart" } }
        val cartWrite = api.requests.last { it.method == "PUT" && it.path == "/api/store/cart" }
        assertEquals(101L, cartWrite.body.getJSONArray("items").getJSONObject(0).getLong("product_id"))
        assertEquals(1, cartWrite.body.getJSONArray("items").getJSONObject(0).getInt("quantity"))
        compose.onNode(hasText("Корзина") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("1 шт.").assertExists()
        screenshot("02-store-cart")
        compose.onNodeWithTag("storeInlineCart").assertExists()
        compose.onNodeWithText("Электронная доставка: бесплатно").assertExists()
        compose.onNodeWithText("Оформить заказ").performScrollTo().assertIsNotEnabled()
        input("ФИО", "Тестовый Покупатель")
        input("Телефон", "+79991234567")
        input("E-mail для доставки цифровых товаров", "native-test@example.invalid")
        compose.onNodeWithText("Принимаю публичную оферту").performScrollTo().performClick()
        compose.onNodeWithText("Согласен на обработку персональных данных для доставки").performScrollTo().performClick()
        compose.onNodeWithText("Мне исполнилось 18 лет").performScrollTo().performClick()
        compose.onNodeWithText("Оформить заказ").performScrollTo().assertIsEnabled()
        screenshot("03-checkout-confirmation")
        compose.onNodeWithText("Оформить заказ").performClick()
        compose.waitUntil { api.requests.any { it.path == "/api/store/checkout" } }
        val checkout = api.requests.single { it.path == "/api/store/checkout" }
        assertEquals("digital", checkout.body.getString("delivery_method"))
        assertEquals("native-test@example.invalid", checkout.body.getString("digital_delivery_email"))
        assertTrue(checkout.body.getBoolean("accept_public_offer"))
        assertTrue(checkout.body.getBoolean("accept_privacy_policy"))
        assertTrue(checkout.body.getBoolean("accept_age_18"))
        assertFalse(checkout.headers["Idempotency-Key"].isNullOrBlank())
        compose.onNodeWithText("Сохранённая попытка заказа").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Перейти к оплате").assertExists()
        screenshot("04-checkout-created")
    }

    @Test fun recipeChoiceAndWeightAreSentToServerCalculator() {
        compose.setContent { KolbaTheme { RecipesScreen(api) } }
        compose.onNodeWithTag("site-select-Категория").assertExists()
        compose.onNodeWithTag("site-select-Сорт").assertTextContains("Докторская тестовая")
        compose.onNodeWithTag("site-select-Вариант ингредиента").performScrollTo().performClick()
        compose.onNodeWithText("Говядина", useUnmergedTree = false).performClick()
        compose.onNodeWithTag("site-select-Вариант ингредиента").assertTextContains("Говядина")
        val savedChoice = api.requests.single { it.path == "/api/recipes/doktorskaya-test/choices" }
        assertEquals("beef", savedChoice.body.getJSONObject("choices").getString("Мясное сырьё"))
        assertTrue(api.requests.none { it.path == "/api/calc/recipe" })
        input("Вес партии (кг)", "2")
        compose.onNodeWithText("Рассчитать").performScrollTo().performClick()
        compose.waitUntil { api.requests.any { it.path == "/api/calc/recipe" } }
        val request = api.requests.single { it.path == "/api/calc/recipe" }
        assertEquals("doktorskaya-test", request.body.getString("sausage_slug"))
        assertEquals(2, request.body.getInt("weight_kg"))
        assertEquals("beef", request.body.getJSONObject("choices").getString("Мясное сырьё"))
        compose.onNodeWithText("Результат на 2 кг").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 кг 400 гр").assertExists()
        compose.onNodeWithText("Распечатать PDF").assertExists()
        screenshot("05-recipe-calculation")
        compose.onNodeWithTag("site-select-Вариант ингредиента").performScrollTo().performClick()
        compose.onNodeWithText("Свинина", useUnmergedTree = false).performClick()
        compose.waitUntil { api.requests.count { it.path == "/api/calc/recipe" } == 2 }
        val recalculated = api.requests.last { it.path == "/api/calc/recipe" }
        assertEquals("pork", recalculated.body.getJSONObject("choices").getString("Мясное сырьё"))
        assertEquals(2, recalculated.body.getInt("weight_kg"))
    }

    @Test fun loginRequiresSecondFactorBeforeAuthenticating() {
        api.requireSecondFactor = true
        var signedIn: JSONObject? = null
        compose.setContent { KolbaTheme { AuthScreen(api) { signedIn = it } } }
        input("Логин", "recipe-reader")
        input("Пароль", "smoke-test-password")
        screenshot("06-login")
        compose.onNodeWithText("Войти", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Код 2FA или резервный код").assertExists()
        compose.runOnIdle { assertNull(signedIn) }
        val login = api.requests.single { it.path == "/api/auth/login" }
        assertEquals("recipe-reader", login.body.getString("username"))
        assertEquals("smoke-test-password", login.body.getString("password"))
        input("Код 2FA или резервный код", "123456")
        compose.onNodeWithText("Подтвердить").performScrollTo().performClick()
        compose.waitUntil { signedIn != null }
        val secondFactor = api.requests.single { it.path == "/api/auth/login/2fa" }
        assertEquals("smoke-challenge", secondFactor.body.getString("challenge_token"))
        assertEquals("123456", secondFactor.body.getString("code"))
        compose.runOnIdle { assertEquals(7L, signedIn!!.getLong("id")) }
    }

    @Test fun bannerEditPreservesOtherBannersAndRevision() {
        compose.setContent { KolbaTheme { AdminBannersScreen(api) } }
        compose.onNodeWithText("1. Колбаски на покупки").assertExists()
        compose.onNodeWithText("Сохранить", substring = false).assertIsNotEnabled()
        compose.onAllNodesWithText("Редактировать").onFirst().performScrollTo().performClick()
        input("Заголовок", "Сезонные предложения")
        screenshot("07-banner-editor")
        compose.onNodeWithText("Сохранить", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Баннеры сохранены для сайта и приложения.").assertExists()
        val writes = api.requests.filter { it.method == "PUT" && it.path == "/api/admin/store/banners" }
        assertEquals(1, writes.size)
        assertEquals("rev-1", writes.single().body.getString("revision"))
        val banners = writes.single().body.getJSONArray("banners")
        assertEquals(3, banners.length())
        assertEquals("Сезонные предложения", banners.getJSONObject(0).getString("title"))
        assertEquals("sausages", banners.getJSONObject(0).getString("kind"))
        assertTrue(banners.getJSONObject(0).getString("text").contains("{standard_percent}"))
        assertEquals("referral", banners.getJSONObject(1).getString("id"))
        assertEquals("review-bonus", banners.getJSONObject(2).getString("id"))
        val firstWriteIndex = api.requests.indexOf(writes.single())
        assertTrue(api.requests.take(firstWriteIndex).any { it.method == "GET" && it.path == "/api/admin/store/banners" })
        compose.onNodeWithText("Сохранить", substring = false).assertIsNotEnabled()
    }

    @Test fun staleBannerRevisionKeepsDraftAndNeverOverwritesServer() {
        api.conflictOnBannerSave = true
        compose.setContent { KolbaTheme { AdminBannersScreen(api) } }
        compose.onAllNodesWithText("Редактировать").onFirst().performScrollTo().performClick()
        input("Заголовок", "Мой черновик")
        compose.onNodeWithText("Сохранить", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Список изменён другим администратором").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1. Мой черновик").assertExists()
        compose.onNodeWithText("Сохранить", substring = false).assertIsNotEnabled()
        assertEquals(1, api.requests.count { it.method == "PUT" && it.path == "/api/admin/store/banners" })
        assertEquals("Изменено на сайте", api.adminBanners.getJSONObject(0).getString("title"))
        screenshot("08-banner-conflict")
        compose.onNodeWithText("Загрузить свежую версию").performScrollTo().performClick()
        compose.onNodeWithText("Загрузить", substring = false).performClick()
        compose.onNodeWithText("1. Изменено на сайте").assertExists()
        compose.onNodeWithText("1. Мой черновик").assertDoesNotExist()
        compose.onNodeWithText("Сохранить", substring = false).assertIsNotEnabled()
        assertEquals(1, api.requests.count { it.method == "PUT" && it.path == "/api/admin/store/banners" })
    }

    @Test fun wholeApplicationKeepsNavigationAndBannerDraft() {
        compose.setContent { KolbaTheme { KolbaApp(api, enablePushStartup = false) } }
        compose.onNodeWithText("Колбаски на покупки").assertExists()
        compose.onNodeWithTag("site-bottom-account").assertIsDisplayed()
        screenshot("09-full-app-catalog")
        compose.onNodeWithTag("site-bottom-account").performClick()
        compose.onNodeWithText("Рады вас видеть, Тестовый профиль").assertExists()
        compose.onNodeWithText("Редактор баннеров").assertDoesNotExist()
        compose.onNodeWithTag("site-bottom-more").performClick()
        compose.onNodeWithTag("site-more-admin").performScrollTo().performClick()
        compose.onNodeWithText("Добрый день, Тестовый профиль").assertExists()
        screenshot("11-full-app-admin")
        compose.onNodeWithTag("admin-tab-store").performScrollTo().performClick()
        compose.onNodeWithText("Баннеры", substring = false).performScrollTo().performClick()
        compose.onAllNodesWithText("Редактировать").onFirst().performScrollTo().performClick()
        input("Заголовок", "Черновик после переключения")
        compose.onNodeWithText("Есть несохранённые изменения").assertExists()
        closeSoftKeyboard()
        compose.onNodeWithTag("site-bottom-store").assertDoesNotExist()
        // The website editor tabs stay available while editing; the draft survives section changes.
        compose.onNodeWithTag("admin-tab-store").performScrollTo().performClick()
        pressBack()
        compose.onNodeWithText("Добрый день, Тестовый профиль").assertExists()
        pressBack()
        compose.onNodeWithTag("site-bottom-account").assertIsSelected()
        compose.onNodeWithTag("site-bottom-store").performClick()
        compose.onNodeWithText("Колбаски на покупки").assertExists()
        compose.onNodeWithTag("site-bottom-more").performClick()
        compose.onNodeWithTag("site-more-admin").performScrollTo().performClick()
        compose.onNodeWithTag("admin-tab-store").performScrollTo().performClick()
        compose.onNodeWithText("Баннеры", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Баннеры магазина").assertExists()
        compose.onNodeWithText("1. Черновик после переключения").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Заголовок")).assertTextContains("Черновик после переключения")
        compose.onNodeWithText("Есть несохранённые изменения").assertExists()
        assertTrue(api.requests.none { it.method == "PUT" && it.path == "/api/admin/store/banners" })
        assertEquals("Колбаски на покупки", api.adminBanners.getJSONObject(0).getString("title"))
        screenshot("10-full-app-editor")
    }

    private fun input(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        awaitPresentedUiFrame(instrumentation)
        val image = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("Could not capture $name")
        val directory = File(context.getExternalFilesDir(null), "native-smoke").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}

private data class RecordedRequest(val method: String, val path: String, val body: JSONObject, val headers: Map<String, String>)

/** Fixtures mirror app.py, calculator.py, and store_banners.py; no HTTP transport is used. */
private class FakeApi(context: Context) : ApiClient(context, "https://native-smoke.invalid") {
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    var requireSecondFactor = false
    var conflictOnBannerSave = false
    var revision = "rev-1"
    var cart = JSONArray()
    var adminBanners = JSONArray("""[
      {"id":"sausages","kind":"sausages","title":"Колбаски на покупки","text":"Возвращаем {standard_percent}%, с PREMIUM — {premium_percent}%.","image_url":"","link_url":"","link_text":"Подробнее","is_active":true,"sort_order":10},
      {"id":"referral","kind":"referral","title":"Пригласите друга","text":"Бонус за первую покупку друга.","image_url":"","link_url":"","link_text":"Подробнее","is_active":true,"sort_order":20},
      {"id":"review-bonus","kind":"review-bonus","title":"Колбаски за отзывы","text":"{review_bonus_sausages} колбасок за содержательный отзыв.","image_url":"","link_url":"","link_text":"Подробнее","is_active":true,"sort_order":30}
    ]""")

    init { userId = 7L }

    override suspend fun getCart(expectedUserId: Long): JSONObject {
        check(userId == expectedUserId)
        return get("/api/store/cart")
    }
    override suspend fun putCart(expectedUserId: Long, body: JSONObject): JSONObject {
        check(userId == expectedUserId)
        return put("/api/store/cart", body)
    }

    private fun record(method: String, path: String, body: JSONObject = JSONObject(), headers: Map<String, String> = emptyMap()) {
        requests += RecordedRequest(method, path, JSONObject(body.toString()), headers.toMap())
    }

    override suspend fun get(path: String): JSONObject {
        record("GET", path)
        return when (path) {
            "/api/auth/google/config", "/api/auth/vk/config" -> JSONObject().put("enabled", false)
            "/api/android/push/config" -> JSONObject().put("enabled", false)
            "/api/access/status" -> JSONObject().put("can_use_app", true)
            "/api/bootstrap" -> JSONObject("""{"categories_count":1,"equipment_count":0,"faq_count":0,"handbook_sections":[],"payments":{"plans":[]}}""")
            "/api/account/billing" -> JSONObject("""{"status":"none","tier":"free","autopay_enabled":false}""")
            "/api/admin/dashboard" -> JSONObject("""{"kpi":{},"recent_orders":[],"sales_daily":[],"open_tickets":[],"latest_users":[]}""")
            "/api/auth/me" -> JSONObject("""{"user":{"id":7,"username":"Тестовый профиль","is_admin":true,"requires_pd_consent":false},"csrf_token":"smoke-csrf-token"}""")
            "/api/store", "/api/store?include_inactive=1" -> catalog()
            "/api/store/cart" -> cartResponse()
            "/api/categories" -> JSONObject("""{"items":[{"slug":"cooked","title":"Варёные колбасы","sausages_count":1,"items":[{"display_name":"Докторская тестовая","slug":"doktorskaya-test"}]}]}""")
            "/api/recipes/doktorskaya-test" -> JSONObject("""{
              "slug":"doktorskaya-test","display_name":"Докторская тестовая","category_slug":"cooked","category_name":"Варёные колбасы","recipe_text":"Подготовьте сырьё и следуйте технологии рецепта.","recipe_photo_url":"",
              "ingredients":[{"full_name":"Мясное сырьё","value":700,"is_numeric":true,"amount_unit":"g","short_key":"Указать цену за 1кг мяса","link":"","product_id":0,"selected_option_key":"pork","options":[
                {"option_key":"pork","full_name":"Свинина","value":700,"is_numeric":true,"amount_unit":"g","short_key":"Указать цену за 1кг свинины","link":"","product_id":0},
                {"option_key":"beef","full_name":"Говядина","value":700,"is_numeric":true,"amount_unit":"g","short_key":"Указать цену за 1кг говядины","link":"","product_id":0}
              ]}]
            }""")
            "/api/admin/store/banners" -> JSONObject().put("revision", revision).put("banners", JSONArray(adminBanners.toString()))
            else -> throw AssertionError("Unexpected GET $path")
        }
    }

    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        record("POST", path, body, headers)
        return when (path) {
            "/api/auth/login" -> if (requireSecondFactor) JSONObject("""{"requires_2fa":true,"challenge_token":"smoke-challenge"}""") else JSONObject("""{"user":{"id":7,"username":"recipe-reader","is_admin":false}}""")
            "/api/auth/login/2fa" -> JSONObject("""{"user":{"id":7,"username":"recipe-reader","is_admin":false}}""")
            "/api/recipes/doktorskaya-test/choices" -> JSONObject().put("slug", "doktorskaya-test").put("choices", JSONObject(body.getJSONObject("choices").toString()))
            "/api/calc/recipe" -> JSONObject("""{
              "sausage_slug":"doktorskaya-test","sausage_name":"Докторская тестовая","weight_kg":2,"total_cost":512.0,
              "items":[{"full_name":"Говядина","ingredient_full_name":"Мясное сырьё","selected_option_key":"beef","short_key":"Указать цену за 1кг говядины","is_text":false,"quantity_per_kg":700,"amount_value":1400,"amount_text":"1 кг 400 гр","amount_unit":"g","price_unit":"kg","unit_price":365.71,"cost":512.0,"link":"","product_id":0}]
            }""")
            "/api/store/checkout" -> JSONObject("""{"order_id":"smoke-order-1","payment_url":"https://payment.example.invalid/order/smoke-order-1","order":{"order_id":"smoke-order-1","status":"new","total_kopecks":99000,"items":[{"title":"Книга рецептов","quantity":1,"amount_kopecks":99000}]}}""")
            else -> throw AssertionError("Unexpected POST $path")
        }
    }

    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        record("PUT", path, body, headers)
        return when (path) {
            "/api/store/cart" -> {
                cart = JSONArray(body.getJSONArray("items").toString())
                cartResponse()
            }
            "/api/admin/store/banners" -> {
                if (conflictOnBannerSave) {
                    revision = "rev-other-admin"
                    adminBanners.getJSONObject(0).put("title", "Изменено на сайте")
                    throw ApiException(409, "Баннеры уже изменены другим администратором.")
                }
                assertEquals("rev-1", body.getString("revision"))
                adminBanners = JSONArray(body.getJSONArray("banners").toString())
                revision = "rev-2"
                JSONObject().put("revision", revision).put("banners", JSONArray(adminBanners.toString()))
            }
            else -> throw AssertionError("Unexpected PUT $path")
        }
    }

    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PATCH $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")

    private fun cartResponse(): JSONObject {
        val rows = JSONArray()
        for (index in 0 until cart.length()) {
            val item = cart.getJSONObject(index)
            rows.put(JSONObject().put("product_id", item.getLong("product_id")).put("quantity", item.getInt("quantity"))
                .put("title", "Книга рецептов").put("photo_url", "").put("price_kopecks", 99000).put("weight_grams", 0))
        }
        return JSONObject().put("items", rows).put("user_id", 7).put("premium_active", false).put("rewards", JSONObject().put("sausages_kopecks", 0))
    }

    private fun catalog(): JSONObject = JSONObject("""{
      "user_id":7,"premium_active":false,"premium_discount_percent":0,
      "products":[{"id":101,"title":"Книга рецептов","photo_url":"","description":"Практические рецептуры","price_kopecks":99000,"base_price_kopecks":99000,"product_type":"digital_book","category_id":"books","category_ids":["books"],"variant_label":"PDF","weight_grams":0,"rating_average":0,"review_count":0}],
      "categories":[{"id":"books","title":"Электронные книги"}],"delivery_methods":[],"delivery_sizes":[],
      "banners":[{"id":"sausages","kind":"sausages","title":"Колбаски на покупки","text":"Возвращаем 5% колбасками.","image_url":"","link_url":"","link_text":"","is_active":true,"sort_order":10},{"id":"referral","kind":"referral","title":"Пригласите друга","text":"Бонус за первую покупку друга.","image_url":"","link_url":"","link_text":"","is_active":true,"sort_order":20}],
      "promo_settings":{"sausages":{"is_active":1,"standard_percent":5,"premium_percent":10,"max_redemption_percent":50}},
      "payments":{"enabled":true,"test_mode":true},"ozon_delivery":{"enabled":false,"checkout_enabled":false},"rewards":{"sausages_kopecks":0}
    }""")
}
