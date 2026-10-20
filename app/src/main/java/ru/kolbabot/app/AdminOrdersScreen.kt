package ru.kolbabot.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun adminOrderStatus(status: String): String = siteOrderStatus(status).label

@Composable fun AdminOrdersScreen(api: ApiClient, onBack: () -> Unit) {
    var status by rememberSaveable { mutableStateOf("current") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedClient by rememberSaveable(status) { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    BackHandler(selected != null || status == "digital") { selected = null; if (status == "digital") status = "current" }
    selected?.let { raw ->
        AdminOrderDetails(api, JSONObject(raw), onBack = { selected = null }, onChanged = { selected = null; revision++ })
        return
    }
    if (status == "digital") { AdminCommerceScreen(api, "digital") { status = "current" }; return }
    key(revision) {
        RemotePage("Заказы магазина", api, "/api/admin/store/orders?status=$status&limit=1000", onBack) { data, reload ->
            BackHandler(expandedClient.isNotBlank()) { expandedClient = "" }
            BackHandler(query.isNotBlank()) { query = "" }
            Text("Нажмите на реквизит, чтобы скопировать его.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("current" to "Текущие", "completed" to "Готовые", "failed" to "Отменённые", "digital" to "Цифровые").forEach { (value, label) ->
                    FilterChip(selected = status == value, onClick = { status = value }, label = { Text(label) })
                }
            }
            OutlinedTextField(query, { query = it }, label = { Text("Поиск по номеру, имени, телефону, почте или адресу") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = reload) { Text("Обновить заказы") }
            val items = data.items("items")
            val clients = data.items("clients").ifEmpty {
                items.groupBy { "${it.optLong("user_id")}|${it.optString("customer_name")}|${it.optString("phone")}|${it.optString("subscription_tier_at_checkout")}" }.values.map { orders ->
                    JSONObject().put("customer_name", orders.first().optString("customer_name")).put("user_id", orders.first().optLong("user_id"))
                        .put("phone", orders.first().optString("phone")).put("orders", JSONArray(orders))
                }
            }
            val matching = clients.mapNotNull { client ->
                val orders = client.items("orders").filter { order ->
                    query.isBlank() || listOf("order_id", "customer_name", "phone", "digital_delivery_email", "address", "payment_id", "user_id")
                        .any { order.optString(it).contains(query.trim(), ignoreCase = true) }
                }
                if (orders.isEmpty()) null else client to orders
            }
            Text("Найдено заказов: ${matching.sumOf { it.second.size }}")
            if (items.size >= 1000) Text("Показаны последние 1000 заказов выбранной группы. Поиск работает по загруженному списку.", style = MaterialTheme.typography.bodySmall)
            if (matching.isEmpty()) Text("Заказы не найдены.")
            var visible by remember(status, query, data) { mutableIntStateOf(30) }
            matching.take(visible).forEach { (client, orders) ->
                val clientKey = "${client.optLong("user_id")}:${orders.first().optString("order_id")}"
                val expanded = expandedClient == clientKey
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OrderCopyText(client.optString("customer_name", "Без имени"), emphasis = true)
                            OrderCopyText(client.optLong("user_id").toString(), "user #${client.optLong("user_id")}", small = true)
                            if (client.optString("phone").isNotBlank()) OrderCopyText(client.optString("phone"), small = true)
                        }
                        TextButton(modifier = Modifier.testTag("expand-client-${orders.first().optString("order_id")}"), onClick = { expandedClient = if (expanded) "" else clientKey }) {
                            Text(if (expanded) "Свернуть" else "${orders.size} заказ(ов)")
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        orders.map { it.optString("status") }.distinct().forEach { OrderStatusPill(it) }
                    }
                    if (status != "failed") OrderCopyText(money(orders.filter { it.optString("status") in listOf("paid", "ready") }.sumOf { it.optLong("total_kopecks") }), emphasis = true)
                    if (expanded) orders.forEach { order ->
                        OrderCompactRow(order, customer = false) { selected = order.toString() }
                    }
                }
            }
            if (matching.size > visible) TextButton(onClick = { visible += 30 }) { Text("Показать ещё покупателей") }
        }
    }
}

