package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal object AdminToolsContract {
    fun query(filters: JSONObject, offset: Int = 0, limit: Int = 100): String {
        val fields = filters.keys().asSequence().toList().sorted().mapNotNull { key ->
            val value = filters.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim().orEmpty()
            if (value.isBlank()) null else "${adminEncode(key)}=${adminEncode(value)}"
        }
        return (fields + "offset=${offset.coerceAtLeast(0)}" + "limit=$limit").joinToString("&")
    }
    fun plan(raw: JSONObject): JSONObject {
        val amount = raw.optLong("base_amount_rub")
        val discount = raw.optDouble("discount_percent", Double.NaN)
        require(amount > 0) { "Цена должна быть больше нуля." }
        require(discount.isFinite() && discount >= 0 && discount <= 99) { "Скидка должна быть от 0 до 99%." }
        val features = raw.optString("features").lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        return JSONObject().put("title", raw.optString("title").trim()).put("description", raw.optString("description").trim())
            .put("features", JSONArray(features)).put("base_amount_rub", amount).put("discount_percent", discount)
    }
    fun promo(raw: JSONObject): JSONObject {
        val code = raw.optString("code").trim().uppercase(java.util.Locale.ROOT)
        val percent = raw.optDouble("discount_percent", Double.NaN)
        require(code.isNotBlank()) { "Введите промокод." }
        require(percent.isFinite() && percent >= 0 && percent <= 99) { "Скидка должна быть от 0 до 99%." }
        return JSONObject().put("code", code).put("tier", "premium").put("discount_percent", percent)
    }
    fun bonus(raw: JSONObject): JSONObject {
        val amount = raw.optLong("sausages", -1)
        val active = raw.optBoolean("is_active")
        require(amount in 0..1_000_000) { "Количество колбасок должно быть от 0 до 1 000 000." }
        require(!active || amount > 0) { "Для включённого бонуса укажите хотя бы 1 колбаску." }
        return JSONObject().put("is_active", active).put("sausages", amount)
    }
    fun migration(source: String, truncate: Boolean): JSONObject = JSONObject()
        .put("source_sqlite", source.trim().ifBlank { "auto" }).put("truncate", truncate)
}

private data class ToolsConfirmation(val title: String, val description: String, val action: suspend () -> Unit)

