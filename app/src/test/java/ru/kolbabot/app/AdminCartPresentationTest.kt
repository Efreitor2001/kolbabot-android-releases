package ru.kolbabot.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminCartPresentationTest {
    @Test fun naiveStorageDatesAlreadyUseMoscowTime() {
        assertEquals("16.10.2026 12:30 МСК", adminCartDate("2026-10-16 12:30:00"))
        assertEquals("16.10.2026 12:30 МСК", adminCartDate("2026-10-16T09:30:00Z"))
        assertEquals("16.10.2026 12:30 МСК", adminCartDate("2026-10-16T12:30:00+03:00"))
        assertEquals("без даты", adminCartDate("null"))
    }

    @Test fun concreteVariantNamesArePreservedAndUnrelatedMetadataIsIgnored() {
        assertEquals("Оболочка · 10 м", adminCartItemTitle(JSONObject("""{"product_id":71,"title":"Оболочка","variant_label":"10 м","debug_metadata":{"title":"system"}}""")))
        assertEquals("Оболочка 10 м", adminCartItemTitle(JSONObject("""{"product_id":71,"title":"Оболочка 10 м","variant_label":"10 м"}""")))
        assertEquals("Оболочка · 50 м", adminCartItemTitle(JSONObject("""{"product_id":72,"product":{"title":"Оболочка","variant_label":"50 м"}}""")))
        assertEquals("Товар #73", adminCartItemTitle(JSONObject("""{"product_id":73,"title":null,"metadata":{"title":"system"}}""")))
    }
}
