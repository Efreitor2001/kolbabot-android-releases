package ru.kolbabot.app

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Optional renderer observation/interception for deterministic Android tests. */
internal data class OzonMapHooks(
    val intercept: (WebResourceRequest) -> WebResourceResponse? = { null },
    val onCreated: (WebView) -> Unit = {},
)
internal val LocalOzonMapHooks = staticCompositionLocalOf<OzonMapHooks?> { null }

@SuppressLint("ResourceType")
private fun mapFont(context: android.content.Context): WebResourceResponse {
    // Resources.openRawResource supports file-backed resources, not only res/raw:
    // it resolves TypedValue.assetCookie/string and opens that asset. This is a
    // packaged TTF (not a font-provider XML), shared with the native UI.
    return WebResourceResponse("font/ttf", null, context.resources.openRawResource(R.font.manrope_regular))
}

@Composable
internal fun OzonPickupPicker(api: ApiClient, selected: JSONObject?, onDismiss: () -> Unit, onSelect: (JSONObject) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var status by remember { mutableStateOf("Введите город, улицу или номер ПВЗ либо выберите точку на карте.") }
    var searchAttempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var manualAddress by rememberSaveable { mutableStateOf("") }
    var manualName by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val mapHeight = (LocalConfiguration.current.screenHeightDp * 0.44f).coerceIn(280f, 390f).dp
    LaunchedEffect(query, searchAttempt) {
        results = emptyList()
        loading = false
        if (query.trim().length < 2) {
            status = "Введите минимум два символа или выберите точку на карте."
            return@LaunchedEffect
        }
        status = "Ищем по мере ввода…"
        delay(280)
        loading = true
        try {
            val found = api.get("${OzonDelivery.API}points?q=${URLEncoder.encode(query.trim(), "UTF-8")}&limit=40")
            ensureActive()
            results = found.items("items").filter(OzonDelivery::validPoint)
            status = if (results.isEmpty()) "Ничего не найдено. Уточните город или улицу." else "Найдено: ${results.size}"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = e.message ?: "Не удалось загрузить пункты Ozon. Повторите поиск." }
        finally { loading = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Ozon Delivery", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Выберите пункт выдачи", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onDismiss) { Text("Закрыть") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (manualOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(manualAddress, { manualAddress = it.take(500) }, Modifier.fillMaxWidth(),
                            label = { Text("Адрес ПВЗ Ozon") }, minLines = 3)
                        OutlinedTextField(manualName, { manualName = it.take(200) }, Modifier.fillMaxWidth(),
                            label = { Text("Название или номер ПВЗ (необязательно)") }, singleLine = true)
                        Text("Укажите адрес так, как он указан в Ozon. Такой ПВЗ будет обработан вручную.", style = MaterialTheme.typography.bodySmall)
                        Button({ onSelect(JSONObject().put("checkout_mode", "manual").put("full_address", manualAddress.trim())
                            .put("name", manualName.trim().ifBlank { "ПВЗ Ozon" })) }, enabled = manualAddress.trim().length >= 5) { Text("Использовать этот ПВЗ") }
                        OutlinedButton({ manualOpen = false }) { Text("Отмена") }
                    }
                    return@Column
                }
                OzonMap(api, selected, Modifier.fillMaxWidth().height(mapHeight).testTag("ozonPickupMap"), onSelect) { points ->
                    results = points
                    status = "В этом месте ${points.size} ПВЗ. Выберите нужный в списке."
                    keyboard?.hide()
                }
                OutlinedTextField(query, { query = it.take(240) }, Modifier.fillMaxWidth().testTag("ozonPointSearch"),
                    label = { Text("Город, улица или номер ПВЗ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchAttempt++; keyboard?.hide() }))
                Button({ searchAttempt++; keyboard?.hide() }, enabled = query.trim().length >= 2 && !loading) { Text("Найти") }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (results.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("ozonPointResults"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { point ->
                        Card(onClick = { onSelect(point) }, modifier = Modifier.fillMaxWidth().testTag("ozonPoint_${point.optLong("delivery_point_id")}"),
                            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(OzonDelivery.pointName(point), style = MaterialTheme.typography.titleSmall)
                                Text(point.optString("full_address"), style = MaterialTheme.typography.bodyMedium)
                                val type = if (point.optString("type") == "postamat") "Постамат" else "ПВЗ"
                                val storage = point.optInt("storage_period_days")
                                Text(type + if (storage > 0) " · хранение $storage дн." else "", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                OutlinedButton({ manualOpen = true }) { Text("Моего ПВЗ нет в списке") }
                }
            }
        }
    }
}

/** No cookies or tokens are passed to JavaScript; only the three map GETs are bridged. */
private class OzonMapBridge(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val selected: JSONObject?,
    private val tiles: OzonMapTiles,
    private val reply: (String) -> Unit,
    private val choose: (JSONObject) -> Unit,
    private val show: (List<JSONObject>) -> Unit,
) {
    private val requests = ConcurrentHashMap<String, Job>()
    private val trustedPoints = ConcurrentHashMap<String, JSONObject>()
    @Volatile private var disposed = false

    @JavascriptInterface fun initialPoint(): String = selected?.toString() ?: "null"

    @JavascriptInterface fun request(id: String, path: String) {
        if (disposed || !id.matches(Regex("[0-9]{1,9}")) || !OzonDelivery.allowedMapPath(path) || requests.size >= 8) return
        // JavaScript interfaces run on WebView's JavaBridge thread. Do not rely
        // on a caller's (possibly immediate) coroutine context for UI callbacks.
        val job = scope.launch(Dispatchers.Main.immediate, start = CoroutineStart.LAZY) {
            try {
                val data = api.get(path)
                ensureActive()
                if (disposed) return@launch
                if (path == "${OzonDelivery.API}map-config") {
                    data.put("pmtiles_url", if (tiles.configure(data.optString("pmtiles_url"))) tiles.localUrl else "")
                }
                if (path.substringBefore('?') == "${OzonDelivery.API}map-point-info") {
                    data.items("items").filter(OzonDelivery::validPoint).forEach { trustedPoints[pointKey(it)] = it }
                }
                reply("window.OzonNativeReply(${JSONObject.quote(id)},${data},null);")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!disposed) reply("window.OzonNativeReply(${JSONObject.quote(id)},null,${JSONObject().put("message", e.message ?: "Не удалось загрузить карту").put("status", (e as? ApiException)?.status ?: 0)});")
            } finally { requests.remove(id) }
        }
        requests.put(id, job)?.cancel()
        job.start()
    }

    @JavascriptInterface fun cancel(id: String) { requests.remove(id)?.cancel() }

    @JavascriptInterface fun selectPoint(raw: String) {
        if (disposed || raw.length > 32768) return
        val point = runCatching { trustedPoints[pointKey(JSONObject(raw))] }.getOrNull() ?: return
        scope.launch(Dispatchers.Main.immediate) { if (!disposed) choose(JSONObject(point.toString())) }
    }

    @JavascriptInterface fun renderPoints(raw: String) {
        if (disposed || raw.length > 1048576) return
        val points = runCatching {
            val values = JSONArray(raw)
            (0 until values.length().coerceAtMost(100)).mapNotNull { index ->
                values.optJSONObject(index)?.let { trustedPoints[pointKey(it)] }
            }
        }.getOrDefault(emptyList())
        scope.launch(Dispatchers.Main.immediate) { if (!disposed) show(points) }
    }

    fun dispose() { disposed = true; requests.values.forEach(Job::cancel); requests.clear(); trustedPoints.clear() }
    private fun pointKey(point: JSONObject) = "${point.optLong("delivery_point_id")}|${point.optString("full_address")}|${point.optString("checkout_mode")}" 
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OzonMap(api: ApiClient, selected: JSONObject?, modifier: Modifier, onSelect: (JSONObject) -> Unit, onPoints: (List<JSONObject>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val choose by rememberUpdatedState(onSelect)
    val show by rememberUpdatedState(onPoints)
    val tiles = remember(api) { OzonMapTiles(api.baseUrl) }
    val mapHooks = LocalOzonMapHooks.current
    var locationRequest by remember { mutableStateOf<Pair<String, GeolocationPermissions.Callback>?>(null) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        locationRequest?.let { (origin, callback) ->
            callback.invoke(origin, permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true || permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true, false)
        }
        locationRequest = null
    }
    val webView = remember(api) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setGeolocationEnabled(true)
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                    if (!origin.trimEnd('/').equals(api.baseUrl.trimEnd('/'), true)) { callback.invoke(origin, false, false); return }
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) callback.invoke(origin, true, false)
                    else {
                        locationRequest?.let { (previous, previousCallback) -> previousCallback.invoke(previous, false, false) }
                        locationRequest = origin to callback
                        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url ?: return null
                    mapHooks?.intercept?.invoke(request)?.let { return it }
                    if (url.host != android.net.Uri.parse(api.baseUrl).host || url.path?.startsWith("/__native_ozon_map__/") != true) return null
                    val name = url.path?.removePrefix("/__native_ozon_map__/")
                    if (name == "basemap.pmtiles" && request.method == "GET") {
                        return tiles.read(request.requestHeaders.entries.firstOrNull { it.key.equals("Range", true) }?.value)
                    }
                    if (name == "ozon-manrope.ttf") return mapFont(context)
                    if (name !in setOf("ozon-pvz-map.js", "ozon-native-bridge.js", "ozon-map.css", "ozon-maplibre.js", "ozon-maplibre.css", "ozon-pmtiles.js", "ozon-basemaps.js"))
                        return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    return WebResourceResponse(if (name!!.endsWith(".js")) "application/javascript" else "text/css", "UTF-8", context.assets.open(name))
                }
            }
            mapHooks?.onCreated?.invoke(this)
        }
    }
    DisposableEffect(webView) {
        val bridge = OzonMapBridge(api, scope, selected, tiles, { script -> webView.evaluateJavascript(script, null) }, { choose(it) }, { show(it) })
        webView.addJavascriptInterface(bridge, "AndroidOzon")
        // Preserve the site's HTTPS origin: its PMTiles CDN uses origin-specific CORS.
        webView.loadDataWithBaseURL("${api.baseUrl}/__native_ozon_map__/", context.assets.open("ozon-map.html").bufferedReader().use { it.readText() }, "text/html", "UTF-8", null)
        onDispose {
            locationRequest?.let { (origin, callback) -> callback.invoke(origin, false, false) }
            locationRequest = null
            bridge.dispose()
            webView.removeJavascriptInterface("AndroidOzon")
            webView.stopLoading()
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}
