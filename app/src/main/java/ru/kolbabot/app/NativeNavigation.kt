package ru.kolbabot.app

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import java.net.URI
import java.net.URLDecoder

internal data class AppDestination(val section: String, val productId: Long = 0, val articleId: Long = 0, val orderId: String = "")
internal object NativeNavigation { val requests = MutableSharedFlow<AppDestination>(extraBufferCapacity = 8) }

/** Only known same-origin sections are routed. AdminScreen independently verifies access. */
internal fun nativeDestination(url: String, baseUrl: String = BuildConfig.BASE_URL): AppDestination? = runCatching {
    val base = URI(baseUrl)
    val uri = base.resolve(url)
    if (uri.scheme != "https" || uri.host != base.host || uri.port != base.port || uri.userInfo != null) return null
    if (uri.path in listOf("/admin", "/admin/")) {
        return if (uri.rawQuery.isNullOrEmpty() && uri.fragment.isNullOrEmpty()) AppDestination("admin") else null
    }
    if (uri.path !in listOf("", "/")) return null
    val params = uri.rawQuery.orEmpty().split('&').mapNotNull {
        val pair = it.split('=', limit = 2)
        if (pair.size != 2) null else URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
    }.toMap()
    if (params.keys.any { it !in setOf("product", "store_product", "article", "order", "store_order_id") }) return null
    (params["product"] ?: params["store_product"])?.toLongOrNull()?.takeIf { it > 0 }?.let { return AppDestination("store", productId = it) }
    params["article"]?.toLongOrNull()?.takeIf { it > 0 }?.let { return AppDestination("articles", articleId = it) }
    (params["order"] ?: params["store_order_id"])?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,160}")) }?.let { return AppDestination("orders", orderId = it) }
    if (params.isNotEmpty()) return null
    when (val section = uri.fragment.orEmpty()) {
        "", "home", "store" -> AppDestination("store")
        "recipes", "handbook", "articles", "equipment", "faq", "custom", "account", "support", "notifications", "admin" -> AppDestination(section)
        else -> null
    }
}.getOrNull()

fun shareLink(context: Context, title: String, url: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    }, "Поделиться"))
}
