package ru.kolbabot.app

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class AdminToolsPanelTest {
    @get:Rule val compose = createComposeRule()
    @Test fun toolsKeepInlineFormsAndStatsAndBonusUseExistingApi() {
        val api = ToolsPanelApi(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent { KolbaTheme { CompositionLocalProvider(LocalSiteAdmin provides true) { AdminToolsPanel(api, {}) } } }
        listOf("Технические работы", "Бонус за регистрацию", "Аналитика", "Синхронизация цен", "Оформление сайта", "Тарифы и скидки", "Промокоды", "Импорт данных", "Миграция БД")
            .forEach { compose.onNodeWithTag("tool-$it").assertExists() }
        compose.onNodeWithText("Открыть").assertDoesNotExist()
        compose.onNodeWithText("Сохранить бонус").performScrollTo().performClick()
        compose.waitUntil { api.bonus != null }
        assertEquals(7, api.bonus!!.getInt("sausages"))
        assertTrue(api.bonus!!.getBoolean("is_active"))
        compose.onNodeWithTag("site-select-Scope статистики").performScrollTo().performClick()
        compose.onNodeWithTag("site-option-ad").performClick()
        compose.onNodeWithText("Обновить статистику").performScrollTo().performClick()
        compose.waitUntil { api.reads.contains("/api/admin/stats?scope=ad") }
        compose.onNodeWithText("Клики: 4", substring = true).assertExists()
        compose.onNodeWithText("Обновить / перенести БД").performScrollTo().performClick()
        compose.onNodeWithText("Выполнить перенос базы?").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        assertEquals(0, api.posts)
    }
}

private class ToolsPanelApi(context: Context) : ApiClient(context, "https://tools-panel.invalid") {
    val reads = CopyOnWriteArrayList<String>()
    var bonus: JSONObject? = null
    var posts = 0
    override suspend fun get(path: String): JSONObject {
        reads += path
        return when (path) {
            "/api/admin/bootstrap" -> JSONObject("""{"maintenance":{"forced":false},"registration_bonus_settings":{"is_active":true,"sausages":7},"site_social_links":{}}""")
            "/api/admin/subscriptions/plans", "/api/admin/promocodes" -> JSONObject("""{"items":[]}""")
            "/api/admin/stats?scope=ad" -> JSONObject("""{"total_clicks":4,"unique_users":2,"unique_buttons":3}""")
            else -> error("Unexpected GET $path")
        }
    }
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String,String>): JSONObject {
        check(path == "/api/admin/settings/registration-bonus"); bonus = JSONObject(body.toString()); return JSONObject()
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String,String>): JSONObject { posts++; error("Unexpected POST $path") }
}
