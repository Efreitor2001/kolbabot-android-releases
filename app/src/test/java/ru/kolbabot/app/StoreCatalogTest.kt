package ru.kolbabot.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoreCatalogTest {
    private fun product(id: Long, group: String = "", price: Long = 100) = JSONObject()
        .put("id", id).put("title", "Оболочка коллагеновая").put("variant_group", group).put("price_kopecks", price)
        .put("product_type", "physical").put("category_id", "shells")
    @Test fun catalogGroupsOnlyExplicitPhysicalVariantsAndKeepsCheapestSku() {
        val heavy = product(1, "shell", 500).put("weight_grams", 200)
        val light = product(2, "shell", 200).put("weight_grams", 100)
        val separate = product(3, "different", 300)
        val book = product(4, "shell", 800).put("product_type", "digital_book")
        val result = visibleStoreProducts(listOf(heavy, light, separate, book), "", "")
        assertEquals(listOf(2L, 3L, 4L), result.map { it.getLong("id") })
        assertEquals(100, result.first().getInt("weight_grams"))
        assertEquals(200, heavy.getInt("weight_grams"))
    }
    @Test fun searchKeepsTheMatchingVariantInsteadOfCheaperDifferentSize() {
        val large = product(1, "shell", 500).put("variant_label", "80 мм")
        val small = product(2, "shell", 100).put("variant_label", "24 мм")
        assertEquals(1L, visibleStoreProducts(listOf(large, small), "shells", "80 мм").first().getLong("id"))
    }
    @Test fun searchToleratesTyposAndRussianYoWithoutChangingSkuIdentity() {
        val row = product(1).put("title", "Соль нитритная для варёной колбасы")
        assertTrue(productSearchScore(row, "нитртиная") >= .45)
        assertTrue(productSearchScore(row, "вареной") >= .45)
        assertEquals(0.0, productSearchScore(row, "шприц"), .001)
    }
    @Test fun secondaryCategoryStillFindsProduct() {
        val row = product(1).put("category_ids", JSONArray().put("sale"))
        assertEquals(1, visibleStoreProducts(listOf(row), "sale", "").size)
        assertTrue(visibleStoreProducts(listOf(row), "books", "").isEmpty())
    }
    @Test fun guestMergeAddsQuantitiesForSameSkuAndNeverMixesVariants() {
        val server = listOf(JSONObject("""{"product_id":10,"quantity":998} """))
        val guest = listOf(JSONObject("""{"product_id":10,"quantity":3} """), JSONObject("""{"product_id":11,"quantity":2} """))
        val result = mergeCartQuantities(server, guest)
        assertEquals(listOf(10L, 11L), result.map { it.getLong("product_id") })
        assertEquals(listOf(999, 2), result.map { it.getInt("quantity") })
        assertEquals(998, server.first().getInt("quantity"))
    }
    @Test fun nativeLinksAcceptOnlyKnownRoutesOnOurOrigin() {
        assertEquals(82L, nativeDestination("https://kolbabot.ru/?product=82#store")?.productId)
        assertEquals(91L, nativeDestination("/?article=91#articles")?.articleId)
        assertEquals("support", nativeDestination("/#support")?.section)
        assertEquals("store_123", nativeDestination("/?order=store_123#account")?.orderId)
        assertNull(nativeDestination("/?ref=FRIEND#account"))
        assertNull(nativeDestination("/?order_id=subscription123#account"))
        assertEquals("admin", nativeDestination("/admin")?.section)
        assertEquals("admin", nativeDestination("/#admin")?.section)
        listOf("https://evil.invalid/?product=82", "https://kolbabot.ru@evil.invalid/?article=91", "http://kolbabot.ru/?product=1", "/admin?role=admin", "/api/admin/users", "https://evil.invalid/admin", "javascript:alert(1)").forEach {
            assertNull(it, nativeDestination(it))
        }
    }
}
