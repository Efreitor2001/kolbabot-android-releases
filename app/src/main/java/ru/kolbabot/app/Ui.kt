package ru.kolbabot.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Html
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.text.NumberFormat
import java.util.Locale

fun money(kopecks: Long): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply {
    minimumFractionDigits = 0; maximumFractionDigits = 2
}.format(kopecks / 100.0) + " ₽"
fun plainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()

@Composable fun KolbaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("kolbabot_appearance", Context.MODE_PRIVATE) }
    val systemDark = isSystemInDarkTheme()
    var preference by remember(settings) { mutableStateOf(settings.getString("theme", null)) }
    val dark = preference?.let { it == "dark" } ?: systemDark
    val view = LocalView.current
    SideEffect {
        (context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalSiteDark provides dark, LocalToggleTheme provides {
        preference = if (dark) "light" else "dark"
        settings.edit().putString("theme", preference).apply()
    }) { SiteMaterialTheme(content) }
}

@Composable private fun SiteMaterialTheme(content: @Composable () -> Unit) {
    // Keep JavaScript and Compose on the same semantic colors from static/theme.css.
    val scheme = if (LocalSiteDark.current) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme.copy(
        primary = SiteColors.Brand, onPrimary = SiteColors.Surface,
        primaryContainer = SiteColors.AccentSoft, onPrimaryContainer = SiteColors.Brand,
        secondary = SiteColors.Brand, onSecondary = SiteColors.Surface,
        secondaryContainer = SiteColors.Soft, onSecondaryContainer = SiteColors.Ink,
        tertiary = SiteColors.Accent, onTertiary = SiteColors.Surface,
        tertiaryContainer = SiteColors.SuccessSoft, onTertiaryContainer = SiteColors.StoreGreen,
        background = SiteColors.BackgroundTop, onBackground = SiteColors.Ink,
        surface = SiteColors.Surface, onSurface = SiteColors.Ink,
        surfaceVariant = SiteColors.Soft, onSurfaceVariant = SiteColors.Muted,
        surfaceContainer = SiteColors.Surface, surfaceContainerHigh = SiteColors.Soft,
        surfaceContainerLow = SiteColors.BackgroundTop, surfaceContainerLowest = SiteColors.Surface,
        outline = SiteColors.InputBorder, outlineVariant = SiteColors.CustomerBorder,
        error = SiteColors.Error, onError = SiteColors.Surface,
        errorContainer = SiteColors.ErrorBackground, onErrorContainer = SiteColors.Error,
        surfaceTint = Color.Transparent,
    ), shapes = Shapes(extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(20.dp)),
        typography = Typography(
            displayLarge = siteType(40, 48, FontWeight.Bold),
            displayMedium = siteType(34, 42, FontWeight.Bold),
            displaySmall = siteType(30, 38, FontWeight.Bold),
            headlineLarge = siteType(27, 34, FontWeight.Bold),
            headlineMedium = siteType(24, 31, FontWeight.Bold),
            headlineSmall = siteType(21, 28, FontWeight.Bold),
            titleLarge = siteType(19, 26, FontWeight.Bold),
            titleMedium = siteType(17, 24, FontWeight.Bold),
            titleSmall = siteType(14, 20, FontWeight.Bold),
            bodyLarge = siteType(16, 24), bodyMedium = siteType(14, 22), bodySmall = siteType(12, 18),
            labelLarge = siteType(14, 20, FontWeight.Bold),
            labelMedium = siteType(12, 17, FontWeight.Bold), labelSmall = siteType(11, 16, FontWeight.Bold),
        ), content = content)
}

private fun siteType(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = SiteFontFamily, fontSize = size.sp,
        lineHeight = height.sp, fontWeight = weight, letterSpacing = 0.sp)

