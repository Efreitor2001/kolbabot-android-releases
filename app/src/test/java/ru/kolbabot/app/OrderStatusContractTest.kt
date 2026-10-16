package ru.kolbabot.app

import org.junit.Assert.*
import org.junit.Test

class OrderStatusContractTest {
    @Test fun customerLabelsAndPillsMatchTheMobileSite() {
        val packing = siteOrderStatus("paid", customer = true)
        assertEquals("Ожидает сборки", packing.label)
        assertEquals(0xFF92400EL, packing.foreground)
        assertEquals(0xFFFEF3C7L, packing.background)
        val ready = siteOrderStatus("ready", customer = true)
        assertEquals("Готов", ready.label)
        assertEquals(0xFF166534L, ready.foreground)
        assertEquals(0xFFDCFCE7L, ready.background)
        val refunded = siteOrderStatus("refunded", customer = true)
        assertEquals("Возврат", refunded.label)
        assertEquals(0xFF991B1BL, refunded.foreground)
        assertEquals(0xFFFEE2E2L, refunded.background)
    }

    @Test fun adminAndUnpaidOrdersRetainTheirActualPaymentState() {
        assertEquals("Оплачен", siteOrderStatus("paid").label)
        assertEquals("Отменён / возврат", siteOrderStatus("refunded").label)
        assertEquals("Ожидает оплаты", siteOrderStatus("payment_pending", true).label)
        assertEquals("Новый", siteOrderStatus("new", true).label)
        assertEquals("Ошибка оплаты", siteOrderStatus("payment_failed").label)
        assertEquals("Просрочен", siteOrderStatus("payment_expired").label)
    }
}
