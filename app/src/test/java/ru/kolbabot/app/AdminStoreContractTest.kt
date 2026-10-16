package ru.kolbabot.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class AdminStoreContractTest {
    private fun physical() = JSONObject("""{
        "id":301,"source_id":"vk-456_007","title":"Оболочка 45 мм · 10 м",
        "category_id":"shells","category_name":"Оболочки","description":"Описание товара",
        "price_kopecks":34567,"price_rub":345.67,"old_price_kopecks":45678,"old_price_rub":456.78,
        "currency":"RUB","weight":"750","weight_grams":750,"length":"10","dimensions":"20/15/10",
        "stock":23,"is_active":1,"sort_order":80,"source_url":"https://example.invalid/sku/007",
        "photo_path":"/static/products/301.jpg","variant_group":"Оболочка 45 мм","variant_label":"10 м",
        "product_type":"physical","related_ids":[302,303],"book_config":{}
    }""")
    private fun kopecks(payload: JSONObject, key: String): Long = BigDecimal(payload.get(key).toString()).movePointRight(2).longValueExact()

    @Test fun physicalSkuKeepsItsOwnPriceWeightLengthAndVariant() {
        val product = physical()
        val payload = adminProductPayload(product)
        assertTrue(payload.getBoolean("preserve_existing_content"))
        assertEquals(34567L, kopecks(payload, "price"))
        assertEquals(45678L, kopecks(payload, "old_price"))
        assertEquals(750L, payload.getLong("weight_grams"))
        assertEquals("10", payload.getString("length"))
        assertEquals("20/15/10", payload.getString("dimensions"))
        assertEquals("Оболочка 45 мм", payload.getString("variant_group"))
        assertEquals("10 м", payload.getString("variant_label"))
        assertEquals(23, payload.getInt("stock"))
        assertEquals("vk-456_007", product.getString("source_id"))
        assertFalse("Source SKU is server-owned and must not be reassigned", payload.has("source_id"))
        assertFalse(payload.has("id"))
    }

    @Test fun integerKopecksFallbackIsConvertedToRublesExactlyOnce() {
        val product = physical().apply { remove("price_rub"); remove("old_price_rub"); put("price_kopecks", 101); put("old_price_kopecks", 0) }
        val payload = adminProductPayload(product)
        assertEquals(101L, kopecks(payload, "price"))
        assertEquals(0L, kopecks(payload, "old_price"))
    }

    @Test fun relatedPayloadPreservesExistingWritableFieldsAndDoesNotMutateSource() {
        val product = physical()
        val source = product.toString()
        val payload = adminProductPayload(product).put("related_ids", JSONArray(listOf(304, 305)))
        listOf("title", "description", "category_id", "category_name", "currency", "source_url", "photo_path", "stock", "weight_grams", "length", "dimensions", "sort_order", "variant_group", "variant_label", "product_type").forEach { key ->
            assertEquals("Changed $key while preparing the form", product.get(key), payload.get(key))
        }
        assertEquals(source, product.toString())
        assertEquals("[304,305]", payload.getJSONArray("related_ids").toString())
        assertEquals(34567L, kopecks(payload, "price"))
    }

    @Test fun digitalBookTitleAndBothFileVersionsSurvivePayloadPreparation() {
        val config = JSONObject("""{"title":"Рецептуры","source_sha256":"standard-sha","source_format":"pdf","original_filename":"book.pdf","filename_template":"book_{instance_id}.pdf","mobile":{"source_sha256":"mobile-sha","source_format":"docx","original_filename":"phone.docx"}}""")
        val product = physical().put("product_type", "digital_book").put("book_config", config).put("is_active", false)
        val payload = adminProductPayload(product)
        assertEquals(config.toString(), payload.getJSONObject("book_config").toString())
        assertEquals("digital_book", payload.getString("product_type"))
        assertFalse(payload.getBoolean("is_active"))
        assertEquals("[302,303]", payload.getJSONArray("related_ids").toString())
    }

    @Test fun NumericBooleanAndLegacyStringVisibilityStayBoolean() {
        listOf(true, 1, "1").forEach { assertTrue(adminProductPayload(physical().put("is_active", it)).getBoolean("is_active")) }
        listOf(false, 0, "0").forEach { assertFalse(adminProductPayload(physical().put("is_active", it)).getBoolean("is_active")) }
    }
}
