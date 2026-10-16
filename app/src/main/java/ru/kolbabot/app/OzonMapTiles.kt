package ru.kolbabot.app

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.URI

/** Streams only bounded public PMTiles byte ranges; no app session is forwarded. */
internal class OzonMapTiles(private val origin: String) {
    @Volatile private var source: URI? = null
    val localUrl: String get() = "$origin/__native_ozon_map__/basemap.pmtiles"

    fun configure(url: String): Boolean {
        source = safeSource(url)
        return source != null
    }

    fun read(rangeHeader: String?): WebResourceResponse {
        val remote = source ?: return failure("Файл карты не настроен")
        val range = byteRange(rangeHeader) ?: return failure("Ожидался ограниченный диапазон карты")
        var connection: HttpURLConnection? = null
        return try {
            connection = remote.toURL().openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Range", "bytes=${range.first}-${range.last}")
                setRequestProperty("Accept", "application/octet-stream")
            }
            if (connection.responseCode != 206) throw IllegalStateException("CDN не поддержал Range-запрос")
            val contentRange = connection.getHeaderField("Content-Range").orEmpty()
            val actual = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(contentRange)
                ?: throw IllegalStateException("CDN вернул некорректный Content-Range")
            val start = actual.groupValues[1].toLong()
            val end = actual.groupValues[2].toLong()
            if (start != range.first || end < start || end > range.last) throw IllegalStateException("CDN вернул другой диапазон")
            val active = connection
            val stream = object : FilterInputStream(active.inputStream) {
                override fun close() { try { super.close() } finally { active.disconnect() } }
            }
            WebResourceResponse("application/octet-stream", null, 206, "Partial Content", mapOf(
                "Content-Range" to contentRange, "Content-Length" to (end - start + 1).toString(),
                "Accept-Ranges" to "bytes", "Access-Control-Allow-Origin" to origin,
                "Access-Control-Expose-Headers" to "Content-Range, Content-Length, Accept-Ranges", "Cache-Control" to "no-store",
            ), stream)
        } catch (_: Exception) {
            connection?.disconnect()
            failure("Не удалось загрузить базовую карту. Поиск ПВЗ продолжает работать.")
        }
    }

    private fun failure(message: String) = WebResourceResponse("text/plain", "UTF-8", 502, "Map Unavailable", emptyMap(), ByteArrayInputStream(message.toByteArray()))

    companion object {
        internal fun safeSource(url: String): URI? = runCatching {
            if (url.any { it <= ' ' || it == '\\' }) return null
            URI(url).takeIf { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null &&
                it.fragment == null && it.path.endsWith(".pmtiles", true) }
        }.getOrNull()

        internal fun byteRange(value: String?): LongRange? = runCatching {
            val match = Regex("bytes=([0-9]+)-([0-9]+)").matchEntire(value.orEmpty()) ?: return null
            val start = match.groupValues[1].toLong()
            val end = match.groupValues[2].toLong()
            if (start < 0 || end < start || end - start >= 16L * 1024 * 1024) null else start..end
        }.getOrNull()
    }
}
