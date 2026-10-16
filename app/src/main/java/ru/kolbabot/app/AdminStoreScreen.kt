package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal fun adminProductPayload(product: JSONObject): JSONObject = JSONObject().apply {
    put("preserve_existing_content", true)
    listOf("title", "category_id", "category_name", "description", "currency", "source_url", "photo_path", "stock", "weight_grams", "length", "dimensions", "sort_order", "variant_group", "variant_label", "product_type", "book_config", "related_ids").forEach { key ->
        if (product.has(key) && !product.isNull(key)) put(key, product.get(key))
    }
    put("price", product.optDouble("price_rub", product.optLong("price_kopecks") / 100.0))
    put("old_price", product.optDouble("old_price_rub", product.optLong("old_price_kopecks") / 100.0))
    put("is_active", product.opt("is_active").let { it == true || it == 1 || it == "1" })
}

@Composable fun AdminStoreScreen(api: ApiClient, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    var route by rememberSaveable { mutableStateOf("products") }; var selected by rememberSaveable { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }; var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }; var pendingDelete by remember { mutableStateOf<JSONObject?>(null) }
    var expandedItem by rememberSaveable(route, search) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(revision) { busy = true; error = ""; try { data = api.get("/api/store?include_inactive=1") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() } finally { busy = false } }
    fun refresh() { selected = ""; revision++ }
    fun action(work: suspend () -> Unit) { if (!busy) { busy = true; error = ""; scope.launch {
        try { work(); revision++ } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() } finally { busy = false }
    } } }
    val back = { if (selected.isNotBlank()) selected = "" else if (expandedItem.isNotBlank()) expandedItem = "" else if (route != "products") route = "products" else if (search.isNotBlank()) search = "" else onBack() }
    BackHandler { back() }
    val collection = data ?: JSONObject()
    val products = collection.items("products"); val categories = collection.items("categories")
    val item = if (selected.isNotBlank()) JSONObject(selected) else null
    if (item != null) {
        when (route) {
            "products" -> {
                val initial = adminProductPayload(item).apply {
                    if (!has("product_type")) put("product_type", "physical")
                    if (!has("category_id")) put("category_id", categories.firstOrNull()?.optString("id").orEmpty())
                    put("book_title", item.optJSONObject("book_config")?.optString("title").orEmpty())
                    if (!item.has("id")) put("is_active", true)
                }
                val fields = listOf(
                    AdminField("product_type", "Тип товара", "choice", true, listOf("physical" to "Физический товар", "digital_book" to "Электронная книга")),
                    AdminField("category_id", "Категория", "choice", true, categories.map { it.optString("id") to it.optString("title") }),
                    AdminField("title", "Название", required = true), AdminField("variant_group", "Общее название карточки вариантов"), AdminField("variant_label", "Название этого варианта"),
                    AdminField("description", "Описание", "multiline"), AdminField("price", "Цена, ₽", "decimal", true), AdminField("old_price", "Старая цена, ₽", "decimal"),
                    AdminField("weight_grams", "Вес, г", "int"), AdminField("length", "Длина, м"), AdminField("dimensions", "Габариты Д/Ш/В, см"), AdminField("stock", "Остаток", "int"),
                    AdminField("photo_path", "Ссылка или путь фотографии"), AdminField("source_url", "Ссылка источника"), AdminField("sort_order", "Порядок", "int"),
                    AdminField("book_title", "Название книги (для электронной книги)"), AdminField("is_active", "Активно", "bool")
                )
                AdminEditForm(if (item.has("id")) "Редактирование товара" else "Новый товар", fields, initial, { refresh() }) { payload ->
                    payload.put("preserve_existing_content", true)
                    payload.put("category_name", categories.find { it.optString("id") == payload.optString("category_id") }?.optString("title") ?: item.optString("category_name", "Магазин"))
                    payload.put("currency", item.optString("currency", "RUB"))
                    if (payload.optString("product_type") == "digital_book") payload.put("book_config", JSONObject().put("title", payload.optString("book_title")))
                    payload.remove("book_title")
                    if (item.has("id")) api.patch("/api/admin/store/products/${item.optLong("id")}", payload) else api.post("/api/admin/store/products", payload)
                }
            }
            "related" -> AdminRelatedProducts(api, item, products) { route = "products"; refresh() }
            "categories" -> AdminEditForm(if (item.has("id")) "Категория магазина" else "Новая категория", listOf(AdminField("title", "Название", required = true), AdminField("sort_order", "Порядок", "int")), item, { refresh() }) { payload ->
                if (item.has("id")) api.patch("/api/admin/store/categories/${adminEncode(item.optString("id"))}", payload) else api.post("/api/admin/store/categories", payload)
            }
            "delivery", "sizes" -> AdminEditForm(item.optString("title", item.optString("code")),
                if (route == "sizes") listOf(AdminField("price", "Стоимость, ₽", "decimal", true), AdminField("is_active", "Включено", "bool")) else listOf(AdminField("is_active", "Доступно покупателям", "bool")),
                JSONObject(item.toString()).put("price", item.optLong("price_kopecks") / 100.0), { refresh() }) { payload ->
                api.patch("/api/admin/store/${if (route == "sizes") "delivery-sizes" else "delivery"}/${adminEncode(item.optString("code"))}", payload)
            }
        }
        return
    }
    if (route == "promos") { AdminStorePromotions(api, collection) { route = "products"; revision++ }; return }
    if (route == "minimum") { AdminEditForm("Минимальный заказ", listOf(AdminField("minimum_order_rub", "Минимальная сумма товаров, ₽", "int", true)), collection.optJSONObject("store_settings") ?: JSONObject(), { route = "products"; revision++ }) { api.patch("/api/admin/store/settings", it) }; return }
    if (route == "import") { AdminStoreImport(api) { route = "products"; revision++ }; return }
    Page("Магазин · админка", onBack = back) {
        ErrorMessage(error); if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row { TextButton(onClick = { revision++ }, enabled = !busy) { Text("Обновить") }; TextButton(onClick = { onNavigate("banners") }) { Text("Баннеры") }; TextButton(onClick = { onNavigate("digital") }) { Text("Книги") } }
        val tabs = listOf("products" to "Товары", "categories" to "Категории", "delivery" to "Доставка", "sizes" to "Тарифы посылок", "promos" to "Акции", "minimum" to "Минимальный заказ", "import" to "Импорт и VK")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { (key, label) -> FilterChip(selected = route == key, onClick = { route = key }, enabled = !busy, label = { Text(label) }) }
        }
        when (route) {
            "products" -> {
                Button(onClick = { selected = "{}" }, enabled = !busy) { Text("Новый товар") }
                OutlinedTextField(search, { search = it }, label = { Text("Поиск по названию, варианту или номеру") }, modifier = Modifier.fillMaxWidth())
                Text("Варианты с одинаковым общим названием объединяются в карточку. Их цены, вес и остатки сохраняются отдельно.", style = MaterialTheme.typography.bodySmall)
                products.filter { search.isBlank() || listOf(it.optString("title"), it.optString("variant_label"), it.optString("id")).any { v -> v.contains(search, true) } }.forEach { product ->
                    val itemKey = product.optLong("id").toString()
                    SiteDisclosure(product.optString("title"), expandedItem == itemKey, { expandedItem = if (expandedItem == itemKey) "" else itemKey },
                        modifier = Modifier.testTag("admin-product-$itemKey"),
                        summary = "${money(product.optLong("price_kopecks"))} · Остаток: ${product.optInt("stock")} · ${if (product.optInt("is_active") == 1) "В продаже" else "Скрыт"}",
                        accent = MaterialTheme.colorScheme.tertiary, bodyPadding = 12.dp, bodySpacing = 8.dp) {
                        if (product.optString("variant_label").isNotBlank()) Text(product.optString("variant_label"))
                        Row { TextButton(onClick = { selected = product.toString() }, enabled = !busy) { Text("Изменить") }; TextButton(onClick = { route = "related"; selected = product.toString() }, enabled = !busy) { Text("Связанные товары") } }
                        Row {
                            val index = products.indexOf(product)
                            listOf(-1 to "↑", 1 to "↓").forEach { (step, label) -> TextButton(enabled = !busy && index + step in products.indices, onClick = {
                                val ordered = products.toMutableList(); java.util.Collections.swap(ordered, index, index + step)
                                action { api.post("/api/admin/store/products/reorder", JSONObject().put("product_ids", JSONArray(ordered.map { it.optLong("id") }))) }
                            }) { Text(label) } }
                            TextButton(enabled = !busy, onClick = { pendingDelete = JSONObject().put("kind", "product").put("item", product) }) { Text("Удалить") }
                        }
                    }
                }
            }
            "categories" -> {
                Button(onClick = { selected = "{}" }, enabled = !busy) { Text("Новая категория") }
                categories.forEach { category ->
                  val itemKey = category.optString("id")
                  SiteDisclosure(category.optString("title"), expandedItem == itemKey, { expandedItem = if (expandedItem == itemKey) "" else itemKey },
                      summary = "Товаров: ${category.optInt("products_count")}", bodyPadding = 12.dp, bodySpacing = 8.dp) {
                    Row { TextButton(onClick = { selected = category.toString() }) { Text("Изменить") }; TextButton(onClick = { pendingDelete = JSONObject().put("kind", "category").put("item", category) }) { Text("Удалить") } }
                } }
            }
            "delivery", "sizes" -> {
                if (route == "sizes") Text("Стоимость доставки определяется весом и тарифами посылок.")
                collection.items(if (route == "sizes") "delivery_sizes" else "delivery_methods").forEach { delivery ->
                  val itemKey = delivery.optString("code")
                  val active = delivery.optInt("is_active") == 1 || delivery.optBoolean("is_active")
                  SiteDisclosure(delivery.optString("title", itemKey), expandedItem == itemKey, { expandedItem = if (expandedItem == itemKey) "" else itemKey },
                      summary = listOfNotNull(if (route == "sizes") money(delivery.optLong("price_kopecks")) else null, if (active) "Включено" else "Выключено").joinToString(" · "),
                      accent = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, bodyPadding = 12.dp, bodySpacing = 8.dp) {
                    TextButton(onClick = { selected = delivery.toString() }) { Text("Изменить") }
                } }
            }
        }
    }
    pendingDelete?.let { pending ->
        val current = pending.getJSONObject("item"); val category = pending.optString("kind") == "category"
        var replacement by remember(pending.toString()) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!busy) pendingDelete = null }, title = { Text("Удалить «${current.optString("title") }»?") }, text = {
            Column {
                Text("Это действие удалит запись на сервере.")
                if (category && current.optInt("products_count") > 0) {
                    Text("Перенести товары в категорию:")
                    categories.filter { it.optString("id") != current.optString("id") }.forEach { other -> Row {
                        RadioButton(selected = replacement == other.optString("id"), onClick = { replacement = other.optString("id") }); Text(other.optString("title"))
                    } }
                }
            }
        }, confirmButton = { TextButton(enabled = !busy && (!category || current.optInt("products_count") == 0 || replacement.isNotBlank()), onClick = {
            action { if (category) api.delete("/api/admin/store/categories/${adminEncode(current.optString("id"))}", JSONObject().put("reassign_to", replacement))
                else api.delete("/api/admin/store/products/${current.optLong("id")}"); pendingDelete = null }
        }) { Text("Удалить") } }, dismissButton = { TextButton(enabled = !busy, onClick = { pendingDelete = null }) { Text("Отмена") } })
    }
}

