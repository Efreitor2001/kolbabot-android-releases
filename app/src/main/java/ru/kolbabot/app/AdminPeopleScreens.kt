package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var dossier by remember { mutableStateOf<JSONObject?>(null) }
    var listedProfile by remember { mutableStateOf<JSONObject?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<PeopleConfirmation?>(null) }
    var newAdminName by rememberSaveable { mutableStateOf("") }
    var newAdminPassword by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(api, selectedId, revision) {
        dossier = null; error = ""; loading = false
        if (selectedId <= 0) return@LaunchedEffect
        loading = true
        try { dossier = api.get("/api/admin/users/$selectedId/dossier") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() }
        finally { loading = false }
    }
    // The website profile uses the public admin users response, including server-normalized
    // subscription fields; the raw dossier is reserved for its explicitly expanded records.
    val user = remember(listedProfile, dossier, selectedId) {
        listedProfile?.takeIf { it.optLong("id") == selectedId }
            ?: dossier?.let(AdminPeopleContract::userFromDossier) ?: JSONObject()
    }
    val present = selectedId > 0 && user.optLong("id") == selectedId && !loading
    val canEdit = present && !busy
    val protectedFromBan = selectedId == api.userId || AdminPeopleContract.flag(user, "is_admin")
    var username by remember(selectedId, user) { mutableStateOf(user.optString("username")) }
    var password by remember(selectedId, user) { mutableStateOf("") }
    var isAdmin by remember(selectedId, user) { mutableStateOf(AdminPeopleContract.flag(user, "is_admin")) }
    var isActive by remember(selectedId, user) { mutableStateOf(AdminPeopleContract.flag(user, "is_active")) }
    var sausages by remember(selectedId, user) { mutableStateOf(user.optLong("store_sausages").toString()) }
    var hours by rememberSaveable(selectedId) { mutableStateOf("24") }
    var accessMode by rememberSaveable(selectedId) { mutableStateOf("replace") }
    fun action(success: String, work: suspend () -> Unit) {
        busy = true; error = ""; notice = ""
        scope.launch {
            try { work(); password = ""; notice = success; revision++ }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message.orEmpty() }
            finally { busy = false }
        }
    }
    fun ask(title: String, body: String, success: String, work: suspend () -> Unit) {
        confirm = PeopleConfirmation(title, body) { work(); password = ""; if (success.isNotBlank()) notice = success; revision++ }
    }
    Page("Пользователи", "Полный профиль пользователя, управление правами, отключение и сервисные действия.", onBack) {
        key(revision) { AdminUserPicker(api, selectedId.takeIf { it > 0 }, { selectedId = it ?: 0; notice = "" }, label = "Пользователи", enabled = !busy, onUserResolved = { listedProfile = it }) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorMessage(error)
        if (present) {
            val role = if (AdminPeopleContract.flag(user, "is_admin")) "Админ" else "Обычный пользователь"
            val status = if (AdminPeopleContract.flag(user, "is_active")) "Активен" else "Отключен"
            val twofa = if (AdminPeopleContract.flag(user, "totp_enabled")) "2FA: включен" else if (AdminPeopleContract.flag(user, "twofa_enrolled")) "2FA: отвязан" else "2FA: не подключен"
            val tier = if (user.optString("subscription_tier") == "premium") "PREMIUM" else "Базовый"
            val subscriptionStatus = user.optString("subscription_status").ifBlank { "inactive" }
            val until = user.optString("subscription_active_until").takeUnless { it.isBlank() || it == "null" }?.let { ", до: ${adminCartDate(it)}" }.orEmpty()
            val lastLogin = user.optString("last_login_at").takeUnless { it.isBlank() || it == "null" }?.let { ", вход: ${adminCartDate(it)}" }.orEmpty()
            Text("$role, $status, $twofa, подписка: $tier ($subscriptionStatus$until)$lastLogin", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        }
        Text("Профиль пользователя", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("admin-user-profile-heading"))
        if (present) SectionCard("Профиль пользователя #$selectedId") { AdminUserProfileFields(user) }
        else Text("Выберите пользователя в списке.", color = SiteColors.Muted)
        Text("Полное досье пользователя", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("admin-user-dossier-heading"))
        dossier?.let { AdminUserDossier(it) } ?: Text("Выберите пользователя в списке.", color = SiteColors.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(isAdmin, { isAdmin = it }, enabled = canEdit); Text("Админ") }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(isActive, { isActive = it }, enabled = canEdit && !protectedFromBan); Text("Активен") }
        }
        OutlinedTextField(username, { username = it }, label = { Text("Логин") }, singleLine = true, enabled = canEdit, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Новый пароль") }, singleLine = true, enabled = canEdit,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        Text("Логин и пароль применяются кнопкой «Сохранить пользователя». Пустой пароль сохраняет текущий. Новый пароль: от 10 символов, со строчной и заглавной буквами, цифрой и спецсимволом.", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        Text("Колбаски магазина", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(sausages, { sausages = it }, label = { Text("Баланс колбасок") }, singleLine = true, enabled = canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Text("Сохранение пользователя перезаписывает указанный баланс колбасок.", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        Text("Управление доступом", style = MaterialTheme.typography.titleMedium)
        SiteSelect("Тариф", "premium", listOf("premium" to "PREMIUM"), {}, enabled = canEdit)
        OutlinedTextField(hours, { hours = it }, label = { Text("Часов доступа") }, singleLine = true, enabled = canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        SiteSelect("Режим", accessMode, listOf("replace" to "Выдать/перезаписать", "extend" to "Продлить сверху"), { accessMode = it }, enabled = canEdit)
        Button(enabled = canEdit && (hours.toLongOrNull() ?: 0) in 1L..8760L, onClick = {
            val target = selectedId
            val payload = AdminPeopleContract.access(JSONObject().put("duration_hours", hours.toLong()).put("action", accessMode))
            ask("Применить доступ?", "${if (accessMode == "extend") "Продлить" else "Выдать"} PREMIUM пользователю #$target на $hours ч.?", "Доступ обновлён.") { api.post("/api/admin/users/$target/access", payload) }
        }) { Text("Применить доступ") }
        TextButton(enabled = canEdit, onClick = { val target = selectedId; ask("Снять доступ?", "Пользователь #$target потеряет действующий доступ PREMIUM.", "Доступ снят.") { api.post("/api/admin/users/$target/access/revoke") } }) { Text("Снять доступ") }
        TextButton(enabled = canEdit, onClick = { val target = selectedId; ask("Отключить автоплатёж?", "Автосписания пользователя #$target будут отключены, сохранённые токены отвязаны.", "Автоплатёж отключён.") { api.post("/api/admin/users/$target/autopay/disable") } }) { Text("Отключить автоплатёж") }
        Text("Безопасность", style = MaterialTheme.typography.titleMedium)
        TextButton(enabled = canEdit && !protectedFromBan && AdminPeopleContract.flag(user, "is_active"), onClick = { val target = selectedId; ask("Забанить пользователя?", "Пользователь #$target не сможет входить в аккаунт.", "Пользователь забанен.") { api.patch("/api/admin/users/$target", JSONObject().put("is_active", false)) } }) { Text("Забанить") }
        TextButton(enabled = canEdit && !AdminPeopleContract.flag(user, "is_active"), onClick = { val target = selectedId; action("Пользователь разбанен.") { api.patch("/api/admin/users/$target", JSONObject().put("is_active", true)) } }) { Text("Разбанить") }
        TextButton(enabled = canEdit, onClick = { val target = selectedId; ask("Завершить все сессии?", "Пользователю #$target потребуется войти заново на всех устройствах.", "Все сессии завершены.") { api.post("/api/admin/users/$target/sessions/revoke") } }) { Text("Завершить все сессии") }
        TextButton(enabled = canEdit, onClick = { val target = selectedId; ask("Сбросить 2FA?", "Настройка двухэтапного входа пользователя #$target будет сброшена.", "Двухэтапный вход сброшен.") { api.patch("/api/admin/users/$target", JSONObject().put("reset_2fa", true)) } }) { Text("Сбросить 2FA") }
        Button(enabled = canEdit && username.isNotBlank() && (sausages.toLongOrNull() ?: -1) >= 0, onClick = {
            val target = selectedId
            val payload = AdminPeopleContract.credentials(JSONObject().put("username", username).put("password", password))
                .put("is_admin", isAdmin).put("is_active", isActive).put("store_sausages", sausages.toLong())
            if ((AdminPeopleContract.flag(user, "is_admin") && !isAdmin) || (AdminPeopleContract.flag(user, "is_active") && !isActive)) {
                ask("Сохранить изменения прав?", "Изменятся права или возможность входа пользователя #$target.", "Пользователь #$target обновлён.") { api.patch("/api/admin/users/$target", payload) }
            } else action("Пользователь #$target обновлён.") { api.patch("/api/admin/users/$target", payload) }
        }) { Text("Сохранить пользователя") }
        TextButton(enabled = canEdit && AdminPeopleContract.flag(user, "is_admin"), onClick = { val target = selectedId; ask("Снять права админа?", "Пользователь #$target потеряет права управления сайтом.", "Права администратора сняты.") { api.patch("/api/admin/users/$target", JSONObject().put("is_admin", false)) } }) { Text("Снять права админа") }
        TextButton(enabled = canEdit, onClick = { val target = selectedId; ask("Попытаться списать оплату сейчас?", "Будет выполнено принудительное списание по сохранённому способу оплаты пользователя #$target. После сетевой ошибки сначала проверьте платежи, прежде чем повторять.", "") {
            val result = api.post("/api/admin/payments/tbank/renewals/run", JSONObject().put("user_id", target).put("limit", 1).put("force", true))
            notice = "Обработано: ${result.optInt("processed")}; успешно: ${result.optInt("success")}; ошибок: ${result.optInt("failed")}."
        } }) { Text("Тест автосписания по пользователю") }
        OutlinedButton(enabled = !busy, onClick = { revision++ }) { Text("Обновить список") }
        Text("Добавить администратора по логину", style = MaterialTheme.typography.titleMedium)
        Text("Укажите логин существующего пользователя или создайте нового администратора с паролем.", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        OutlinedTextField(newAdminName, { newAdminName = it }, label = { Text("Логин администратора") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(newAdminPassword, { newAdminPassword = it }, label = { Text("Пароль (для нового или сброса)") }, singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        Text("Пароль должен содержать строчную и заглавную буквы, цифру и спецсимвол.", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        Button(enabled = !busy && newAdminName.isNotBlank(), onClick = {
            val payload = AdminPeopleContract.credentials(JSONObject().put("username", newAdminName).put("password", newAdminPassword))
            ask("Выдать права админа?", "Пользователь ${newAdminName.trim()} сможет управлять пользователями, контентом, заказами и настройками сайта.", "Права администратора выданы.") {
                val response = api.post("/api/admin/users/promote", payload); newAdminPassword = ""
                response.optJSONObject("user")?.optLong("id")?.takeIf { it > 0 }?.let { selectedId = it }
            }
        }) { Text("Выдать права админа") }
        if (notice.isNotBlank()) Text(notice, color = SiteColors.Accent)
    }
    PeopleConfirmDialog(confirm) { confirm = null }
}

@Composable private fun AdminUserProfileFields(user: JSONObject) {
    val fields = linkedMapOf("id" to "ID", "username" to "Логин", "email" to "Email", "email_verified" to "Email подтвержден",
        "first_name" to "Имя", "last_name" to "Фамилия", "phone" to "Телефон из аккаунта", "oauth_provider" to "Провайдер входа",
        "oauth_provider_label" to "Источник входа", "oauth_profile_consent_at" to "Согласие на данные OAuth", "is_admin" to "Админ", "is_active" to "Активен", "totp_enabled" to "2FA включен", "twofa_enrolled" to "2FA подключен",
        "created_at" to "Создан", "updated_at" to "Обновлен", "last_login_at" to "Последний вход", "pd_consent_at" to "Согласие ПД", "cookie_notice_ack_at" to "Согласие cookie", "subscription_tier" to "Уровень подписки",
        "subscription_status" to "Статус подписки", "subscription_active_until" to "Оплачен до", "subscription_autopay_enabled" to "Автоплатеж", "subscription_autopay_method" to "Метод автоплатежа",
        "subscription_has_rebill" to "Есть RebillId", "subscription_has_sbp" to "Есть SBP токен", "subscription_source" to "Источник доступа",
        "subscription_last_order_id" to "Последний order_id", "subscription_last_payment_id" to "Последний payment_id", "subscription_updated_at" to "Подписка обновлена", "store_sausages" to "Колбаски")
    fields.forEach { (key, label) ->
        val value = user.opt(key)
        val text = when {
            value == null || value == JSONObject.NULL || value.toString().isBlank() -> "—"
            value is Boolean -> if (value) "Да" else "Нет"
            key == "subscription_tier" -> if (value.toString() == "premium") "PREMIUM" else "Базовый"
            key.endsWith("_at") || key.endsWith("_until") -> adminCartDate(value.toString()).takeUnless { it == "без даты" } ?: value.toString()
            else -> value.toString()
        }
        Text("$label: $text", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun AdminUserDossier(data: JSONObject) {
    Text("Снимок на: ${data.optString("generated_at", "—")}", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
    val referrals = data.optJSONObject("referrals") ?: JSONObject()
    val invited = referrals.items("invited")
    SectionCard("Реферальная программа · приглашено: ${invited.size}") {
        referrals.optJSONObject("referred_by")?.let { Text("Сам зарегистрирован по приглашению пользователя #${it.optLong("referrer_user_id")} · ${it.optString("username", "Без имени")}") }
        if (invited.isEmpty()) Text("Пользователь пока никого не пригласил.")
        invited.forEach { person ->
            Text("${person.optString("username", "Без имени")} · ${person.optString("email")}")
            val reward = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("ru-RU")).format(person.optLong("reward_kopecks") / 100.0)
            val rewardLabel = when {
                !person.isNull("reversed_at") && person.optString("reversed_at").isNotBlank() -> "$reward колбасок · отменено возвратом"
                !person.isNull("rewarded_at") && person.optString("rewarded_at").isNotBlank() -> "$reward колбасок начислено"
                else -> "Первая покупка ещё не оплачена или акция была выключена"
            }
            Text("Пользователь #${person.optLong("referred_user_id")} · ${person.optString("registered_at")}", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
            Text("Первая покупка: ${person.optString("first_paid_order_id").takeUnless { it == "null" || it.isBlank() } ?: "ещё не было"} · $rewardLabel", style = MaterialTheme.typography.bodySmall)
        }
    }
    data.items("tables").forEach { table ->
        val rows = table.items("rows")
        SectionCard("${table.optString("table")} · всего: ${table.optLong("total", rows.size.toLong())}") {
            if (rows.isEmpty()) Text("Записей нет.")
            rows.forEachIndexed { index, row ->
                key(table.optString("table"), index) {
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    SiteDisclosure("Запись #${index + 1}", expanded, { expanded = !expanded }, modifier = Modifier.testTag("admin-dossier-${table.optString("table")}-record-$index")) {
                        row.keys().forEach { key ->
                            val value = row.opt(key)
                            Text("$key: ${when (value) { null, JSONObject.NULL -> "—"; is JSONObject -> value.toString(2); is org.json.JSONArray -> value.toString(2); else -> value.toString() }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSupportPeopleScreen(api: ApiClient, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableLongStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    RemotePage("Обращения поддержки", api, "/api/admin/support/tickets?status=all&limit=300", onBack) { data, reload ->
        val tickets = data.items("items")
        val selected = tickets.firstOrNull { it.optLong("id") == selectedId } ?: tickets.firstOrNull()
        var answer by rememberSaveable(selected?.optLong("id")) { mutableStateOf("") }
        OutlinedButton(enabled = !busy, onClick = reload) { Text("Обновить") }
        SiteSelect("Обращение", selected?.optLong("id")?.toString().orEmpty(), tickets.map {
            it.optLong("id").toString() to "#${it.optLong("id")} · ${it.optString("username", "Пользователь")}"
        }, { selectedId = it.toLong(); notice = "" }, enabled = !busy)
        selected?.let { ticket ->
            SectionCard {
                Text("#${ticket.optLong("id")} · ${ticket.optString("username", "Пользователь")}", style = MaterialTheme.typography.titleSmall)
                Text(ticket.optString("question_text"))
                val existing = ticket.optString("answer_text").takeUnless { it == "null" }.orEmpty()
                if (existing.isNotBlank()) Text("Ответ: $existing")
            }
        } ?: Text("Выберите обращение.", color = SiteColors.Muted)
        OutlinedTextField(answer, { answer = it }, label = { Text("Ответ") }, minLines = 4, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        Button(enabled = !busy && selected != null && answer.isNotBlank(), onClick = {
            val ticketId = selected!!.optLong("id"); val submitted = answer.trim()
            busy = true; error = ""; notice = ""
            scope.launch {
                try { api.post("/api/admin/support/tickets/$ticketId/answer", JSONObject().put("answer_text", submitted)); answer = ""; notice = "Ответ отправлен."; reload() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message.orEmpty() }
                finally { busy = false }
            }
        }) { Text("Отправить ответ") }
        ErrorMessage(error)
        if (notice.isNotBlank()) Text(notice, color = SiteColors.Accent)
    }
}

@Composable
private fun AdminNotificationsPeopleScreen(api: ApiClient, onBack: () -> Unit) {
    var scopeName by rememberSaveable { mutableStateOf("global") }
    var title by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var targetUser by rememberSaveable { mutableLongStateOf(0) }
    var confirm by remember { mutableStateOf<PeopleConfirmation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var limit by remember { mutableIntStateOf(200) }
    val coroutineScope = rememberCoroutineScope()
    RemotePage("Оповещения", api, "/api/admin/notifications?limit=$limit", onBack) { data, reload ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("global" to "Общее", "personal" to "Личное").forEach { (value, label) ->
                FilterChip(selected = scopeName == value, enabled = !busy, onClick = { scopeName = value }, label = { Text(label) })
            }
        }
        if (scopeName == "personal") AdminUserPicker(api, targetUser.takeIf { it > 0 }, { targetUser = it ?: 0 }, enabled = !busy, label = "Пользователь")
        OutlinedTextField(title, { if (it.length <= 240) title = it }, label = { Text("Заголовок") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(message, { if (it.length <= 20000) message = it }, label = { Text("Сообщение") }, minLines = 5, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && title.isNotBlank() && message.isNotBlank() && (scopeName == "global" || targetUser > 0), onClick = {
            val payload = AdminPeopleContract.notification(JSONObject().put("scope", scopeName).put("target_user_id", targetUser).put("title", title).put("message", message))
            busy = true; error = ""; notice = ""
            coroutineScope.launch {
                try { api.post("/api/admin/notifications", payload); title = ""; message = ""; notice = "Оповещение отправлено."; reload() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message.orEmpty() }
                finally { busy = false }
            }
        }) { Text(if (scopeName == "global") "Отправить всем" else "Отправить выбранному") }
        ErrorMessage(error)
        if (notice.isNotBlank()) Text(notice, color = SiteColors.Accent)
        Text("История оповещений", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = reload) { Text("Обновить") }
        data.items("items").forEach { item ->
            SectionCard(item.optString("title")) {
                Text("#${item.optLong("id")} · ${item.optString("created_at")}", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
                Text(if (item.optString("scope") == "personal") "Личное: ${item.optString("target_username")} (#${item.optLong("target_user_id")})" else "Общее · получателей: ${item.optLong("recipients_count")}")
                Text(item.optString("message"))
                Text("Прочитано: ${item.optLong("read_count")}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { scopeName = item.optString("scope", "global"); targetUser = item.optLong("target_user_id"); title = item.optString("title"); message = item.optString("message") }) { Text("Создать на основе") }
                TextButton(onClick = { confirm = PeopleConfirmation("Удалить оповещение?", "Оповещение «${item.optString("title")}» пропадёт у всех его получателей.") { api.delete("/api/admin/notifications/${item.optLong("id")}"); reload() } }) { Text("Удалить") }
            }
        }
        if (data.items("items").isEmpty()) Text("Оповещений нет.")
        if (data.items("items").size >= limit && limit < 500) OutlinedButton(onClick = { limit = (limit + 200).coerceAtMost(500) }) { Text("Загрузить больше") }
    }
    PeopleConfirmDialog(confirm) { confirm = null }
}
