package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** List records expose their existing fields/actions only after an explicit expansion. */
@Composable internal fun AdminListEntry(id: String, title: String, summary: String = "", count: Int? = null,
                                      initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    key(id) {
        var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
        BackHandler(expanded) { expanded = false }
        SiteDisclosure(title, expanded, { expanded = !expanded }, summary = summary, count = count,
            modifier = Modifier.testTag("admin-entry-$id"), bodyPadding = 12.dp, bodySpacing = 8.dp, content = content)
    }
}

@Composable fun AdminCommerceScreen(api: ApiClient, section: String, onBack: () -> Unit) {
    when (section) {
        "carts" -> AdminCarts(api, onBack)
        "reviews" -> AdminReviews(api, onBack)
        "digital" -> AdminDigitalSales(api, onBack)
        else -> Page("Магазин", onBack = onBack) { Text("Раздел не найден.") }
    }
}

@Composable private fun AdminCarts(api: ApiClient, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    RemotePage("Корзины покупателей", api, "/api/admin/store/carts?limit=500", onBack) { data, reload ->
        BackHandler(query.isNotBlank()) { query = "" }
        val summary = data.optJSONObject("summary") ?: JSONObject()
        AdminListEntry("cart-summary", "Сводка", "${summary.optInt("carts_count")} · ${money(summary.optLong("total_kopecks"))}") {
            AdminCopyField("Корзин", summary.optInt("carts_count").toString())
            AdminCopyField("Позиций", summary.optInt("positions_count").toString())
            AdminCopyField("Товаров", summary.optInt("items_quantity").toString())
            AdminCopyField("Потенциальная сумма", money(summary.optLong("total_kopecks")))
            AdminCopyField("Средняя корзина", money(summary.optLong("average_kopecks")))
        }
        OutlinedTextField(query, { query = it }, label = { Text("Найти покупателя, почту или товар") }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = reload) { Text("Обновить корзины") }
        val all = data.items("items")
        val carts = all.filter { query.isBlank() || it.toString().contains(query.trim(), true) }
        if (all.size >= 500) Text("Показаны 500 последних корзин; поиск по загруженным данным.")
        if (carts.isEmpty()) Text("Корзины не найдены.")
        var visible by remember(query, data) { mutableIntStateOf(30) }
        carts.take(visible).forEach { cart ->
            key(cart.optLong("user_id")) {
                AdminListEntry("cart-${cart.optLong("user_id")}", cart.optString("username", "Покупатель"),
                    "${cart.optInt("positions_count")} поз. · ${cart.optInt("items_quantity")} шт. · ${money(cart.optLong("total_kopecks"))}") {
                    AdminCopyField("Пользователь", cart.optLong("user_id").toString())
                    AdminCopyField("Почта", cart.optString("email"))
                    AdminCopyField("Обновлена", cart.optString("updated_at"))
                    AdminListEntry("cart-items-${cart.optLong("user_id")}", "Состав корзины", count = cart.items("items").size) {
                        AdminCopyField("Общий вес, г", cart.optLong("total_weight_grams").toString())
                        cart.items("items").forEach { item ->
                            HorizontalDivider()
                            AdminCopyField("Товар", item.optString("title"))
                            AdminCopyField("ID товара", item.optLong("product_id").toString())
                            AdminCopyField("Количество", item.optInt("quantity").toString())
                            AdminCopyField("Цена", money(item.optLong("price_kopecks")))
                            AdminCopyField("Сумма", money(item.optLong("amount_kopecks")))
                            AdminCopyField("Вес, г", item.optLong("weight_grams").toString())
                            if (!item.optBoolean("is_active")) Text("Товар скрыт от покупателей")
                        }
                        AdminJsonDetails(cart)
                    }
                }
            }
        }
        if (visible < carts.size) TextButton(onClick = { visible += 30 }) { Text("Показать ещё корзины") }
    }
}

