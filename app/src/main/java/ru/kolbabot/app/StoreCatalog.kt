package ru.kolbabot.app

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal fun variantGroup(product: JSONObject): String = if (product.optString("product_type") == "digital_book") "" else product.optString("variant_group").trim().takeUnless { it == "null" }.orEmpty()

internal fun storeSearchText(text: String): String = text.lowercase(Locale.ROOT).replace('ё', 'е')
    .replace(Regex("([a-zа-я])(\\d)"), "$1 $2").replace(Regex("(\\d)([a-zа-я])"), "$1 $2")
    .replace(Regex("[^0-9a-zа-я\\s]"), " ").replace(Regex("\\s+"), " ").trim()

private fun searchDistance(a: String, b: String, limit: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1); current[0] = i + 1
        for (j in b.indices) current[j + 1] = minOf(previous[j + 1] + 1, current[j] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
        if (current.minOrNull()!! > limit) return limit + 1
        previous = current
    }
    return previous.last()
}

private fun searchLimit(length: Int) = when { length <= 2 -> 0; length <= 4 -> 1; length <= 7 -> 2; length <= 12 -> 3; else -> 4 }
private fun tokenScore(query: String, candidate: String): Double {
    if (query == candidate) return 1.0
    if (candidate.startsWith(query)) return .94
    if (candidate.contains(query)) return .88
    if (query.length >= 3 && candidate.length >= 3 && query.contains(candidate)) return .72
    val limit = searchLimit(query.length)
    if (limit == 0) return 0.0
    if (query.length >= 3 && candidate.length > query.length) {
        val distance = searchDistance(query, candidate.take(query.length), limit)
        if (distance <= limit) return maxOf(.68, 1.0 - distance.toDouble() / query.length)
    }
    val distance = searchDistance(query, candidate, limit)
    return if (distance <= limit) maxOf(.48, 1.0 - distance.toDouble() / maxOf(query.length, candidate.length)) else 0.0
}

internal fun productSearchScore(product: JSONObject, search: String): Double {
    val query = storeSearchText(search).take(120)
    if (query.isEmpty()) return 1.0
    val hay = storeSearchText("${product.optString("title")} ${variantGroup(product)} ${product.optString("variant_label")}").take(500)
    if (hay.contains(query)) return 1.0
    val compact = query.replace(" ", ""); val compactHay = hay.replace(" ", "")
    var window = if (compactHay.contains(compact)) .98 else 0.0
    val limit = searchLimit(compact.length)
    if (window == 0.0 && compact.length >= 3) {
        for (size in maxOf(3, compact.length - limit)..minOf(compactHay.length, compact.length + limit)) {
            for (i in 0..compactHay.length - size) {
                val distance = searchDistance(compact, compactHay.substring(i, i + size), limit)
                if (distance <= limit) window = maxOf(window, 1.0 - distance.toDouble() / compact.length)
            }
        }
        if (window < .5) window = 0.0
    }
    val words = hay.split(' ').filter(String::isNotBlank)
    val candidates = words + words.zipWithNext { a, b -> a + b }
    val scores = query.split(' ').map { word -> candidates.maxOfOrNull { tokenScore(word, it) } ?: 0.0 }
    val matched = scores.count { it >= .5 }
    val average = scores.average()
    val token = if ((matched >= kotlin.math.ceil(scores.size * .6) && average >= .45) || (scores.all { it >= .42 } && average >= .48))
        maxOf(average, matched.toDouble() / scores.size * .88) else 0.0
    return maxOf(window * .96, token)
}

