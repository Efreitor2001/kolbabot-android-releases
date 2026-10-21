package ru.kolbabot.app

import android.content.Context
import android.graphics.Bitmap
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

/** Native storefront controls with an in-memory API; no real orders or requests. */
@RunWith(AndroidJUnit4::class)
class StoreCartActionSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var api: CartActionApi

    @Before fun setup() {
        clear()
        api = CartActionApi(context)
    }

    @After fun clear() {
        context.getSharedPreferences("guest_cart", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("store_checkout", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun catalogSwitchesToEditableStepperAndKeepsVariantsSeparate() {
        compose.setContent { KolbaTheme { StoreScreen(api) } }
        compose.onNodeWithTag("store-add-101").performScrollTo().assertIsEnabled()
        screenshot("catalog-add")
        compose.onNodeWithTag("store-add-101").performClick()
        compose.onNodeWithTag("store-add-101").assertDoesNotExist()
        compose.onNodeWithTag("store-quantity-input-101").assertTextEquals("1")
        screenshot("catalog-stepper")
        compose.onNode(hasContentDescription("Увеличить количество") and hasAnyAncestor(hasTestTag("store-quantity-101")))
            .performClick()
        compose.onNodeWithTag("store-quantity-input-101").assertTextEquals("2")
        compose.onNodeWithText("2 шт.").assertExists()

        compose.runOnIdle { api.failWrites = true }
        compose.onNodeWithTag("store-quantity-input-101").performTextReplacement("10")
        compose.onNodeWithTag("store-quantity-input-101").performImeAction()
        compose.onNodeWithTag("store-quantity-input-101").assertTextEquals("2")
        compose.onNodeWithText("Проверочная ошибка сохранения").assertExists()
        compose.runOnIdle { assertEquals(2, api.quantities[101]); api.failWrites = false }

        compose.onNodeWithTag("store-variant-102").performScrollTo().performClick()
        compose.onNodeWithTag("store-add-102").assertExists()
        compose.onNodeWithTag("store-add-102").performClick()
        compose.onNodeWithTag("store-quantity-input-102").assertTextEquals("1")
        compose.onNodeWithTag("store-variant-101").performScrollTo().performClick()
        compose.onNodeWithTag("store-quantity-input-101").assertTextEquals("2")

        compose.onNodeWithTag("store-quantity-input-101").performTextReplacement("999")
        compose.onNodeWithTag("store-quantity-input-101").performImeAction()
        compose.onNodeWithTag("store-quantity-input-101").assertTextEquals("999")
        compose.onNode(hasContentDescription("Увеличить количество") and hasAnyAncestor(hasTestTag("store-quantity-101")))
            .assertIsNotEnabled()
        compose.onNodeWithTag("store-quantity-input-101").performTextReplacement("0")
        compose.onNodeWithTag("store-quantity-input-101").performImeAction()
        compose.onNodeWithTag("store-add-101").assertExists()
        compose.runOnIdle {
            assertEquals(mapOf(102L to 1), api.quantities)
        }
        compose.onNode(hasSetTextAction() and hasText("Поиск")).performScrollTo().performTextReplacement("80 мм")
        compose.onNodeWithTag("store-quantity-input-102").assertTextEquals("1")
        compose.onNodeWithTag("store-product-101").assertDoesNotExist()
    }

    @Test fun detailWaitsForCartAndReturnsToCatalogOnlyAfterSuccessfulAdd() {
        api.quantities[101] = 2
        val initialRead = CompletableDeferred<Unit>()
        api.waitForRead = initialRead
        compose.setContent { KolbaTheme { StoreScreen(api, initialProductId = 101) } }
        compose.onNodeWithTag("store-detail-add-101").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { initialRead.complete(Unit) }
        // StoreCart returns through Dispatchers.IO after the suspended read; Compose
        // idleness alone does not mean that background coroutine has completed.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("store-detail-add-101") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("store-detail-add-101").assertIsEnabled()
        screenshot("detail-add")
        api.failWrites = true
        compose.onNodeWithTag("store-detail-add-101").performClick()
        compose.onNodeWithText("Проверочная ошибка сохранения").assertExists()
        compose.onNodeWithTag("store-detail-add-101").assertExists().assertIsEnabled()
        compose.runOnIdle { assertEquals(2, api.quantities[101]); api.failWrites = false }
        compose.onNodeWithTag("store-detail-add-101").performClick()
        compose.onNodeWithTag("store-detail-add-101").assertDoesNotExist()
        compose.onNodeWithTag("store-quantity-input-101").performScrollTo().assertTextEquals("3")
        compose.onNodeWithText("Товар добавлен в корзину").assertExists()
        compose.runOnIdle { assertEquals(3, api.quantities[101]) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        awaitPresentedUiFrame(instrumentation)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "cart-actions/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

private class CartActionApi(context: Context) : ApiClient(context, "https://cart-actions.invalid") {
    override var userId: Long = 7
    val quantities = linkedMapOf<Long, Int>()
    var failWrites = false
    var waitForRead: CompletableDeferred<Unit>? = null
    private val products = listOf(
        JSONObject("""{"id":101,"title":"Оболочка тестовая","variant_group":"shell","variant_label":"24 мм","price_kopecks":15000,"product_type":"physical","weight_grams":100}"""),
        JSONObject("""{"id":102,"title":"Оболочка тестовая","variant_group":"shell","variant_label":"80 мм","price_kopecks":25000,"product_type":"physical","weight_grams":200}""")
    )
    override suspend fun get(path: String): JSONObject = when {
        path == "/api/store" -> JSONObject().put("products", JSONArray(products)).put("banners", JSONArray())
        path.startsWith("/api/store/products/") -> JSONObject(products.first { it.getLong("id") == path.substringAfterLast('/').toLong() }.toString())
        else -> error("Unexpected API read: $path")
    }
    override suspend fun getCart(expectedUserId: Long): JSONObject {
        check(userId == expectedUserId)
        waitForRead?.await()
        return cart()
    }
    override suspend fun putCart(expectedUserId: Long, body: JSONObject): JSONObject {
        check(userId == expectedUserId)
        if (failWrites) throw java.io.IOException("Проверочная ошибка сохранения")
        quantities.clear()
        body.items("items").forEach { quantities[it.getLong("product_id")] = it.getInt("quantity") }
        return cart()
    }
    private fun cart() = JSONObject().put("user_id", userId).put("items", JSONArray(quantities.map { (id, quantity) ->
        JSONObject(products.first { it.getLong("id") == id }.toString()).put("product_id", id).put("quantity", quantity)
    }))
}
