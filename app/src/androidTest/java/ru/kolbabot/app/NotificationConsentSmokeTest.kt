package ru.kolbabot.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** No real SDK, OS dialog, worker or network registration is invoked by these consent tests. */
@RunWith(AndroidJUnit4::class)
class NotificationConsentSmokeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: IsolatedPushContext
    private lateinit var api: ConsentFakeApi
    private lateinit var platform: ConsentFakePlatform
    private val permissionResults = CopyOnWriteArrayList<(Boolean) -> Unit>()

    @Before fun setUp() {
        context = IsolatedPushContext(InstrumentationRegistry.getInstrumentation().targetContext)
        context.getSharedPreferences("kolbabot_push_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("secure_session", Context.MODE_PRIVATE).edit().clear().commit()
        api = ConsentFakeApi(context)
        platform = ConsentFakePlatform()
    }

    private fun setConsentContent(content: @Composable () -> Unit) {
        compose.setContent {
            // Keep the real host's permission registry while isolating preference storage.
            // An Application-backed ContextWrapper cannot supply an ActivityResult owner.
            val activityResultOwner = checkNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides activityResultOwner,
            ) { KolbaTheme { Column(Modifier.verticalScroll(rememberScrollState())) { content() } } }
        }
    }

    @Test fun firstLaunchRequestsPermissionEvenBeforeLoginAndDoesNotRegisterAGuest() {
        platform.permissionAllowed = false; platform.permissionNeeded = true
        setConsentContent { NotificationStartup(api, 0, platform) { permissionResults += it } }
        compose.waitUntil { permissionResults.size == 1 }
        assertTrue(api.posts.isEmpty())
        compose.onNodeWithText("Включить уведомления?").assertDoesNotExist()
        compose.runOnIdle {
            platform.permissionAllowed = true; platform.permissionNeeded = false
            permissionResults.single()(true)
        }
        compose.waitForIdle()
        assertTrue(platform.enabledFor.isEmpty())
        assertTrue(api.posts.isEmpty())
    }

    @Test fun verifiedLoginConnectsByDefaultButWaitsForTheServerAcknowledgement() {
        api.switchUser(42)
        platform.registrationGate = CompletableDeferred()
        setConsentContent {
            NotificationStartup(api, 42, platform) { permissionResults += it }
            NotificationPreferences(api, platform = platform)
        }
        compose.waitUntil { platform.enabledFor.isNotEmpty() }
        compose.onNodeWithTag("push-opt-in").assertIsOn()
        compose.onNodeWithTag("push-server-status").assertTextEquals("Подключение к серверу не завершено")
        assertTrue(api.posts.isEmpty())
        compose.runOnIdle { platform.registrationGate!!.complete(Unit) }
        compose.waitUntil { api.posts.size == 1 }
        compose.onNodeWithTag("push-server-status").assertTextEquals("Подключено к серверу для этого аккаунта")
        assertTrue(permissionResults.isEmpty())
    }

    @Test fun permissionRefusalIsNotRepeatedOnStartupAndManualRetryRemainsAvailable() {
        api.switchUser(42)
        platform.permissionAllowed = false; platform.permissionNeeded = true
        var mounted by mutableStateOf(true)
        var settings by mutableStateOf(false)
        setConsentContent {
            if (settings) NotificationPreferences(api, platform = platform, requestPermission = { permissionResults += it })
            else if (mounted) NotificationStartup(api, 42, platform) { permissionResults += it }
        }
        compose.waitUntil { permissionResults.size == 1 }
        compose.runOnIdle { permissionResults.single()(false); mounted = false }
        compose.runOnIdle { mounted = true }
        compose.waitForIdle()
        assertEquals(1, permissionResults.size)
        assertTrue(platform.enabledFor.isEmpty())
        compose.runOnIdle { settings = true }
        compose.onNodeWithTag("push-opt-in").assertIsOn()
        compose.onNodeWithTag("push-server-status").assertTextEquals("Ожидаем разрешение Android")
        compose.onNodeWithText("Разрешить уведомления", substring = false).performScrollTo().performClick()
        assertEquals(2, permissionResults.size)
        compose.runOnIdle {
            platform.permissionAllowed = true; platform.permissionNeeded = false
            permissionResults.last()(true)
        }
        compose.waitUntil { api.posts.size == 1 }
        compose.onNodeWithTag("push-server-status").assertTextEquals("Подключено к серверу для этого аккаунта")
    }

    @Test fun switchingOffIsRememberedButAnotherAccountIsEnabledByDefault() {
        api.switchUser(42)
        var settings by mutableStateOf(true)
        var verified by mutableLongStateOf(42)
        setConsentContent {
            if (settings) NotificationPreferences(api, platform = platform)
            else NotificationStartup(api, verified, platform) { permissionResults += it }
        }
        compose.onNodeWithTag("push-opt-in").assertIsOn().performClick()
        compose.onNodeWithTag("push-opt-in").assertIsOff()
        compose.runOnIdle { settings = false }
        compose.waitForIdle()
        assertTrue(platform.enabledFor.isEmpty())
        compose.runOnIdle { api.switchUser(43); verified = 43 }
        compose.waitUntil { api.posts.size == 1 }
        assertEquals(listOf(43L), platform.enabledFor.toList())
        compose.runOnIdle { api.switchUser(42); verified = 42 }
        compose.waitForIdle()
        assertEquals(listOf(43L), platform.enabledFor.toList())
    }

    @Test fun anUnconfiguredServerDoesNotStartTheSdkAndKeepsTheDefaultOnPreference() {
        api.switchUser(42); api.serverReady = false
        setConsentContent {
            NotificationStartup(api, 42, platform) { permissionResults += it }
            NotificationPreferences(api, platform = platform)
        }
        compose.waitUntil { platform.retries > 0 }
        compose.onNodeWithTag("push-opt-in").assertIsOn()
        compose.onNodeWithTag("push-server-status").assertTextEquals("Подключение к серверу не завершено")
        assertEquals(0, platform.tokenRequests)
        assertTrue(api.posts.isEmpty())
        assertEquals(42L, context.getSharedPreferences("kolbabot_push_v1", Context.MODE_PRIVATE).getLong("desired_user", 0))
        assertEquals(0L, context.getSharedPreferences("kolbabot_push_v1", Context.MODE_PRIVATE).getLong("bound_user", 0))
    }

    @Test fun permissionReturningAfterLogoutWaitsForANewVerifiedLogin() {
        api.switchUser(42)
        platform.permissionAllowed = false; platform.permissionNeeded = true
        var verified by mutableLongStateOf(42)
        setConsentContent { NotificationStartup(api, verified, platform) { permissionResults += it } }
        compose.waitUntil { permissionResults.size == 1 }
        compose.runOnIdle { api.switchUser(0); verified = 0 }
        compose.runOnIdle {
            platform.permissionAllowed = true; platform.permissionNeeded = false
            permissionResults.single()(true)
        }
        compose.waitForIdle()
        assertTrue(api.posts.isEmpty())
        compose.runOnIdle { api.switchUser(43) }
        compose.waitForIdle()
        assertTrue(api.posts.isEmpty())
        compose.runOnIdle { verified = 43 }
        compose.waitUntil { api.posts.size == 1 }
        assertEquals(listOf(43L), platform.enabledFor.toList())
    }

    @Test fun aSavedOldBindingIsNotReportedConnectedWhileServerReregistrationIsPending() {
        api.switchUser(42)
        context.getSharedPreferences("kolbabot_push_v1", Context.MODE_PRIVATE).edit()
            .putLong("desired_user", 42).putLong("bound_user", 42)
            .putString("registered_token", "old-isolated-token").putString("latest_token", "old-isolated-token")
            .putBoolean("registration_pending", true).commit()
        setConsentContent { NotificationPreferences(api, platform = platform) }
        compose.onNodeWithTag("push-server-status").assertTextEquals("Подключение к серверу не завершено")
        assertTrue(api.posts.isEmpty())
        assertTrue(platform.enabledFor.isEmpty())
    }
}

