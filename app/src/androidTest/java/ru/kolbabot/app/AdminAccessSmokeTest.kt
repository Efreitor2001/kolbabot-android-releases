package ru.kolbabot.app

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Synthetic identities only. Every write fails the test; production is never contacted. */
@RunWith(AndroidJUnit4::class)
class AdminAccessSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assertNoAdminEntry() {
        listOf("Админ", "Редактор баннеров", "Настроить двухфакторный вход").forEach { label ->
            compose.onNodeWithText(label, substring = false).assertDoesNotExist()
        }
    }

    @Test fun ordinaryAccountHasNoAdministrativeEntryOrRequests() {
        val api = AccessFakeApi(context, 42, false)
        compose.setContent { KolbaTheme { AccountScreen(api, api.profile()) {} } }
        compose.onNodeWithText("Рады вас видеть,", substring = true).assertExists()
        compose.waitUntil { api.reads.contains("/api/auth/me") }
        assertNoAdminEntry()
        assertTrue(api.reads.none { it.startsWith("/api/admin/") })
    }

    @Test fun signedOutIdentityCannotUseACachedAdminProfile() {
        val api = AccessFakeApi(context, 0, false)
        val stale = JSONObject().put("id", 7).put("username", "Бывший вход").put("is_admin", true)
        compose.setContent { KolbaTheme { AccountScreen(api, stale) {} } }
        compose.onNodeWithText("Личный кабинет").assertExists()
        assertNoAdminEntry()
        compose.onNodeWithText("Бывший вход").assertDoesNotExist()
        assertTrue(api.reads.isEmpty())
    }

    @Test fun verifiedAdministratorUsesTheSeparateMenuInsteadOfAccountEntries() {
        val api = AccessFakeApi(context, 7, true)
        compose.setContent { KolbaTheme { AccountScreen(api, api.profile()) {} } }
        compose.onNodeWithText("Рады вас видеть,", substring = true).assertExists()
        compose.waitUntil { api.reads.contains("/api/auth/me") }
        assertNoAdminEntry()
        assertTrue(api.reads.all { it == "/api/auth/me" || it == "/api/android/push/config" || it == "/api/store/cart" || it == "/api/account/billing" || it == "/api/bootstrap" })
    }

    @Test fun rejectedSessionCannotExposeTheCachedProfileOrLoadItsBalance() {
        val api = AccessFakeApi(context, 7, true).apply { rejectAuth = true }
        var signedOut by mutableStateOf(false)
        val stale = JSONObject().put("id", 7).put("username", "Личный секретный профиль").put("is_admin", true)
        compose.setContent { KolbaTheme { if (signedOut) Text("Вход в аккаунт") else AccountScreen(api, stale) { signedOut = true } } }
        compose.onNodeWithText("Вход в аккаунт").assertExists()
        compose.onNodeWithText("Личный секретный профиль").assertDoesNotExist()
        assertNoAdminEntry()
        assertTrue(api.reads.all { it == "/api/auth/me" })
    }

    @Test fun directAdminScreenRejectsAnOrdinaryUserBeforeLoadingAnySection() {
        val api = AccessFakeApi(context, 42, false)
        var closed by mutableStateOf(false)
        compose.setContent { KolbaTheme { if (closed) Text("Обычный кабинет") else AdminScreen(api) { closed = true } } }
        compose.onNodeWithText("Обычный кабинет").assertExists()
        assertNoAdminEntry()
        assertTrue(api.reads.none { it.startsWith("/api/admin/") })
    }

    @Test fun restoredAdministrativeRouteMustReverifyTheRole() {
        val api = AccessFakeApi(context, 7, true)
        val restoration = StateRestorationTester(compose)
        var closed by mutableStateOf(false)
        restoration.setContent { KolbaTheme { if (closed) Text("Обычный кабинет") else AdminScreen(api) { closed = true } } }
        compose.onNodeWithTag("admin-tab-users").performScrollTo().performClick()
        compose.onNodeWithText("Добавить администратора по логину").assertExists()
        var previousRequests = 0
        var previousIdentityChecks = 0
        // RemotePage also refreshes when its lifecycle observer first receives ON_RESUME.
        // Establish the permission-change boundary only after those initial effects settle.
        compose.runOnIdle {
            previousRequests = api.adminReads.size
            previousIdentityChecks = api.reads.count { it == "/api/auth/me" }
            api.serverAdmin = false
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Обычный кабинет").assertExists()
        assertNoAdminEntry()
        assertTrue("Restoring the saved route must obtain fresh server identity proof.",
            api.reads.count { it == "/api/auth/me" } > previousIdentityChecks)
        assertEquals("A restored route must not reload an admin section after the role was revoked.", previousRequests, api.adminReads.size)
        assertTrue("Every administrative request must belong to the authorized identity: ${api.adminReads}",
            api.adminReads.all { it.userId == 7L && it.isAdmin })
    }

    @Test fun roleRevokedWhileInANestedScreenClosesItOnResume() {
        val api = AccessFakeApi(context, 7, true)
        var closed by mutableStateOf(false)
        lateinit var owner: AccessLifecycleOwner
        compose.runOnIdle { owner = AccessLifecycleOwner() }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            KolbaTheme { if (closed) Text("Раздел закрыт") else AdminScreen(api) { closed = true } }
        } }
        compose.onNodeWithTag("admin-tab-users").performScrollTo().performClick()
        compose.onNodeWithTag("admin-user-picker").assertExists()
        compose.waitUntil(timeoutMillis = 5_000) { api.reads.any { it.startsWith("/api/admin/users?") } }
        compose.runOnIdle { owner.moveTo(Lifecycle.State.CREATED); api.serverAdmin = false }
        compose.runOnIdle { owner.moveTo(Lifecycle.State.RESUMED) }
        compose.onNodeWithText("Раздел закрыт").assertExists()
        compose.onNodeWithText("Добавить администратора по логину").assertDoesNotExist()
        assertNoAdminEntry()
    }

    @Test fun changingAccountDropsAnOpenAdministrativeRoute() {
        val api = AccessFakeApi(context, 7, true)
        var closed by mutableStateOf(false)
        lateinit var owner: AccessLifecycleOwner
        compose.runOnIdle { owner = AccessLifecycleOwner() }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            KolbaTheme { if (closed) Text("Раздел закрыт") else AdminScreen(api) { closed = true } }
        } }
        compose.onNodeWithTag("admin-tab-users").performScrollTo().performClick()
        compose.onNodeWithText("Добавить администратора по логину").assertExists()
        var previousRequests = 0
        compose.runOnIdle {
            previousRequests = api.adminReads.size
            owner.moveTo(Lifecycle.State.CREATED)
            api.switchUser(42, false)
        }
        compose.runOnIdle { owner.moveTo(Lifecycle.State.RESUMED) }
        compose.onNodeWithText("Раздел закрыт").assertExists()
        assertNoAdminEntry()
        assertEquals("Switching accounts must not reuse the previous account's administrative route.", previousRequests, api.adminReads.size)
        assertTrue("Every administrative request must belong to the former authorized identity: ${api.adminReads}",
            api.adminReads.all { it.userId == 7L && it.isAdmin })
    }
}

