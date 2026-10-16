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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
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
    MaterialTheme(colorScheme = lightColorScheme(
        // Brand and surface colors come from static/styles.css, shared with the site.
        primary = SiteColors.Brand, onPrimary = Color.White,
        primaryContainer = Color(0xFFEBEFFB), onPrimaryContainer = Color(0xFF3C61D3),
        secondary = SiteColors.Brand2, onSecondary = Color.White,
        secondaryContainer = Color(0xFFF1ECFB), onSecondaryContainer = Color(0xFF7245D8),
        tertiary = Color(0xFF147A66), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEAF6F1), onTertiaryContainer = Color(0xFF0B6050),
        background = SiteColors.BackgroundTop, onBackground = SiteColors.Ink,
        surface = Color.White, onSurface = SiteColors.Ink,
        surfaceVariant = Color(0xFFF4F3EE), onSurfaceVariant = Color(0xFF6B7280),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF4F3EE),
        surfaceContainerLow = Color(0xFFFBFBF9), surfaceContainerLowest = Color.White,
        outline = SiteColors.InputBorder, outlineVariant = SiteColors.CustomerBorder,
        error = SiteColors.Error, onError = Color.White,
        errorContainer = SiteColors.ErrorBackground, onErrorContainer = SiteColors.Error,
        surfaceTint = Color.Transparent,
    ), shapes = Shapes(extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(20.dp)),
        typography = Typography(
            // Literata is declared by the site but not loaded; its rendered heading fallback is serif.
            displayLarge = siteType(40, 48, FontWeight.Bold, serif = true),
            displayMedium = siteType(34, 42, FontWeight.Bold, serif = true),
            displaySmall = siteType(30, 38, FontWeight.Bold, serif = true),
            headlineLarge = siteType(27, 34, FontWeight.Bold, serif = true),
            headlineMedium = siteType(24, 31, FontWeight.Bold, serif = true),
            headlineSmall = siteType(21, 28, FontWeight.Bold, serif = true),
            titleLarge = siteType(19, 26, FontWeight.Bold),
            titleMedium = siteType(17, 24, FontWeight.Bold),
            titleSmall = siteType(14, 20, FontWeight.Bold),
            bodyLarge = siteType(16, 24), bodyMedium = siteType(14, 22), bodySmall = siteType(12, 18),
            labelLarge = siteType(14, 20, FontWeight.Bold),
            labelMedium = siteType(12, 17, FontWeight.Bold), labelSmall = siteType(11, 16, FontWeight.Bold),
        ), content = content)
}

private fun siteType(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, serif: Boolean = false) =
    TextStyle(fontFamily = if (serif) FontFamily.Serif else SiteFontFamily, fontSize = size.sp,
        lineHeight = height.sp, fontWeight = weight, letterSpacing = 0.16.sp)

