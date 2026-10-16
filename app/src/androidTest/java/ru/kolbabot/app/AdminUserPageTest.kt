package ru.kolbabot.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Same-page user controls with isolated data; payment and network operations are forbidden. */
@RunWith(AndroidJUnit4::class)
class AdminUserPageTest {
    @get:Rule val compose = createComposeRule()
    private fun open(): AdminUserPageApi {
        val api = AdminUserPageApi(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent { KolbaTheme { AdminPeopleScreen(api, "users") { error("Unexpected navigation") } } }
        compose.onNodeWithTag("admin-user-picker").performClick()
        compose.onNodeWithTag("admin-user-picker-user-44").performClick()
        compose.onNodeWithText("Профиль пользователя #44").assertExists()
        return api
    }

    @Test fun profileAndControlsStayBelowPickerAndSavePreservesTheSelectedUser() {
        val api = open()
        compose.onNodeWithTag("admin-user-picker").assertTextContains("#44", substring = true)
        compose.onNodeWithText("Метод автоплатежа: sbp").assertExists()
        compose.onNodeWithText("Есть SBP токен: Да").assertExists()
        compose.onNodeWithText("Сохранить пользователя").assertExists()
        compose.onNodeWithText("opaque_dossier_marker: fixture-only").assertDoesNotExist()
        compose.onNodeWithTag("admin-dossier-miniapp_users-record-0").performScrollTo().performClick()
        compose.onNodeWithText("opaque_dossier_marker: fixture-only").assertExists()
        compose.onNodeWithTag("admin-user-picker").assertExists()
        compose.onNodeWithText("Логин", substring = false).performScrollTo().performTextReplacement("changed-reader")
        compose.onNodeWithText("Баланс колбасок").performScrollTo().performTextReplacement("150")
        closeSoftKeyboard()
        compose.onNodeWithText("Сохранить пользователя").performScrollTo().performClick()
        compose.waitUntil { api.writes.size == 1 }
        val write = api.writes.single()
        assertEquals("PATCH /api/admin/users/44", write.first)
        assertEquals("changed-reader", write.second.getString("username"))
        assertEquals(150, write.second.getInt("store_sausages"))
        assertFalse(write.second.has("password"))
        assertTrue(write.second.getBoolean("is_active"))
        assertFalse(write.second.getBoolean("is_admin"))
        compose.onNodeWithTag("admin-user-picker").assertTextContains("#44", substring = true)
        compose.onNodeWithText("Профиль пользователя #44").assertExists()
    }

    @Test fun accessModeDropdownAndConfirmationSendOnlyTheExistingAccessContract() {
        val api = open()
        compose.onNodeWithText("Часов доступа").performScrollTo().performTextReplacement("48")
        closeSoftKeyboard()
        compose.onNodeWithTag("site-select-Режим").performScrollTo().performClick()
        compose.onNodeWithTag("site-option-extend").performClick()
        compose.onNodeWithText("Применить доступ", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Применить доступ?").assertIsDisplayed()
        assertTrue(api.writes.isEmpty())
        compose.onNodeWithText("Подтвердить").performClick()
        compose.waitUntil { api.writes.size == 1 }
        val write = api.writes.single()
        assertEquals("POST /api/admin/users/44/access", write.first)
        assertEquals("premium", write.second.getString("tier"))
        assertEquals(48, write.second.getInt("duration_hours"))
        assertEquals("extend", write.second.getString("action"))
        assertEquals(3, write.second.length())
        compose.onNodeWithTag("admin-user-picker").assertTextContains("#44", substring = true)
        compose.onNodeWithText("Профиль пользователя #44").assertExists()
    }
}

private class AdminUserPageApi(context: Context) : ApiClient(context, "https://admin-user-page.invalid") {
    val writes = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private var username = "reader"
    private var sausages = 75
    init { userId = 900000719 }
    private fun profile() = JSONObject().put("id", 44).put("username", username).put("email", "reader@example.invalid")
        .put("is_admin", false).put("is_active", true).put("store_sausages", sausages)
        .put("subscription_autopay_method", "sbp").put("subscription_has_sbp", true)
    override suspend fun get(path: String): JSONObject = when (path) {
        "/api/admin/users?limit=2000" -> JSONObject().put("items", JSONArray().put(profile()))
        "/api/admin/users/44/dossier" -> JSONObject().put("generated_at", "2026-10-16 12:00:00")
            .put("tables", JSONArray().put(JSONObject().put("table", "miniapp_users").put("total", 1)
                .put("rows", JSONArray().put(profile().put("opaque_dossier_marker", "fixture-only")))))
        else -> throw AssertionError("Unexpected GET $path")
    }
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        check(path == "/api/admin/users/44")
        writes += "PATCH $path" to JSONObject(body.toString())
        username = body.getString("username"); sausages = body.getInt("store_sausages")
        return JSONObject().put("user", profile())
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        check(path == "/api/admin/users/44/access") { "Unexpected write $path" }
        writes += "POST $path" to JSONObject(body.toString())
        return JSONObject().put("ok", true)
    }
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")
}