internal fun visibleStoreProducts(products: List<JSONObject>, category: String, search: String): List<JSONObject> {
    val candidates = products.filter { product ->
        val ids = product.optJSONArray("category_ids")
        category.isBlank() || product.optString("category_id") == category || (0 until (ids?.length() ?: 0)).any { ids?.optString(it) == category }
    }
    val searching = storeSearchText(search).isNotEmpty()
    val ranked = if (searching) candidates.map { it to productSearchScore(it, search) }.filter { it.second >= .45 }
        .sortedWith(compareByDescending<Pair<JSONObject, Double>> { it.second }.thenBy { it.first.optInt("sort_order") }.thenBy { it.first.optLong("id") }).map { it.first }
        else candidates
    val groups = linkedMapOf<String, JSONObject>()
    ranked.forEach { product ->
        val group = variantGroup(product); val key = if (group.isNotBlank()) "group:$group" else "sku:${product.optLong("id") }"
        val previous = groups[key]
        if (previous == null || (!searching && product.optLong("price_kopecks") < previous.optLong("price_kopecks"))) groups[key] = product
    }
    return groups.values.toList()
}

internal fun mergeCartQuantities(server: List<JSONObject>, guest: List<JSONObject>): List<JSONObject> {
    val quantities = linkedMapOf<Long, Int>()
    (server + guest).forEach { item ->
        val id = item.optLong("product_id", item.optLong("id")); val count = item.optInt("quantity").coerceIn(0, 999)
        if (id > 0 && count > 0) quantities[id] = ((quantities[id] ?: 0) + count).coerceAtMost(999)
    }
    return quantities.map { (id, count) -> JSONObject().put("product_id", id).put("quantity", count) }
}