@Composable
private fun ToolsConfirm(action: ToolsConfirmation?, onDismiss: () -> Unit) {
    if (action == null) return
    val scope = rememberCoroutineScope()
    var busy by remember(action) { mutableStateOf(false) }
    var error by remember(action) { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(action.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(action.description); ErrorMessage(error) } },
        confirmButton = { TextButton(enabled = !busy && error.isBlank(), onClick = {
            busy = true
            scope.launch {
                try { action.action(); onDismiss() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Операция не выполнена." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Выполняется…" else "Подтвердить") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun AdminToolsScreen(api: ApiClient, section: String, onBack: () -> Unit) {
    when (section) {
        "dashboard" -> AdminDashboardTools(api, onBack)
        "security" -> AdminJournalTools(api, true, onBack)
        "db" -> AdminJournalTools(api, false, onBack)
        else -> AdminToolsMenu(api, onBack)
    }
}

@Composable
private fun AdminDashboardTools(api: ApiClient, onBack: () -> Unit) {
    RemotePage("Панель управления", api, "/api/admin/dashboard", onBack) { data, reload ->
        Text("Обновлено: ${data.optString("generated_at")}")
        OutlinedButton(onClick = reload) { Text("Обновить показатели") }
        val kpi = data.optJSONObject("kpi") ?: JSONObject()
        SectionCard("Выручка за месяц") {
            Text(money(kpi.optLong("revenue_month_kopecks")), style = MaterialTheme.typography.headlineMedium)
            Text("Магазин: ${money(kpi.optLong("store_revenue_month_kopecks"))}")
            Text("Подписки: ${money(kpi.optLong("subscription_revenue_month_kopecks"))}")
            Text("Прошлый месяц: ${money(kpi.optLong("revenue_previous_month_kopecks"))}")
            if (!kpi.isNull("revenue_change_percent")) Text("Изменение: ${kpi.optDouble("revenue_change_percent")}%")
        }
        SectionCard("Пользователи и заказы") {
            listOf("users_total" to "Активных пользователей", "users_online" to "Сейчас на сайте", "users_new_today" to "Регистраций сегодня", "users_new_month" to "Регистраций за месяц", "active_subscriptions" to "Действующих подписок", "orders_today" to "Заказов сегодня", "orders_month" to "Заказов за месяц", "orders_waiting" to "Заказов ожидают обработки", "tickets_open" to "Открытых обращений").forEach { (key, label) ->
                AdminCopyField(label, kpi.optLong(key).toString())
            }
        }
        val attention = data.optJSONObject("attention") ?: JSONObject()
        SectionCard("Требует внимания") {
            listOf("orders_waiting" to "Необработанные заказы", "tickets_open" to "Обращения без ответа", "low_stock_products" to "Товары с низким остатком", "digital_sales_attention" to "Электронные книги с незавершённой выдачей", "draft_articles" to "Черновики статей").forEach { (key, label) -> AdminCopyField(label, attention.optLong(key).toString()) }
        }
        val days = data.items("daily")
        if (days.isNotEmpty()) SectionCard("Выручка за 14 дней") {
            val maximum = days.maxOfOrNull { it.optLong("revenue_kopecks") }?.coerceAtLeast(1) ?: 1
            days.forEach { day ->
                Text("${day.optString("date")} · ${money(day.optLong("revenue_kopecks"))} · операций: ${day.optInt("transactions")}")
                LinearProgressIndicator(progress = { (day.optLong("revenue_kopecks").toFloat() / maximum).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }
        val content = data.optJSONObject("content") ?: JSONObject()
        SectionCard("Материалы и каталог") {
            listOf("articles_published" to "Опубликовано статей", "articles_total" to "Всего статей", "recipes_filled" to "Заполнено рецептов", "recipes_total" to "Всего рецептов", "products_active" to "Активных товаров", "products_total" to "Всего товаров", "equipment_active" to "Активных позиций оборудования", "equipment_total" to "Всего оборудования", "handbook_articles" to "Статей справочника", "faq_entries" to "Вопросов FAQ", "reviews_total" to "Отзывов").forEach { (key, label) -> AdminCopyField(label, content.optLong(key).toString()) }
        }
        SectionCard("Последние заказы") {
            data.items("recent_orders").forEach { order ->
                Text("${order.optString("order_id")} · ${order.optString("customer_name")} · ${money(order.optLong("total_kopecks"))}")
                Text("${order.optString("created_at")} · ${order.optString("status")}")
                HorizontalDivider()
            }
        }
        SectionCard("Новые пользователи") {
            data.items("recent_users").forEach { user ->
                Text("#${user.optLong("id")} · ${user.optString("username")} · ${user.optString("created_at")}")
            }
        }
    }
}

@Composable
private fun AdminJournalTools(api: ApiClient, security: Boolean, onBack: () -> Unit) {
    var filterText by rememberSaveable(security) { mutableStateOf("{}") }
    var offset by rememberSaveable(security) { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    val fields = if (security) listOf(
        AdminField("method", "Метод запроса", "choice", options = listOf("" to "Все", "GET" to "GET", "POST" to "POST", "PUT" to "PUT", "PATCH" to "PATCH", "DELETE" to "DELETE")),
        AdminField("path", "Путь содержит"), AdminField("request_id", "ID запроса"), AdminField("ip", "IP содержит"),
        AdminField("admin_user_id", "Администратор", "user"), AdminField("status_from", "Код ответа от", "int"), AdminField("status_to", "Код ответа до", "int"),
        AdminField("created_from", "Дата от: ГГГГ-ММ-ДД ЧЧ:ММ:СС"), AdminField("created_to", "Дата до: ГГГГ-ММ-ДД ЧЧ:ММ:СС"),
    ) else listOf(
        AdminField("source", "Источник", "choice", options = listOf("all" to "Все", "payments" to "Платежи", "access" to "Доступ", "audit" to "Действия администраторов", "support" to "Поддержка")),
        AdminField("level", "Уровень", "choice", options = listOf("all" to "Все", "info" to "Информация", "warn" to "Предупреждения", "error" to "Ошибки")),
        AdminField("user_id", "Пользователь", "user"), AdminField("search", "Поиск по событиям"),
    )
    BackHandler(editing) { editing = false }
    if (editing) {
        AdminEditForm("Фильтры журнала", fields, JSONObject(filterText), { editing = false }, userApi = api) { values -> filterText = values.toString(); offset = 0 }
    } else {
        val endpoint = if (security) "/api/admin/security/audit" else "/api/admin/db/events"
        RemotePage(if (security) "Журнал безопасности" else "Журнал базы данных", api, "$endpoint?${AdminToolsContract.query(JSONObject(filterText), offset)}", onBack) { data, reload ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = true }) { Text("Фильтры") }
                OutlinedButton(onClick = { filterText = "{}"; offset = 0 }) { Text("Сбросить") }
                TextButton(onClick = reload) { Text("Обновить") }
            }
            val rows = data.items("items")
            val total = data.optInt("total")
            Text("Записи ${if (rows.isEmpty()) 0 else offset + 1}–${offset + rows.size} из $total")
            rows.forEachIndexed { index, row ->
                key(offset + index) {
                    var expanded by remember { mutableStateOf(false) }
                    SectionCard(if (security) "${row.optString("method")} ${row.optString("path")}" else row.optString("message", "Событие")) {
                        if (security) {
                            Text("${row.optString("created_at")} · ответ ${row.optInt("status_code")} · администратор #${row.optLong("admin_user_id")}")
                            AdminCopyField("IP-адрес", row.optString("ip_address"))
                            AdminCopyField("ID запроса", row.optString("request_id"))
                        } else Text("${row.optString("ts")} · ${row.optString("source")} · ${row.optString("level")} · #${row.optLong("user_id")}")
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Свернуть" else "Подробности") }
                        if (expanded) AdminJsonDetails(row)
                    }
                }
            }
            if (rows.isEmpty()) Text("Событий по выбранным фильтрам нет.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = offset > 0, onClick = { offset = (offset - 100).coerceAtLeast(0) }) { Text("Предыдущие") }
                OutlinedButton(enabled = offset + rows.size < total, onClick = { offset += 100 }) { Text("Следующие") }
            }
        }
    }
}

@Composable
private fun AdminToolsMenu(api: ApiClient, onBack: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf("") }
    BackHandler(screen.isNotBlank()) { screen = "" }
    val back = { screen = "" }
    when (screen) {
        "stats" -> AdminStatsTools(api, back)
        "plans" -> AdminPlansTools(api, back)
        "promocodes" -> AdminPromoTools(api, back)
        "settings" -> AdminSiteSettingsTools(api, back)
        "branding" -> AdminBrandingTools(api, back)
        "imports" -> AdminImportTools(api, back)
        "migration" -> AdminMigrationTools(api, back)
        "market" -> AdminMarketTools(api, back)
        else -> Page("Инструменты", onBack = onBack) {
            listOf(
                "stats" to ("Статистика и Excel" to "Клики по кнопкам, пользователям и разделам"),
                "plans" to ("Тариф PREMIUM" to "Стоимость, скидка и описание подписки"),
                "promocodes" to ("Промокоды" to "Создание и удаление кодов для подписки"),
                "settings" to ("Настройки сайта" to "Техработы, бонус регистрации, социальные сети"),
                "branding" to ("Оформление сайта" to "Главная иллюстрация и значок сайта"),
                "market" to ("Синхронизация цен" to "Обновить каталог из существующего источника"),
                "imports" to ("Импорт каталога Excel" to "Загрузка полного каталога из файла"),
                "migration" to ("Обновление и перенос базы" to "Схема PostgreSQL и перенос SQLite с подтверждением"),
            ).forEach { (value, copy) -> SectionCard(copy.first) { Text(copy.second); OutlinedButton(onClick = { screen = value }) { Text("Открыть") } } }
        }
    }
}

@Composable
private fun AdminStatsTools(api: ApiClient, onBack: () -> Unit) {
    var scope by rememberSaveable { mutableStateOf("all") }
    RemotePage("Статистика", api, "/api/admin/stats?scope=$scope", onBack) { data, reload ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "Всё", "ad" to "Реклама", "sausage" to "Рецепты").forEach { (value, label) -> FilterChip(scope == value, { scope = value }, { Text(label) }) }
        }
        SectionCard("Итоги") {
            AdminCopyField("Всего кликов", data.optLong("total_clicks").toString())
            AdminCopyField("Пользователей", data.optLong("unique_users").toString())
            AdminCopyField("Разных кнопок", data.optLong("unique_buttons").toString())
            OutlinedButton(onClick = reload) { Text("Обновить") }
            AdminDownloadButton(api, "/api/admin/stats/export?scope=$scope", "Скачать Excel", "kolbabot-statistics-$scope.xlsx")
        }
        SectionCard("Популярные кнопки") { data.items("top_buttons").forEach { row ->
            Text("${row.optString("button_label", row.optString("button_key"))} · ${row.optLong("total_clicks")} кликов · ${row.optLong("unique_users")} пользователей")
        } }
        SectionCard("Активные пользователи") { data.items("top_users").forEach { row ->
            Text("#${row.optLong("user_id")} · ${row.optLong("total_clicks")} кликов · ${row.optLong("unique_buttons")} кнопок")
            AdminCopyField("Пользователь", row.optString("user_link"))
        } }
    }
}

@Composable
private fun AdminPlansTools(api: ApiClient, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    BackHandler(editing != null) { editing = null }
    val selected = editing
    if (selected != null) {
        val initial = JSONObject(selected.toString()).apply {
            val array = selected.optJSONArray("features") ?: JSONArray()
            put("features", (0 until array.length()).joinToString("\n") { array.optString(it) })
        }
        AdminEditForm("Настройки тарифа PREMIUM", listOf(AdminField("title", "Название", required = true), AdminField("description", "Описание", "multiline"),
            AdminField("features", "Преимущества — по одному на строку", "multiline"), AdminField("base_amount_rub", "Базовая цена, ₽", "long", true), AdminField("discount_percent", "Скидка, %", "decimal", true)), initial, { editing = null }) { raw ->
            api.patch("/api/admin/subscriptions/plans/premium", AdminToolsContract.plan(raw))
        }
    } else RemotePage("Тарифы подписки", api, "/api/admin/subscriptions/plans", onBack) { data, reload ->
        OutlinedButton(onClick = reload) { Text("Обновить") }
        data.items("items").forEach { plan -> SectionCard(plan.optString("title", "PREMIUM")) {
            Text(plan.optString("description"))
            Text("Базовая цена: ${plan.optLong("base_amount_rub")} ₽ · скидка: ${plan.optDouble("discount_percent")}%")
            Text("Итоговая цена: ${plan.optLong("final_amount_rub")} ₽")
            val features = plan.optJSONArray("features") ?: JSONArray()
            for (i in 0 until features.length()) Text("• ${features.optString(i)}")
            OutlinedButton(onClick = { editing = plan }) { Text("Изменить тариф") }
        } }
    }
}

@Composable
private fun AdminPromoTools(api: ApiClient, onBack: () -> Unit) {
    var create by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<ToolsConfirmation?>(null) }
    BackHandler(create) { create = false }
    if (create) AdminEditForm("Новый промокод PREMIUM", listOf(AdminField("code", "Промокод", required = true), AdminField("discount_percent", "Скидка, %", "decimal", true)), JSONObject().put("discount_percent", 10), { create = false }) { values ->
        api.post("/api/admin/promocodes", AdminToolsContract.promo(values))
    } else RemotePage("Промокоды", api, "/api/admin/promocodes", onBack) { data, reload ->
        Button(onClick = { create = true }) { Text("Создать промокод") }
        OutlinedButton(onClick = reload) { Text("Обновить") }
        data.items("items").forEach { promo -> SectionCard(promo.optString("code")) {
            Text("PREMIUM · скидка ${promo.optDouble("discount_percent")}%")
            AdminCopyField("Создан", promo.optString("created_at"))
            TextButton(onClick = { confirmation = ToolsConfirmation("Удалить промокод?", "Код ${promo.optString("code")} больше нельзя будет применить при оплате подписки.") {
                api.delete("/api/admin/promocodes/${adminEncode(promo.optString("code"))}"); reload()
            } }) { Text("Удалить") }
        } }
        if (data.items("items").isEmpty()) Text("Промокодов нет.")
    }
    ToolsConfirm(confirmation) { confirmation = null }
}

@Composable
private fun AdminSiteSettingsTools(api: ApiClient, onBack: () -> Unit) {
    var form by remember { mutableStateOf("") }
    var initial by remember { mutableStateOf(JSONObject()) }
    var confirmation by remember { mutableStateOf<ToolsConfirmation?>(null) }
    BackHandler(form.isNotBlank()) { form = "" }
    when (form) {
        "bonus" -> AdminEditForm("Бонус за регистрацию", listOf(AdminField("is_active", "Начислять бонус новым пользователям", "bool"), AdminField("sausages", "Количество колбасок", "long", true)), initial, { form = "" }) { values ->
            api.patch("/api/admin/settings/registration-bonus", AdminToolsContract.bonus(values))
        }
        "social" -> AdminEditForm("Социальные сети сайта", listOf("vk" to "ВКонтакте", "max" to "MAX", "youtube" to "YouTube", "instagram" to "Instagram", "telegram" to "Telegram", "facebook" to "Facebook", "tiktok" to "TikTok").map { AdminField(it.first, it.second) }, initial, { form = "" }) { values ->
            api.patch("/api/admin/settings/social-links", JSONObject().put("site_social_links", values))
        }
        else -> RemotePage("Настройки сайта", api, "/api/admin/bootstrap", onBack) { data, reload ->
            val maintenance = data.optJSONObject("maintenance") ?: JSONObject()
            SectionCard("Технические работы") {
                Text(if (maintenance.optBoolean("active")) "Сайт закрыт для обычных пользователей" else "Сайт доступен пользователям")
                AdminCopyField("Режим", maintenance.optString("mode"))
                val forced = maintenance.optBoolean("forced")
                OutlinedButton(onClick = { confirmation = ToolsConfirmation(if (forced) "Открыть сайт?" else "Закрыть сайт на техработы?", if (forced) "Ручной режим технических работ будет выключен." else "Обычные пользователи временно потеряют доступ к сайту и API. Администраторы сохранят доступ к управлению.") {
                    api.patch("/api/admin/settings/maintenance", JSONObject().put("forced", !forced)); reload()
                } }) { Text(if (forced) "Выключить ручные техработы" else "Включить ручные техработы") }
                AdminJsonDetails(maintenance)
            }
            val bonus = data.optJSONObject("registration_bonus_settings") ?: JSONObject()
            SectionCard("Бонус за регистрацию") {
                Text("${if (bonus.optBoolean("is_active")) "Включён" else "Выключен"} · ${bonus.optLong("sausages")} колбасок")
                OutlinedButton(onClick = { initial = JSONObject(bonus.toString()); form = "bonus" }) { Text("Изменить бонус") }
            }
            val social = data.optJSONObject("site_social_links") ?: JSONObject()
            SectionCard("Социальные сети") {
                social.keys().forEach { name -> AdminCopyField(name, social.optString(name)) }
                OutlinedButton(onClick = { initial = JSONObject(social.toString()); form = "social" }) { Text("Изменить ссылки") }
            }
            OutlinedButton(onClick = reload) { Text("Обновить настройки") }
        }
    }
    ToolsConfirm(confirmation) { confirmation = null }
}

@Composable
private fun AdminBrandingTools(api: ApiClient, onBack: () -> Unit) {
    var heroRevision by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    Page("Оформление сайта", onBack = onBack) {
        SectionCard("Главное изображение") {
            NetworkImage("${api.baseUrl}/site-hero?v=$heroRevision", "Главное изображение сайта", Modifier.fillMaxWidth().height(180.dp))
            Text("Новый файл заменит текущую иллюстрацию сайта.")
            AdminUploadButton(api, "/api/admin/branding/upload", "Выбрать и заменить изображение", arrayOf("image/*"), mapOf("kind" to "hero")) { result = it; heroRevision++ }
        }
        SectionCard("Значок сайта") {
            Text("Новый файл заменит текущий значок сайта.")
            AdminUploadButton(api, "/api/admin/branding/upload", "Выбрать и заменить значок", arrayOf("image/*"), mapOf("kind" to "favicon")) { result = it }
        }
        result?.let { SectionCard("Загружено") { AdminCopyField("Файл", it.optString("path")); AdminCopyField("Размер, байт", it.optLong("size_bytes").toString()) } }
    }
}

@Composable
private fun AdminImportTools(api: ApiClient, onBack: () -> Unit) {
    var result by remember { mutableStateOf<JSONObject?>(null) }
    Page("Импорт полного каталога", onBack = onBack) {
        Text("Выберите Excel в формате, который используется на сайте. После выбора файл будет отправлен на сервер и данные каталога будут импортированы.")
        AdminUploadButton(api, "/api/admin/import/full-excel", "Выбрать Excel и импортировать", arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) { result = it }
        result?.let { SectionCard("Результат импорта") { AdminJsonDetails(it.optJSONObject("result") ?: it) } }
    }
}

@Composable
private fun AdminMarketTools(api: ApiClient, onBack: () -> Unit) {
    var token by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var confirmation by remember { mutableStateOf<ToolsConfirmation?>(null) }
    Page("Синхронизация цен", onBack = onBack) {
        Text("Обновляет цены каталога из существующего внешнего источника. Необязательный токен используется только для этого запуска.")
        OutlinedTextField(token, { token = it }, label = { Text("VK token — можно оставить пустым") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val request = JSONObject().put("market_token", token.trim())
            confirmation = ToolsConfirmation("Обновить цены каталога?", "Текущие цены будут синхронизированы с внешним источником.") { result = api.post("/api/admin/market-sync", request); token = "" }
        }) { Text("Синхронизировать цены") }
        result?.let { value -> SectionCard("Результат синхронизации") {
            Text("Статус: ${value.optString("status")}")
            Text("Обновлено: ${value.optInt("updated_count")} · пропущено: ${value.optInt("skipped_count")}")
            val error = value.optString("error_text").takeUnless { it == "null" }.orEmpty()
            ErrorMessage(error)
            AdminJsonDetails(value)
        } }
    }
    ToolsConfirm(confirmation) { confirmation = null }
}

@Composable
private fun AdminMigrationTools(api: ApiClient, onBack: () -> Unit) {
    var source by rememberSaveable { mutableStateOf("auto") }
    var truncate by rememberSaveable { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<ToolsConfirmation?>(null) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    Page("Обновить или перенести базу", onBack = onBack) {
        Text("Проверяет схему PostgreSQL и переносит данные из указанных SQLite-файлов на сервере. Пути относятся к серверу сайта.")
        OutlinedTextField(source, { source = it }, label = { Text("Источник SQLite: auto или пути через запятую") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(truncate, { truncate = it })
            Text("Очистить таблицы PostgreSQL перед переносом", Modifier.weight(1f))
        }
        if (truncate) ErrorMessage("Очистка удаляет текущие данные в таблицах назначения. Для восстановления потребуется резервная копия.")
        Button(onClick = {
            val request = AdminToolsContract.migration(source, truncate)
            confirmation = ToolsConfirmation("Выполнить перенос базы?", "Источник: ${request.optString("source_sqlite")}. " + if (truncate) "Текущие таблицы PostgreSQL будут очищены перед импортом. Подтвердите только при наличии проверенной резервной копии." else "Будут обновлены схема и данные PostgreSQL без предварительной очистки.") {
                result = api.post("/api/admin/db/migrate", request)
            }
        }) { Text("Проверить параметры и выполнить") }
        result?.let { SectionCard("Результат переноса") { Text(it.optString("output")); AdminJsonDetails(it.optJSONObject("result") ?: it) } }
    }
    ToolsConfirm(confirmation) { confirmation = null }
}