@Composable fun Page(title: String, subtitle: String = "", onBack: (() -> Unit)? = null, eyebrow: String = "", content: @Composable ColumnScope.() -> Unit) {
    // Register before content so a nested editor, dialog or picker handles Back first.
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    val admin = LocalSiteAdmin.current
    val pageScroll = rememberScrollState()
    val heading = if (!admin && onBack == null) sitePageHeading(title) else null
    val caption = eyebrow.ifBlank { heading?.eyebrow.orEmpty() }
    val headingShape = RoundedCornerShape(if (admin) 18.dp else 20.dp)
    Column(Modifier.fillMaxSize().sitePageBackground()
        .verticalScroll(pageScroll).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (admin) CompositionLocalProvider(LocalAdminScrollOffset provides { pageScroll.value }) { LocalAdminChrome.current?.invoke() }
        if (!admin && onBack == null) LocalSiteChrome.current?.let { SiteChrome(it, scrollOffset = { pageScroll.value }) }
        onBack?.let { TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) { Text("‹ Назад") } }
        Surface(Modifier.fillMaxWidth().then(if (admin) Modifier else Modifier.dropShadow(headingShape,
            Shadow(radius = 42.dp, offset = DpOffset(0.dp, 18.dp), color = SiteColors.Brand.copy(alpha = .075f)))), shape = headingShape,
            color = Color.Transparent, border = BorderStroke(1.dp,
                if (admin) SiteColors.Border else SiteColors.Brand.copy(alpha = 0.18f))) {
            Box(if (admin) Modifier.background(Color.White) else Modifier.sitePageHeadingBackground()) {
                heading?.let { mark ->
                    Surface(Modifier.align(Alignment.BottomEnd).offset(x = 15.dp, y = 22.dp).size(76.dp)
                        .rotate(4f).alpha(0.15f).clearAndSetSemantics {}, shape = RoundedCornerShape(23.dp), color = Color.Transparent,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.62f))) {
                        Box(Modifier.background(Brush.linearGradient(mark.colors)), contentAlignment = Alignment.Center) {
                            when (mark.symbol) {
                                "store" -> Icon(painterResource(R.drawable.site_nav_store), null, Modifier.size(34.dp), tint = Color.White)
                                "account" -> Icon(painterResource(R.drawable.site_nav_account), null, Modifier.size(34.dp), tint = Color.White)
                                else -> Text(mark.symbol, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
                Column(Modifier.padding(start = 18.dp, end = if (heading == null) 18.dp else 38.dp,
                    top = if (admin) 14.dp else 20.dp, bottom = if (admin) 14.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (caption.isNotBlank()) Text(caption.uppercase(Locale.forLanguageTag("ru-RU")),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 0.96.sp), color = SiteColors.Brand)
                    Text(title, style = if (admin) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
                }
            }
        }
        content()
        Spacer(Modifier.height(8.dp))
    }
}

private fun Modifier.sitePageHeadingBackground() = drawWithCache {
    val extent = (size.width + size.height) * .25f
    val start = Offset(size.width * .5f - extent, size.height * .5f - extent)
    val end = Offset(size.width * .5f + extent, size.height * .5f + extent)
    val base = Brush.linearGradient(0f to SiteColors.Brand.copy(alpha = .095f), .52f to Color.White.copy(alpha = .95f),
        1f to SiteColors.Accent.copy(alpha = .07f), start = start, end = end)
    val glow = Brush.radialGradient(listOf(SiteColors.Brand2.copy(alpha = .16f), Color.Transparent),
        center = Offset(size.width * .92f, size.height * .08f), radius = kotlin.math.hypot(size.width * .92f, size.height * .92f) * .31f)
    onDrawBehind { drawRect(base); drawRect(glow) }
}

private fun Modifier.sitePageBackground() = drawWithCache {
    val direction = Offset(.0871557f, .9961947f) // CSS linear-gradient(175deg).
    val extent = (size.width * direction.x + size.height * direction.y) * .5f
    val center = Offset(size.width * .5f, size.height * .5f)
    val base = Brush.linearGradient(listOf(SiteColors.BackgroundTop, SiteColors.BackgroundBottom),
        start = center - direction * extent, end = center + direction * extent)
    fun noise(x: Float, y: Float, radius: Float, color: Color) = Brush.radialGradient(listOf(color, Color.Transparent),
        center = Offset(size.width * x, size.height * y),
        radius = kotlin.math.hypot(size.width * maxOf(x, 1f - x), size.height * maxOf(y, 1f - y)) * radius)
    val blue = noise(.18f, .16f, .4f, SiteColors.Brand.copy(alpha = .04f))
    val green = noise(.84f, .2f, .3f, SiteColors.Accent.copy(alpha = .03f))
    val purple = noise(.74f, .8f, .34f, SiteColors.Brand2.copy(alpha = .04f))
    onDrawBehind { drawRect(base); drawRect(purple); drawRect(green); drawRect(blue) }
}

private data class SitePageHeading(val eyebrow: String, val symbol: String, val colors: List<Color>)
private fun sitePageHeading(title: String): SitePageHeading? {
    val standard = listOf(SiteColors.Brand, SiteColors.Brand2)
    val green = listOf(Color(0xFF14836E), Color(0xFF24A98E))
    val purple = listOf(Color(0xFF5B4CC4), Color(0xFF8B5BD9))
    val blue = listOf(Color(0xFF31528F), Color(0xFF4F75B9))
    if (title.startsWith("Рады вас видеть,")) return SitePageHeading("Личный кабинет", "account", standard)
    return when (title) {
        "Всё для домашнего производства", "Магазин" -> SitePageHeading("Магазин", "store", green)
        "Готовые рецепты", "Рецепты" -> SitePageHeading("Рецептуры", "≡", standard)
        "Свой состав" -> SitePageHeading("Калькулятор", "∑", purple)
        "Справочник технолога", "Справочник" -> SitePageHeading("База знаний", "i", blue)
        "Статьи" -> SitePageHeading("Полезные материалы", "Aa", purple)
        "Оборудование" -> SitePageHeading("Инструменты", "⌁", listOf(Color(0xFF3E5565), Color(0xFF6F8592)))
        "Частые вопросы", "FAQ" -> SitePageHeading("Быстрая помощь", "?", blue)
        "Поддержка" -> SitePageHeading("Связь с нами", "↗", green)
        "Личный кабинет" -> SitePageHeading("Личный кабинет", "account", standard)
        "Больше возможностей Колбабота" -> SitePageHeading("PREMIUM", "P", listOf(Color(0xFFB87A12), Color(0xFFE3B54F)))
        else -> null
    }
}

@Composable fun SectionCard(title: String = "", content: @Composable ColumnScope.() -> Unit) {
    val admin = LocalSiteAdmin.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp,
        if (admin) SiteColors.Border else SiteColors.CustomerBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = if (admin) 0.85f else 0.94f))) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable fun NetworkImage(url: String, description: String, modifier: Modifier = Modifier) {
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
    val bitmap = image
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
