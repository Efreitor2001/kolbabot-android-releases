package ru.kolbabot.app

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Isolated preferences and a fake transport: no production data or transactions. */
@RunWith(AndroidJUnit4::class)
class VisitorAnalyticsSmokeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var api: AnalyticsSmokeApi

    @Before fun setup() {
        context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("analytics_smoke_$name", mode)
        }
        listOf("first_party_analytics", "secure_session").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        api = AnalyticsSmokeApi(context)
        api.analytics.confirmAccount(0)
    }

    @Test fun noRequestBeforeConsentAndWithdrawalClearsPrivateIdentifier() {
        compose.setContent { KolbaTheme {
            AnalyticsConsentCard(api)
            VisitorAnalyticsLifecycle(api, active = true, identityRevision = 0)
        } }
        compose.onNodeWithText("Разрешить").assertIsDisplayed()
        compose.onNodeWithText("Отказаться").assertIsDisplayed()
        screenshot("consent")
        compose.runOnIdle {
            assertEquals(0, api.visits)
            assertFalse(context.getSharedPreferences("first_party_analytics", 0).contains("installation_id"))
        }
        compose.onNodeWithText("Разрешить").performClick()
        compose.waitUntil { api.visits == 1 }
        compose.onNodeWithText("Отозвать").performClick()
        compose.onNodeWithText("Сбор отключён").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, api.visits)
            assertFalse(context.getSharedPreferences("first_party_analytics", 0).contains("installation_id"))
            assertFalse(context.getSharedPreferences("first_party_analytics", 0).contains("guest_token"))
        }
    }

    @Test fun refusalPersistsWithoutAnyVisit() {
        compose.setContent { KolbaTheme {
            AnalyticsConsentCard(api)
            VisitorAnalyticsLifecycle(api, active = true, identityRevision = 0)
        } }
        compose.onNodeWithText("Отказаться").performClick()
        compose.runOnIdle {
            assertEquals(0, api.visits)
            assertEquals(AnalyticsConsent.DENIED, VisitorAnalytics(context).consent.value)
        }
    }

    @Test fun transportFailureKeepsUiAndConsentUsable() {
        api.failVisits = true
        api.analytics.choose(AnalyticsConsent.GRANTED)
        compose.setContent { KolbaTheme {
            AnalyticsConsentCard(api)
            VisitorAnalyticsLifecycle(api, active = true, identityRevision = 0)
        } }
        compose.waitUntil { api.visits == 1 }
        compose.onNodeWithText("Сбор разрешён").assertIsDisplayed()
        compose.onNodeWithText("Отозвать").performClick()
        compose.onNodeWithText("Сбор отключён").assertIsDisplayed()
        compose.runOnIdle { assertEquals(AnalyticsConsent.DENIED, api.analytics.consent.value) }
    }

    @Test fun adminShowsFourPeriodsAndRefreshesWithoutCountingAnAdminVisit() {
        api.analytics.choose(AnalyticsConsent.GRANTED)
        compose.setContent { KolbaTheme { Column {
            VisitorAnalyticsLifecycle(api, active = false, identityRevision = 0)
            AdminDashboardScreen(api, {})
        } } }
        compose.onNodeWithText("Посещаемость").assertExists()
        compose.onNodeWithTag("dashboard-visitors").performScrollTo().assertIsDisplayed()
        screenshot("admin-visitors")
        listOf("Сегодня", "7 дней", "30 дней", "Всё время", "Всего", "Сайт", "Android").forEach {
            compose.onNodeWithText(it, substring = false).assertExists()
        }
        compose.runOnIdle { assertEquals(0, api.visits) }
        compose.onNodeWithText("Обновить").performScrollTo().performClick()
        compose.waitUntil { api.statsReads >= 2 }
        compose.onNodeWithText("120", substring = false).assertExists()
        compose.runOnIdle { assertEquals(0, api.visits) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        awaitPresentedUiFrame(instrumentation)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "analytics/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

private class AnalyticsSmokeApi(context: Context) : ApiClient(context, "https://analytics.invalid") {
    @Volatile var visits = 0
    @Volatile var statsReads = 0
    var failVisits = false
    override suspend fun reportAnalytics(visit: AnalyticsVisit, current: () -> Boolean): JSONObject? {
        if (!current()) return null
        visits++
        if (failVisits) throw java.io.IOException("Simulated offline transport")
        return JSONObject().put("guest_token", "private-test-proof")
    }
    override suspend fun get(path: String): JSONObject = when (path) {
        "/api/admin/analytics" -> {
            statsReads++
            JSONObject("""{"timezone":"Europe/Moscow","periods":{
              "day":{"total":1,"web":1,"android":1},"week":{"total":2,"web":2,"android":1},
              "month":{"total":3,"web":3,"android":1},"all":{"total":4,"web":4,"android":1}}}""")
        }
        "/api/admin/dashboard" -> JSONObject("""{"kpi":{"users_total":120}}""")
        else -> error("Unexpected API read: $path")
    }
}