private class IsolatedPushContext(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("notification_consent_smoke_$name", mode)
}

private class ConsentFakeApi(context: Context) : ApiClient(context, "https://notification-consent.invalid") {
    @Volatile var serverReady = true
    val posts = CopyOnWriteArrayList<String>()
    fun switchUser(id: Long) { userId = id }
    override suspend fun get(path: String): JSONObject {
        check(path == "/api/android/push/config")
        return JSONObject().put("enabled", serverReady)
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        check(path == "/api/android/push/token" && serverReady)
        posts += path
        return JSONObject().put("registered", true)
    }
}

private class ConsentFakePlatform : PushSettingsPlatform {
    @Volatile var permissionAllowed = true
    @Volatile var permissionNeeded = false
    @Volatile var retries = 0
    @Volatile var tokenRequests = 0
    var registrationGate: CompletableDeferred<Unit>? = null
    val enabledFor = CopyOnWriteArrayList<Long>()
    override fun allowed(context: Context) = permissionAllowed
    override fun needsPermission(context: Context) = permissionNeeded
    override fun problem(context: Context) = ""
    override suspend fun verify(context: Context, api: ApiClient) {
        if (!api.get("/api/android/push/config").optBoolean("enabled")) throw ApiException(503, "Служба push-уведомлений пока не подключена на сервере.")
    }
    override suspend fun enable(context: Context, api: ApiClient, uid: Long) {
        check(permissionAllowed && api.userId == uid)
        enabledFor += uid
        val prefs = context.getSharedPreferences("kolbabot_push_v1", Context.MODE_PRIVATE)
        prefs.edit().putLong("desired_user", uid).remove("bound_user").remove("registered_token").commit()
        verify(context, api)
        tokenRequests++
        registrationGate?.await()
        check(api.userId == uid)
        check(api.post("/api/android/push/token", JSONObject().put("token", "isolated-token-not-a-real-device")).optBoolean("registered"))
        prefs.edit().putLong("bound_user", uid).putString("latest_token", "isolated-token").putString("registered_token", "isolated-token").commit()
    }
    override suspend fun disable(context: Context, api: ApiClient) {
        context.getSharedPreferences("kolbabot_push_v1", Context.MODE_PRIVATE).edit()
            .remove("desired_user").remove("bound_user").remove("registered_token").commit()
    }
    override fun retry(context: Context) { retries++ }
}
