package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

internal object AdminPeopleContract {
    fun credentials(raw: JSONObject): JSONObject {
        val username = raw.optString("username").trim()
        require(username.isNotEmpty()) { "Введите логин." }
        return JSONObject().put("username", username).apply {
            val password = raw.optString("password")
            if (password.isNotEmpty()) put("password", password)
        }
    }
    fun access(raw: JSONObject): JSONObject {
        val hours = raw.optLong("duration_hours")
        require(hours > 0) { "Продолжительность должна быть больше нуля." }
        val action = raw.optString("action", "replace")
        require(action in setOf("replace", "extend")) { "Выберите действие с подпиской." }
        return JSONObject().put("tier", "premium").put("duration_hours", hours).put("action", action)
    }
    fun notification(raw: JSONObject): JSONObject {
        val scope = raw.optString("scope", "global")
        require(scope in setOf("global", "personal")) { "Выберите получателей." }
        val title = raw.optString("title").trim()
        val message = raw.optString("message").trim()
        require(title.isNotEmpty() && title.length <= 240) { "Заголовок: от 1 до 240 символов." }
        require(message.isNotEmpty() && message.length <= 20000) { "Сообщение: от 1 до 20 000 символов." }
        val target = if (scope == "personal") raw.optLong("target_user_id") else 0L
        require(scope != "personal" || target > 0) { "Выберите получателя из списка." }
        return JSONObject().put("scope", scope).put("target_user_id", target).put("title", title).put("message", message)
    }
    fun userFromDossier(dossier: JSONObject): JSONObject {
        val tables = dossier.items("tables").associateBy { it.optString("table") }
        val original = tables["miniapp_users"]?.items("rows")?.firstOrNull() ?: JSONObject()
        val result = JSONObject(original.toString())
        val access = tables["miniapp_user_access"]?.items("rows")?.firstOrNull()
        if (access != null) {
            var tier = if (access.optString("tier").lowercase(java.util.Locale.ROOT) == "premium") "premium" else "none"
            var status = access.optString("status").lowercase(java.util.Locale.ROOT).takeIf { it in setOf("active", "inactive", "revoked", "expired") } ?: "inactive"
            if (status == "active" && subscriptionExpired(access.optString("active_until"), dossier.optString("generated_at"), dossier.optString("app_timezone")) == true) {
                status = "expired"; tier = "none"
            } else if (status != "active") tier = "none"
            else if (tier == "none") status = "inactive"
            result.put("subscription_tier", tier)
                .put("subscription_status", status)
                .put("subscription_active_until", access.opt("active_until"))
                .put("subscription_autopay_enabled", flag(access, "is_autopay"))
        }
        result.put("store_sausages", (tables["store_user_rewards"]?.items("rows")?.firstOrNull()?.optLong("sausages_kopecks") ?: 0L) / 100)
        return result
    }
    private data class DossierTime(val local: LocalDateTime? = null, val instant: Instant? = null)
    private fun dossierTime(raw: String): DossierTime? {
        val value = raw.trim().replaceFirst(' ', 'T')
        val instant = runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        if (instant != null) return DossierTime(instant = instant)
        return runCatching { DossierTime(local = LocalDateTime.parse(value)) }.getOrNull()
    }
    /** Uses server time; the phone's clock/time zone must not alter access shown to an admin. */
    fun subscriptionExpired(activeUntil: String, generatedAt: String, zoneName: String = ""): Boolean? {
        val until = dossierTime(activeUntil) ?: return null
        val now = dossierTime(generatedAt) ?: return null
        if (until.local != null && now.local != null) return !until.local.isAfter(now.local)
        val zone = runCatching { ZoneId.of(zoneName) }.getOrNull()
        val untilInstant = until.instant ?: zone?.let { until.local?.atZone(it)?.toInstant() } ?: return null
        val nowInstant = now.instant ?: zone?.let { now.local?.atZone(it)?.toInstant() } ?: return null
        return !untilInstant.isAfter(nowInstant)
    }
    fun flag(value: JSONObject, key: String): Boolean = when (val raw = value.opt(key)) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        else -> raw == "true" || raw == "1"
    }
}

private data class PeopleConfirmation(val title: String, val message: String, val perform: suspend () -> Unit)

