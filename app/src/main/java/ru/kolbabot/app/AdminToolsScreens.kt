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

internal data class ToolsConfirmation(val title: String, val description: String, val action: suspend () -> Unit)

@Composable
internal fun ToolsConfirm(action: ToolsConfirmation?, onDismiss: () -> Unit) {
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
        "dashboard" -> AdminDashboardScreen(api, onBack)
        "security" -> AdminJournalTools(api, true, onBack)
        "db" -> AdminJournalTools(api, false, onBack)
        else -> AdminToolsPanel(api, onBack)
    }
}

@Composable
private fun AdminJournalTools(api: ApiClient, security: Boolean, onBack: () -> Unit) {
    var filterText by rememberSaveable(security) { mutableStateOf("{}") }
    var offset by rememberSaveable(security) { mutableIntStateOf(0) }
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
        val endpoint = if (security) "/api/admin/security/audit" else "/api/admin/db/events"
        RemotePage(if (security) "Журнал безопасности" else "Журнал базы данных", api, "$endpoint?${AdminToolsContract.query(JSONObject(filterText), offset)}", onBack) { data, reload ->
            AdminEditForm("Фильтры журнала", fields, JSONObject(filterText), {}, embedded = true, userApi = api, saveLabel = "Применить фильтры") { values -> filterText = values.toString(); offset = 0 }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