private class AccessLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    init { registry.currentState = Lifecycle.State.RESUMED }
    fun moveTo(state: Lifecycle.State) { registry.currentState = state }
}

private data class AccessAdminRead(val path: String, val userId: Long, val isAdmin: Boolean)

private class AccessFakeApi(context: Context, id: Long, admin: Boolean) : ApiClient(context, "https://access-smoke.invalid") {
    val reads = CopyOnWriteArrayList<String>()
    val adminReads = CopyOnWriteArrayList<AccessAdminRead>()
    @Volatile var serverAdmin = admin
    @Volatile var rejectAuth = false
    init { userId = id }
    fun profile() = JSONObject().put("id", userId).put("username", "Проверочный пользователь").put("is_admin", serverAdmin).put("admin_2fa_enabled", true)
    fun switchUser(id: Long, admin: Boolean) { userId = id; serverAdmin = admin }
    override suspend fun get(path: String): JSONObject {
        reads.add(path)
        if (path.startsWith("/api/admin/")) adminReads.add(AccessAdminRead(path, userId, serverAdmin))
        return when {
            path == "/api/auth/me" -> if (userId > 0 && !rejectAuth) JSONObject().put("user", profile()) else throw ApiException(401, "Требуется вход")
            path == "/api/android/push/config" -> JSONObject().put("enabled", false).put("project_id", "")
            path == "/api/store/cart" -> JSONObject().put("rewards", JSONObject().put("sausages_kopecks", 0))
            path == "/api/account/billing" -> JSONObject().put("tier", "none").put("status", "inactive")
            path == "/api/bootstrap" -> JSONObject().put("payments", JSONObject().put("enabled", false))
            path == "/api/admin/dashboard" -> { check(serverAdmin); JSONObject() }
            path.startsWith("/api/admin/users?") -> { check(serverAdmin); JSONObject().put("items", JSONArray()) }
            else -> throw AssertionError("Unexpected GET $path")
        }
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected POST $path")
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PATCH $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")
}
