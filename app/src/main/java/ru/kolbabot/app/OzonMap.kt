package ru.kolbabot.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.io.File
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.math.round
import kotlin.math.roundToInt

/** Offline tiles and a renderer observer for the device test. Production always uses a server-configured HTTPS URL. */
internal data class OzonMapTestConfig(val tiles: File, val onStyle: (MapLibreMap) -> Unit)
internal val LocalOzonMapTestConfig = staticCompositionLocalOf<OzonMapTestConfig?> { null }

private const val POINTS_SOURCE = "ozon-pvz"
private const val POINTS_LAYER = "ozon-pvz-circle"
private val moscow = LatLng(55.751574, 37.573856)

private data class OzonViewport(val south: Double, val north: Double, val west: Double, val east: Double, val zoom: Int) {
    val path get() = "${OzonDelivery.API}map-points?min_lat=$south&max_lat=$north&min_lon=$west&max_lon=$east&zoom=$zoom"
}

private data class OzonCluster(val location: LatLng, val count: Int, val source: String, val ids: List<String>,
                               val bounds: LatLngBounds?, val requiresZoom: Boolean, val sameBuilding: Boolean) {
    val key get() = "${location.latitude}/${location.longitude}/$source/${ids.firstOrNull()}/$count"

    fun feature(): Feature = Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
        addStringProperty("key", key)
        addNumberProperty("count", count)
    }

    val lookupPath get() = "${OzonDelivery.API}map-point-info?ids=${URLEncoder.encode(ids.joinToString(","), "UTF-8")}&source=${URLEncoder.encode(source, "UTF-8")}"
}

private fun JSONObject.coordinate(latitude: String, longitude: String): LatLng? {
    val lat = optDouble(latitude)
    val lon = optDouble(longitude)
    return if (lat in -90.0..90.0 && lon in -180.0..180.0) LatLng(lat.coerceIn(-85.0, 85.0), lon) else null
}

private fun JSONObject.cluster(defaultSource: String): OzonCluster? {
    val position = optJSONObject("coordinate")?.coordinate("lat", "long") ?: return null
    val box = optJSONObject("viewport")
    val low = box?.optJSONObject("left_bottom")?.coordinate("lat", "long")
    val high = box?.optJSONObject("right_top")?.coordinate("lat", "long")
    val bounds = if (low != null && high != null && low.latitude < high.latitude && low.longitude < high.longitude)
        LatLngBounds.from(high.latitude, high.longitude, low.latitude, low.longitude) else null
    val values = optJSONArray("map_point_ids")
    val ids = (0 until (values?.length() ?: 0).coerceAtMost(100)).mapNotNull { index ->
        values?.optString(index)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) && it != "null" }
    }
    return OzonCluster(position, optInt("points_count", 1).coerceAtLeast(1), optString("source").ifBlank { defaultSource },
        ids, bounds, optBoolean("requires_zoom"), optBoolean("is_same_building"))
}

@Composable
internal fun OzonMap(api: ApiClient, selected: JSONObject?, modifier: Modifier, onSelect: (JSONObject) -> Unit,
                     onPoints: (List<JSONObject>) -> Unit) {
    val testConfig = LocalOzonMapTestConfig.current
    var tiles by remember(api) { mutableStateOf<String?>(null) }
    var pointsSource by remember(api) { mutableStateOf("seller") }
    var error by remember(api) { mutableStateOf("") }
    LaunchedEffect(api, testConfig) {
        try {
            val config = api.get("${OzonDelivery.API}map-config")
            pointsSource = config.optString("points_source").ifBlank { "seller" }
            tiles = testConfig?.tiles?.let { Uri.fromFile(it).toString() } ?: OzonDelivery.basemapUrl(config.optString("pmtiles_url"))
            if (tiles == null) error = "Файл базовой карты пока не настроен. Поиск ПВЗ ниже продолжает работать."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty().ifBlank { "Карта временно недоступна." } }
    }
    val source = tiles
    if (source != null) {
        OzonMapView(api, source, pointsSource, selected, modifier, onSelect, onPoints)
    } else {
        Surface(modifier, shape = RoundedCornerShape(14.dp), color = SiteColors.Soft, border = BorderStroke(1.dp, SiteColors.Border)) {
            Box(Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                Text(error.ifBlank { "Загрузка карты…" }, color = if (error.isEmpty()) SiteColors.Muted else SiteColors.Error)
            }
        }
    }
}