@Composable private fun AdminOrderDetails(api: ApiClient, order: JSONObject, onBack: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    var uncertain by rememberSaveable(order.optString("order_id")) { mutableStateOf(false) }
    var customer by remember { mutableStateOf<JSONObject?>(null) }
    var more by rememberSaveable { mutableStateOf(false) }
    val orderId = order.optString("order_id")
    val pathId = Uri.encode(orderId)
    Page("Заказ $orderId", "Нажмите на значение, чтобы скопировать его без изменений.", onBack) {
        ErrorMessage(error)
        SectionCard("Получатель и доставка") {
            AdminCopyField("Номер заказа", orderId)
            AdminCopyField("ФИО", order.optString("customer_name"))
            AdminCopyField("Телефон", order.optString("phone"))
            AdminCopyField("Почта для цифровой доставки", order.optString("digital_delivery_email"))
            AdminCopyField("Адрес", order.optString("address"))
            AdminCopyField("Пользователь", order.optLong("user_id").toString())
            AdminCopyField("Способ доставки", order.optString("delivery_method_title"))
            AdminCopyField("Стоимость доставки", money(order.optLong("delivery_price_kopecks")))
            val details = order.optJSONObject("delivery_details")
            if (details != null && details.length() > 0) AdminJsonDetails(details)
            TextButton(enabled = !busy, onClick = {
                busy = true; error = ""
                scope.launch {
                    try {
                        val dossier = api.get("/api/admin/users/${order.optLong("user_id")}/dossier")
                        customer = dossier.items("tables").firstOrNull { it.optString("table") == "miniapp_users" }?.items("rows")?.firstOrNull() ?: JSONObject()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Не удалось получить профиль покупателя." }
                    finally { busy = false }
                }
            }) { Text("Показать почту и профиль покупателя") }
            customer?.let { user ->
                AdminCopyField("Почта аккаунта", user.optString("email"))
                AdminCopyField("Логин", user.optString("username"))
                AdminCopyField("Телефон аккаунта", user.optString("phone"))
                if (user.optString("email").isBlank()) Text("Почта в профиле не указана.")
            }
        }
        SectionCard("Оплата") {
            OrderStatusPill(order.optString("status"))
            AdminCopyField("Статус Т-Банка", order.optString("tbank_status"))
            AdminCopyField("Номер платежа Т-Банка", order.optString("payment_id"))
            AdminCopyField("Товары до скидки", money(order.optLong("products_original_total_kopecks", order.optLong("products_total_kopecks"))))
            AdminCopyField("Скидка PREMIUM", money(order.optLong("subscription_discount_kopecks")))
            AdminCopyField("Товары со скидкой", money(order.optLong("products_total_kopecks")))
            AdminCopyField("Списано колбасками", money(order.optLong("sausages_spent_kopecks")))
            AdminCopyField("Итого", money(order.optLong("total_kopecks")))
            AdminCopyField("Создан", order.optString("created_at"))
            AdminCopyField("Обновлён", order.optString("updated_at"))
        }
        order.items("items").forEachIndexed { index, item ->
            SectionCard("${index + 1}. ${item.optString("title", "Товар")}") {
                AdminCopyField("Название товара", item.optString("title"))
                AdminCopyField("Количество", item.optInt("quantity", 1).toString())
                AdminCopyField("Вариант", item.optString("variant_label"))
                AdminCopyField("Артикул", item.optString("source_id"))
                AdminCopyField("ID товара", item.optLong("product_id").toString())
                AdminCopyField("Цена", money(item.optLong("price_kopecks")))
                AdminCopyField("Сумма", money(item.optLong("amount_kopecks")))
                if (item.optBoolean("is_digital")) Text(if (item.optBoolean("digital_delivered")) "Цифровой товар отправлен" else "Цифровой товар: отправка не подтверждена")
                else { AdminCopyField("Вес, г", item.optLong("weight_grams").toString()); AdminCopyField("Длина", item.optString("length")); AdminCopyField("Габариты", item.optString("dimensions")) }
            }
        }
        SectionCard("Документ и действия") {
            AdminOrderReceiptButton(api, orderId)
            if (uncertain) Text("Ответ на действие не получен или требует проверки. Обновите заказы и проверьте статус перед повторной попыткой.")
            if (order.optString("status") == "paid") {
                Button(enabled = !busy && !uncertain, onClick = { confirmation = "complete" }) { Text("Заказ собран") }
                OutlinedButton(enabled = !busy && !uncertain, onClick = { confirmation = "cancel" }) { Text("Отменить и вернуть оплату") }
            }
            TextButton(enabled = !busy, onClick = onChanged) { Text("Обновить список заказов") }
            TextButton(onClick = { more = !more }) { Text(if (more) "Скрыть все реквизиты" else "Все реквизиты заказа") }
            if (more) AdminJsonDetails(order)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (confirmation.isNotBlank()) AlertDialog(
        onDismissRequest = { if (!busy) confirmation = "" },
        title = { Text(if (confirmation == "cancel") "Отменить заказ и вернуть оплату?" else "Подтвердить готовность заказа?") },
        text = { Text(if (confirmation == "cancel") "Заказ $orderId на ${money(order.optLong("total_kopecks"))}. Сервер запросит возврат в Т-Банке и уведомит покупателя."
            else "Заказ $orderId будет отмечен готовым. Покупатель получит уведомление.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val action = confirmation
            busy = true; error = ""
            scope.launch {
                try { api.post("/api/admin/store/orders/$pathId/$action"); confirmation = ""; onChanged() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    error = failure.message ?: "Не удалось получить результат действия."
                    uncertain = failure !is ApiException || failure.status >= 500 || failure.status == 409
                    confirmation = ""
                } finally { busy = false }
            }
        }) { Text(if (confirmation == "cancel") "Подтвердить возврат" else "Подтвердить") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmation = "" }) { Text("Назад") } }
    )
}

@Composable private fun AdminOrderReceiptButton(api: ApiClient, orderId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    OutlinedButton(enabled = !busy, onClick = {
        busy = true; error = ""
        scope.launch {
            try {
                val (receipt, contentType) = api.downloadFile("/admin/store/orders/${Uri.encode(orderId)}/print")
                check(contentType.contains("text/html")) { "Сервер не вернул товарный чек." }
                val (styles, _) = api.downloadFile("/static/order-print.css")
                // The exported receipt must not depend on relative server assets.
                val standalone = String(receipt, Charsets.UTF_8)
                    .replace("<link rel=\"stylesheet\" href=\"/static/order-print.css\">", "<style>${String(styles, Charsets.UTF_8)}</style>")
                    .replace("<script type=\"module\" src=\"/static/order-print.js\"></script>", "<script>document.getElementById('receiptPrintButton')?.addEventListener('click',()=>window.print());</script>")
                val file = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "shared").apply { mkdirs() }
                    File(directory, "order-${orderId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.html").apply { writeText(standalone, Charsets.UTF_8) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Сохранить или отправить товарный чек"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Не удалось получить товарный чек." }
            finally { busy = false }
        }
    }) { Text(if (busy) "Получаем чек…" else "Скачать товарный чек") }
    ErrorMessage(error)
}
