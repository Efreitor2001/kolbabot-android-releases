package ru.kolbabot.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AdminDashboardTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun dashboardRendersAllDaysAndExactSectionOrderAndActions() {
        val api = DashboardApi(instrumentation.targetContext)
        var destination = ""
        compose.setContent { KolbaTheme { AdminDashboardScreen(api, {}, { destination = it }, "Анна") } }
        compose.onNodeWithText("Добрый день, Анна").assertIsDisplayed()
        compose.onNodeWithText("+20% к тому же периоду").assertExists()
        screenshot("01-dashboard-kpi")
        val tags = listOf("dashboard-sales", "dashboard-attention", "dashboard-actions", "dashboard-orders", "dashboard-users", "dashboard-content")
        val positions = tags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot().top.value }
        assertEquals(positions.sorted(), positions)
        compose.onNodeWithTag("dashboard-chart").performScrollTo().assertIsDisplayed()
        for (index in 0..13) compose.onNodeWithTag("dashboard-day-$index").assertExists()
        screenshot("02-dashboard-chart")
        compose.onNodeWithTag("dashboard-day-13").performClick()
        compose.onNodeWithText("Операций: 13", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Закрыть").performClick()
        compose.onNodeWithText("Нет ответа в поддержке").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("support", destination) }
        compose.onNodeWithText("Цифровая выдача").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("digital", destination) }
        compose.onNodeWithText("Все заказы").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("orders", destination) }
        compose.onNodeWithTag("dashboard-content").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Статьи: 75%").assertExists()
        compose.onNodeWithContentDescription("Товары: 0%").assertExists()
        screenshot("03-dashboard-content")
        assertTrue(api.reads > 0)
    }

    @Test fun emptyDashboardHasHonestEmptyStatesAndRefreshRecovers() {
        val api = DashboardApi(instrumentation.targetContext).apply { empty = true }
        compose.setContent { KolbaTheme { AdminDashboardScreen(api, {}) } }
        compose.onNodeWithText("За выбранный период данных пока нет.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Всё спокойно — срочных задач нет.").assertExists()
        compose.onNodeWithText("Заказов пока нет.").assertExists()
        compose.onNodeWithText("Новых пользователей пока нет.").assertExists()
        compose.runOnIdle { api.empty = false }
        compose.onNodeWithText("Обновить").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard-day-13").assertExists()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        awaitPresentedUiFrame(instrumentation)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "dashboard/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot failed")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}

private class DashboardApi(context: Context) : ApiClient(context, "https://dashboard.invalid") {
    var reads = 0
    var empty = false
    override suspend fun get(path: String): JSONObject {
        check(path == "/api/admin/dashboard")
        reads++
        if (empty) return JSONObject()
        val days = org.json.JSONArray()
        for (index in 0..13) days.put(JSONObject().put("date", "2026-10-${(index + 1).toString().padStart(2, '0')}")
            .put("revenue_kopecks", index * 10000).put("transactions", index)
            .put("store_revenue_kopecks", index * 8000).put("subscription_revenue_kopecks", index * 2000))
        return JSONObject("""{"generated_at":"2026-10-16T09:00:00Z",
          "kpi":{"revenue_month_kopecks":1234500,"revenue_change_percent":20,"orders_waiting":3,"orders_month":20,"orders_today":2,"active_subscriptions":18,"users_online":7,"online_window_seconds":300,"users_total":120,"users_new_today":2,"users_new_month":13,"tickets_open":2},
          "attention":{"orders_waiting":3,"tickets_open":2,"digital_sales_attention":1},
          "content":{"articles_published":3,"articles_total":4,"recipes_filled":8,"recipes_total":10},
          "recent_orders":[{"order_id":"A101","customer_name":"Тестовый покупатель","total_kopecks":42000,"status":"paid"}],
          "recent_users":[{"id":5,"username":"Тестовый пользователь","email":"test@example.invalid"}]}""").put("daily", days)
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String,String>): JSONObject = error("Unexpected mutation $path")
}