@Composable
private fun PeopleConfirmDialog(action: PeopleConfirmation?, onClose: () -> Unit) {
    if (action == null) return
    var busy by remember(action) { mutableStateOf(false) }
    var error by remember(action) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(action.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(action.message); ErrorMessage(error) } },
        confirmButton = { TextButton(enabled = !busy && error.isBlank(), onClick = {
            busy = true
            scope.launch {
                try { action.perform(); onClose() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Операция не выполнена." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Выполняется…" else "Подтвердить") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Отмена") } })
}

@Composable
fun AdminPeopleScreen(api: ApiClient, section: String, onBack: () -> Unit) {
    when (section) {
        "support" -> AdminSupportPeopleScreen(api, onBack)
        "notifications" -> AdminNotificationsPeopleScreen(api, onBack)
        else -> AdminUsersPeopleScreen(api, onBack)
    }
}

@Composable
private fun AdminUsersPeopleScreen(api: ApiClient, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableLongStateOf(0L) }
    var createAdmin by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<PeopleConfirmation?>(null) }
    BackHandler(selectedId > 0 || createAdmin) { selectedId = 0; createAdmin = false }
    if (selectedId > 0) {
        AdminPersonDetail(api, selectedId, { selectedId = it }) { selectedId = 0 }
    } else if (createAdmin) {
        AdminEditForm("Создать или назначить администратора", listOf(
            AdminField("mode", "Действие", "choice", options = listOf("existing" to "Назначить существующего пользователя", "new" to "Создать нового администратора")),
            AdminField("user_id", "Пользователь", "user", required = true, visibleWhen = "mode" to "existing"),
            AdminField("username", "Логин нового администратора", required = true, visibleWhen = "mode" to "new"),
            AdminField("password", "Пароль нового администратора", "password", required = true, visibleWhen = "mode" to "new"),
        ), JSONObject().put("mode", "existing"), onBack = { createAdmin = false }, userApi = api) { values ->
            if (values.optString("mode") == "existing") {
                val id = values.getLong("user_id")
                api.patch("/api/admin/users/$id", JSONObject().put("is_admin", true))
                selectedId = id
            } else {
                val response = api.post("/api/admin/users/promote", AdminPeopleContract.credentials(values))
                selectedId = response.optJSONObject("user")?.optLong("id") ?: 0L
            }
            createAdmin = false
        }
    } else Page("Пользователи", onBack = onBack) {
        AdminUserPicker(api, null, { selectedId = it ?: 0 })
        Text("Выберите пользователя, чтобы открыть профиль, доступ, баланс и действия с аккаунтом.")
        OutlinedButton(onClick = {
            confirm = PeopleConfirmation("Новый администратор", "Администратор сможет управлять пользователями, контентом, заказами и настройками сайта. Продолжить к форме?") { createAdmin = true }
        }) { Text("Администратор +") }
    }
    PeopleConfirmDialog(confirm) { confirm = null }
}

private val dossierTitles = mapOf(
    "miniapp_users" to "Профиль", "miniapp_oauth_identities" to "Подключённые способы входа",
    "miniapp_user_access" to "Доступ и подписка", "miniapp_tbank_payments" to "Оплаты подписки",
    "miniapp_sessions" to "Сессии", "store_orders" to "Заказы магазина", "store_user_rewards" to "Баланс колбасок",
    "store_reward_transactions" to "История колбасок", "support_tickets" to "Обращения в поддержку",
)

@Composable
private fun AdminPersonDetail(api: ApiClient, userId: Long, onSelectUser: (Long) -> Unit, onBack: () -> Unit) {
    var form by remember(userId) { mutableStateOf("") }
    var initial by remember(userId) { mutableStateOf(JSONObject()) }
    var confirm by remember(userId) { mutableStateOf<PeopleConfirmation?>(null) }
    var result by remember(userId) { mutableStateOf<JSONObject?>(null) }
    var refreshKey by remember(userId) { mutableIntStateOf(0) }
    BackHandler(form.isNotEmpty()) { form = "" }
    when (form) {
        "credentials" -> AdminEditForm("Логин и пароль #$userId", listOf(
            AdminField("username", "Логин", required = true),
            AdminField("password", "Новый пароль — пусто, чтобы сохранить текущий", "password"),
        ), initial, { form = "" }) { values -> api.patch("/api/admin/users/$userId", AdminPeopleContract.credentials(values)); form = ""; refreshKey++ }
        "rewards" -> AdminEditForm("Баланс колбасок #$userId", listOf(AdminField("store_sausages", "Новый полный баланс колбасок", "long", true)), initial, { form = "" }) { values ->
            require(values.optLong("store_sausages", -1) >= 0) { "Баланс не может быть отрицательным." }
            api.patch("/api/admin/users/$userId", JSONObject().put("store_sausages", values.getLong("store_sausages"))); form = ""; refreshKey++
        }
        "access" -> AdminEditForm("Выдать или продлить PREMIUM #$userId", listOf(
            AdminField("duration_hours", "Продолжительность в часах", "long", true),
            AdminField("action", "Действие", "choice", options = listOf("replace" to "Выдать от текущего момента", "extend" to "Продлить имеющийся доступ")),
        ), JSONObject().put("duration_hours", 24).put("action", "extend"), { form = "" }) { values ->
            api.post("/api/admin/users/$userId/access", AdminPeopleContract.access(values)); form = ""; refreshKey++
        }
        else -> key(userId, refreshKey) {
            RemotePage("Пользователь #$userId", api, "/api/admin/users/$userId/dossier", onBack) { dossier, reload ->
                AdminUserPicker(api, userId, { it?.let(onSelectUser) })
                val user = AdminPeopleContract.userFromDossier(dossier)
                if (user.optLong("id") <= 0) {
                    ErrorMessage("Пользователь не найден.")
                } else {
                    SectionCard(user.optString("username", "Пользователь")) {
                        AdminCopyField("ID", userId.toString())
                        listOf("email" to "Почта", "phone" to "Телефон", "first_name" to "Имя", "last_name" to "Фамилия", "created_at" to "Регистрация", "last_login_at" to "Последний вход").forEach { (field, label) ->
                            val value = user.optString(field).takeUnless { it == "null" }.orEmpty()
                            if (value.isNotBlank()) AdminCopyField(label, value)
                        }
                        Text("${if (AdminPeopleContract.flag(user, "is_admin")) "Администратор" else "Пользователь"} · ${if (AdminPeopleContract.flag(user, "is_active")) "Активен" else "Заблокирован"}")
                        Text("Двухэтапный вход: ${if (AdminPeopleContract.flag(user, "totp_enabled")) "включён" else "выключен"}")
                        OutlinedButton(onClick = { initial = JSONObject().put("username", user.optString("username")); form = "credentials" }) { Text("Изменить логин или пароль") }
                    }
                    SectionCard("Доступ и колбаски") {
                        Text("Колбаски: ${user.optLong("store_sausages")}")
                        Text("Тариф: ${user.optString("subscription_tier", "none")} · ${user.optString("subscription_status", "inactive")}")
                        Text("Действует до: ${user.optString("subscription_active_until").takeUnless { it.isBlank() || it == "null" } ?: "Нет активного доступа"}")
                        Text("Автопродление: ${if (AdminPeopleContract.flag(user, "subscription_autopay_enabled")) "включено" else "выключено"}")
                        OutlinedButton(onClick = { initial = JSONObject().put("store_sausages", user.optLong("store_sausages")); form = "rewards" }) { Text("Изменить баланс колбасок") }
                        OutlinedButton(onClick = { form = "access" }) { Text("Выдать / продлить PREMIUM") }
                        TextButton(onClick = { confirm = PeopleConfirmation("Отозвать доступ?", "Пользователь #$userId потеряет действующий доступ PREMIUM.") { api.post("/api/admin/users/$userId/access/revoke"); reload() } }) { Text("Отозвать доступ") }
                        TextButton(onClick = { confirm = PeopleConfirmation("Отключить автопродление?", "Новые автоматические списания подписки пользователя #$userId будут отключены.") { api.post("/api/admin/users/$userId/autopay/disable"); reload() } }) { Text("Отключить автопродление") }
                    }
                    SectionCard("Безопасность") {
                        val isAdmin = AdminPeopleContract.flag(user, "is_admin")
                        val active = AdminPeopleContract.flag(user, "is_active")
                        TextButton(onClick = { confirm = PeopleConfirmation(if (isAdmin) "Снять права администратора?" else "Назначить администратором?", "Изменятся права управления сайтом у пользователя #$userId.") {
                            api.patch("/api/admin/users/$userId", JSONObject().put("is_admin", !isAdmin)); reload()
                        } }) { Text(if (isAdmin) "Снять права администратора" else "Назначить администратором") }
                        TextButton(enabled = !active || (userId != api.userId && !isAdmin), onClick = { confirm = PeopleConfirmation(if (active) "Заблокировать пользователя?" else "Разблокировать пользователя?", "Изменится возможность входа в аккаунт #$userId.") {
                            api.patch("/api/admin/users/$userId", JSONObject().put("is_active", !active)); reload()
                        } }) { Text(if (active) "Заблокировать аккаунт" else "Разблокировать аккаунт") }
                        TextButton(onClick = { confirm = PeopleConfirmation("Завершить все сессии?", "Пользователю #$userId потребуется войти заново на всех устройствах, включая сайт и приложение.") { api.post("/api/admin/users/$userId/sessions/revoke"); reload() } }) { Text("Завершить все сессии") }
                        TextButton(onClick = { confirm = PeopleConfirmation("Сбросить двухэтапный вход?", "Настройка двухэтапного входа пользователя #$userId будет сброшена. Для доступа администратора потребуется настроить её снова.") { api.patch("/api/admin/users/$userId", JSONObject().put("reset_2fa", true)); reload() } }) { Text("Сбросить 2FA") }
                    }
                    SectionCard("Принудительное автосписание") {
                        Text("Запускает реальную попытку оплаты подписки для этого пользователя, даже если очередной срок ещё не наступил.")
                        OutlinedButton(onClick = { confirm = PeopleConfirmation("Попытаться списать оплату сейчас?", "Будет выполнено принудительное списание по сохранённому способу оплаты пользователя #$userId. После сетевой ошибки сначала проверьте платежи и личное дело, прежде чем повторять.") {
                            result = api.post("/api/admin/payments/tbank/renewals/run", JSONObject().put("user_id", userId).put("limit", 1).put("force", true)); reload()
                        } }) { Text("Запустить с подтверждением") }
                    }
                    result?.let { SectionCard("Результат операции") { AdminJsonDetails(it) } }
                    SectionCard("Реферальная программа") { AdminJsonDetails(dossier.optJSONObject("referrals") ?: JSONObject()) }
                    dossier.items("tables").forEach { table ->
                        val rows = table.items("rows")
                        var expanded by remember(table.optString("table")) { mutableStateOf(false) }
                        SectionCard(dossierTitles[table.optString("table")] ?: table.optString("table")) {
                            Text("Записей: ${table.optLong("total")} · загружено: ${rows.size}")
                            if (rows.isNotEmpty()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Свернуть" else "Показать записи") }
                            if (expanded) rows.forEachIndexed { index, row ->
                                if (index > 0) HorizontalDivider()
                                AdminJsonDetails(row)
                            }
                        }
                    }
                    OutlinedButton(onClick = reload) { Text("Обновить личное дело") }
                }
            }
        }
    }
    PeopleConfirmDialog(confirm) { confirm = null }
}

@Composable
private fun AdminSupportPeopleScreen(api: ApiClient, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("open") }
    var search by rememberSaveable { mutableStateOf("") }
    var ticket by remember { mutableStateOf<JSONObject?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var count by remember(filter, search) { mutableIntStateOf(50) }
    var expandedId by rememberSaveable(filter, search) { mutableLongStateOf(0) }
    BackHandler(ticket != null) { ticket = null }
    val selected = ticket
    if (selected != null) {
        Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                Text("Вопрос: ${selected.optString("question_text")}", Modifier.verticalScroll(rememberScrollState()).padding(20.dp))
            }
            Box(Modifier.weight(1f)) {
                AdminEditForm("Ответ на обращение #${selected.optLong("id")}", listOf(AdminField("answer_text", "Ответ пользователю", "multiline", true)),
                    JSONObject().put("answer_text", selected.optString("answer_text").takeUnless { it == "null" }.orEmpty()), { ticket = null }) { values ->
                    require(values.optString("answer_text").isNotBlank()) { "Введите ответ." }
                    api.post("/api/admin/support/tickets/${selected.optLong("id")}/answer", JSONObject().put("answer_text", values.getString("answer_text")))
                    ticket = null; refreshKey++
                }
            }
        }
    } else key(refreshKey) {
        RemotePage("Поддержка", api, "/api/admin/support/tickets?status=$filter&limit=500", onBack) { data, reload ->
            BackHandler(expandedId > 0 || search.isNotBlank()) { if (expandedId > 0) expandedId = 0 else search = "" }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("open" to "Открытые", "answered" to "С ответом", "all" to "Все").forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                }
            }
            OutlinedTextField(search, { search = it }, label = { Text("Поиск по обращению или пользователю") }, modifier = Modifier.fillMaxWidth())
            val tickets = data.items("items").filter { row -> search.isBlank() || listOf("id", "username", "user_id", "question_text", "answer_text").any { row.optString(it).contains(search, true) } }
            Text("Обращений: ${tickets.size}")
            OutlinedButton(onClick = reload) { Text("Обновить") }
            tickets.take(count).forEach { row ->
                val id = row.optLong("id")
                val answer = row.optString("answer_text").takeUnless { it == "null" }.orEmpty()
                val answered = answer.isNotBlank() || row.optString("answered_at").let { it.isNotBlank() && it != "null" }
                SiteDisclosure("#${id} · ${row.optString("username", "Пользователь")}", expandedId == id,
                    { expandedId = if (expandedId == id) 0 else id }, summary = row.optString("question_text"),
                    accent = if (answered) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    bodyPadding = 14.dp, bodySpacing = 8.dp) {
                    Text("Пользователь #${row.optLong("user_id")} · ${row.optString("created_at")}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SiteListBadge(if (answered) "С ответом" else "Открыто", if (answered) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                        if (AdminPeopleContract.flag(row, "is_premium")) SiteListBadge("PREMIUM", MaterialTheme.colorScheme.secondary)
                    }
                    Text(row.optString("question_text"))
                    if (answer.isNotBlank()) { HorizontalDivider(); Text("Ответ: $answer"); Text(row.optString("answer_updated_at").takeUnless { it == "null" }.orEmpty()) }
                    if (answer.isBlank() && row.optString("answered_at").let { it.isBlank() || it == "null" }) {
                        Button(onClick = { ticket = row }) { Text("Ответить") }
                    } else Text("Обращение закрыто")
                }
            }
            if (count < tickets.size) OutlinedButton(onClick = { count += 50 }) { Text("Показать ещё") }
            if (tickets.isEmpty()) Text("Обращений по этому фильтру нет.")
        }
    }
}

