package ru.kolbabot.app

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/** Shared contract for the native checkout and the isolated map renderer. */
internal object OzonDelivery {
    const val API = "/api/store/ozon-delivery/"

    fun manual(point: JSONObject?): Boolean = point?.optString("checkout_mode") == "manual"

    fun validPoint(point: JSONObject): Boolean = point.optString("full_address").isNotBlank() &&
        (manual(point) || point.optLong("delivery_point_id") > 0)

    fun pointName(point: JSONObject): String {
        val name = point.optString("name").ifBlank { "ПВЗ Ozon" }
        val number = point.optString("delivery_point_number").trim()
        return if (number.isNotBlank() && !name.contains(number)) "$name $number" else name
    }

    fun daysText(days: Int): String {
        if (days <= 0) return ""
        val suffix = when {
            days % 10 == 1 && days % 100 != 11 -> "день"
            days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
            else -> "дней"
        }
        return "$days $suffix"
    }

    fun estimateDays(response: JSONObject?, pointId: Long): Int =
        if (pointId > 0 && response?.optLong("delivery_point_id") == pointId)
            response.optInt("estimated_delivery_days").coerceAtLeast(0) else 0

    // Includes every value that can change the server quote. The displayed quote
    // becomes invalid synchronously, before a replacement coroutine starts.
    fun quoteKey(pointId: Long, phone: String, rows: List<JSONObject>, premium: Boolean): String {
        val digits = phone.filter(Char::isDigit)
        if (pointId <= 0 || digits.length < 10) return ""
        return "$pointId|$digits|$premium|" + rows.sortedBy { it.optLong("product_id") }.joinToString("|") {
            listOf(it.optLong("product_id"), it.optInt("quantity"), it.optLong("price_kopecks"),
                it.optLong("shipping_weight_grams", it.optLong("weight_grams")), it.optString("dimensions")).joinToString(":")
        }
    }

    /** A map script can only read these three endpoints, never arbitrary session APIs. */
    fun allowedMapPath(path: String): Boolean = runCatching {
        if (path.length > 8192 || path.contains('\\')) return false
        val uri = URI(path)
        if (uri.isAbsolute || uri.rawAuthority != null || uri.rawFragment != null || uri.rawPath != uri.path) return false
        val allowed = when (uri.path) {
            "${API}map-config" -> emptySet()
            "${API}map-points" -> setOf("min_lat", "max_lat", "min_lon", "max_lon", "zoom")
            "${API}map-point-info" -> setOf("ids", "source")
            else -> return false
        }
        val query = uri.rawQuery.orEmpty()
        query.isEmpty() || query.split('&').all {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") in allowed
        }
    }.getOrDefault(false)
}
