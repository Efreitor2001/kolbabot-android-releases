package ru.kolbabot.app

import org.junit.Assert.*
import org.junit.Test

class PushContractTest {
    private fun data(uid: String = "42", id: String = "123") = mapOf("user_id" to uid, "notification_id" to id, "title" to "Заказ", "body" to "Заказ готов")

    @Test fun dataOnlyNotificationRequiresAnExplicitValidRecipientAndMessageId() {
        assertEquals(AccountPush(42, 123, "Заказ", "Заказ готов"), PushContract.parse(data()))
        listOf("", "0", "-1", "1.5", "invalid", "99999999999999999999999999").forEach { invalid ->
            assertNull(PushContract.parse(data(uid = invalid)))
            assertNull(PushContract.parse(data(id = invalid)))
        }
        assertNull(PushContract.parse(mapOf("notification_id" to "123", "title" to "Сообщение без получателя")))
    }

    @Test fun deliveryIsBoundToBothOptInAndServerRegisteredAccount() {
        val message = PushContract.parse(data())!!
        assertTrue(PushContract.canDisplay(message, 42, 42, 42))
        assertTrue(PushContract.canDisplay(message, 42, 42, 0)) // Cold-start process before session restoration.
        assertFalse(PushContract.canDisplay(message, 0, 42, 42)) // User opted out or logged out.
        assertFalse(PushContract.canDisplay(message, 42, 0, 42)) // Registration has not succeeded.
        assertFalse(PushContract.canDisplay(message, 99, 99, 99)) // Delayed delivery from a former account.
        assertFalse(PushContract.canDisplay(message, 42, 42, 99)) // Account switched while delivery was queued.
        assertFalse(PushContract.canDisplay(message, 42, 99, 42))
    }

    @Test fun payloadIsBoundedAndEmptyMessagesAreIgnored() {
        assertNull(PushContract.parse(data() + mapOf("title" to " ", "body" to " ")))
        val value = PushContract.parse(data() + mapOf("title" to "x".repeat(900), "body" to "y".repeat(3000)))!!
        assertEquals(160, value.title.length)
        assertEquals(1500, value.body.length)
    }

    @Test fun duplicateKeysAreAccountScopedAndOutOfOrderMessagesAreRetained() {
        var seen = PushContract.nextSeen(emptyList(), "42:100")
        seen = PushContract.nextSeen(seen, "42:99")
        seen = PushContract.nextSeen(seen, "43:100")
        assertEquals(listOf("42:100", "42:99", "43:100"), seen)
        assertEquals(listOf("42:99", "43:100", "42:100"), PushContract.nextSeen(seen, "42:100"))
    }

    @Test fun duplicateMemoryIsBoundedWithoutDroppingRecentKeys() {
        var seen = emptyList<String>()
        for (id in 1..600) seen = PushContract.nextSeen(seen, "42:$id")
        assertEquals(512, seen.size)
        assertEquals("42:89", seen.first())
        assertEquals("42:600", seen.last())
    }

    @Test fun aTokenOrMessageFromAnotherProviderCannotReuseTheCurrentBinding() {
        assertTrue(PushContract.matchesProvider("firebase", "firebase"))
        assertTrue(PushContract.matchesProvider("huawei", "huawei"))
        assertFalse(PushContract.matchesProvider("firebase", "huawei"))
        assertFalse(PushContract.matchesProvider("huawei", "firebase"))
        assertFalse(PushContract.matchesProvider("", ""))
        assertFalse(PushContract.matchesProvider("unknown", "unknown"))
    }

    @Test fun huaweiJsonPayloadRetainsTheSameAccountAndMessageContract() {
        val message = PushTransports.huaweiData("""{"user_id":"42","notification_id":"123","title":"Заказ","body":"Заказ готов"}""")!!
        assertEquals(PushContract.parse(data()), PushContract.parse(message))
        assertFalse(PushContract.canDisplay(PushContract.parse(message)!!, 42, 42, 99))
    }

    @Test fun huaweiRejectsMalformedOversizedOrNonStringDataBeforeDispatch() {
        listOf("not json", "[]", """{"user_id":42}""", """{"body":null}""",
            """{"body":"${"я".repeat(2500)}"}""").forEach { raw -> assertNull(PushTransports.huaweiData(raw)) }
    }
}
