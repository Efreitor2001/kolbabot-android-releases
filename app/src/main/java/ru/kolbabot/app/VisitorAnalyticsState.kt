package ru.kolbabot.app

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal enum class AnalyticsConsent { UNDECIDED, GRANTED, DENIED }

internal data class AnalyticsSnapshot(
    val consent: AnalyticsConsent = AnalyticsConsent.UNDECIDED,
    val installationId: String = "",
    val guestToken: String = "",
    val accountId: Long = -1,
    val delivered: String = "",
    val guestReset: Boolean = false,
)

internal data class AnalyticsVisit(
    val installationId: String, val guestToken: String, val accountId: Long,
    val day: String, val generation: Long,
    val guestReset: Boolean = false,
)

/** The installation and the current guest segment are deliberately different identities. */
internal class VisitorAnalyticsState(
    initial: AnalyticsSnapshot = AnalyticsSnapshot(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val persist: (AnalyticsSnapshot) -> Unit = {},
) {
    var snapshot = initial
        private set
    private var verified = false
    private var generation = 0L
    private var lastAttempt = Long.MIN_VALUE

    @Synchronized fun choose(consent: AnalyticsConsent) {
        generation++
        lastAttempt = Long.MIN_VALUE
        snapshot = if (consent == AnalyticsConsent.GRANTED) snapshot.copy(
            consent = consent, installationId = snapshot.installationId.ifBlank(newId), delivered = ""
        ) else snapshot.copy(consent = consent, installationId = "", guestToken = "", delivered = "", guestReset = false)
        persist(snapshot)
    }

    @Synchronized fun confirmAccount(accountId: Long) {
        if (!verified || snapshot.accountId != accountId) {
            generation++
            lastAttempt = Long.MIN_VALUE
            // Only guest -> account may carry a token. Logout and account switching cannot.
            val token = snapshot.guestToken.takeIf { snapshot.accountId <= 0 && accountId >= 0 }.orEmpty()
            val reset = if (accountId > 0) false else snapshot.guestReset || snapshot.accountId > 0
            snapshot = snapshot.copy(accountId = accountId, guestToken = token, guestReset = reset,
                delivered = snapshot.delivered.takeIf { snapshot.accountId == accountId }.orEmpty())
            persist(snapshot)
        }
        verified = true
    }

    @Synchronized fun prepare(now: Instant): AnalyticsVisit? {
        if (!verified || snapshot.consent != AnalyticsConsent.GRANTED || snapshot.installationId.isBlank()) return null
        val day = now.atZone(ZoneId.of("Europe/Moscow")).toLocalDate().toString()
        if (snapshot.delivered == "${snapshot.accountId}:$day") return null
        val millis = now.toEpochMilli()
        if (lastAttempt != Long.MIN_VALUE && millis >= lastAttempt && millis - lastAttempt < 60_000) return null
        lastAttempt = millis
        return AnalyticsVisit(snapshot.installationId, snapshot.guestToken, snapshot.accountId, day, generation, snapshot.guestReset)
    }

    @Synchronized fun isCurrent(visit: AnalyticsVisit) =
        snapshot.consent == AnalyticsConsent.GRANTED && generation == visit.generation &&
            snapshot.accountId == visit.accountId && snapshot.installationId == visit.installationId

    @Synchronized fun delivered(visit: AnalyticsVisit, guestToken: String?) {
        if (!isCurrent(visit)) return
        snapshot = snapshot.copy(
            guestToken = if (visit.accountId == 0L) guestToken.orEmpty() else "",
            delivered = "${visit.accountId}:${visit.day}",
            guestReset = snapshot.guestReset && (visit.accountId != 0L || guestToken.isNullOrBlank()),
        )
        persist(snapshot)
    }

    @Synchronized fun ignored(visit: AnalyticsVisit) {
        if (!isCurrent(visit)) return
        // A server-consumed/expired proof is not an accepted visit. Retry later without it.
        snapshot = snapshot.copy(guestToken = "", delivered = "")
        persist(snapshot)
    }
}
