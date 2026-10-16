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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

fun money(kopecks: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(kopecks / 100.0)
fun plainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()

@Composable fun KolbaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        // Brand and surface colors come from static/styles.css, shared with the site.
        primary = Color(0xFF3C61D3), onPrimary = Color.White,
        primaryContainer = Color(0xFFEBEFFB), onPrimaryContainer = Color(0xFF3C61D3),
        secondary = Color(0xFF7245D8), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF1ECFB), onSecondaryContainer = Color(0xFF7245D8),
        tertiary = Color(0xFF147A66), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEAF6F1), onTertiaryContainer = Color(0xFF0B6050),
        background = Color(0xFFFBFBF9), onBackground = Color(0xFF2C2D30),
        surface = Color.White, onSurface = Color(0xFF2C2D30),
        surfaceVariant = Color(0xFFF4F3EE), onSurfaceVariant = Color(0xFF6B7280),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF4F3EE),
        surfaceContainerLow = Color(0xFFFBFBF9), surfaceContainerLowest = Color.White,
        outline = Color(0xFF6B7280), outlineVariant = Color(0xFFE4E4E2)
    ), shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp)),
        typography = Typography(
            headlineLarge = Typography().headlineLarge.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
            headlineMedium = Typography().headlineMedium.copy(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
            titleLarge = Typography().titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold)
        ), content = content)
}

@Composable fun Page(title: String, subtitle: String = "", onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    // Register before content so a nested editor, dialog or picker handles Back first.
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF8F9FC), Color(0xFFF4F3EE))))
        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            onBack?.let { TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) { Text("‹ Назад") } }
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.width(64.dp).height(4.dp).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)), RoundedCornerShape(4.dp)))
        }
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable fun SectionCard(title: String = "", content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        if (title.isNotBlank()) Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))))
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable fun RemotePage(title: String, api: ApiClient, path: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.(JSONObject, () -> Unit) -> Unit) {
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
    Page(title, onBack = onBack) {
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