/** Guest selections stay on this device. A saved exact PUT makes an interrupted merge repeatable. */
internal class StoreCart(context: Context, private val api: ApiClient) {
    private val preferences = context.applicationContext.getSharedPreferences("guest_cart", Context.MODE_PRIVATE)
    private fun rows(key: String): List<JSONObject> = runCatching { JSONObject().put("items", JSONArray(preferences.getString(key, "[]"))).items("items") }.getOrDefault(emptyList())
    private fun array(rows: List<JSONObject>) = JSONArray().apply { rows.forEach { put(it) } }
    private fun body(rows: List<JSONObject>) = JSONObject().put("items", array(rows))
    private fun guest(catalog: JSONObject): JSONObject {
        val products = catalog.items("products").associateBy { it.optLong("id") }
        return body(rows("items").mapNotNull { row -> products[row.optLong("product_id")]?.let { product ->
            JSONObject(product.toString()).put("product_id", product.optLong("id")).put("quantity", row.optInt("quantity"))
        } })
    }
    private fun notice(cart: JSONObject, text: String): JSONObject = JSONObject(cart.toString()).put("merge_notice", text)
    private suspend fun rejectedMerge(uid: Long, pending: String, reason: String): JSONObject {
        api.withCartOwner(uid) {
            val snapshot = "${pending}_guest"
            val edit = preferences.edit()
            if (preferences.contains(snapshot)) {
                // The saved PUT includes private server items; restore only its guest portion.
                val restored = mergeCartQuantities(rows("items"), rows(snapshot))
                edit.putString("items", array(restored).toString())
            } else {
                edit.putString("rejected_$uid", preferences.getString(pending, "[]"))
            }
            check(edit.remove(pending).remove(snapshot).commit()) { "Не удалось восстановить гостевую корзину." }
        }
        return notice(api.getCart(uid), "$reason Гостевые товары сохранены на устройстве. Корзину аккаунта можно редактировать; после освобождения места обновите её.")
    }
    private suspend fun loadUnlocked(catalog: JSONObject, uid: Long): JSONObject {
        if (uid <= 0) return api.withCartOwner(uid) { guest(catalog) }
        val pending = "pending_$uid"
        if (!preferences.contains(pending)) {
            val server = api.getCart(uid)
            val local = api.withCartOwner(uid) { rows("items") }
            if (local.isEmpty()) return server
            val merged = mergeCartQuantities(server.items("items"), local)
            if (merged.size > MAX_LINES) return notice(server,
                "Гостевые товары пока не добавлены: вместе получится ${merged.size} позиций, максимум — $MAX_LINES. Уберите лишние позиции из корзины аккаунта и обновите её. Гостевые товары сохранены на устройстве.")
            api.withCartOwner(uid) {
                check(preferences.edit().putString(pending, array(merged).toString())
                    .putString("${pending}_guest", array(local).toString()).remove("items").commit()) { "Не удалось сохранить корзину на устройстве." }
            }
        }
        check(api.userId == uid) { "Аккаунт изменился. Откройте корзину заново." }
        val exact = runCatching {
            val array = JSONArray(preferences.getString(pending, ""))
            require(array.length() in 1..MAX_LINES)
            (0 until array.length()).map { index -> array.getJSONObject(index).also {
                require(it.optLong("product_id") > 0 && it.optInt("quantity") in 1..999)
            } }
        }.getOrElse { return rejectedMerge(uid, pending, "Не удалось восстановить предыдущую попытку объединения.") }
        val saved = try { api.putCart(uid, body(exact)) }
        catch (failure: ApiException) {
            if (failure.status in setOf(400, 422)) return rejectedMerge(uid, pending, "Сервер отклонил объединение корзин.")
            throw failure
        }
        api.withCartOwner(uid) {
            check(preferences.edit().remove(pending).remove("${pending}_guest").commit()) { "Не удалось подтвердить сохранение корзины." }
        }
        return saved
    }
    suspend fun load(catalog: JSONObject): JSONObject {
        val uid = api.userId
        return lock.withLock {
            val result = loadUnlocked(catalog, uid)
            api.withCartOwner(uid) { result }
        }
    }
    suspend fun save(items: List<JSONObject>, catalog: JSONObject): JSONObject {
        val uid = api.userId
        return lock.withLock { saveUnlocked(items, catalog, uid) }
    }
    private suspend fun saveUnlocked(items: List<JSONObject>, catalog: JSONObject, uid: Long): JSONObject {
        val sanitized = mergeCartQuantities(emptyList(), items)
        // Old versions/recovery may contain more rows. Always allow removal,
        // while preventing a new oversized guest cart or a rejected server PUT.
        require(sanitized.size <= MAX_LINES || (uid <= 0 && sanitized.size < rows("items").size)) {
            "В корзине может быть не больше $MAX_LINES разных товаров. Уберите лишние позиции."
        }
        if (uid > 0) return api.putCart(uid, body(sanitized))
        return api.withCartOwner(uid) {
            check(preferences.edit().putString("items", array(sanitized).toString()).commit()) { "Не удалось сохранить корзину на устройстве." }
            guest(catalog)
        }
    }
    private suspend fun updateQuantity(id: Long, catalog: JSONObject, quantity: (Int) -> Int): JSONObject {
        require(id > 0) { "Неизвестный товар." }
        val uid = api.userId
        return lock.withLock {
            val current = loadUnlocked(catalog, uid)
            val currentRows = current.items("items")
            val previous = currentRows.firstOrNull { it.optLong("product_id", it.optLong("id")) == id }
                ?.optInt("quantity") ?: 0
            val target = quantity(previous).coerceIn(0, 999)
            if (target == previous) return@withLock api.withCartOwner(uid) { current }
            val updated = currentRows.mapNotNull { row ->
                if (row.optLong("product_id", row.optLong("id")) != id) row
                else if (target == 0) null
                else JSONObject(row.toString()).put("quantity", target)
            }.toMutableList()
            if (target > 0 && previous == 0) updated += JSONObject().put("product_id", id).put("quantity", target)
            val saved = saveUnlocked(updated, catalog, uid)
            api.withCartOwner(uid) { saved }
        }
    }
    suspend fun changeQuantity(id: Long, delta: Int, catalog: JSONObject): JSONObject =
        updateQuantity(id, catalog) { (it.toLong() + delta).coerceIn(0L, 999L).toInt() }
    suspend fun adjustQuantity(id: Long, delta: Int, catalog: JSONObject): JSONObject = changeQuantity(id, delta, catalog)
    suspend fun setQuantity(id: Long, quantity: Int, catalog: JSONObject): JSONObject = updateQuantity(id, catalog) { quantity }
    suspend fun add(id: Long, catalog: JSONObject): JSONObject = changeQuantity(id, 1, catalog)
    companion object { private val lock = Mutex(); private const val MAX_LINES = 100 }
}