@Composable
private fun AdminNotificationsPeopleScreen(api: ApiClient, onBack: () -> Unit) {
    var draft by remember { mutableStateOf<JSONObject?>(null) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var confirm by remember { mutableStateOf<PeopleConfirmation?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var limit by remember { mutableIntStateOf(200) }
    var expandedId by rememberSaveable(filter) { mutableLongStateOf(0) }
    BackHandler(draft != null) { draft = null }
    val current = draft
    if (current != null) AdminEditForm("Отправить оповещение", listOf(
        AdminField("scope", "Получатели", "choice", options = listOf("global" to "Все активные пользователи", "personal" to "Один пользователь")),
        AdminField("target_user_id", "Получатель", "user", required = true, visibleWhen = "scope" to "personal"),
        AdminField("title", "Заголовок", required = true), AdminField("message", "Сообщение", "multiline", true),
    ), current, { draft = null }, userApi = api) { values ->
        api.post("/api/admin/notifications", AdminPeopleContract.notification(values)); draft = null; refreshKey++
    } else key(refreshKey) {
        RemotePage("Оповещения", api, "/api/admin/notifications?limit=$limit", onBack) { data, reload ->
            BackHandler(expandedId > 0) { expandedId = 0 }
            Button(onClick = { draft = JSONObject().put("scope", "global").put("target_user_id", 0) }) { Text("Создать оповещение") }
            Text("Отправленное сообщение можно удалить или использовать как основу нового. Удаление уберёт его и у получателей.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "Все", "global" to "Общие", "personal" to "Личные").forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                }
            }
            val items = data.items("items").filter { filter == "all" || it.optString("scope") == filter }
            OutlinedButton(onClick = reload) { Text("Обновить") }
            items.forEach { item ->
                val id = item.optLong("id")
                SiteDisclosure(item.optString("title"), expandedId == id, { expandedId = if (expandedId == id) 0 else id },
                    summary = item.optString("message"), accent = if (item.optString("scope") == "personal") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    bodyPadding = 14.dp, bodySpacing = 8.dp) {
                    Text("#${item.optLong("id")} · ${item.optString("created_at")}")
                    Text(if (item.optString("scope") == "personal") "Личное: ${item.optString("target_username")} (#${item.optLong("target_user_id")})" else "Общее · получателей: ${item.optLong("recipients_count")}")
                    Text(item.optString("message"))
                    Text("Прочитано: ${item.optLong("read_count")}")
                    TextButton(onClick = { draft = JSONObject().put("scope", item.optString("scope")).put("target_user_id", item.optLong("target_user_id")).put("title", item.optString("title")).put("message", item.optString("message")) }) { Text("Создать на основе") }
                    TextButton(onClick = { confirm = PeopleConfirmation("Удалить оповещение?", "Оповещение «${item.optString("title")}» пропадёт у всех его получателей.") { api.delete("/api/admin/notifications/${item.optLong("id")}"); reload() } }) { Text("Удалить") }
                }
            }
            if (items.isEmpty()) Text("Оповещений нет.")
            if (data.items("items").size >= limit && limit < 500) OutlinedButton(onClick = { limit = (limit + 200).coerceAtMost(500) }) { Text("Загрузить больше") }
            if (data.items("items").size >= 500) Text("Показаны последние 500 оповещений — предел журнала на сервере.")
        }
    }
    PeopleConfirmDialog(confirm) { confirm = null }
}
