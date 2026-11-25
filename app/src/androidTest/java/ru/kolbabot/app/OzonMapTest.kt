package ru.kolbabot.app

import android.content.Context
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
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** MapLibre Native renders a real PMTiles archive and invokes the production pickup endpoints. */
@RunWith(AndroidJUnit4::class)
class OzonMapTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val selected = AtomicReference<JSONObject>()

    @Test fun nativeRendererLoadsPmtilesAndSelectsAPickupPoint() {
        val api = MapApi(context)
        val map = AtomicReference<MapLibreMap>()
        val tiles = File(context.cacheDir, "ozon-pickup-test.pmtiles").apply { writeBytes(pmtilesFixture()) }
        compose.setContent { KolbaTheme {
            CompositionLocalProvider(LocalOzonMapTestConfig provides OzonMapTestConfig(tiles) { map.set(it) }) {
                OzonPickupPicker(api, null, {}, { selected.set(it) })
            }
        } }
        val mapCenter = LatLng(55.751574, 37.573856)
        compose.waitUntil(25_000) {
            map.get()?.let { current ->
                compose.runOnIdle {
                    val point = current.projection.toScreenLocation(mapCenter)
                    current.queryRenderedFeatures(point, "water").isNotEmpty() &&
                        current.queryRenderedFeatures(point, "ozon-pvz-circle").isNotEmpty()
                }
            } == true
        }
        assertTrue(api.reads.any { it.startsWith("${OzonDelivery.API}map-points?") })
        screenshot("02-native-maplibre-pmtiles-marker")
        compose.onNodeWithContentDescription("Карта пунктов выдачи Ozon").assertIsDisplayed().performTouchInput { click(center) }
        compose.waitUntil(5000) { selected.get() != null }
        assertEquals(101L, selected.get().getLong("delivery_point_id"))
        assertEquals("Москва, Тестовая улица, 1", selected.get().getString("full_address"))
        assertTrue(api.reads.any { it.startsWith("${OzonDelivery.API}map-point-info?ids=101") })
    }

    @Test fun mapFailureKeepsNativeSearchAndPointSelectionAvailable() {
        val api = MapApi(context, fail = true)
        compose.setContent { KolbaTheme { OzonPickupPicker(api, null, {}, { selected.set(it) }) } }
        compose.onNodeWithText("Карта временно недоступна").assertIsDisplayed()
        compose.onNodeWithContentDescription("Карта пунктов выдачи Ozon").assertDoesNotExist()
        compose.onNodeWithTag("ozonPointSearch").performScrollTo().performTextInput("Москва")
        compose.onNodeWithTag("ozonPointSearch").performImeAction()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("ozonPoint_101").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ozonPointResults").performScrollTo()
        screenshot("03-map-error-search-available")
        compose.onNodeWithTag("ozonPoint_101").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(101L, selected.get().getLong("delivery_point_id"))
    }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("No map screenshot")
        val directory = File(context.getExternalFilesDir(null), "ozon-smoke").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
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

/** A PMTiles v3 archive with one real MVT water polygon at zoom 0. */
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
    // Keep the initial 16 KiB PMTiles read in-range, as with the production archive.
    return header.array() + directory + metadata + tile + ByteArray(16_384)
}
