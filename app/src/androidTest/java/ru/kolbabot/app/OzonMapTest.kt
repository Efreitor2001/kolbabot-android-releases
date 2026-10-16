package ru.kolbabot.app

import android.content.Context
import android.graphics.Bitmap
import android.webkit.GeolocationPermissions
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual bundled MapLibre + PMTiles + Protomaps runtime, without Internet or checkout. */
@RunWith(AndroidJUnit4::class)
class OzonMapTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val view = AtomicReference<WebView>()
    private val selected = AtomicReference<JSONObject>()
    private val tileReads = AtomicInteger()
    private val fixture = pmtilesFixture()

    @Test fun realRendererLoadsRangeTilesAndSelectingMapPointUsesNativeBridge() {
        val api = MapApi(context)
        show(api)
        awaitMap(20000, "document.querySelectorAll('.ozon-pvz-map-marker.is-point').length === 1", api)
        assertTrue("PMTiles must be read through actual HTTP Range requests", tileReads.get() >= 2)
        assertTrue(api.reads.any { it.startsWith("${OzonDelivery.API}map-points?") })
        assertEquals("true", js("Boolean(mapInstance && mapInstance.isStyleLoaded())"))
        assertEquals("true", js("Boolean(mapInstance.getCanvas().getContext('webgl2') || mapInstance.getCanvas().getContext('webgl'))"))
        screenshot("02-real-maplibre-pmtiles-marker")
        js("document.querySelector('.ozon-pvz-map-marker.is-point').click(); true")
        compose.waitUntil(5000) { selected.get() != null }
        assertEquals(101L, selected.get().getLong("delivery_point_id"))
        assertEquals("Москва, Тестовая улица, 1", selected.get().getString("full_address"))
        assertTrue(api.reads.any { it.startsWith("${OzonDelivery.API}map-point-info?ids=101") })
        assertEquals("null", js("window.OzonNativeRequest('/api/admin/users').catch(() => null); null"))
        assertFalse(api.reads.contains("/api/admin/users"))
    }

    @Test fun mapFailureKeepsSearchAvailableAndForeignOriginCannotAskForLocation() {
        val api = MapApi(context, fail = true)
        show(api)
        awaitMap(10000, "Boolean(document.getElementById('ozonPvzMapStatus')?.textContent.includes('Карта временно недоступна'))", api)
        val allowed = AtomicReference<Boolean>()
        instrumentation.runOnMainSync {
            view.get().webChromeClient!!.onGeolocationPermissionsShowPrompt("https://untrusted.invalid", GeolocationPermissions.Callback { _, grant, _ -> allowed.set(grant) })
        }
        assertEquals(false, allowed.get())
        compose.onNodeWithTag("ozonPointSearch").performScrollTo().performTextInput("Москва")
        compose.onNodeWithTag("ozonPointSearch").performImeAction()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("ozonPoint_101").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ozonPointResults").performScrollTo()
        screenshot("03-map-error-search-available")
        compose.onNodeWithTag("ozonPoint_101").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(101L, selected.get().getLong("delivery_point_id"))
    }

    private fun show(api: MapApi) {
        val hooks = OzonMapHooks(intercept = ::resource, onCreated = { view.set(it) })
        compose.setContent { KolbaTheme {
            CompositionLocalProvider(LocalOzonMapHooks provides hooks) {
                OzonPickupPicker(api, null, {}, { selected.set(it) })
            }
        } }
    }

    private fun resource(request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        // loadDataWithBaseURL may expose its data: main document here. Only
        // replace network resources, never the actual bundled HTML document.
        if (request.isForMainFrame || uri.scheme != "https") return null
        if (uri.path == "/__native_ozon_map__/basemap.pmtiles") {
            val range = OzonMapTiles.byteRange(request.requestHeaders.entries.firstOrNull { it.key.equals("Range", true) }?.value)
                ?: throw AssertionError("MapLibre/PMTiles did not request a bounded range")
            val start = range.first.toInt()
            val end = minOf(range.last.toInt(), fixture.lastIndex)
            tileReads.incrementAndGet()
            return WebResourceResponse("application/octet-stream", null, 206, "Partial Content", mapOf(
                "Content-Range" to "bytes $start-$end/${fixture.size}", "Content-Length" to (end - start + 1).toString(),
                "Accept-Ranges" to "bytes", "Access-Control-Allow-Origin" to "https://map-fixture.invalid",
                "Access-Control-Expose-Headers" to "Content-Range, Content-Length, Accept-Ranges",
            ), ByteArrayInputStream(fixture.copyOfRange(start, end + 1)))
        }
        if (uri.host == "map-fixture.invalid") return null // Real production asset loader.
        if (uri.path.orEmpty().endsWith(".json")) return response("application/json", "{}".toByteArray())
        if (uri.path.orEmpty().endsWith(".png")) return response("image/png", android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", android.util.Base64.DEFAULT))
        // Empty fixture glyph response. The fixture vector tile has only water,
        // so it needs no glyphs; all network requests are intercepted regardless.
        return response("application/octet-stream", ByteArray(0))
    }

    private fun response(type: String, bytes: ByteArray) = WebResourceResponse(type, null, 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(bytes))

    private fun awaitMap(timeout: Long, condition: String, api: MapApi) {
        try {
            compose.waitUntil(timeout) { view.get() != null && js(condition) == "true" }
        } catch (failure: Throwable) {
            val page = if (view.get() == null) "WebView not created" else js("JSON.stringify({ready:document.readyState,status:document.getElementById('ozonPvzMapStatus')?.textContent,html:document.body?.innerText})")
            screenshot("map-failure-diagnostic")
            throw AssertionError("Map did not become ready: $page; tile ranges=${tileReads.get()}; API=${api.reads}", failure)
        }
    }

    private fun js(expression: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { view.get().evaluateJavascript(expression) { result.set(it); done.countDown() } }
        assertTrue("Map JavaScript timed out", done.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("No map screenshot")
        val directory = File(context.getExternalFilesDir(null), "ozon-smoke").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private class MapApi(context: Context, private val fail: Boolean = false) : ApiClient(context, "https://map-fixture.invalid") {
        val reads = CopyOnWriteArrayList<String>()
        override suspend fun get(path: String): JSONObject {
            reads += path
            return when {
                path == "${OzonDelivery.API}map-config" -> {
                    if (fail) throw ApiException(503, "Карта временно недоступна")
                    JSONObject("""{"enabled":true,"pmtiles_url":"https://tiles-fixture.invalid/test.pmtiles","points_source":"directory"}""")
                }
                path.startsWith("${OzonDelivery.API}map-points?") -> JSONObject("""{"source":"directory","clusters":[{"coordinate":{"lat":55.751574,"long":37.573856},"points_count":1,"map_point_ids":[101]}]}""")
                path.startsWith("${OzonDelivery.API}map-point-info?") || path.startsWith("${OzonDelivery.API}points?") -> JSONObject().put("items", JSONArray().put(
                    JSONObject("""{"delivery_point_id":101,"name":"ПВЗ Ozon","full_address":"Москва, Тестовая улица, 1","coordinates":{"latitude":55.751574,"longitude":37.573856}}""")))
                else -> error("Unexpected map GET $path")
            }
        }
        override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = error("Map must never mutate server state: $path")
    }
}

/** A tiny valid PMTiles v3 archive with one real MVT water polygon at zoom 0. */
private fun pmtilesFixture(): ByteArray {
    fun integers(vararg values: Int): ByteArray = ByteArrayOutputStream().apply {
        values.forEach { original ->
            var value = original
            while (value >= 128) { write((value and 127) or 128); value = value ushr 7 }
            write(value)
        }
    }.toByteArray()
    fun field(tag: Int, data: ByteArray) = integers(tag, data.size) + data
    val geometry = integers(9, 0, 0, 26, 8192, 0, 0, 8192, 8191, 0, 15)
    val feature = integers(8, 1) + field(18, integers(0, 0)) + integers(24, 3) + field(34, geometry)
    val layer = field(10, "water".toByteArray()) + field(18, feature) + field(26, "kind".toByteArray()) +
        field(34, field(10, "ocean".toByteArray())) + integers(40, 4096, 120, 2)
    val tile = field(26, layer)
    val directory = integers(1, 0, 1, tile.size, 1)
    val metadata = """{"vector_layers":[{"id":"water","fields":{"kind":"String"}}],"attribution":"Synthetic test polygon"}""".toByteArray()
    val header = ByteBuffer.allocate(127).order(ByteOrder.LITTLE_ENDIAN)
    header.put("PMTiles".toByteArray()).put(3.toByte())
    header.putLong(127).putLong(directory.size.toLong())
    header.putLong(127L + directory.size).putLong(metadata.size.toLong())
    header.putLong(127L + directory.size + metadata.size).putLong(0)
    header.putLong(127L + directory.size + metadata.size).putLong(tile.size.toLong())
    header.putLong(1).putLong(1).putLong(1)
    header.put(1).put(1).put(1).put(1).put(0).put(0)
    header.putInt(-1800000000).putInt(-850000000).putInt(1800000000).putInt(850000000)
    header.put(0).putInt(375738560).putInt(557515740)
    return header.array() + directory + metadata + tile
}
