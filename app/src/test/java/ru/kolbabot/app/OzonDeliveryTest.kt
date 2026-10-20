package ru.kolbabot.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OzonDeliveryTest {
    @Test fun mapTilesOnlyAcceptBoundedByteRangesAndHttpsPmtiles() {
        assertEquals(0L..126L, OzonMapTiles.byteRange("bytes=0-126"))
        assertEquals(16384L..65535L, OzonMapTiles.byteRange("bytes=16384-65535"))
        listOf(null, "", "bytes=0-", "bytes=-500", "bytes=10-5", "bytes=0-999999999", "bytes=0-10,30-40").forEach {
            assertNull(it, OzonMapTiles.byteRange(it))
        }
        assertNotNull(OzonMapTiles.safeSource("https://maps.kolbabot.ru/russia-2026-09.pmtiles"))
        listOf("http://maps.kolbabot.ru/map.pmtiles", "https://user:secret@maps.kolbabot.ru/map.pmtiles", "file:///map.pmtiles", "https://maps.kolbabot.ru/data.json").forEach {
            assertNull(it, OzonMapTiles.safeSource(it))
        }
    }
    @Test fun mapBridgeCannotReadOtherAuthenticatedEndpoints() {
        listOf("map-config", "map-points?min_lat=50&max_lat=60&min_lon=30&max_lon=40&zoom=10", "map-point-info?ids=1%2C2&source=directory").forEach {
            assertTrue(it, OzonDelivery.allowedMapPath(OzonDelivery.API + it))
        }
        listOf("https://evil.example/api/store/ozon-delivery/map-config", "//evil.example/map-config", "/api/auth/me", "/api/admin/users",
            OzonDelivery.API + "../orders", OzonDelivery.API + "map-config#fragment", OzonDelivery.API + "map%2Dconfig",
            OzonDelivery.API + "map-config?redirect=/api/auth/me", OzonDelivery.API + "quote", OzonDelivery.API + "map-points?token=x").forEach {
            assertFalse(it, OzonDelivery.allowedMapPath(it))
        }
    }

    @Test fun estimateBelongsToExactPointAndUsesServerDaysWithoutOffset() {
        val response = JSONObject("""{"delivery_point_id":101,"estimated_delivery_days":5}""")
        assertEquals(5, OzonDelivery.estimateDays(response, 101))
        assertEquals(0, OzonDelivery.estimateDays(response, 102))
        assertEquals(0, OzonDelivery.estimateDays(null, 101))
        assertEquals("1 день", OzonDelivery.daysText(1))
        assertEquals("2 дня", OzonDelivery.daysText(2))
        assertEquals("11 дней", OzonDelivery.daysText(11))
        assertEquals("22 дня", OzonDelivery.daysText(22))
        assertEquals("", OzonDelivery.daysText(0))
    }

    @Test fun quoteInvalidatesWhenPointPhoneOrCartChanges() {
        val row = JSONObject("""{"product_id":1,"quantity":2,"price_kopecks":10000,"shipping_weight_grams":250}""")
        val original = OzonDelivery.quoteKey(101, "+7 (999) 123-45-67", listOf(row), false)
        assertEquals(original, OzonDelivery.quoteKey(101, "79991234567", listOf(row), false))
        assertNotEquals(original, OzonDelivery.quoteKey(102, "79991234567", listOf(row), false))
        assertNotEquals(original, OzonDelivery.quoteKey(101, "79991234568", listOf(row), false))
        assertNotEquals(original, OzonDelivery.quoteKey(101, "79991234567", listOf(row), true))
        assertNotEquals(original, OzonDelivery.quoteKey(101, "79991234567", listOf(JSONObject(row.toString()).put("quantity", 3)), false))
        assertEquals("", OzonDelivery.quoteKey(101, "799", listOf(row), false))
    }

    @Test fun manualDirectoryPointUsesAddressAndNameRatherThanZeroId() {
        val manual = JSONObject("""{"checkout_mode":"manual","full_address":"Москва, Тестовая 1","name":"ПВЗ Ozon","delivery_point_number":"123"}""")
        assertTrue(OzonDelivery.manual(manual))
        assertTrue(OzonDelivery.validPoint(manual))
        assertEquals("ПВЗ Ozon 123", OzonDelivery.pointName(manual))
        assertFalse(OzonDelivery.validPoint(JSONObject("""{"full_address":"Москва"}""")))
        assertTrue(OzonDelivery.validPoint(JSONObject("""{"delivery_point_id":101,"full_address":"Москва"}""")))
    }
}