@Composable private fun AdminReviews(api: ApiClient, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    key(revision) {
        RemotePage("Отзывы покупателей", api, "/api/admin/store/reviews", onBack) { data, reload ->
            BackHandler(query.isNotBlank()) { query = "" }
            ErrorMessage(error)
            OutlinedTextField(query, { query = it }, label = { Text("Найти товар, автора или текст отзыва") }, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !busy, onClick = reload) { Text("Обновить отзывы") }
            val all = data.items("items")
            val rows = all.filter { query.isBlank() || it.toString().contains(query.trim(), true) }
            if (all.size >= 1000) Text("Показаны последние 1000 отзывов.")
            if (rows.isEmpty()) Text("Отзывы не найдены.")
            var visible by remember(query, data) { mutableIntStateOf(30) }
            rows.take(visible).forEach { review ->
                AdminListEntry("review-${review.optLong("id")}", review.optString("product_title", "Товар #${review.optLong("product_id")}"),
                    "${review.optString("user_nickname")} · ${review.optInt("rating")} / 5 · ${review.optString("created_at").take(10)}") {
                    AdminCopyField("Номер отзыва", review.optLong("id").toString())
                    AdminCopyField("Автор", review.optString("user_nickname"))
                    AdminCopyField("Пользователь", review.optLong("user_id").toString())
                    AdminCopyField("Оценка", "${review.optInt("rating")} / 5")
                    AdminCopyField("Отзыв", review.optString("review_text"))
                    AdminCopyField("Создан", review.optString("created_at"))
                    if (review.optLong("review_bonus_kopecks") > 0) AdminCopyField("Начислено колбасками", money(review.optLong("review_bonus_kopecks")))
                    OutlinedButton(enabled = !busy, onClick = { selected = review }) { Text("Удалить отзыв") }
                }
            }
            if (visible < rows.size) TextButton(onClick = { visible += 30 }) { Text("Показать ещё отзывы") }
        }
    }
    selected?.let { review -> AlertDialog(onDismissRequest = { if (!busy) selected = null },
        title = { Text("Удалить отзыв?") }, text = { Text("Отзыв №${review.optLong("id")} о товаре «${review.optString("product_title")}» будет удалён.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; error = ""
            scope.launch {
                try { api.delete("/api/admin/store/reviews/${review.optLong("id")}"); selected = null; revision++ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Не удалось удалить отзыв."; selected = null }
                finally { busy = false }
            }
        }) { Text("Удалить") } }, dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Отмена") } }) }
}

@Composable private fun AdminDigitalSales(api: ApiClient, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("delivered") }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    if (tab == "books") { AdminBookFiles(api) { tab = "delivered" }; return }
    key(revision) {
        RemotePage("Цифровые продажи", api, "/api/admin/store/digital-sales?status=$tab&limit=1000", onBack) { data, reload ->
            BackHandler(query.isNotBlank()) { query = "" }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("delivered" to "Успешно", "attention" to "Требуют внимания", "books" to "Файлы книг").forEach { (value, label) ->
                    FilterChip(selected = tab == value, onClick = { tab = value }, label = { Text(label) })
                }
            }
            ErrorMessage(error)
            if (notice.isNotBlank()) Text(notice)
            AdminListEntry("digital-summary-$tab", "Продажи", "Экземпляров в сводке: ${data.optInt("total_quantity")}") {
                Text("Экземпляров в сводке: ${data.optInt("total_quantity")}")
                data.items("summary").forEach { AdminCopyField(it.optString("title"), "${it.optInt("quantity")} шт.") }
            }
            OutlinedTextField(query, { query = it }, label = { Text("Найти заказ, экземпляр, почту или покупателя") }, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !busy, onClick = reload) { Text("Обновить отправки") }
            val all = data.items("items")
            val rows = all.filter { query.isBlank() || it.toString().contains(query.trim(), true) }
            if (all.size >= 1000) Text("Показаны последние 1000 отправок выбранной группы; сводка также ограничена ответом сервера.")
            if (rows.isEmpty()) Text(if (tab == "attention") "Отправок, требующих внимания, нет." else "Отправки не найдены.")
            var visible by remember(tab, query, data) { mutableIntStateOf(30) }
            rows.take(visible).forEach { sale ->
                val status = when (sale.optString("status")) {
                    "delivered" -> "Отправлен"; "failed" -> "Ошибка"; "processing" -> "Отправляется"
                    "delivery_committed" -> "Отправка зафиксирована"; "reserved" -> "Ожидает отправки"; else -> sale.optString("status")
                }
                AdminListEntry("sale-${sale.optLong("id")}", sale.optString("product_title", "Цифровой товар"),
                    "$status · ${sale.optString("order_id")} · ${sale.optString("delivery_email")}") {
                    AdminCopyField("Номер заказа", sale.optString("order_id"))
                    AdminCopyField("Экземпляр", sale.optString("instance_id"))
                    AdminCopyField("ФИО", sale.optString("customer_name"))
                    AdminCopyField("Почта", sale.optString("delivery_email"))
                    AdminCopyField("Статус отправки", status)
                    AdminCopyField("Создан", sale.optString("created_at"))
                    AdminCopyField("Обновлён", sale.optString("updated_at"))
                    AdminCopyField("Отправлен", sale.optString("delivered_at"))
                    if (sale.optString("error_text").isNotBlank()) AdminCopyField("Ошибка отправки", sale.optString("error_text"))
                    if (tab == "attention") OutlinedButton(enabled = !busy, onClick = { selected = sale }) { Text("Повторить отправку") }
                }
            }
            if (visible < rows.size) TextButton(onClick = { visible += 30 }) { Text("Показать ещё отправки") }
        }
    }
    selected?.let { sale -> AlertDialog(onDismissRequest = { if (!busy) selected = null },
        title = { Text("Повторить отправку книги?") }, text = { Text("Экземпляр ${sale.optString("instance_id")} будет отправлен покупателю на ${sale.optString("delivery_email")}. Сервер проверит оплату и состояние отправки.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; error = ""; notice = ""
            scope.launch {
                try {
                    val result = api.post("/api/admin/store/digital-sales/${sale.optLong("id")}/retry")
                    if (result.optBoolean("delivered") || result.optBoolean("already_delivered")) notice = "Отправка подтверждена сервером."
                    else error = result.optJSONObject("item")?.optString("error_text").orEmpty().ifBlank { "Отправка пока не подтверждена. Проверьте состояние экземпляра." }
                    selected = null; revision++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = (failure.message ?: "Нет ответа сервера.") + " Обновите список перед повторной отправкой."; selected = null }
                finally { busy = false }
            }
        }) { Text("Отправить") } }, dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Отмена") } }) }
}

@Composable private fun AdminBookFiles(api: ApiClient, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var legacy by rememberSaveable { mutableStateOf(false) }
    BackHandler(selected != null || legacy) { selected = null; legacy = false }
    if (legacy) {
        RemotePage("Исходник старых заказов", api, "/api/admin/store/digital-book", { legacy = false }) { data, _ ->
            val book = data.optJSONObject("book") ?: JSONObject()
            Text("Общий исходник сохранён для старых заказов. Новые книги загружаются отдельно в карточку товара.")
            AdminJsonDetails(book)
            if (book.optBoolean("active")) AdminDownloadButton(api, "/api/admin/store/digital-book/preview?sha256=${book.optString("sha256")}", "Скачать тестовый PDF", "legacy-book-test.pdf", JSONObject())
        }
        return
    }
    selected?.let { raw ->
        val product = JSONObject(raw)
        val config = product.optJSONObject("book_config") ?: JSONObject()
        val productId = product.optLong("id")
        Page("Файлы: ${product.optString("title")}", onBack = { selected = null }) {
            Text("Для изменения названия и публикации откройте карточку товара. Загруженные файлы сохраняются сразу; после замены проверьте тестовый PDF.")
            AdminCopyField("ID товара", productId.toString())
            AdminCopyField("Название книги", config.optString("title"))
            listOf("standard" to "Обычная версия", "mobile" to "Мобильная версия").forEach { (variant, label) ->
                val source = if (variant == "mobile") config.optJSONObject("mobile") ?: JSONObject() else config
                val ready = source.optString("source_sha256").isNotBlank() || (variant == "standard" && source.optBoolean("legacy_source"))
                SectionCard(label) {
                    if (!ready) Text("Файл этой версии ещё не загружен.")
                    AdminCopyField("Имя файла", source.optString("original_filename"))
                    AdminCopyField("Загружен", source.optString("uploaded_at"))
                    AdminCopyField("Версия файла", source.optString("source_sha256"))
                    if (source.optInt("page_count") > 0) AdminCopyField("Страниц", source.optInt("page_count").toString())
                    AdminUploadButton(api, "/api/admin/store/products/$productId/book?variant=$variant", "Загрузить DOCX или PDF",
                        mimeTypes = arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), onUploaded = { result ->
                            selected = (result.optJSONObject("product") ?: JSONObject(product.toString()).put("book_config", result.optJSONObject("book") ?: config)).toString()
                        })
                    if (ready) AdminDownloadButton(api, "/api/admin/store/products/$productId/book/preview", "Скачать тестовый PDF", "book-$productId-$variant-test.pdf",
                        JSONObject().put("variant", variant).put("source_sha256", source.optString("source_sha256"))
                            .put("book_config", JSONObject().put("title", config.optString("title"))))
                }
            }
        }
        return
    }
    RemotePage("Файлы электронных книг", api, "/api/admin/bootstrap", onBack) { data, reload ->
        TextButton(onClick = reload) { Text("Обновить книги") }
        val books = data.items("store_products").filter { it.optString("product_type") == "digital_book" }
        if (books.isEmpty()) Text("Создайте электронную книгу в разделе товаров, затем загрузите её файл.")
        books.forEach { product ->
            AdminListEntry("book-${product.optLong("id")}", product.optString("title"),
                if (product.optInt("is_active") == 1 || product.optBoolean("is_active")) "Опубликована" else "Скрыта / черновик") {
                AdminCopyField("ID товара", product.optLong("id").toString())
                OutlinedButton(onClick = { selected = product.toString() }) { Text("Файлы и тестовый PDF") }
            }
        }
        TextButton(onClick = { legacy = true }) { Text("Исходник для старых заказов") }
    }
}
