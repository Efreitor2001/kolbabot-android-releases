package ru.kolbabot.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class VisitorAnalyticsTest {
    private val today = Instant.parse("2026-10-19T18:00:00Z")
    private fun granted(): VisitorAnalyticsState = VisitorAnalyticsState(newId = { "installation" }).apply {
        choose(AnalyticsConsent.GRANTED)
        confirmAccount(0)
    }

    @Test fun defaultAndRefusalDoNotCreateOrSendIdentifier() {
        var generated = 0
        val state = VisitorAnalyticsState(newId = { generated++; "id" })
        state.confirmAccount(0)
        assertNull(state.prepare(today))
        state.choose(AnalyticsConsent.DENIED)
        assertNull(state.prepare(today))
        assertEquals(0, generated)
        assertEquals("", state.snapshot.installationId)
    }

    @Test fun sameGuestTenOpensOnlySendsOnceAndPersistsAcrossRestarts() {
        val state = granted()
        val first = state.prepare(today)!!
        state.delivered(first, "guest-proof")
        repeat(10) { assertNull(state.prepare(today.plusSeconds(it * 61L))) }
        val restored = VisitorAnalyticsState(state.snapshot, newId = { error("Must retain installation") })
        assertNull(restored.prepare(today)) // no sending before /auth/me confirms identity
        restored.confirmAccount(0)
        assertEquals("installation", restored.snapshot.installationId)
        assertEquals("guest-proof", restored.snapshot.guestToken)
        assertNull(restored.prepare(today))
    }

    @Test fun moscowCalendarMidnightCreatesNextDayReport() {
        val today = Instant.parse("2026-10-19T20:59:30Z")
        val state = granted()
        val first = state.prepare(today)!!
        assertEquals("2026-10-19", first.day)
        state.delivered(first, "guest")
        val next = state.prepare(today.plusSeconds(61))!!
        assertEquals("2026-10-20", next.day)
        assertEquals(first.installationId, next.installationId)
        assertEquals("guest", next.guestToken)
    }

    @Test fun guestProofMergesOnLoginOnceAndIsClearedOnLogoutAndAccountSwitch() {
        val state = granted()
        state.delivered(state.prepare(today)!!, "guest-proof")
        state.confirmAccount(17)
        val login = state.prepare(today)!!
        assertEquals("guest-proof", login.guestToken)
        state.delivered(login, null)
        assertEquals("", state.snapshot.guestToken)
        state.confirmAccount(18)
        assertEquals("", state.prepare(today)!!.guestToken)
        state.confirmAccount(0)
        assertEquals("", state.prepare(today)!!.guestToken)
        assertEquals("installation", state.snapshot.installationId)
    }

    @Test fun staleGuestResponseCannotReappearAfterLoginOrLogoutOrRevoke() {
        val state = granted()
        val guest = state.prepare(today)!!
        state.confirmAccount(8)
        state.delivered(guest, "stale-proof")
        assertEquals("", state.snapshot.guestToken)
        val authenticated = state.prepare(today)!!
        state.confirmAccount(0)
        state.delivered(authenticated, "stale-auth-proof")
        assertEquals("", state.snapshot.guestToken)
        val pending = state.prepare(today)!!
        state.choose(AnalyticsConsent.DENIED)
        state.delivered(pending, "stale-after-revoke")
        assertEquals("", state.snapshot.installationId)
        assertEquals("", state.snapshot.guestToken)
        assertNull(state.prepare(today.plusSeconds(600)))
    }

    @Test fun restartAfterAccountExpiryDoesNotCarryPreviousGuestToken() {
        val state = VisitorAnalyticsState(AnalyticsSnapshot(AnalyticsConsent.GRANTED, "same-install", "old-proof", 22))
        state.confirmAccount(0)
        val guest = state.prepare(today)!!
        assertEquals("", guest.guestToken)
        assertTrue(guest.guestReset)
    }

    @Test fun guestResetRequiresObservedLogoutAndSurvivesOfflineRestartUntilAcknowledged() {
        val state = granted()
        assertFalse(state.prepare(today)!!.guestReset)
        state.confirmAccount(22)
        state.confirmAccount(0)
        val afterLogout = state.prepare(today)!!
        assertTrue(afterLogout.guestReset)
        val restarted = VisitorAnalyticsState(state.snapshot)
        restarted.confirmAccount(0)
        val retry = restarted.prepare(today)!!
        assertTrue(retry.guestReset)
        restarted.delivered(retry, "new-guest-segment")
        assertFalse(restarted.snapshot.guestReset)
        restarted.confirmAccount(23)
        assertFalse(restarted.prepare(today)!!.guestReset)
    }

    @Test fun logoutMustReportFreshGuestEvenWhenNoAuthenticatedReportCompleted() {
        val state = granted()
        state.delivered(state.prepare(today)!!, "before-login")
        state.confirmAccount(22)
        state.confirmAccount(0)
        val nextGuest = state.prepare(today)!!
        assertTrue(nextGuest.guestReset)
        assertEquals("", nextGuest.guestToken)
    }

    @Test fun failedReportRetriesWithSameIdentityAndBoundedFrequency() {
        val state = granted()
        val first = state.prepare(today)!!
        assertNull(state.prepare(today.plusSeconds(30)))
        val retry = state.prepare(today.plusSeconds(60))!!
        assertEquals(first.installationId, retry.installationId)
        assertEquals(first.day, retry.day)
    }

    @Test fun consumedGuestTokenIsClearedAndDoesNotMarkDayDelivered() {
        val state = VisitorAnalyticsState(AnalyticsSnapshot(AnalyticsConsent.GRANTED, "installation", "consumed", 0))
        state.confirmAccount(0)
        val stale = state.prepare(today)!!
        state.ignored(stale)
        assertEquals("", state.snapshot.delivered)
        assertEquals("", state.snapshot.guestToken)
        val retry = state.prepare(today.plusSeconds(61))!!
        assertEquals("", retry.guestToken)
        state.delivered(retry, "new-segment")
        assertNull(state.prepare(today.plusSeconds(130)))
    }

    @Test fun dashboardPreservesCombinedDedupedCountsAndAllFourPeriods() {
        val value = JSONObject("""{"periods":{
          "day":{"total":1,"web":1,"android":1},
          "week":{"total":2,"web":2,"android":1},
          "month":{"total":3,"web":2,"android":2},
          "all":{"total":4,"web":3,"android":2}}}""")
        val rows = analyticsRows(value)
        assertEquals(listOf("Сегодня", "7 дней", "30 дней", "Всё время"), rows.map { it.first })
        assertEquals(listOf(1L, 1L, 1L), rows.first().second)
        assertEquals(listOf(4L, 3L, 2L), rows.last().second)
    }

    @Test(expected = org.json.JSONException::class)
    fun unavailableStatisticsAreNotPresentedAsZeros() {
        analyticsRows(JSONObject())
    }
}
