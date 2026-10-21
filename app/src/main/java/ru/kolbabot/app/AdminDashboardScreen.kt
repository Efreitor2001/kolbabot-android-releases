package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal data class DashboardDay(val date: String, val revenue: Long, val transactions: Int,
    val store: Long, val subscriptions: Long) {
    val label: String get() = Regex("^\\d{4}-(\\d{2})-(\\d{2})$").matchEntire(date)?.let { "${it.groupValues[2]}.${it.groupValues[1]}" } ?: "—"
    fun fraction(maximum: Long): Float = if (revenue > 0 && maximum > 0) ((revenue.toDouble() / maximum * 100).roundToInt().coerceAtLeast(4) / 100f).coerceAtMost(1f) else 0f
}

internal fun dashboardDays(data: JSONObject): List<DashboardDay> = data.items("daily").map {
    DashboardDay(it.optString("date"), it.optLong("revenue_kopecks").coerceAtLeast(0), it.optInt("transactions").coerceAtLeast(0),
        it.optLong("store_revenue_kopecks").coerceAtLeast(0), it.optLong("subscription_revenue_kopecks").coerceAtLeast(0))
}

private fun dashboardInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()
    ?: runCatching { LocalDateTime.parse(raw.replace(' ', 'T')).atZone(ZoneId.of("Europe/Moscow")).toInstant() }.getOrNull()
private fun dashboardDate(raw: String, fallback: String = "—"): String = dashboardInstant(raw)?.atZone(ZoneId.of("Europe/Moscow"))
    ?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.forLanguageTag("ru-RU"))) ?: fallback
private fun dashboardNumber(value: Long) = java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")).format(value.coerceAtLeast(0))

