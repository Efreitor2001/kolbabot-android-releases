package ru.kolbabot.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** admin-panel-tools in the site's mobile DOM order, with inline native forms. */
@Composable internal fun AdminToolsPanel(api: ApiClient, onBack: () -> Unit) {
    var bootstrap by remember { mutableStateOf<JSONObject?>(null) }
    var plans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var promos by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var statsScope by rememberSaveable { mutableStateOf("all") }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<ToolsConfirmation?>(null) }
    val scope = rememberCoroutineScope()
    OnResume { revision++ }
    LaunchedEffect(api, revision) {
        error = ""
        try { bootstrap = api.get("/api/admin/bootstrap") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() }
        try { plans = api.get("/api/admin/subscriptions/plans").items("items") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() }
        try { promos = api.get("/api/admin/promocodes").items("items") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() }
    }
    fun complete() { saved = "Сохранено." }
    Page("Инструменты", "Все действия сгруппированы по задачам: аналитика, контент, продажи и импорт.", onBack) {
        ErrorMessage(error)
        if (saved.isNotBlank()) Text(saved, color = SiteColors.StoreGreen)
        if (bootstrap == null) {
            if (error.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth())
            else OutlinedButton(onClick = { revision++ }) { Text("Повторить") }
        }
        bootstrap?.let { data ->
            ToolGroup("Технические работы", "Плановое окно: ежедневно 02:50–04:00 МСК. Админка остаётся открытой.") {
                val maintenance = data.optJSONObject("maintenance") ?: JSONObject()
                var forced by remember(maintenance.toString()) { mutableStateOf(maintenance.optBoolean("forced")) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Принудительно закрыть сайт для пользователей", Modifier.weight(1f))
                    Checkbox(forced, { forced = it })
                }
                Text(if (maintenance.optBoolean("active")) "Технические работы включены" else "Сайт доступен пользователям", color = SiteColors.Muted)
                Button(onClick = {
                    val requested = forced
                    confirmation = ToolsConfirmation("Сохранить режим техработ?", if (requested) "Сайт будет закрыт для пользователей. Админка останется открытой." else "Принудительный режим будет выключен.") {
                        api.patch("/api/admin/settings/maintenance", JSONObject().put("forced", requested)); revision++
                    }
                }) { Text("Сохранить режим техработ") }
            }
            ToolGroup("Бонус за регистрацию", "Автоматическое начисление новым пользователям сразу после создания аккаунта.") {
                AdminEditForm("Бонус за регистрацию", listOf(AdminField("is_active", "Начислять колбаски новым пользователям", "bool"),
                    AdminField("sausages", "Количество колбасок", "long", true)), data.optJSONObject("registration_bonus_settings") ?: JSONObject(),
                    ::complete, embedded = true, saveLabel = "Сохранить бонус") { api.patch("/api/admin/settings/registration-bonus", AdminToolsContract.bonus(it)) }
            }
        }
        ToolGroup("Аналитика", "Просмотр метрик и выгрузка отчета.") {
            SiteSelect("Scope статистики", statsScope, listOf("all" to "all", "ad" to "ad", "sausage" to "sausage"), { statsScope = it })
            Button(enabled = !busy, onClick = { busy = true; scope.launch {
                try { stats = api.get("/api/admin/stats?scope=$statsScope") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message.orEmpty() }
                finally { busy = false }
            } }) { Text("Обновить статистику") }
            AdminDownloadButton(api, "/api/admin/stats/export?scope=$statsScope", "Скачать Excel", "kolbabot-statistics-$statsScope.xlsx")
            stats?.let { Text("Клики: ${it.optLong("total_clicks")}\nПользователи: ${it.optLong("unique_users")}\nКнопки: ${it.optLong("unique_buttons")}") }
        }
        ToolGroup("Синхронизация цен", "Обновление цен каталога из внешнего источника. Токен вводится здесь, не в env.") {
            AdminEditForm("Синхронизация цен", listOf(AdminField("market_token", "VK TOKEN (динамический)", "password")), JSONObject(),
                {}, embedded = true, saveLabel = "Синхронизировать цены") { values ->
                confirmation = ToolsConfirmation("Обновить цены каталога?", "Текущие цены будут синхронизированы с внешним источником.") {
                    val result = api.post("/api/admin/market-sync", values)
                    saved = "Синхронизация: ${result.optString("status")}, обновлено: ${result.optInt("updated_count")}."
                }
            }
        }
        ToolGroup("Оформление сайта", "Изображения интерфейса, которые видят пользователи.") {
            Text("Картинка в шапке (квадратная)")
            AdminUploadButton(api, "/api/admin/branding/upload", "Загрузить картинку шапки", arrayOf("image/*"), mapOf("kind" to "hero"))
            Text("Значок вкладки (favicon)")
            AdminUploadButton(api, "/api/admin/branding/upload", "Загрузить favicon", arrayOf("image/*"), mapOf("kind" to "favicon"))
            HorizontalDivider()
            Text("Социальные сети", style = MaterialTheme.typography.titleMedium)
            Text("Иконка появится справа от названия сайта, только если для неё сохранена ссылка.", color = SiteColors.Muted)
            bootstrap?.let { data -> AdminEditForm("Социальные сети", listOf("vk" to "ВКонтакте", "max" to "MAX", "youtube" to "YouTube", "instagram" to "Instagram", "telegram" to "Telegram", "facebook" to "Facebook", "tiktok" to "TikTok")
                .map { AdminField(it.first, it.second) }, data.optJSONObject("site_social_links") ?: JSONObject(), ::complete,
                embedded = true, saveLabel = "Сохранить ссылки") { api.patch("/api/admin/settings/social-links", JSONObject().put("site_social_links", it)) } }
        }
        ToolGroup("Тарифы и скидки", "Базовые настройки платных планов и скидок.") {
            plans.forEach { plan ->
                val initial = JSONObject(plan.toString()).put("features", plan.optJSONArray("features")?.let { a -> (0 until a.length()).joinToString("\n") { a.optString(it) } }.orEmpty())
                AdminEditForm("Тариф PREMIUM", listOf(AdminField("title", "Название", required = true), AdminField("description", "Описание", "multiline"),
                    AdminField("features", "Преимущества — по одному на строку", "multiline"), AdminField("base_amount_rub", "Базовая цена, ₽", "long", true), AdminField("discount_percent", "Скидка, %", "decimal", true)),
                    initial, ::complete, embedded = true, saveLabel = "Сохранить тариф") { api.patch("/api/admin/subscriptions/plans/premium", AdminToolsContract.plan(it)) }
            }
        }
        ToolGroup("Промокоды", "Скидка по промокоду действует только на первый период подписки (первый платёж). Автопродления списываются по цене тарифа без промокода.") {
            AdminEditForm("Промокоды", listOf(AdminField("code", "Код", required = true), AdminField("tier", "Тариф", "choice", options = listOf("premium" to "PREMIUM")),
                AdminField("discount_percent", "Скидка (%)", "decimal", true)), JSONObject().put("tier", "premium").put("discount_percent", 10),
                ::complete, embedded = true, saveLabel = "Создать промокод") { api.post("/api/admin/promocodes", AdminToolsContract.promo(it)); revision++ }
            promos.forEach { promo -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${promo.optString("code")} · ${promo.optDouble("discount_percent")}%", Modifier.weight(1f))
                TextButton(onClick = { confirmation = ToolsConfirmation("Удалить промокод?", promo.optString("code")) {
                    api.delete("/api/admin/promocodes/${adminEncode(promo.optString("code"))}"); revision++
                } }) { Text("Удалить") }
            } }
        }
        ToolGroup("Импорт данных", "Загрузка Excel-файлов для FAQ и полного каталога.") {
            val mime = arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")
            Text("Импорт FAQ Excel")
            AdminUploadButton(api, "/api/admin/import/faq-excel", "Импорт FAQ", mime)
            Text("Импорт полного Excel")
            AdminUploadButton(api, "/api/admin/import/full-excel", "Импорт полного каталога", mime)
        }
        ToolGroup("Миграция БД", "Проверка и обновление схемы PostgreSQL. Для переноса SQLite оставьте авто-поиск или укажите пути к файлам через запятую.") {
            AdminEditForm("Миграция БД", listOf(AdminField("source_sqlite", "Источник SQLite"), AdminField("truncate", "Очистить PostgreSQL перед переносом SQLite", "bool")),
                JSONObject().put("source_sqlite", "auto").put("truncate", false), {}, embedded = true, saveLabel = "Обновить / перенести БД") { values ->
                val payload = AdminToolsContract.migration(values.optString("source_sqlite"), values.optBoolean("truncate"))
                confirmation = ToolsConfirmation("Выполнить перенос базы?", if (payload.optBoolean("truncate")) "Текущие таблицы PostgreSQL будут очищены перед импортом. Потребуется проверенная резервная копия." else "Будут обновлены схема и данные PostgreSQL без предварительной очистки.") {
                    val result = api.post("/api/admin/db/migrate", payload); saved = result.optString("output").ifBlank { "Обновление завершено." }
                }
            }
        }
    }
    ToolsConfirm(confirmation) { confirmation = null }
}

@Composable private fun ToolGroup(title: String, note: String, body: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("tool-$title"), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SiteColors.Border), color = Color.White.copy(alpha = .85f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(note, color = SiteColors.Muted, style = MaterialTheme.typography.bodySmall)
            body()
        }
    }
}
