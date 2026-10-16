package ru.kolbabot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var openNotifications by mutableStateOf(false)
    private var incomingLink by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openNotifications = intent.getBooleanExtra(OPEN_NOTIFICATIONS_EXTRA, false)
        incomingLink = intent.dataString
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        val api = (application as KolbaApplication).api
        setContent { KolbaTheme { KolbaApp(api, openNotifications, incomingLink, { incomingLink = null; intent.data = null }) {
            openNotifications = false
            intent.removeExtra(OPEN_NOTIFICATIONS_EXTRA)
        } } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotifications = intent.getBooleanExtra(OPEN_NOTIFICATIONS_EXTRA, false)
        incomingLink = intent.dataString
    }
}

@Composable fun KolbaApp(api: ApiClient, openNotifications: Boolean = false, incomingLink: String? = null, onLinkOpened: () -> Unit = {}, enablePushStartup: Boolean = true, onNotificationsOpened: () -> Unit = {}) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var user by remember { mutableStateOf<JSONObject?>(null) }
    var verifiedPushUserId by remember { mutableLongStateOf(0) }
    var checking by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    val expiry by api.sessionExpiry.collectAsState()
    val screens = rememberSaveableStateHolder()
    var pendingProduct by rememberSaveable { mutableLongStateOf(0) }
    var pendingArticle by rememberSaveable { mutableLongStateOf(0) }
    var pendingOrder by rememberSaveable { mutableStateOf("") }
    var pendingSection by rememberSaveable { mutableStateOf("") }
    var selectedSection by rememberSaveable { mutableStateOf("store") }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var adminReturnTab by rememberSaveable { mutableIntStateOf(0) }
    var adminReturnSection by rememberSaveable { mutableStateOf("store") }
    val context = LocalContext.current
    fun leaveAdmin() {
        tab = adminReturnTab.takeIf { it in 0..4 } ?: 0
        selectedSection = adminReturnSection.takeUnless { it == "admin" }.orEmpty().ifBlank { "store" }
        pendingSection = ""; pendingProduct = 0; pendingArticle = 0; pendingOrder = ""
    }
    fun navigate(destination: AppDestination) {
        moreOpen = false
        if (destination.section == "admin" && tab != 5) {
            adminReturnTab = tab
            adminReturnSection = selectedSection
        }
        selectedSection = destination.section
        pendingProduct = destination.productId
        pendingArticle = destination.articleId
        pendingOrder = destination.orderId
        pendingSection = destination.section.takeUnless { it == "admin" }.orEmpty()
        tab = when (destination.section) {
            "recipes" -> 1; "handbook", "articles", "equipment", "faq" -> 2
            "custom" -> 3; "admin" -> 5; "account", "support", "notifications", "orders", "paywall" -> 4; else -> 0
        }
    }
    LaunchedEffect(api) { NativeNavigation.requests.collect { navigate(it) } }
    LaunchedEffect(incomingLink) {
        incomingLink?.let {
            val destination = nativeDestination(it, api.baseUrl)
            if (destination != null) navigate(destination) else openBrowser(context, it)
            onLinkOpened()
        }
    }
    LaunchedEffect(openNotifications) { if (openNotifications) navigate(AppDestination("notifications")) }
    OnResume { revision++ }
    LaunchedEffect(tab) { if (tab == 4 || tab == 5) revision++ }
    LaunchedEffect(expiry) { if (expiry > 0) { user = null; verifiedPushUserId = 0; tab = 4; selectedSection = "account"; moreOpen = false } }
    LaunchedEffect(revision) {
        try {
            user = api.get("/api/auth/me").optJSONObject("user")
            val verified = user
            verifiedPushUserId = if (verified != null && !verified.optBoolean("requires_pd_consent") && !verified.optBoolean("requires_2fa_setup"))
                verified.optLong("id").takeIf { it == api.userId } ?: 0 else 0
        }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: ApiException) { if (e.status == 401) { api.clearSession(); user = null; verifiedPushUserId = 0 } }
        catch (_: Exception) { /* Public catalog remains available during a temporary connection failure. */ }
        finally { checking = false }
    }
    BackHandler(tab != 0) {
        if (tab == 5) leaveAdmin() else {
            tab = 0; selectedSection = "store"
            pendingSection = ""; pendingProduct = 0; pendingArticle = 0; pendingOrder = ""
        }
    }
    Scaffold(bottomBar = {
        SiteBottomNavigation(selectedSection, moreOpen) { section ->
            if (section == "more") moreOpen = true else {
                moreOpen = false
                selectedSection = section
                pendingSection = ""; pendingProduct = 0; pendingArticle = 0; pendingOrder = ""
                // Preserve each native screen's saved draft when using the bottom bar.
                tab = when (section) { "recipes" -> 1; "custom" -> 3; "account" -> 4; else -> 0 }
            }
        }
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
              screens.SaveableStateProvider("${user?.optLong("id") ?: 0}_$tab") {
                when {
                    checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    user?.optBoolean("requires_pd_consent") == true -> ConsentScreen(api) { revision++ }
                    tab == 0 -> StoreScreen(api, pendingProduct, { pendingProduct = 0 }) { section -> navigate(AppDestination(section)) }
                    tab == 1 -> RecipesScreen(api, onOpenProduct = { navigate(AppDestination("store", productId = it)) })
                    tab == 2 -> KnowledgeScreen(api, onOpenAccount = { navigate(AppDestination("account")) },
                        initialArticleId = pendingArticle, onArticleOpened = { pendingArticle = 0 }, initialSection = pendingSection, onSectionOpened = { pendingSection = "" },
                        onSectionChanged = { selectedSection = it })
                    tab == 3 -> CustomCalculatorScreen(api)
                    user == null -> AuthScreen(api) { user = it; revision++ }
                    tab == 5 -> AdminScreen(api, ::leaveAdmin)
                    else -> AccountScreen(api, user!!, openNotifications || pendingSection == "notifications", {
                        pendingSection = ""; onNotificationsOpened()
                    }, initialSection = pendingSection, onSectionOpened = { pendingSection = "" }, initialOrderId = pendingOrder,
                        onOrderOpened = { pendingOrder = "" }, onSectionChanged = { selectedSection = it }) { user = null; verifiedPushUserId = 0; revision++ }
                }
              }
            }
        }
    }
    if (moreOpen) SiteNavigationSheet(api, user?.optLong("id") ?: 0L, selectedSection,
        onDismiss = { moreOpen = false }, onSelect = { navigate(AppDestination(it)) })
    if (enablePushStartup) NotificationStartup(api, if (checking) 0 else verifiedPushUserId)
}
