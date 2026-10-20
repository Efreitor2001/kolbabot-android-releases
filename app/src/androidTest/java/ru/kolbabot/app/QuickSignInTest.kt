package ru.kolbabot.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList

/** Isolated browser handoff tests: no real network, browser launch, or provider account is used. */
@RunWith(AndroidJUnit4::class)
class QuickSignInTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val origin = "https://quick-sign-in.invalid"

    @Before fun clearPending() { MobileSignInStore(context, origin).clear() }
    @After fun removePending() { MobileSignInStore(context, origin).clear() }

    @Test fun googleRequiresAllConsentsAndUsesDirectProviderHandoff() {
        val api = QuickSignInApi(context, origin)
        val opened = CopyOnWriteArrayList<String>()
        lateinit var lifecycle: SignInLifecycleOwner
        compose.runOnIdle { lifecycle = SignInLifecycleOwner() }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
            KolbaTheme { AuthScreen(api, onOpenBrowser = { opened.add(it) }) {} }
        } }
        compose.waitUntil { compose.onAllNodesWithTag("auth-google").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("oauth-pd").assertExists()
        compose.onNodeWithTag("auth-tab-login").performScrollTo()
        // Let the renderer present the loaded provider state before the full-window screenshot.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        screenshot("01-native-login-top")
        compose.onNodeWithTag("auth-google").performScrollTo().performClick()
        compose.onNodeWithText("Отметьте согласия перед входом через Google.").assertExists()
        assertTrue(api.posts.isEmpty())
        assertTrue(opened.isEmpty())
        listOf("oauth-pd", "oauth-cookie", "oauth-adult").forEach {
            compose.onNodeWithTag(it).performScrollTo().performClick().assertIsOn()
        }
        compose.onNodeWithTag("auth-google").performScrollTo()
        screenshot("02-native-login-providers")
        compose.onNodeWithTag("auth-google").performClick()
        compose.waitUntil { opened.size == 1 }
        val url = URI(opened.single())
        assertEquals(origin, "${url.scheme}://${url.host}")
        assertEquals("/api/auth/google/start", url.path)
        val query = url.rawQuery.split('&').associate { part ->
            val pieces = part.split('=', limit = 2)
            pieces[0] to URLDecoder.decode(pieces[1], "UTF-8")
        }
        assertEquals("/mobile/auth/approve?flow=${api.flowId}", query["next"])
        listOf("pd_consent", "cookie_consent", "age_consent").forEach { assertEquals("1", query[it]) }
        val pending = MobileSignInStore(context, origin).read()!!
        assertEquals("google", pending.provider)
        assertFalse(opened.single().contains(pending.verifier))
        assertEquals(ApiContract.challenge(pending.verifier), api.posts.single().second.getString("code_challenge"))
        assertEquals("S256", api.posts.single().second.getString("code_challenge_method"))
        // STARTED background state never polls; the resumed host owns the first check.
        assertEquals(listOf("/api/mobile/auth/start"), api.posts.map { it.first })
        compose.runOnIdle { lifecycle.moveTo(Lifecycle.State.RESUMED) }
        compose.waitUntil { api.posts.any { it.first == "/api/mobile/auth/exchange" } }
        compose.onNodeWithText("Подтвердите вход в браузере").performScrollTo().assertIsDisplayed()
        screenshot("03-native-login-pending")
        compose.runOnIdle { lifecycle.moveTo(Lifecycle.State.STARTED) }
    }

    @Test fun fallbackUsesOneHonestSiteChooserAndRemainsAvailableWithoutProviders() {
        val api = QuickSignInApi(context, origin, providers = false)
        val opened = CopyOnWriteArrayList<String>()
        lateinit var lifecycle: SignInLifecycleOwner
        compose.runOnIdle { lifecycle = SignInLifecycleOwner() }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
            KolbaTheme { AuthScreen(api, onOpenBrowser = { opened.add(it) }) {} }
        } }
        compose.onNodeWithTag("auth-tab-login").assertIsSelected()
        compose.onNodeWithTag("auth-tab-register").performClick().assertIsSelected()
        compose.onNodeWithText("Начнём знакомство").assertExists()
        compose.onNodeWithTag("auth-tab-reset").performClick().assertIsSelected()
        compose.onNodeWithText("Вернём доступ").assertExists()
        compose.onNodeWithTag("auth-tab-login").performClick()
        compose.onNodeWithText("Открыть быстрый вход").performScrollTo().performClick()
        compose.waitUntil { opened.size == 1 }
        assertEquals("$origin/mobile/auth/approve?flow=${api.flowId}", opened.single())
        assertEquals("site", MobileSignInStore(context, origin).read()!!.provider)
        compose.onNodeWithTag("auth-google").assertDoesNotExist()
    }

    @Test fun encryptedPendingFlowSurvivesStoreRecreationButNeverExtendsExpiry() {
        val api = QuickSignInApi(context, origin)
        val verifier = "v".repeat(64)
        val now = System.currentTimeMillis()
        val flow = MobileSignIn.start(origin, api.startResponse(), verifier, "google", now)
        MobileSignInStore(context, origin).write(flow)
        val encrypted = context.getSharedPreferences("pending_mobile_sign_in", Context.MODE_PRIVATE).getString("value", "")!!
        assertTrue(encrypted.isNotBlank())
        assertFalse(encrypted.contains(verifier))
        assertFalse(encrypted.contains(flow.flowId))
        val restored = MobileSignInStore(context, origin).read()!!
        assertEquals(flow, restored)
        assertNull(MobileSignInStore(context, "https://other.invalid").read())
        MobileSignInStore(context, origin).write(flow.copy(expiresAt = now - 1))
        assertNull(MobileSignInStore(context, origin).read())
        assertNull(context.getSharedPreferences("pending_mobile_sign_in", Context.MODE_PRIVATE).getString("value", null))
    }

    @Test fun registrationKeepsTheWebsiteFieldsAndHidesConsentsOnlyAfterRequestingCode() {
        val api = QuickSignInApi(context, origin, providers = false)
        var authenticated: JSONObject? = null
        compose.setContent { KolbaTheme { AuthScreen(api, onOpenBrowser = { error("No browser in form test") }) { authenticated = it } } }
        compose.onNodeWithTag("auth-tab-register").performClick()
        compose.onNodeWithText("Код друга, если есть").assertDoesNotExist()
        enter("Логин", "new-member")
        enter("Email", "member@example.invalid")
        enter("Пароль", "valid-password")
        enter("Повторите пароль", "valid-password")
        closeSoftKeyboard()
        listOf("register-pd", "register-cookie", "register-adult").forEach { compose.onNodeWithTag(it).performScrollTo().performClick() }
        compose.onNodeWithText("Отправить код", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Код подтверждения из email").assertExists()
        listOf("Логин", "Email", "Пароль", "Повторите пароль").forEach { compose.onNode(hasSetTextAction() and hasText(it)).assertExists() }
        listOf("register-pd", "register-cookie", "register-adult").forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
        assertEquals(listOf("/api/auth/register"), api.posts.map { it.first })
        val submitted = api.posts.single().second
        assertEquals("valid-password", submitted.getString("password"))
        assertEquals("", submitted.getString("referral_code"))
        listOf("pd_consent", "cookie_consent", "age_consent").forEach { assertTrue(submitted.getBoolean(it)) }
        enter("Код подтверждения из email", "123456")
        closeSoftKeyboard()
        screenshot("04-native-register-code")
        compose.onNodeWithText("Подтвердить и зарегистрироваться").performScrollTo().performClick()
        compose.waitUntil { authenticated != null }
        assertEquals("/api/auth/register/confirm", api.posts.last().first)
        assertEquals("new-member", api.posts.last().second.getString("username"))
        assertEquals("member@example.invalid", api.posts.last().second.getString("email"))
        assertEquals("123456", api.posts.last().second.getString("code"))
    }

    @Test fun passwordResetKeepsEmailThenCodeThenPasswordFieldsInWebsiteOrder() {
        val api = QuickSignInApi(context, origin, providers = false)
        compose.setContent { KolbaTheme { AuthScreen(api, onOpenBrowser = { error("No browser in form test") }) {} } }
        compose.onNodeWithTag("auth-tab-reset").performClick()
        enter("Email", "member@example.invalid")
        closeSoftKeyboard()
        compose.onNodeWithText("Отправить код сброса").performScrollTo().performClick()
        compose.onNodeWithText("Код из email").assertExists()
        val labels = listOf("Email", "Код из email", "Новый пароль", "Повторите новый пароль")
        val positions = labels.map { compose.onNode(hasSetTextAction() and hasText(it)).getUnclippedBoundsInRoot().top.value }
        assertEquals(positions.sorted(), positions)
        enter("Код из email", "12345678")
        enter("Новый пароль", "replacement-password")
        enter("Повторите новый пароль", "replacement-password")
        closeSoftKeyboard()
        screenshot("05-native-reset-code")
        compose.onNodeWithText("Сбросить пароль").performScrollTo().performClick()
        compose.onNodeWithText("Добро пожаловать").assertExists()
        assertEquals(listOf("/api/auth/password-reset/request", "/api/auth/password-reset/confirm"), api.posts.map { it.first })
        assertEquals("member@example.invalid", api.posts.last().second.getString("email"))
        assertEquals("12345678", api.posts.last().second.getString("code"))
        assertEquals("replacement-password", api.posts.last().second.getString("new_password"))
        assertEquals("replacement-password", api.posts.last().second.getString("confirm_password"))
    }

    private fun enter(label: String, value: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        awaitPresentedUiFrame(instrumentation)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val directory = File(context.getExternalFilesDir(null), "quick-sign-in").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

private class SignInLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    init { registry.currentState = Lifecycle.State.STARTED }
    fun moveTo(state: Lifecycle.State) { registry.currentState = state }
}

private class QuickSignInApi(context: Context, origin: String, private val providers: Boolean = true) : ApiClient(context, origin) {
    val posts = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    val flowId = "q".repeat(43)
    fun startResponse() = JSONObject().put("flow_id", flowId).put("authorization_url", "/mobile/auth/approve?flow=$flowId")
        .put("display_code", "123-456").put("expires_in", 300).put("interval", 2)
    override suspend fun get(path: String): JSONObject = when(path) {
        "/api/auth/google/config", "/api/auth/vk/config" -> JSONObject().put("enabled", providers)
        else -> throw AssertionError("Unexpected GET $path")
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        posts.add(path to JSONObject(body.toString()))
        return when(path) {
            "/api/mobile/auth/start" -> startResponse()
            "/api/mobile/auth/exchange" -> JSONObject().put("pending", true)
            "/api/auth/register", "/api/auth/password-reset/request", "/api/auth/password-reset/confirm" -> JSONObject().put("ok", true)
            "/api/auth/register/confirm" -> JSONObject().put("user", JSONObject().put("id", 44).put("username", "new-member"))
            else -> throw AssertionError("Unexpected POST $path")
        }
    }
}
