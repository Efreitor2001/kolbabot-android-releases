package ru.kolbabot.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class MobileSignInTest {
    private val id = "a".repeat(43)
    private val verifier = "b".repeat(64)
    private val base = "https://kolbabot.ru"
    private fun response(url: String = "/mobile/auth/approve?flow=$id") = JSONObject()
        .put("flow_id", id).put("authorization_url", url).put("display_code", "123-456")
        .put("expires_in", 300).put("interval", 2)

    @Test fun googleGoesDirectlyToProviderStartAndReturnsToTheSameApproval() {
        val flow = MobileSignIn.start(base, response(), verifier, "google", 1000)
        val url = URI(flow.browserUrl(base))
        assertEquals("/api/auth/google/start", url.path)
        val query = url.rawQuery.split('&').associate {
            val pair = it.split('=', limit = 2)
            pair[0] to URLDecoder.decode(pair[1], "UTF-8")
        }
        assertEquals("/mobile/auth/approve?flow=$id", query["next"])
        assertEquals("1", query["pd_consent"])
        assertFalse(url.toString().contains(verifier))
    }

    @Test fun restoredFlowKeepsOriginalExpiryAndVerifier() {
        val flow = MobileSignIn.start(base, response(), verifier, "site", 1000)
        val restored = MobileSignIn.restore(base, flow.json(), 2000)!!
        assertEquals(301000L, restored.expiresAt)
        assertEquals(verifier, restored.verifier)
        assertEquals(flow.authorizationUrl, restored.browserUrl(base))
        assertNull(MobileSignIn.restore(base, flow.json(), 301000))
    }

    @Test fun refusesForeignOriginCredentialsPortFragmentAndWrongFlow() {
        listOf("https://evil.example/mobile/auth/approve?flow=$id",
            "https://kolbabot.ru:444/mobile/auth/approve?flow=$id",
            "https://user@kolbabot.ru/mobile/auth/approve?flow=$id",
            "/mobile/auth/approve?flow=$id#other", "/mobile/auth/approve?flow=other",
            "/mobile/auth/approve?flow=$id&next=https://evil.example").forEach { url ->
            assertTrue(url, runCatching { MobileSignIn.start(base, response(url), verifier, "site", 1000) }.isFailure)
        }
    }

    @Test fun cannotExtendServerExpiryOnRestoreOrUseMalformedPkce() {
        val flow = MobileSignIn.start(base, response(), verifier, "site", 1000)
        assertNull(MobileSignIn.restore(base, flow.json().put("expires_at", 900000), 2000))
        assertTrue(runCatching { MobileSignIn.start(base, response(), "short", "site", 1000) }.isFailure)
    }
}
