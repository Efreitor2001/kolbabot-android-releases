package ru.kolbabot.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated fixtures: selecting or searching a user must never send a mutation. */
@RunWith(AndroidJUnit4::class)
class AdminUserPickerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun duplicateNamesAreDisambiguatedAndSearchKeepsTheSelectedId() {
        val api = PickerFakeApi(context)
        var selected by mutableStateOf<Long?>(null)
        compose.setContent { KolbaTheme { AdminUserPicker(api, selected, { selected = it }) } }
        compose.onNodeWithTag("admin-user-picker").performClick()
        compose.onNodeWithTag("admin-user-picker-search").performTextInput("second@example.org")
        compose.onNodeWithTag("admin-user-picker-user-41").assertDoesNotExist()
        compose.onNodeWithTag("admin-user-picker-user-42").performClick()
        compose.runOnIdle { assertEquals(42L, selected) }
        compose.onNodeWithTag("admin-user-picker").assertTextContains("#42", substring = true)
        compose.onNodeWithTag("admin-user-picker").performClick()
        compose.onNodeWithTag("admin-user-picker-search").performTextInput("nobody")
        compose.onNodeWithText("Пользователи не найдены.").assertExists()
        compose.runOnIdle { assertEquals(42L, selected) }
    }

    @Test fun optionalFilterCanBeClearedWithoutSelectingTheFirstUser() {
        val api = PickerFakeApi(context)
        var selected by mutableStateOf<Long?>(42L)
        compose.setContent { KolbaTheme { AdminUserPicker(api, selected, { selected = it }, allowEmpty = true) } }
        compose.onNodeWithTag("admin-user-picker").performClick()
        compose.onNodeWithText("Все пользователи", substring = false).performClick()
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithTag("admin-user-picker").assertTextContains("Все пользователи", substring = true)
    }

    @Test fun anIdOutsideTheLoadedListIsResolvedBeforeItCanBeSelected() {
        val api = PickerFakeApi(context)
        var selected by mutableStateOf<Long?>(null)
        compose.setContent { KolbaTheme { AdminUserPicker(api, selected, { selected = it }) } }
        compose.onNodeWithTag("admin-user-picker").performClick()
        compose.onNodeWithTag("admin-user-picker-search").performTextInput("9001")
        compose.waitUntil { compose.onAllNodesWithTag("admin-user-picker-user-9001").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("admin-user-picker-user-9001").performClick()
        compose.runOnIdle { assertEquals(9001L, selected) }
    }
}

private class PickerFakeApi(context: Context) : ApiClient(context, "https://user-picker.invalid") {
    private fun user(id: Long, email: String) = JSONObject().put("id", id).put("username", "operator")
        .put("first_name", "Александр").put("email", email)
    override suspend fun get(path: String): JSONObject = when (path) {
        "/api/admin/users?limit=2000" -> JSONObject().put("items", JSONArray()
            .put(user(41, "first@example.org")).put(user(42, "second@example.org")))
        "/api/admin/users/9001/dossier" -> JSONObject().put("tables", JSONArray().put(JSONObject()
            .put("table", "miniapp_users").put("rows", JSONArray().put(user(9001, "outside@example.org")))))
        else -> error("Unexpected read: $path")
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = error("Unexpected write")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = error("Unexpected write")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = error("Unexpected write")
}