@Composable fun Page(title: String, subtitle: String = "", onBack: (() -> Unit)? = null, eyebrow: String = "", content: @Composable ColumnScope.() -> Unit) {
    // Register before content so a nested editor, dialog or picker handles Back first.
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    val admin = LocalSiteAdmin.current
    val pageScroll = rememberScrollState()
    Column(Modifier.fillMaxSize().background(SiteColors.BackgroundTop)
        .verticalScroll(pageScroll).padding(horizontal = 16.dp)
        .padding(top = if (!admin && onBack == null) 0.dp else 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (admin) CompositionLocalProvider(LocalAdminScrollOffset provides { pageScroll.value }) { LocalAdminChrome.current?.invoke() }
        if (!admin && onBack == null) LocalSiteChrome.current?.let { SiteChrome(it, scrollOffset = { pageScroll.value }) }
        onBack?.let { TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) { Text("‹ Назад") } }
        SitePageHeading(title, subtitle, eyebrow, admin)
        content()
        if (!admin && onBack == null) LocalSiteChrome.current?.let { chrome -> SiteFooter(chrome) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SitePageHeading(title: String, subtitle: String, eyebrow: String, admin: Boolean) {
    val symbol = when {
        title == "Готовые рецепты" -> R.drawable.site_section_recipes
        title == "Свой состав" -> R.drawable.site_section_custom
        title == "Справочник технолога" -> R.drawable.site_section_handbook
        title == "Статьи" -> R.drawable.site_section_articles
        title == "Оборудование" -> R.drawable.site_section_equipment
        title == "Частые вопросы" || title == "FAQ" -> R.drawable.site_section_faq
        title == "Поддержка" -> R.drawable.site_section_support
        title.startsWith("Рады вас видеть,") -> R.drawable.site_nav_account
        title == "Больше возможностей Колбабота" -> R.drawable.site_section_premium
        else -> null
    }
    val store = title == "Всё для домашнего производства"
    val text: @Composable ColumnScope.() -> Unit = {
        if (eyebrow.isNotBlank() && !store) {
            Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = SiteColors.Brand)
        }
        val fontSize = when { store -> 30; admin -> 23; symbol != null -> 22; else -> 24 }
        Text(title, style = siteType(fontSize, if (store) 35 else fontSize + 6,
            if (store) FontWeight.ExtraBold else FontWeight.Bold).copy(
                letterSpacing = if (store) (-.045).em else (-.025).em))
        if (subtitle.isNotBlank()) Text(subtitle, style = siteType(13, 20), color = SiteColors.Muted)
    }
    if (!admin && symbol != null) Surface(Modifier.fillMaxWidth(), color = SiteColors.Surface,
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, SiteColors.Border)) {
        Row(Modifier.heightIn(min = 96.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp), content = text)
            if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp > 600) {
                Surface(Modifier.size(52.dp), color = SiteColors.AccentSoft, contentColor = SiteColors.Brand,
                    shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, SiteColors.Border)) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(symbol), null, Modifier.size(28.dp)) }
                }
            }
        }
    } else Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = text)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiteFooter(chrome: SiteChromeState) {
    val context = LocalContext.current
    val muted = SiteColors.Muted
    val linkColors = ButtonDefaults.textButtonColors(contentColor = muted)
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        listOf("Оферта" to "/public-offer", "Подписка" to "/subscription-agreement",
            "Платёжные данные" to "/payment-credentials", "Персональные данные" to "/privacy-policy",
            "Cookie" to "/cookie-policy", "Конфиденциальность Android" to "/android/privacy",
            "obolo4ki@kolbabot.ru" to "mailto:obolo4ki@kolbabot.ru").forEach { (title, url) ->
            TextButton({ openLink(context, url) }, colors = linkColors,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp)) {
                Text(title, fontSize = 11.sp)
            }
        }
        TextButton({ chrome.onNavigate("analytics") }, colors = linkColors,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp)) {
            Text(if (chrome.analyticsEnabled) "Аналитика: включена" else "Аналитика: выключена", fontSize = 11.sp)
        }
    }
}

@Composable fun SectionCard(title: String = "", content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, SiteColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = SiteColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = SiteColors.Ink, modifier = Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}

@Composable fun ErrorMessage(message: String) {
    if (message.isNotBlank()) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable fun OnResume(action: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(action)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) current() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable fun RemotePage(title: String, api: ApiClient, path: String, onBack: (() -> Unit)? = null,
    subtitle: String = "", eyebrow: String = "", content: @Composable ColumnScope.(JSONObject, () -> Unit) -> Unit) {
    var data by remember(path, api.userId) { mutableStateOf<JSONObject?>(null) }
    var error by remember(path) { mutableStateOf("") }
    var loading by remember(path) { mutableStateOf(true) }
    var refresh by remember(path) { mutableIntStateOf(0) }
    OnResume { refresh++ }
    LaunchedEffect(path, refresh, api.userId) {
        loading = true; error = ""
        try { data = api.get(path) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Не удалось загрузить данные." }
        finally { loading = false }
    }
    Page(title, subtitle, onBack = onBack, eyebrow = eyebrow) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) {
            ErrorMessage(error)
            OutlinedButton(onClick = { refresh++ }) { Text("Повторить") }
        }
        data?.let { content(it) { refresh++ } }
    }
}

@Composable internal fun rememberSiteImage(url: String): android.graphics.Bitmap? {
    val image by produceState<android.graphics.Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            try {
                val parsed = URI(BuildConfig.BASE_URL).resolve(url)
                if (parsed.scheme != "https" || parsed.userInfo != null) return@withContext null
                val connection = parsed.toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10000; connection.readTimeout = 10000
                    connection.instanceFollowRedirects = false
                    if (connection.responseCode != 200) return@withContext null
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var count: Int
                        while (input.read(buffer).also { count = it } >= 0) {
                            if (output.size() + count > 8 * 1024 * 1024) return@withContext null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1200).coerceAtLeast(1) }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } finally { connection.disconnect() }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        }
    }
    return image
}

@Composable fun NetworkImage(url: String, description: String, modifier: Modifier = Modifier) {
    val bitmap = rememberSiteImage(url)
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = description, modifier = modifier, contentScale = ContentScale.Fit)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Text("К", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

fun openLink(context: Context, url: String) {
    nativeDestination(url)?.let { destination ->
        if (NativeNavigation.requests.tryEmit(destination)) return
    }
    openBrowser(context, url)
}

fun openBrowser(context: Context, url: String) {
    try {
        val uri = Uri.parse(if (url.startsWith("/") && !url.startsWith("//")) BuildConfig.BASE_URL + url else url)
        require(uri.scheme in listOf("https", "mailto", "tel"))
        if (uri.scheme == "https") CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        else context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) { Toast.makeText(context, "Не удалось открыть ссылку. Установите браузер.", Toast.LENGTH_LONG).show() }
}

fun sharePdf(context: Context, bytes: ByteArray, fileName: String) {
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    val safeName = fileName.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9._-]"), "_").take(100).let { if (it.endsWith(".pdf")) it else "$it.pdf" }
    val file = File(folder, safeName).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Сохранить или отправить PDF"))
}
