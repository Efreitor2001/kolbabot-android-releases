package ru.kolbabot.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdminPeopleContractTest {
    @Test fun changingLoginDoesNotResetPasswordOrAccidentallyChangePermissions() {
        val result = AdminPeopleContract.credentials(JSONObject().put("username", " operator ").put("password", "").put("is_admin", true).put("is_active", false))
        assertEquals("operator", result.getString("username"))
        assertFalse(result.has("password"))
        assertFalse(result.has("is_admin"))
        assertFalse(result.has("is_active"))
    }

    @Test fun explicitlyEnteredPasswordIsNotTrimmed() {
        val result = AdminPeopleContract.credentials(JSONObject().put("username", "operator").put("password", " secret with spaces "))
        assertEquals(" secret with spaces ", result.getString("password"))
        assertThrows(IllegalArgumentException::class.java) { AdminPeopleContract.credentials(JSONObject().put("username", " ")) }
    }

    @Test fun accessRequiresPositiveHoursAndKnownExplicitAction() {
        listOf(0L, -1L).forEach { duration ->
            assertThrows(IllegalArgumentException::class.java) { AdminPeopleContract.access(JSONObject().put("duration_hours", duration).put("action", "extend")) }
        }
        assertThrows(IllegalArgumentException::class.java) { AdminPeopleContract.access(JSONObject().put("duration_hours", 24).put("action", "revoke")) }
        val result = AdminPeopleContract.access(JSONObject().put("duration_hours", 72).put("action", "extend").put("tier", "none"))
        assertEquals("premium", result.getString("tier"))
        assertEquals(72L, result.getLong("duration_hours"))
        assertEquals("extend", result.getString("action"))
    }

    @Test fun personalNotificationsCannotFallBackToBroadcast() {
        val input = JSONObject().put("scope", "personal").put("title", "Вопрос").put("message", "Ответ").put("target_user_id", 0)
        assertThrows(IllegalArgumentException::class.java) { AdminPeopleContract.notification(input) }
        val result = AdminPeopleContract.notification(input.put("target_user_id", 42))
        assertEquals("personal", result.getString("scope"))
        assertEquals(42L, result.getLong("target_user_id"))
    }

    @Test fun globalNotificationDropsAStalePersonalRecipientAndRejectsOversizeText() {
        val input = JSONObject().put("scope", "global").put("title", " Объявление ").put("message", " Текст ").put("target_user_id", 42)
        val result = AdminPeopleContract.notification(input)
        assertEquals(0L, result.getLong("target_user_id"))
        assertEquals("Объявление", result.getString("title"))
        assertEquals("Текст", result.getString("message"))
        assertThrows(IllegalArgumentException::class.java) { AdminPeopleContract.notification(JSONObject(input.toString()).put("title", "я".repeat(241))) }
        assertThrows(IllegalArgumentException::class.java) { AdminPeopleContract.notification(JSONObject(input.toString()).put("message", "я".repeat(20001))) }
        assertThrows(IllegalArgumentException::class.java) { AdminPeopleContract.notification(JSONObject(input.toString()).put("scope", "unknown")) }
    }

    @Test fun dossierCombinesRealTablesWithoutChangingRewardUnitsOrAutopayFlag() {
        fun table(name: String, row: JSONObject) = JSONObject().put("table", name).put("rows", JSONArray().put(row))
        val dossier = JSONObject().put("tables", JSONArray()
            .put(table("miniapp_users", JSONObject().put("id", 42).put("username", "operator").put("is_admin", 1)))
            .put(table("miniapp_user_access", JSONObject().put("tier", "premium").put("status", "active").put("active_until", JSONObject.NULL).put("is_autopay", 1)))
            .put(table("store_user_rewards", JSONObject().put("sausages_kopecks", 12345))))
        val result = AdminPeopleContract.userFromDossier(dossier)
        assertEquals(42L, result.getLong("id"))
        assertEquals(123L, result.getLong("store_sausages"))
        assertEquals("premium", result.getString("subscription_tier"))
        assertTrue(result.getBoolean("subscription_autopay_enabled"))
        assertTrue(result.isNull("subscription_active_until"))
        assertTrue(AdminPeopleContract.flag(result, "is_admin"))
        assertFalse(AdminPeopleContract.flag(JSONObject().put("is_active", "0"), "is_active"))
        assertEquals(0L, AdminPeopleContract.userFromDossier(JSONObject()).optLong("id"))
    }

    @Test fun expiryUsesServerTimeAndTreatsTheEndInstantAsExpired() {
        assertEquals(true, AdminPeopleContract.subscriptionExpired("2026-10-15 15:00:00", "2026-10-15 15:00:00"))
        assertEquals(false, AdminPeopleContract.subscriptionExpired("2026-10-15 15:00:01", "2026-10-15 15:00:00"))
        assertNull(AdminPeopleContract.subscriptionExpired("2026-10-15 15:00:00", ""))
        assertNull(AdminPeopleContract.subscriptionExpired("invalid", "2026-10-15 15:00:00"))
    }

    @Test fun expiryComparesOffsetsAsInstantsAndMixedTimestampsOnlyWithServerZone() {
        assertEquals(true, AdminPeopleContract.subscriptionExpired("2026-10-15T16:00:00+03:00", "2026-10-15T13:00:00Z"))
        assertEquals(false, AdminPeopleContract.subscriptionExpired("2026-10-15T16:00:01+03:00", "2026-10-15T13:00:00Z"))
        assertEquals(true, AdminPeopleContract.subscriptionExpired("2026-10-15T13:00:00Z", "2026-10-15 16:00:00", "Europe/Moscow"))
        assertEquals(false, AdminPeopleContract.subscriptionExpired("2026-10-15T13:00:00Z", "2026-10-15 16:00:00", "Asia/Yekaterinburg"))
        assertNull(AdminPeopleContract.subscriptionExpired("2026-10-15T13:00:00Z", "2026-10-15 16:00:00"))
    }

    @Test fun expiredAndRevokedDossiersCannotDisplayEffectivePremium() {
        fun dossier(status: String, until: String) = JSONObject().put("generated_at", "2026-10-15 16:00:00").put("tables", JSONArray()
            .put(JSONObject().put("table", "miniapp_user_access").put("rows", JSONArray().put(JSONObject()
                .put("tier", "premium").put("status", status).put("active_until", until)))))
        val expired = AdminPeopleContract.userFromDossier(dossier("active", "2026-10-15 15:59:59"))
        assertEquals("expired", expired.getString("subscription_status"))
        assertEquals("none", expired.getString("subscription_tier"))
        val revoked = AdminPeopleContract.userFromDossier(dossier("revoked", "2026-12-31 00:00:00"))
        assertEquals("revoked", revoked.getString("subscription_status"))
        assertEquals("none", revoked.getString("subscription_tier"))
    }
}
