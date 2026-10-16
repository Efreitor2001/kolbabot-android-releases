package ru.kolbabot.app

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Real Android Back dispatch with isolated API fixtures. Every write fails immediately. */
@RunWith(AndroidJUnit4::class)
class SystemBackNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val accountId = 900000971L
    @Test fun registrationThenLoginThenCatalogAndMenuDismissInOrder() {
        val api = BackFakeApi(context)
        compose.setContent { KolbaTheme { KolbaApp(api, enablePushStartup = false) } }
        compose.onNodeWithTag("site-bottom-account").performClick()
        compose.onNodeWithText("Регистрация", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Начнём знакомство").assertExists()
        pressBack()
        compose.onNodeWithText("Добро пожаловать").assertExists()
        compose.onNodeWithTag("site-bottom-account").assertDoesNotExist()
        compose.onNodeWithTag("site-bottom-more").assertDoesNotExist()
        pressBack()
        compose.onNodeWithTag("site-bottom-store").assertIsSelected()
        compose.onNodeWithText("Добро пожаловать").assertDoesNotExist()
        compose.onNodeWithTag("site-bottom-more").performClick()
        compose.onNodeWithText("Все разделы").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Все разделы").assertDoesNotExist()
        compose.onNodeWithTag("site-bottom-store").assertIsSelected()
        compose.onNodeWithText("Добро пожаловать").assertDoesNotExist()
    }

    @Test fun authenticationFooterReturnsToTheNativeCatalog() {
        val api = BackFakeApi(context)
        compose.setContent { KolbaTheme { KolbaApp(api, enablePushStartup = false) } }
        compose.onNodeWithTag("site-bottom-account").performClick()
        compose.onNodeWithTag("site-bottom-account").assertDoesNotExist()
        compose.onNodeWithTag("auth-return-home").performScrollTo().performClick()
        compose.onNodeWithTag("site-bottom-store").assertIsSelected()
        compose.onNodeWithText("Добро пожаловать").assertDoesNotExist()
    }

    @Test fun separateAdminSectionReturnsThroughSearchAndListToPreviousAccount() {
        val api = BackFakeApi(context, accountId, admin = true)
        compose.setContent { KolbaTheme { KolbaApp(api, enablePushStartup = false) } }
        compose.onNodeWithTag("site-bottom-account").performClick()
        compose.onNodeWithText("Рады вас видеть,", substring = true).assertExists()
        compose.onNodeWithText("Админ", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Редактор баннеров").assertDoesNotExist()
        compose.onNodeWithTag("site-bottom-more").performClick()
        compose.onNodeWithTag("site-more-admin").performScrollTo().performClick()
        compose.onNodeWithText("Админ-редактор").assertExists()
        compose.onNodeWithText("Рады вас видеть,", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("site-bottom-more").assertDoesNotExist()
        compose.onNodeWithTag("admin-tab-users").performScrollTo().performClick()
        compose.onNodeWithTag("admin-user-picker").performScrollTo().performClick()
        compose.onNodeWithTag("admin-user-picker-search").performTextInput("никого")
        closeSoftKeyboard()
        pressBack()
        compose.onNodeWithTag("admin-user-picker-search").assertDoesNotExist()
        compose.onNodeWithText("Добавить администратора по логину").assertExists()
        compose.onNodeWithTag("admin-user-picker").performScrollTo().performClick()
        compose.onNodeWithTag("admin-user-picker-user-44").performClick()
        compose.onNodeWithText("Профиль пользователя #44").assertExists()
        compose.onNodeWithTag("admin-user-picker").assertExists()
        compose.onNodeWithText("Сохранить пользователя").assertExists()
        pressBack()
        compose.onNodeWithText("Админ-редактор").assertExists()
        pressBack()
        compose.onNodeWithText("Рады вас видеть,", substring = true).assertExists()
        compose.onNodeWithTag("site-bottom-account").assertIsSelected()
        compose.onNodeWithText("Админ-редактор").assertDoesNotExist()
        assertTrue(api.reads.any { it.startsWith("/api/admin/users?") })
    }

    @Test fun adminDeepLinkStillRequiresAFreshRoleAndReturnsToCatalog() {
        val api = BackFakeApi(context, accountId, admin = false)
        compose.setContent { KolbaTheme { KolbaApp(api, incomingLink = "https://back-smoke.invalid/admin", enablePushStartup = false) } }
        compose.waitUntil { api.reads.count { it == "/api/auth/me" } >= 2 }
        compose.onNodeWithTag("site-bottom-store").assertIsSelected()
        compose.onNodeWithText("Админ-редактор").assertDoesNotExist()
        assertTrue(api.reads.none { it.startsWith("/api/admin/") })
    }

    @Test fun accountNotificationsBackReturnsToAccountWithoutLeavingTheScreen() {
        val api = BackFakeApi(context, accountId)
        compose.setContent { KolbaTheme { AccountScreen(api, api.profile()) { error("Unexpected logout") } } }
        compose.onNodeWithText("Уведомления", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Непрочитанных: 0").assertExists()
        pressBack()
        compose.onNodeWithText("Рады вас видеть,", substring = true).assertExists()
        compose.onNodeWithText("Непрочитанных: 0").assertDoesNotExist()
    }

    @Test fun backDismissesDiscardConfirmationAndKeepsTheUnsavedDraft() {
        var closed by mutableStateOf(false)
        compose.setContent { KolbaTheme {
            if (closed) Text("Список материалов") else AdminEditForm("Редактор проверки", listOf(AdminField("title", "Название")),
                JSONObject().put("title", "Исходное"), { closed = true }) { error("Unexpected save") }
        } }
        compose.onNodeWithText("Название").performTextReplacement("Изменённое")
        closeSoftKeyboard()
        pressBack()
        compose.onNodeWithText("Оставить изменения несохранёнными?").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Оставить изменения несохранёнными?").assertDoesNotExist()
        compose.onNodeWithText("Изменённое").assertExists()
        compose.runOnIdle { assertFalse(closed) }
        pressBack()
        compose.onNodeWithText("Выйти без сохранения").performClick()
        compose.onNodeWithText("Список материалов").assertExists()
    }

    @Test fun supportDropdownBackDismissesOptionsBeforeLeavingThePage() {
        val api = BackFakeApi(context, accountId, admin = true)
        var closed by mutableStateOf(false)
        compose.setContent { KolbaTheme {
            if (closed) Text("Админ-меню") else AdminPeopleScreen(api, "support") { closed = true }
        } }
        compose.onNodeWithTag("site-select-Обращение").assertTextContains("#52 · Читатель")
        compose.onNodeWithText("Текст тестового обращения").assertExists()
        compose.onNodeWithTag("site-select-Обращение").performScrollTo().performClick()
        compose.onNodeWithTag("site-option-52").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("site-option-52").assertDoesNotExist()
        compose.runOnIdle { assertFalse(closed) }
        compose.onNodeWithText("Отправить ответ").assertExists()
        pressBack()
        compose.onNodeWithText("Админ-меню").assertExists()
    }

}

private class BackFakeApi(context: Context, id: Long = 0, private val admin: Boolean = false) : ApiClient(context, "https://back-smoke.invalid") {
    val reads = CopyOnWriteArrayList<String>()
    init { userId = id }
    fun profile() = JSONObject().put("id", userId).put("username", "Проверка навигации").put("is_admin", admin).put("admin_2fa_enabled", true)
    private fun listedUser() = JSONObject().put("id", 44).put("username", "Пользователь списка").put("is_active", true)
    override suspend fun get(path: String): JSONObject {
        reads.add(path)
        return when {
            path in setOf("/api/auth/google/config", "/api/auth/vk/config") -> JSONObject().put("enabled", false)
            path == "/api/auth/me" -> JSONObject().put("user", if (userId > 0) profile() else JSONObject.NULL)
            path == "/api/store" -> JSONObject().put("products", JSONArray()).put("categories", JSONArray()).put("banners", JSONArray())
            path == "/api/access/status" -> JSONObject().put("can_use_app", true)
            path == "/api/store/cart" -> JSONObject().put("items", JSONArray()).put("rewards", JSONObject())
            path.startsWith("/api/android/push/config") -> JSONObject().put("enabled", false).put("project_id", "")
            path == "/api/notifications" -> JSONObject().put("items", JSONArray()).put("unread_count", 0)
            path == "/api/account/billing" -> JSONObject().put("tier", "none").put("status", "inactive")
            path == "/api/bootstrap" -> JSONObject().put("payments", JSONObject().put("enabled", false))
            path == "/api/admin/dashboard" -> { check(admin); JSONObject() }
            path.startsWith("/api/admin/users?") -> { check(admin); JSONObject().put("items", JSONArray().put(listedUser())) }
            path == "/api/admin/users/44/dossier" -> { check(admin); JSONObject().put("tables", JSONArray().put(
                JSONObject().put("table", "miniapp_users").put("rows", JSONArray().put(listedUser())))) }
            path.startsWith("/api/admin/support/tickets?") -> { check(admin); JSONObject().put("items", JSONArray().put(
                JSONObject().put("id", 52).put("user_id", 44).put("username", "Читатель").put("question_text", "Текст тестового обращения").put("answer_text", ""))) }
            else -> throw AssertionError("Unexpected GET $path")
        }
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected POST $path")
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PATCH $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")
}
