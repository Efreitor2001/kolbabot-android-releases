package ru.kolbabot.app

import org.junit.Assert.*
import org.junit.Test

class ApiContractTest {
    @Test fun resolveApiPathKeepsSameHttpsOriginAndQuery() {
        assertEquals("https://kolbabot.ru/api/store?category=spices", ApiContract.endpoint("https://kolbabot.ru", "/api/store?category=spices").toString())
    }
    @Test fun endpointRejectsCrossOriginAbsoluteAndBackslashPaths() {
        listOf("https://evil.test/api", "//evil.test/api", "/\\evil.test/api").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { ApiContract.endpoint("https://kolbabot.ru", path) }
        }
        assertThrows(IllegalArgumentException::class.java) { ApiContract.endpoint("http://kolbabot.ru", "/api/store") }
    }
    @Test fun pkceMatchesRfc7636S256Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ApiContract.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun successWrapsListsAndPreservesObjects() {
        assertEquals("recipe", ApiContract.unwrap(200, """{"ok":true,"data":[{"slug":"recipe"}]}""").items("items").single().getString("slug"))
        assertEquals(123L, ApiContract.unwrap(201, """{"ok":true,"data":{"order_id":"123"}}""").getLong("order_id"))
    }
    @Test fun pendingPaymentResponseRemainsSuccessWithoutAutomaticReplay() {
        val pending = ApiContract.unwrap(202, """{"ok":true,"data":{"order_id":"order-1","pending_reconciliation":true,"payment_url":""}}""")
        assertTrue(pending.getBoolean("pending_reconciliation"))
        assertEquals("order-1", pending.getString("order_id"))
    }
    @Test fun structuredQuoteConflictIsAvailableToConfirmationScreen() {
        val failure = assertThrows(ApiException::class.java) {
            ApiContract.unwrap(409, """{"ok":false,"error":"Цена изменилась","details":{"reason":"ozon_quote_changed","quote":{"price_kopecks":34000}}}""")
        }
        assertEquals(409, failure.status)
        assertEquals("ozon_quote_changed", failure.details.getString("reason"))
        assertEquals(34000, failure.details.getJSONObject("quote").getInt("price_kopecks"))
    }
    @Test fun htmlGatewayFailureDoesNotLeakServerHtml() {
        val failure = assertThrows(ApiException::class.java) { ApiContract.unwrap(502, "<html>Proxy failure private internal host</html>") }
        assertEquals(502, failure.status)
        assertFalse(failure.message.contains("internal host"))
    }
}
