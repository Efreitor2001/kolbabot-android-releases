package ru.kolbabot.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URLEncoder

@Composable
internal fun LiveUpdates(api: ApiClient, accountId: Long) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(api, accountId, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var cursor: JSONObject? = null
            while (true) {
                try {
                    val next = api.getLiveUpdates(cursor?.let { URLEncoder.encode(it.toString(), "UTF-8") }, accountId)
                    val snapshot = next.optJSONObject("revisions") ?: JSONObject()
                    cursor = snapshot
                    api.liveRevisions.value = snapshot.keys().asSequence().associateWith { snapshot.optString(it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    delay(2500)
                }
            }
        }
    }
}

@Composable
internal fun liveRevision(api: ApiClient, vararg topics: String): List<String> {
    val revisions = api.liveRevisions.collectAsState().value
    return topics.map { revisions[it].orEmpty() }
}

internal fun liveTopics(path: String): Array<String> {
    val route = path.substringBefore('?')
    return when {
        route.startsWith("/api/admin/support") -> arrayOf("support_admin", "users")
        route.startsWith("/api/support") -> arrayOf("support")
        route.startsWith("/api/admin/notifications") -> arrayOf("notifications_admin", "users")
        route.startsWith("/api/notifications") -> arrayOf("notifications")
        route.startsWith("/api/admin/dashboard") -> arrayOf("support_admin", "orders_admin", "users", "articles", "billing", "analytics")
        route.startsWith("/api/admin/store") -> arrayOf("catalog", "catalog_admin", "orders_admin", "billing")
        route.startsWith("/api/admin/users") -> arrayOf("users", "orders_admin", "support_admin", "billing")
        route.startsWith("/api/admin/articles") -> arrayOf("articles")
        route.startsWith("/api/admin/bootstrap") -> arrayOf("articles", "equipment", "faq", "handbook", "recipes", "plans", "users", "appearance", "billing")
        route.startsWith("/api/admin/") -> arrayOf("audit", "billing", "orders_admin", "appearance")
        route.startsWith("/api/store/orders") -> arrayOf("orders", "account")
        route.startsWith("/api/store/cart") -> arrayOf("cart", "catalog", "account")
        route.startsWith("/api/store") -> arrayOf("catalog", "account")
        route.startsWith("/api/account") || route.startsWith("/api/payments") -> arrayOf("account", "plans", "orders")
        route.startsWith("/api/articles") -> arrayOf("articles")
        route.startsWith("/api/handbook") -> arrayOf("handbook")
        route.startsWith("/api/equipment") -> arrayOf("equipment", "catalog")
        route.startsWith("/api/faq") -> arrayOf("faq")
        route.startsWith("/api/categories") || route.startsWith("/api/recipes") -> arrayOf("recipes", "calculator")
        route.startsWith("/api/tseh") -> arrayOf("tseh", "orders", "catalog")
        route.startsWith("/api/site") || route == "/api/bootstrap" -> arrayOf("appearance", "catalog", "plans")
        else -> emptyArray()
    }
}
