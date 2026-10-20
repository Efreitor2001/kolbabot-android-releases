package ru.kolbabot.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Navigation uses isolated fixtures; every mutation fails immediately. */
@RunWith(AndroidJUnit4::class)
class SiteNavigationSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val publicSections = listOf("store", "recipes", "custom", "handbook", "articles", "equipment", "faq", "support", "account")

    @Test fun bottomBarUsesTheSitesExactOrderLabelsAndGlyphs() {
        var selected by mutableStateOf("store")
        compose.setContent { KolbaTheme { SiteBottomNavigation(selected, false) { selected = it } } }
        val sections = listOf("store", "recipes", "custom", "account", "more")
        val labels = listOf("Магазин", "Рецепты", "Расчёт", "Кабинет", "Ещё")
        val positions = sections.mapIndexed { index, section ->
            compose.onNodeWithTag("site-bottom-$section").assertTextContains(labels[index]).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left
        }
        assertEquals(positions.sorted(), positions)
        listOf("≡", "∑", "•••").forEach { compose.onNodeWithText(it, substring = false).assertExists() }
        compose.onNodeWithText("Знания").assertDoesNotExist()
        compose.onNodeWithTag("site-bottom-custom").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("custom", selected) }
    }

    @Test fun moreKeepsSiteOrderAndOpensNativePublicSections() {
        val api = NavigationFakeApi(context)
        compose.setContent { KolbaTheme { KolbaApp(api, enablePushStartup = false) } }
        compose.onNodeWithTag("site-bottom-more").performClick()
        compose.onNodeWithText("Навигация").assertIsDisplayed()
        compose.onNodeWithText("Все разделы").assertIsDisplayed()
        assertEquals(publicSections, menuSections())
        compose.onNodeWithTag("site-more-admin").assertDoesNotExist()
        compose.onNodeWithTag("site-more-paywall").assertDoesNotExist()
        val routes = listOf("handbook" to "/api/handbook", "articles" to "/api/articles", "equipment" to "/api/equipment", "faq" to "/api/faq")
        routes.forEachIndexed { index, (section, path) ->
            if (index > 0) compose.onNodeWithTag("site-bottom-more").performClick()
            compose.onNodeWithTag("site-more-$section").performScrollTo().performClick()
            compose.waitUntil { api.reads.contains(path) }
            compose.onNodeWithText("Все разделы").assertDoesNotExist()
            compose.onNodeWithTag("site-bottom-more").assertIsSelected()
        }
        compose.onNodeWithTag("site-bottom-more").performClick()
        compose.onNodeWithTag("site-more-custom").performScrollTo().performClick()
        compose.onNodeWithText("Свой состав", substring = false).assertExists()
        compose.onNodeWithTag("site-bottom-custom").assertIsSelected()
        compose.onNodeWithTag("site-bottom-more").performClick()
        compose.onNodeWithTag("site-more-support").performScrollTo().performClick()
        compose.onNodeWithText("Добро пожаловать", substring = false).assertExists()
        assertTrue(api.reads.none { it.startsWith("/api/admin/") || it == "/api/access/status" || it == "/api/support/tickets/my" })
    }

    @Test fun administratorEntryWaitsForFreshProofAndDisappearsAfterRevocation() {
        val api = NavigationFakeApi(context, 7, admin = true)
        api.authGate = CompletableDeferred()
        lateinit var owner: NavigationLifecycleOwner
        compose.runOnIdle { owner = NavigationLifecycleOwner() }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            KolbaTheme { SiteNavigationSheet(api, 7, "store", {}, {}) }
        } }
        compose.onNodeWithTag("site-more-admin").assertDoesNotExist()
        compose.runOnIdle { api.authGate!!.complete(Unit) }
        compose.onNodeWithTag("site-more-admin").performScrollTo().assertIsDisplayed().assertTextEquals("Админ")
        assertEquals(publicSections + "admin", menuSections())
        compose.runOnIdle { owner.moveTo(Lifecycle.State.CREATED); api.serverAdmin = false }
        compose.runOnIdle { owner.moveTo(Lifecycle.State.RESUMED) }
        compose.onNodeWithTag("site-more-admin").assertDoesNotExist()
        assertTrue(api.reads.count { it == "/api/auth/me" } >= 2)
        assertTrue(api.reads.none { it.startsWith("/api/admin/") })
    }

    @Test fun accessEntryUsesConfirmedServerAccessAndCannotExposeAdministration() {
        val api = NavigationFakeApi(context, 42, admin = false).apply { canUseApp = false }
        var selected = ""
        compose.setContent { KolbaTheme { SiteNavigationSheet(api, 42, "store", {}, { selected = it }) } }
        compose.onNodeWithTag("site-more-paywall").performScrollTo().assertIsDisplayed().assertTextEquals("Доступ")
        assertEquals(publicSections + "paywall", menuSections())
        compose.onNodeWithTag("site-more-admin").assertDoesNotExist()
        compose.onNodeWithTag("site-more-paywall").performClick()
        compose.runOnIdle { assertEquals("paywall", selected) }
        assertTrue(api.reads.contains("/api/access/status"))
        assertTrue(api.reads.none { it.startsWith("/api/admin/") })
    }

    @Test fun changingAccountCannotReuseTheMenuAdministratorRole() {
        val api = NavigationFakeApi(context, 7, admin = true)
        var ownerId by mutableLongStateOf(7)
        compose.setContent { KolbaTheme { SiteNavigationSheet(api, ownerId, "store", {}, {}) } }
        compose.onNodeWithTag("site-more-admin").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { api.switchUser(42, false); ownerId = 42 }
        compose.onNodeWithTag("site-more-admin").assertDoesNotExist()
        assertEquals(publicSections, menuSections())
        assertTrue(api.reads.none { it.startsWith("/api/admin/") })
    }

    private fun menuSections(): List<String> = compose.onAllNodes(SemanticsMatcher("site menu entry") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("site-more-") == true
    }).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag].removePrefix("site-more-") }
}

private class NavigationLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    init { registry.currentState = Lifecycle.State.RESUMED }
    fun moveTo(state: Lifecycle.State) { registry.currentState = state }
}

private class NavigationFakeApi(context: Context, id: Long = 0, admin: Boolean = false) : ApiClient(context, "https://navigation-smoke.invalid") {
    val reads = CopyOnWriteArrayList<String>()
    @Volatile var serverAdmin = admin
    @Volatile var canUseApp = true
    var authGate: CompletableDeferred<Unit>? = null
    init { userId = id }
    fun switchUser(id: Long, admin: Boolean) { userId = id; serverAdmin = admin }
    override suspend fun get(path: String): JSONObject {
        reads.add(path)
        return when (path) {
            "/api/auth/google/config", "/api/auth/vk/config" -> JSONObject().put("enabled", false)
            "/api/auth/me" -> {
                authGate?.await()
                JSONObject().put("user", if (userId > 0) JSONObject().put("id", userId).put("username", "Навигационный тест").put("is_admin", serverAdmin) else JSONObject.NULL)
            }
            "/api/access/status" -> { check(userId > 0); JSONObject().put("can_use_app", canUseApp) }
            "/api/store" -> JSONObject().put("products", JSONArray()).put("categories", JSONArray()).put("banners", JSONArray())
            "/api/handbook", "/api/articles", "/api/equipment", "/api/faq" -> JSONObject().put("items", JSONArray())
            else -> throw AssertionError("Unexpected GET $path")
        }
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected POST $path")
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PATCH $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")
}
