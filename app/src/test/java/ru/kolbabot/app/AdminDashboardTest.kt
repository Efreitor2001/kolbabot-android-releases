package ru.kolbabot.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdminDashboardTest {
    @Test fun chartKeepsZeroAndRelativeValuesAndBoundsMalformedCounts() {
        val days = dashboardDays(JSONObject("""{"daily":[
            {"date":"2026-10-16","revenue_kopecks":10000,"transactions":2},
            {"date":"2026-10-15","revenue_kopecks":1,"transactions":1},
            {"date":"invalid","revenue_kopecks":-5,"transactions":-2}] }"""))
        assertEquals("16.10", days[0].label)
        assertEquals(1f, days[0].fraction(10000))
        assertEquals(.04f, days[1].fraction(10000))
        assertEquals(0f, days[2].fraction(10000))
        assertEquals(0, days[2].transactions)
        assertEquals("—", days[2].label)
        assertEquals(0f, days[0].fraction(0))
    }
}
