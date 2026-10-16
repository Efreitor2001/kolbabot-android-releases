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
class AdminStoreSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun editingPricePreservesSkuAndRelatedSaveIsNarrow() {
        val api = AdminStoreFakeApi(context)
        compose.setContent { KolbaTheme { AdminStoreScreen(api, {}, {}) } }
        compose.onNode(hasSetTextAction() and hasText("Поиск по названию, варианту или номеру"))
            .performScrollTo().performTextReplacement("32 мм")
        compose.onNodeWithText("Изменить", substring = false).assertDoesNotExist()
        compose.onNodeWithTag("admin-product-71").performScrollTo().performClick()
        compose.onNodeWithText("Изменить", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Редактирование товара").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Цена, ₽ *"))
            .performScrollTo().performTextReplacement("179,90")
        compose.onNodeWithText("Сохранить", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Магазин · админка").assertExists()

        assertEquals(1, api.writes.size)
        val priceWrite = api.writes[0]
        assertEquals("/api/admin/store/products/71", priceWrite.first)
        val payload = priceWrite.second
        assertEquals(179.90, payload.getDouble("price"), 0.001)
        assertEquals(125L, payload.getLong("weight_grams"))
        assertEquals("Коллагеновая оболочка", payload.getString("variant_group"))
        assertEquals("32 мм · 10 м", payload.getString("variant_label"))
        assertEquals("10 м", payload.getString("length"))
        assertEquals("20/15/5", payload.getString("dimensions"))
        assertEquals(17L, payload.getLong("stock"))
        assertEquals("casings", payload.getString("category_id"))
        assertEquals(AdminStoreFakeApi.DESCRIPTION, payload.getString("description"))
        assertTrue(payload.getBoolean("preserve_existing_content"))

        compose.onNodeWithText("Связанные товары").performScrollTo().performClick()
        compose.onNodeWithText("С этим товаром покупают").assertExists()
        compose.onNodeWithText("Оболочка 36 мм — 20 м").assertExists()
        compose.onNode(isToggleable()).assertIsOff().performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Выбрано 1 из 24").assertExists()
        screenshot("13-admin-store-related")
        compose.onNodeWithText("Сохранить связи").performScrollTo().performClick()
        compose.onNodeWithText("Магазин · админка").assertExists()

        assertEquals(2, api.writes.size)
        val relatedWrite = api.writes[1]
        assertEquals("/api/admin/store/products/71", relatedWrite.first)
        assertEquals(setOf("related_ids"), relatedWrite.second.keys().asSequence().toSet())
        val ids = relatedWrite.second.getJSONArray("related_ids")
        assertEquals(1, ids.length())
        assertEquals(72L, ids.getLong(0))
        assertFalse(relatedWrite.second.has("description"))
        assertFalse(relatedWrite.second.has("price"))
        assertFalse(relatedWrite.second.has("category_id"))
        assertEquals(17990L, api.product.getLong("price_kopecks"))
        assertEquals(AdminStoreFakeApi.DESCRIPTION, api.product.getString("description"))
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val folder = File(context.getExternalFilesDir(null), "native-smoke").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

private class AdminStoreFakeApi(context: Context) : ApiClient(context, "https://native-admin-store.invalid") {
    val writes = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    val product = JSONObject().put("id", 71).put("title", "Оболочка 32 мм — 10 м")
        .put("category_id", "casings").put("category_name", "Оболочки").put("description", DESCRIPTION)
        .put("product_type", "physical").put("currency", "RUB").put("price_kopecks", 14950).put("price_rub", 149.50)
        .put("old_price_kopecks", 19900).put("old_price_rub", 199.0).put("is_active", 1).put("stock", 17)
        .put("weight_grams", 125).put("length", "10 м").put("dimensions", "20/15/5").put("sort_order", 5)
        .put("variant_group", "Коллагеновая оболочка").put("variant_label", "32 мм · 10 м")
        .put("photo_path", "").put("source_url", "").put("related_ids", JSONArray())
    private val other = JSONObject().put("id", 72).put("title", "Оболочка 36 мм — 20 м")
        .put("category_id", "casings").put("category_name", "Оболочки").put("product_type", "physical")
        .put("price_kopecks", 29900).put("is_active", 1).put("stock", 8).put("weight_grams", 250)
        .put("length", "20 м").put("variant_group", "Коллагеновая оболочка").put("variant_label", "36 мм · 20 м")
    init { userId = 7 }

    override suspend fun get(path: String): JSONObject {
        require(path == "/api/store?include_inactive=1") { "Unexpected GET $path" }
        return JSONObject().put("products", JSONArray().put(JSONObject(product.toString())).put(JSONObject(other.toString())))
            .put("categories", JSONArray().put(JSONObject().put("id", "casings").put("title", "Оболочки").put("sort_order", 1).put("products_count", 2)))
            .put("delivery_methods", JSONArray()).put("delivery_sizes", JSONArray()).put("banners", JSONArray())
    }

    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        require(path == "/api/admin/store/products/71") { "Unexpected PATCH $path" }
        writes += path to JSONObject(body.toString())
        body.keys().forEach { key -> product.put(key, body.get(key)) }
        if (body.has("price")) {
            product.put("price_rub", body.getDouble("price"))
            product.put("price_kopecks", kotlin.math.round(body.getDouble("price") * 100).toLong())
        }
        return JSONObject().put("product", JSONObject(product.toString()))
    }

    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected POST $path")
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")

    companion object {
        const val DESCRIPTION = "<p>Коллагеновая оболочка для <strong>домашней колбасы</strong>.</p>"
    }
}
