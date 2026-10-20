package ru.kolbabot.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdminToolsContractTest {
    @Test fun journalFiltersEncodeUserInputWithoutInjectingNewQueryParameters() {
        val result = AdminToolsContract.query(JSONObject().put("search", "id &limit=2000").put("path", "/api/admin").put("ip", " ").put("method", JSONObject.NULL), offset = 100)
        assertEquals("path=%2Fapi%2Fadmin&search=id%20%26limit%3D2000&offset=100&limit=100", result)
        assertEquals("offset=0&limit=100", AdminToolsContract.query(JSONObject(), -10))
    }

    @Test fun planUsesRublesAndRealFeatureArray() {
        val raw = JSONObject().put("title", " PREMIUM ").put("description", " Текст ").put("features", "Первое\n\n  Второе  \n").put("base_amount_rub", 499).put("discount_percent", 12.5).put("final_amount_rub", 1)
        val result = AdminToolsContract.plan(raw)
        assertEquals(499L, result.getLong("base_amount_rub"))
        assertEquals(12.5, result.getDouble("discount_percent"), 0.001)
        assertEquals(listOf("Первое", "Второе"), result.getJSONArray("features").let { array -> (0 until array.length()).map { array.getString(it) } })
        assertFalse(result.has("final_amount_rub"))
        assertThrows(IllegalArgumentException::class.java) { AdminToolsContract.plan(JSONObject(raw.toString()).put("base_amount_rub", 0)) }
        assertThrows(IllegalArgumentException::class.java) { AdminToolsContract.plan(JSONObject(raw.toString()).put("discount_percent", 100)) }
        assertThrows(IllegalArgumentException::class.java) { AdminToolsContract.plan(JSONObject(raw.toString()).apply { remove("discount_percent") }) }
    }

    @Test fun promoCodesAlwaysTargetPremiumAndRejectInvalidDiscount() {
        val raw = JSONObject().put("code", " summer10 ").put("discount_percent", 10).put("tier", "none")
        val result = AdminToolsContract.promo(raw)
        assertEquals("SUMMER10", result.getString("code"))
        assertEquals("premium", result.getString("tier"))
        assertThrows(IllegalArgumentException::class.java) { AdminToolsContract.promo(JSONObject(raw.toString()).put("code", " ")) }
        assertThrows(IllegalArgumentException::class.java) { AdminToolsContract.promo(JSONObject(raw.toString()).put("discount_percent", -1)) }
    }

    @Test fun enabledBonusCannotHaveZeroOrOutOfRangeReward() {
        assertFalse(AdminToolsContract.bonus(JSONObject().put("is_active", false).put("sausages", 0)).getBoolean("is_active"))
        assertEquals(50L, AdminToolsContract.bonus(JSONObject().put("is_active", true).put("sausages", 50)).getLong("sausages"))
        listOf(-1L, 0L, 1000001L).forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) { AdminToolsContract.bonus(JSONObject().put("is_active", true).put("sausages", amount)) }
        }
    }

    @Test fun migrationNeverEnablesTruncateImplicitly() {
        val result = AdminToolsContract.migration(" ", false)
        assertEquals("auto", result.getString("source_sqlite"))
        assertFalse(result.getBoolean("truncate"))
        val explicit = AdminToolsContract.migration(" /data/catalog.sqlite, /data/users.sqlite ", true)
        assertEquals("/data/catalog.sqlite, /data/users.sqlite", explicit.getString("source_sqlite"))
        assertTrue(explicit.getBoolean("truncate"))
    }
}