/** Same dashboard order, responsive KPI grid and bar chart as admin.html. All views are native. */
@Composable internal fun AdminDashboardScreen(api: ApiClient, onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}, adminName: String = "администратор") {
    var data by remember(api) { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var visitors by remember(api) { mutableStateOf<JSONObject?>(null) }
    var visitorsError by remember { mutableStateOf("") }
    var visitorsLoading by remember { mutableStateOf(true) }
    val dashboardScroll = rememberScrollState()
    OnResume { revision++ }
    BackHandler(onBack = onBack)
    LaunchedEffect(api, revision) {
        loading = true; error = ""
        try { data = api.get("/api/admin/dashboard") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Не удалось загрузить данные. Попробуйте обновить." }
        finally { loading = false }
    }
    LaunchedEffect(api, revision, api.userId) {
        visitorsLoading = true; visitorsError = ""
        try {
            val result = api.get("/api/admin/analytics")
            analyticsRows(result)
            visitors = result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            visitors = null
            visitorsError = failure.message ?: "Не удалось загрузить посещаемость."
        } finally { visitorsLoading = false }
    }
    Column(Modifier.fillMaxSize().background(SiteColors.BackgroundTop).verticalScroll(dashboardScroll).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CompositionLocalProvider(LocalAdminScrollOffset provides { dashboardScroll.value }) { LocalAdminChrome.current?.invoke() }
        Text("ОБЗОР БИЗНЕСА", color = SiteColors.Brand, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
        Text("Добрый день, $adminName", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
        Text("Здесь всё, что требует решения прямо сейчас.", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        Text(if (data == null && loading) "Загружаем актуальные данные…" else "Обновлено ${dashboardDate(data?.optString("generated_at").orEmpty(), "только что")}",
            style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        OutlinedButton(onClick = { revision++ }, enabled = !loading && !visitorsLoading, modifier = Modifier.fillMaxWidth()) { Text("Обновить") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorMessage(error)
        DashboardCard("Аудитория", "Посещаемость", "dashboard-visitors") {
            if (visitorsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ErrorMessage(visitorsError)
            visitors?.let { stats ->
                Row(Modifier.fillMaxWidth()) {
                    listOf("Период", "Всего", "Сайт", "Android").forEach { title ->
                        Text(title, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                analyticsRows(stats).forEach { (period, counts) ->
                    HorizontalDivider(color = SiteColors.Border)
                    Row(Modifier.fillMaxWidth()) {
                        Text(period, Modifier.weight(1f), fontSize = 12.sp)
                        counts.forEach { Text(dashboardNumber(it), Modifier.weight(1f), fontSize = 12.sp) }
                    }
                }
                Text("По московскому времени. Один аккаунт на сайте и в Android учитывается один раз в «Всего», поэтому сумма каналов может быть больше.",
                    style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
                stats.optString("started_at").takeUnless { it.isBlank() || it == "null" }?.let {
                    Text("Начало учёта: ${dashboardDate(it, it)}", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
                }
            }
        }
        data?.let { dashboard -> DashboardContents(dashboard, onNavigate) }
    }
}

@Composable private fun DashboardContents(data: JSONObject, onNavigate: (String) -> Unit) {
    val kpi = data.optJSONObject("kpi") ?: JSONObject()
    val compact = LocalConfiguration.current.screenWidthDp <= 620
    val trend = if (kpi.isNull("revenue_change_percent")) "Нет базы для сравнения" else {
        val change = kpi.optDouble("revenue_change_percent")
        "${if (change > 0) "+" else ""}${java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).format(change)}% к тому же периоду"
    }
    val tiles = listOf(
        DashboardKpi("Выручка за месяц", money(kpi.optLong("revenue_month_kopecks")), trend, Color(0xFF1B937C),
            if (kpi.isNull("revenue_change_percent")) SiteColors.Muted else if (kpi.optDouble("revenue_change_percent") >= 0) Color(0xFF13705E) else SiteColors.Error),
        DashboardKpi("Заказы в работе", dashboardNumber(kpi.optLong("orders_waiting")), "За месяц: ${dashboardNumber(kpi.optLong("orders_month"))} · Сегодня: ${dashboardNumber(kpi.optLong("orders_today"))}", Color(0xFFF59E0B)),
        DashboardKpi("Активные подписки", dashboardNumber(kpi.optLong("active_subscriptions")), "Действующий доступ", SiteColors.Brand2),
        DashboardKpi("Сейчас онлайн", dashboardNumber(kpi.optLong("users_online")), "Уникальные аккаунты · за ${kotlin.math.ceil(kpi.optDouble("online_window_seconds", 60.0) / 60).toInt().coerceAtLeast(1)} мин", Color(0xFF16A34A)),
        DashboardKpi("Пользователи", dashboardNumber(kpi.optLong("users_total")), "+${dashboardNumber(kpi.optLong("users_new_today"))} сегодня · +${dashboardNumber(kpi.optLong("users_new_month"))} за месяц", SiteColors.Brand),
        DashboardKpi("Поддержка", dashboardNumber(kpi.optLong("tickets_open")), kpi.optString("oldest_ticket_at").takeUnless { it.isBlank() || it == "null" }
            ?.let { "Самое раннее: ${dashboardDate(it)}" } ?: "Все обращения обработаны", Color(0xFFEF4444)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (compact) KpiTile(tiles.first(), Modifier.fillMaxWidth())
        (if (compact) tiles.drop(1) else tiles).chunked(if (compact) 2 else 3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { KpiTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    val days = dashboardDays(data)
    DashboardCard("Продажи", "Динамика за 14 дней", "dashboard-sales") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("● Выручка", color = SiteColors.Brand, style = MaterialTheme.typography.bodySmall)
            Text(money(days.sumOf { it.revenue }), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        DashboardChart(days)
        Text("Магазин: ${money(days.sumOf { it.store })}", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        Text("Подписки: ${money(days.sumOf { it.subscriptions })}", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
    }
    val attention = data.optJSONObject("attention") ?: JSONObject()
    val tasks = listOf(
        DashboardAction("orders_waiting", "ЗК", "Заказы ждут сборки", "Проверьте оплаченные заказы", "orders"),
        DashboardAction("tickets_open", "ПД", "Нет ответа в поддержке", "Клиенты ожидают обратную связь", "support"),
        DashboardAction("low_stock_products", "ОС", "Заканчиваются остатки", "Пять единиц товара или меньше", "store"),
        DashboardAction("digital_sales_attention", "ЦТ", "Цифровая выдача", "Есть незавершённые отправки", "digital"),
        DashboardAction("draft_articles", "ЧР", "Черновики статей", "Материалы ещё не опубликованы", "articles"),
    )
    DashboardCard("Фокус", "Требует внимания", "dashboard-attention") {
        Text(dashboardNumber(tasks.sumOf { attention.optLong(it.key).coerceAtLeast(0) }), fontWeight = FontWeight.Bold, color = SiteColors.Brand)
        val active = tasks.filter { attention.optLong(it.key) > 0 }
        if (active.isEmpty()) Text("Всё спокойно — срочных задач нет.", color = SiteColors.Muted, fontSize = 13.sp)
        active.forEach { task -> DashboardActionRow(task, dashboardNumber(attention.optLong(task.key))) { onNavigate(task.target) } }
    }
    DashboardCard("Навигация", "Быстрые действия", "dashboard-actions") {
        listOf(DashboardAction("", "ЗК", "Заказы", "Сборка и выдача", "orders"),
            DashboardAction("", "ПД", "Поддержка", "Ответить клиентам", "support"),
            DashboardAction("", "МГ", "Магазин", "Товары и остатки", "store"),
            DashboardAction("", "ПЛ", "Пользователи", "Доступ и роли", "users"),
            DashboardAction("", "СТ", "Статьи", "Контент и черновики", "articles"))
            .forEach { task -> DashboardActionRow(task) { onNavigate(task.target) } }
    }
    DashboardCard("Магазин", "Последние заказы", "dashboard-orders") {
        TextButton(onClick = { onNavigate("orders") }) { Text("Все заказы") }
        val orders = data.items("recent_orders")
        if (orders.isEmpty()) Text("Заказов пока нет.", color = SiteColors.Muted)
        orders.forEach { order ->
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("№ ${order.optString("order_id", "—")} · ${order.optString("customer_name").ifBlank { "Без имени" }}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(dashboardDate(order.optString("created_at"), "Без даты"), fontSize = 11.sp, color = SiteColors.Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(money(order.optLong("total_kopecks")), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OrderStatusPill(order.optString("status"))
                }
            }
            HorizontalDivider(color = SiteColors.Border)
        }
    }
    DashboardCard("Аудитория", "Новые пользователи", "dashboard-users") {
        TextButton(onClick = { onNavigate("users") }) { Text("Все пользователи") }
        val users = data.items("recent_users")
        if (users.isEmpty()) Text("Новых пользователей пока нет.", color = SiteColors.Muted)
        users.forEach { user ->
            val active = user.optString("subscription_status") == "active" && dashboardInstant(user.optString("subscription_active_until"))?.isAfter(Instant.now()) == true
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(user.optString("username").ifBlank { "Пользователь #${user.optLong("id")}" }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(user.optString("email").ifBlank { dashboardDate(user.optString("created_at"), "Email не указан") }, fontSize = 11.sp, color = SiteColors.Muted)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = if (active) Color(0xFF22C55E).copy(alpha = .12f) else Color(0xFFF1F5F9)) {
                    Text(if (active) user.optString("subscription_tier").uppercase(Locale.ROOT) else "Без подписки", Modifier.padding(6.dp),
                        fontSize = 10.sp, color = if (active) Color(0xFF166534) else SiteColors.Muted)
                }
            }
            HorizontalDivider(color = SiteColors.Border)
        }
    }
    val content = data.optJSONObject("content") ?: JSONObject()
    DashboardCard("Наполнение", "Контент и каталог", "dashboard-content") {
        listOf(Triple("Статьи", "articles_published", "articles_total"), Triple("Рецепты", "recipes_filled", "recipes_total"),
            Triple("Товары", "products_active", "products_total"), Triple("Оборудование", "equipment_active", "equipment_total")).forEach { (title, readyKey, totalKey) ->
            val ready = content.optLong(readyKey).coerceAtLeast(0); val total = content.optLong(totalKey).coerceAtLeast(0)
            val fraction = if (total > 0) (ready.toFloat() / total).coerceIn(0f, 1f) else 0f
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${dashboardNumber(ready)} / ${dashboardNumber(total)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0F2F7))
                .semantics { contentDescription = "$title: ${(fraction * 100).roundToInt()}%" }) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Brush.horizontalGradient(listOf(SiteColors.Brand, SiteColors.Brand2))))
            }
            Spacer(Modifier.height(7.dp))
        }
    }
}

private data class DashboardKpi(val title: String, val value: String, val note: String, val tint: Color, val noteColor: Color = SiteColors.Muted)
@Composable private fun KpiTile(item: DashboardKpi, modifier: Modifier) {
    Surface(modifier.heightIn(min = 126.dp), shape = RoundedCornerShape(17.dp), color = Color.White.copy(alpha = .88f), border = BorderStroke(1.dp, SiteColors.Border), shadowElevation = 1.dp) {
        Column(Modifier.drawBehind { drawCircle(item.tint.copy(alpha = .15f), 43.dp.toPx(), Offset(size.width - 21.dp.toPx(), size.height - 15.dp.toPx())) }.padding(14.dp)) {
            Text(item.title.uppercase(Locale.forLanguageTag("ru-RU")), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = SiteColors.Muted, letterSpacing = .8.sp)
            Text(item.value, Modifier.padding(top = 12.dp, bottom = 7.dp), fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.note, fontSize = 12.sp, lineHeight = 16.sp, color = item.noteColor)
        }
    }
}

@Composable private fun DashboardCard(kicker: String, title: String, tag: String, body: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .86f), border = BorderStroke(1.dp, SiteColors.Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(kicker.uppercase(Locale.forLanguageTag("ru-RU")), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = SiteColors.Brand)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            body()
        }
    }
}

private data class DashboardAction(val key: String, val icon: String, val title: String, val note: String, val target: String)
@Composable private fun DashboardActionRow(item: DashboardAction, count: String = "", onClick: () -> Unit) {
    Surface(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color(0xFFF7F8FC), border = BorderStroke(1.dp, SiteColors.Border)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = SiteColors.Brand.copy(alpha = .1f)) {
                Text(item.icon, Modifier.padding(10.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SiteColors.Brand)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(item.note, fontSize = 11.sp, lineHeight = 15.sp, color = SiteColors.Muted)
            }
            if (count.isNotBlank()) Text(count, fontWeight = FontWeight.Bold, color = SiteColors.Brand)
        }
    }
}

@Composable internal fun DashboardChart(days: List<DashboardDay>) {
    var selected by remember { mutableStateOf<DashboardDay?>(null) }
    if (days.isEmpty()) { Text("За выбранный период данных пока нет.", Modifier.heightIn(min = 196.dp), color = SiteColors.Muted); return }
    val compact = LocalConfiguration.current.screenWidthDp <= 620
    val maximum = days.maxOf { it.revenue }
    Row(Modifier.fillMaxWidth().height(if (compact) 196.dp else 226.dp).testTag("dashboard-chart").padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        days.forEachIndexed { index, day ->
            Column(Modifier.weight(1f).testTag("dashboard-day-$index").semantics {
                contentDescription = "${day.label}: ${money(day.revenue)}, операций: ${day.transactions}"
            }.clickable { selected = day }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(if (compact) 150.dp else 178.dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Brush.verticalGradient(listOf(SiteColors.Brand.copy(alpha = .025f), SiteColors.Brand.copy(alpha = .055f)))), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth(.72f).widthIn(max = 28.dp).fillMaxHeight(day.fraction(maximum))
                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(Brush.verticalGradient(listOf(SiteColors.Brand, SiteColors.Brand2))))
                    if (!compact) Text(day.transactions.toString(), Modifier.align(Alignment.TopCenter).padding(top = 7.dp), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = SiteColors.Muted)
                }
                Box(Modifier.height(35.dp).width(34.dp), contentAlignment = Alignment.Center) {
                    Text(day.label, Modifier.rotate(if (compact) -55f else 0f), fontSize = if (compact) 8.sp else 9.sp,
                        maxLines = 1, color = if (index == days.lastIndex) SiteColors.Ink else SiteColors.Muted,
                        fontWeight = if (index == days.lastIndex) FontWeight.ExtraBold else FontWeight.Normal)
                }
            }
        }
    }
    HorizontalDivider(color = SiteColors.Border)
    selected?.let { day -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(day.label) },
        text = { Text("Выручка: ${money(day.revenue)}\nОпераций: ${day.transactions}") },
        confirmButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } }) }
}
