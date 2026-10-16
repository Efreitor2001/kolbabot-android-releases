package ru.kolbabot.app

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** A pending browser handoff survives activity/process recreation, for its original TTL only. */
internal data class MobileSignIn(
    val flowId: String,
    val verifier: String,
    val authorizationUrl: String,
    val displayCode: String,
    val expiresAt: Long,
    val intervalMillis: Long,
    val provider: String = "site"
) {
    fun json(): JSONObject = JSONObject().put("flow_id", flowId).put("verifier", verifier)
        .put("authorization_url", authorizationUrl).put("display_code", displayCode)
        .put("expires_at", expiresAt).put("interval_ms", intervalMillis).put("provider", provider)

    fun browserUrl(baseUrl: String): String = if (provider == "google") {
        val next = URI(authorizationUrl).let { it.rawPath + "?" + it.rawQuery }
        "$baseUrl/api/auth/google/start?next=${URLEncoder.encode(next, "UTF-8")}&pd_consent=1&cookie_consent=1&age_consent=1"
    } else authorizationUrl

    companion object {
        fun start(baseUrl: String, response: JSONObject, verifier: String, provider: String, now: Long): MobileSignIn {
            val flowId = response.getString("flow_id")
            require(flowId.matches(Regex("[A-Za-z0-9_-]{43}")) && verifier.matches(Regex("[A-Za-z0-9._~-]{43,128}")))
            val origin = URI(baseUrl)
            val target = origin.resolve(response.getString("authorization_url"))
            require(target.scheme == "https" && target.host == origin.host && target.port == origin.port &&
                target.userInfo == null && target.fragment == null && target.path == "/mobile/auth/approve") {
                "Сервер вернул некорректную ссылку входа."
            }
            val query = target.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
            require(query.size == 1 && query[0].size == 2 && query[0][0] == "flow" &&
                URLDecoder.decode(query[0][1], "UTF-8") == flowId) { "Некорректная попытка входа." }
            return MobileSignIn(flowId, verifier, target.toString(), response.getString("display_code"),
                now + response.optLong("expires_in", 300).coerceIn(1, 300) * 1000,
                response.optLong("interval", 2).coerceIn(2, 15) * 1000, provider)
        }

        fun restore(baseUrl: String, value: JSONObject, now: Long): MobileSignIn? = runCatching {
            val expires = value.getLong("expires_at")
            if (expires <= now || expires > now + 300_000) return null
            start(baseUrl, JSONObject(value.toString()).put("expires_in", 300)
                .put("interval", value.optLong("interval_ms", 2000) / 1000),
                value.getString("verifier"), value.optString("provider", "site"), now).copy(expiresAt = expires)
        }.getOrNull()
    }
}

internal class MobileSignInStore(context: Context, private val baseUrl: String) {
    private val vault = SessionVault(context, "pending_mobile_sign_in")
    fun read(): MobileSignIn? = runCatching {
        val saved = JSONObject(vault.read() ?: return null)
        if (saved.optString("origin") != baseUrl) return null
        MobileSignIn.restore(baseUrl, saved, System.currentTimeMillis())
    }.getOrNull().also { if (it == null) clear() }
    fun write(flow: MobileSignIn) = vault.write(flow.json().put("origin", baseUrl).toString())
    fun clear() = vault.clear()
}

internal fun openSignInBrowser(context: Context, url: String) {
    // Custom Tabs share the browser's existing provider sessions. No account credentials enter a WebView.
    CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .build().launchUrl(context, Uri.parse(url))
}