@Composable
private fun OzonMapView(api: ApiClient, tiles: String, pointsSource: String, selected: JSONObject?, modifier: Modifier,
                        onSelect: (JSONObject) -> Unit, onPoints: (List<JSONObject>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val choose by rememberUpdatedState(onSelect)
    val show by rememberUpdatedState(onPoints)
    val test = LocalOzonMapTestConfig.current
    var map by remember(tiles) { mutableStateOf<MapLibreMap?>(null) }
    var viewPort by remember(tiles) { mutableStateOf<OzonViewport?>(null) }
    var clusters by remember(tiles) { mutableStateOf(emptyMap<String, OzonCluster>()) }
    var status by remember(tiles) { mutableStateOf("Загрузка карты…") }
    var tileError by remember(tiles) { mutableStateOf("") }
    var selection by remember(tiles) { mutableStateOf<Job?>(null) }
    var locating by remember(tiles) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context, tiles) {
        MapLibre.getInstance(context.applicationContext)
        object : MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)
            .compassEnabled(false).rotateGesturesEnabled(false).tiltGesturesEnabled(false)) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
                }
                return super.dispatchTouchEvent(event)
            }
        }
    }

    fun locate() {
        if (locating) return
        locating = true
        status = "Определяем ваше местоположение…"
        scope.launch {
            try {
                val current = withTimeoutOrNull(8_000) { currentOzonLocation(context) }
                if (current == null) status = "Не удалось получить местоположение. Можно переместить карту вручную."
                else map?.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 12.0), 250)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Не удалось получить местоположение. Можно переместить карту вручную." }
            finally { locating = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate()
        else status = "Разрешите определение местоположения или переместите карту вручную."
    }

    fun select(cluster: OzonCluster, current: MapLibreMap) {
        selection?.cancel()
        val zoom = current.cameraPosition.zoom
        if (cluster.requiresZoom || (cluster.count > 1 && !cluster.sameBuilding && zoom < 18)) {
            val padding = (46 * context.resources.displayMetrics.density).roundToInt()
            val update = cluster.bounds?.let { CameraUpdateFactory.newLatLngBounds(it, padding) }
            if (update != null) current.animateCamera(update, 250)
            else if (cluster.requiresZoom) status = "В этой области много ПВЗ. Приблизьте карту, чтобы выбрать точку."
            else current.animateCamera(CameraUpdateFactory.newLatLngZoom(cluster.location, (zoom + 2).coerceAtMost(19.0)), 250)
            return
        }
        if (cluster.ids.isEmpty()) {
            status = "Ozon не вернул идентификаторы ПВЗ для этой метки. Попробуйте приблизить карту."
            return
        }
        selection = scope.launch {
            status = "Загружаем данные выбранного ПВЗ…"
            try {
                val items = api.get(cluster.lookupPath).items("items").filter(OzonDelivery::validPoint)
                when (items.size) {
                    0 -> status = "Активных ПВЗ в этой точке не найдено. Выберите другую метку."
                    1 -> choose(items[0])
                    else -> {
                        status = "В этом месте ${items.size} ПВЗ. Выберите нужный в списке под картой."
                        show(items)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { status = failure.message.orEmpty().ifBlank { "Не удалось загрузить выбранный ПВЗ." } }
        }
    }
    val onClusterClick by rememberUpdatedState(::select)
    DisposableEffect(mapView, lifecycle) {
        var active = true
        var started = false
        var resumed = false
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.addOnDidFailLoadingMapListener {
            tileError = "Не удалось загрузить базовую карту. Поиск ПВЗ ниже продолжает работать."
        }
        mapView.getMapAsync { current ->
            if (!active) return@getMapAsync
            current.setMinZoomPreference(2.0)
            current.setMaxZoomPreference(19.0)
            current.cameraPosition = CameraPosition.Builder()
                .target(selected?.optJSONObject("coordinates")?.coordinate("latitude", "longitude") ?: moscow)
                .zoom(10.0).build()
            current.addOnCameraIdleListener { viewPort = current.ozonViewport() }
            current.addOnMapClickListener { coordinate ->
                val point = current.projection.toScreenLocation(coordinate)
                val radius = 7f * context.resources.displayMetrics.density
                val feature = current.queryRenderedFeatures(RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius), POINTS_LAYER).firstOrNull()
                val cluster = feature?.getStringProperty("key")?.let(clusters::get)
                if (cluster != null) onClusterClick(cluster, current)
                cluster != null
            }
            val style = buildOzonStyle(context, tiles, test != null)
            current.setStyle(Style.Builder().fromJson(style)) {
                if (!active) return@setStyle
                map = current
                mapView.doOnLayout { viewPort = current.ozonViewport() }
                test?.onStyle?.invoke(current)
            }
        }
        onDispose {
            active = false
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(map, viewPort) {
        val current = map ?: return@LaunchedEffect
        val region = viewPort ?: return@LaunchedEffect
        status = "Загружаем ПВЗ Ozon в этой области…"
        for (attempt in 0..2) {
            try {
                val response = api.get(region.path)
                val parsed = response.items("clusters").mapNotNull { it.cluster(response.optString("source").ifBlank { pointsSource }) }
                clusters = parsed.associateBy(OzonCluster::key)
                current.style?.getSourceAs<GeoJsonSource>(POINTS_SOURCE)
                    ?.setGeoJson(FeatureCollection.fromFeatures(parsed.map(OzonCluster::feature)))
                val count = parsed.sumOf { it.count.toLong() }
                status = when {
                    count == 0L -> "В этой области ПВЗ Ozon не найдено. Переместите карту или измените масштаб."
                    response.optBoolean("truncated") -> "Показано $count ПВЗ Ozon. Приблизьте карту, чтобы увидеть остальные."
                    else -> "В этой области $count ПВЗ Ozon. Приближайте карту или нажмите на метку."
                }
                break
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val code = (failure as? ApiException)?.status
                if (attempt == 2 || (code != null && code != 429 && code < 500)) {
                    status = failure.message.orEmpty().ifBlank { "Не удалось загрузить ПВЗ Ozon на карте." }
                    break
                }
                status = "Повторяем загрузку ПВЗ Ozon…"
                delay(750L * (attempt + 1))
            }
        }
    }

    Surface(modifier, shape = RoundedCornerShape(14.dp), color = SiteColors.Surface, border = BorderStroke(1.dp, SiteColors.Border)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("ПВЗ на карте", style = MaterialTheme.typography.titleSmall)
                    Text("Выберите ваш ПВЗ на карте.", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
                }
                OutlinedButton(onClick = {
                    if (ozonLocationAllowed(context)) locate()
                    else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, enabled = !locating && map != null, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("Моё местоположение", style = MaterialTheme.typography.labelSmall)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp))) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().semantics { contentDescription = "Карта пунктов выдачи Ozon" })
                Column(Modifier.align(Alignment.TopEnd).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("+" to 1.0, "−" to -1.0).forEach { (label, shift) ->
                        OutlinedButton({ map?.animateCamera(CameraUpdateFactory.zoomBy(shift), 180) }, Modifier.size(38.dp),
                            contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp), enabled = map != null) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            Text(tileError.ifBlank { status }, style = MaterialTheme.typography.bodySmall,
                color = if (tileError.isBlank()) SiteColors.Muted else SiteColors.Error)
        }
    }
}

private fun MapLibreMap.ozonViewport(): OzonViewport? = runCatching {
    val box = projection.visibleRegion.latLngBounds
    fun rounded(value: Double) = round(value * 10_000) / 10_000
    val south = rounded(box.latitudeSouth.coerceAtLeast(-90.0))
    val north = rounded(box.latitudeNorth.coerceAtMost(90.0))
    val west = rounded(box.longitudeWest.coerceAtLeast(-180.0))
    val east = rounded(box.longitudeEast.coerceAtMost(180.0))
    if (south >= north || west >= east) null else OzonViewport(south, north, west, east, cameraPosition.zoom.roundToInt().coerceIn(2, 19))
}.getOrNull()

private fun buildOzonStyle(context: Context, tiles: String, offline: Boolean): String {
    val json = context.assets.open("ozon-basemap.json").bufferedReader().use { JSONObject(it.readText()) }
    if (offline) json.remove("sprite")
    val sources = json.getJSONObject("sources")
    sources.getJSONObject("protomaps").put("url", "pmtiles://$tiles")
    sources.put(POINTS_SOURCE, JSONObject("""{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}"""))
    val layers = json.getJSONArray("layers")
    val pins = JSONArray("""[
      {"id":"$POINTS_LAYER","type":"circle","source":"$POINTS_SOURCE","paint":{
        "circle-radius":["case",[">",["get","count"],1],20,13],"circle-color":"#1264e5","circle-stroke-width":3,"circle-stroke-color":"#ffffff"}},
      {"id":"ozon-pvz-dot","type":"circle","source":"$POINTS_SOURCE","filter":["==",["get","count"],1],"paint":{"circle-radius":3.5,"circle-color":"#ffffff"}},
      {"id":"ozon-pvz-count","type":"symbol","source":"$POINTS_SOURCE","filter":[">",["get","count"],1],
        "layout":{"text-field":["to-string",["get","count"]],"text-font":["Noto Sans Regular"],"text-size":12,"text-allow-overlap":true},"paint":{"text-color":"#ffffff"}}
    ]""")
    for (index in 0 until pins.length()) layers.put(pins.getJSONObject(index))
    return json.toString()
}

private fun ozonLocationAllowed(context: Context) = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@SuppressLint("MissingPermission")
private suspend fun currentOzonLocation(context: Context): LatLng? {
    if (!ozonLocationAllowed(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val provider = listOfNotNull(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER.takeIf { fine })
        .firstOrNull { manager.isProviderEnabled(it) } ?: return null
    return suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()
        continuation.invokeOnCancellation { cancellation.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(context)) { location ->
            continuation.resume(location?.let { LatLng(it.latitude, it.longitude) })
        }
    }
}