@Composable private fun AdminRelatedProducts(api: ApiClient, product: JSONObject, products: List<JSONObject>, onBack: () -> Unit) {
    var selected by rememberSaveable(product.optLong("id")) { mutableStateOf((product.optJSONArray("related_ids") ?: JSONArray()).toString()) }
    var query by rememberSaveable { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ids = JSONArray(selected).let { a -> (0 until a.length()).map { a.optLong(it) } }
    Page("С этим товаром покупают", product.optString("title"), onBack) {
        ErrorMessage(error); Text("Выбрано ${ids.size} из 24")
        OutlinedTextField(query, { query = it }, label = { Text("Поиск товара") }, modifier = Modifier.fillMaxWidth())
        products.filter { it.optLong("id") != product.optLong("id") && (query.isBlank() || it.optString("title").contains(query, true)) }.forEach { other ->
            Row { val id = other.optLong("id"); Checkbox(checked = id in ids, enabled = !busy && (ids.size < 24 || id in ids), onCheckedChange = { checked -> selected = JSONArray(if (checked) ids + id else ids - id).toString() }); Text(other.optString("title")) }
        }
        Button(enabled = !busy, onClick = { busy = true; scope.launch {
            try { api.patch("/api/admin/store/products/${product.optLong("id")}", JSONObject().put("related_ids", JSONArray(selected))); onBack() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message.orEmpty() } finally { busy = false }
        } }) { Text("Сохранить связи") }
    }
}

@Composable private fun AdminStorePromotions(api: ApiClient, data: JSONObject, onBack: () -> Unit) {
    val sausages = data.optJSONObject("promo_settings")?.optJSONObject("sausages") ?: JSONObject()
    val reviews = data.optJSONObject("review_bonus_settings") ?: JSONObject(); val referral = data.optJSONObject("referral_settings") ?: JSONObject()
    val initial = JSONObject().put("sausages_active", sausages.opt("is_active")).put("standard_percent", sausages.optInt("standard_percent", 5)).put("premium_percent", sausages.optInt("premium_percent", 10))
        .put("review_active", reviews.opt("is_active")).put("review_amount", reviews.optInt("sausages", 20)).put("referral_active", referral.opt("is_active")).put("referral_percent", referral.optInt("percent", 5))
    AdminEditForm("Акции магазина", listOf(AdminField("sausages_active", "Колбаски за покупки", "bool"), AdminField("standard_percent", "Обычный возврат, %", "int", true), AdminField("premium_percent", "Возврат PREMIUM, %", "int", true),
        AdminField("review_active", "Бонусы за отзывы", "bool"), AdminField("review_amount", "Колбасок за отзыв", "int", true), AdminField("referral_active", "Приведи друга", "bool"), AdminField("referral_percent", "Возврат за покупку друга, %", "int", true)), initial, onBack) { payload ->
        api.patch("/api/admin/store/promos", JSONObject().put("settings", JSONObject()
            .put("sausages", JSONObject().put("is_active", payload.getBoolean("sausages_active")).put("standard_percent", payload.getInt("standard_percent")).put("premium_percent", payload.getInt("premium_percent")))
            .put("review_bonus", JSONObject().put("is_active", payload.getBoolean("review_active")).put("sausages", payload.getInt("review_amount")))
            .put("referral", JSONObject().put("is_active", payload.getBoolean("referral_active")).put("percent", payload.getInt("referral_percent")))))
    }
}

@Composable private fun AdminStoreImport(api: ApiClient, onBack: () -> Unit) {
    var token by remember { mutableStateOf("") }; var result by remember { mutableStateOf<JSONObject?>(null) }; var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Page("Импорт магазина и VK", onBack = onBack) {
        Text("Импорт обновляет каталог данными выбранного YML/XML. Токен VK необязателен: он нужен для полной синхронизации описаний и не сохраняется в приложении.")
        OutlinedTextField(token, { token = it }, label = { Text("VK Market token") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        AdminUploadButton(api, "/api/admin/store/import", "Выбрать и импортировать YML/XML", fields = if (token.isBlank()) emptyMap() else mapOf("market_token" to token)) { result = it }
        Button(enabled = !busy && token.isNotBlank(), onClick = { busy = true; scope.launch {
            try { result = api.post("/api/admin/store/descriptions/refresh", JSONObject().put("market_token", token)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message.orEmpty() } finally { busy = false }
        } }) { Text("Обновить описания из VK") }
        ErrorMessage(error); result?.let { AdminJsonDetails(it) }
    }
}
