package ru.kolbabot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

fun adminEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

data class AdminField(val key: String, val label: String, val kind: String = "text", val required: Boolean = false,
    val options: List<Pair<String, String>> = emptyList(), val visibleWhen: Pair<String, String>? = null)

// Replace the previous confirmation when several different fields are copied quickly.
private var clipboardConfirmation: Toast? = null

@Composable fun AdminCopyField(label: String, value: String) {
    val context = LocalContext.current
    if (value.isNotBlank() && value != "null") {
        Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Скопировать $label") {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
            clipboardConfirmation?.cancel()
            clipboardConfirmation = Toast.makeText(context.applicationContext, "$label: скопировано", Toast.LENGTH_SHORT).also { it.show() }
        }.padding(vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable fun AdminEditForm(title: String, fields: List<AdminField>, initial: JSONObject = JSONObject(), onBack: () -> Unit,
    embedded: Boolean = false, userApi: ApiClient? = null, saveLabel: String = "Сохранить", onSave: suspend (JSONObject) -> Unit) {
    var draft by rememberSaveable(title, initial.toString()) { mutableStateOf(initial.toString()) }
    var privateValues by remember(title) { mutableStateOf(mapOf<String, String>()) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val data = JSONObject(draft)
    val visibleFields = fields.filter { field -> field.visibleWhen?.let { data.optString(it.first) == it.second } ?: true }
    fun set(field: AdminField, value: Any) {
        if (field.kind == "password") privateValues = privateValues + (field.key to value.toString())
        else draft = JSONObject(draft).put(field.key, value).toString()
    }
    fun leave() { if (!busy) { if (draft != initial.toString() || privateValues.values.any { it.isNotBlank() }) discard = true else onBack() } }
    BackHandler(!embedded) { leave() }
    val form: @Composable ColumnScope.() -> Unit = {
        ErrorMessage(error)
        visibleFields.forEach { field ->
            val value = if (field.kind == "password") privateValues[field.key].orEmpty() else data.opt(field.key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
            when (field.kind) {
                "user" -> AdminUserPicker(checkNotNull(userApi), value.toLongOrNull()?.takeIf { it > 0 },
                    onSelected = { set(field, it?.toString().orEmpty()) }, label = field.label, enabled = !busy,
                    allowEmpty = !field.required, tag = "admin-user-${field.key}")
                "bool" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(field.label, Modifier.weight(1f))
                    Checkbox(checked = value == "true" || value == "1", enabled = !busy, onCheckedChange = { set(field, it) })
                }
                "choice" -> SiteSelect(field.label, value, field.options, { set(field, it) }, enabled = !busy)
                else -> OutlinedTextField(value, { set(field, it) }, label = { Text(field.label + if (field.required) " *" else "") }, enabled = !busy,
                    singleLine = field.kind != "multiline", minLines = if (field.kind == "multiline") 3 else 1,
                    visualTransformation = if (field.kind == "password") PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = when (field.kind) { "int", "long" -> KeyboardType.Number; "decimal" -> KeyboardType.Decimal; "password" -> KeyboardType.Password; else -> KeyboardType.Text }), modifier = Modifier.fillMaxWidth())
            }
        }
        Button(enabled = !busy, onClick = {
            error = ""
            val payload = JSONObject()
            try {
                visibleFields.forEach { field ->
                    val raw = if (field.kind == "password") privateValues[field.key].orEmpty() else data.opt(field.key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
                    require(!field.required || raw.isNotBlank()) { "Заполните поле «${field.label}»." }
                    when (field.kind) {
                        "user" -> if (raw.isNotBlank()) payload.put(field.key, raw.toLongOrNull()?.takeIf { it > 0 }
                            ?: error("Выберите пользователя в поле «${field.label}»."))
                        "bool" -> payload.put(field.key, raw == "true" || raw == "1")
                        "int", "long" -> if (raw.isNotBlank()) payload.put(field.key, raw.toLongOrNull() ?: error("В поле «${field.label}» нужно целое число."))
                        "decimal" -> if (raw.isNotBlank()) payload.put(field.key, raw.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: error("В поле «${field.label}» нужно число."))
                        "password" -> if (raw.isNotBlank()) payload.put(field.key, raw)
                        else -> payload.put(field.key, raw)
                    }
                }
                busy = true
                scope.launch {
                    try { onSave(payload); privateValues = emptyMap(); onBack() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Не удалось сохранить." }
                    finally { busy = false }
                }
            } catch (failure: Exception) { error = failure.message.orEmpty() }
        }) { Text(if (busy) "Сохраняем…" else saveLabel) }
    }
    if (embedded) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = form)
    else Page(title, onBack = { leave() }, content = form)
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Оставить изменения несохранёнными?") }, text = { Text("Изменения этой формы ещё не отправлены на сервер.") },
        confirmButton = { TextButton(onClick = { discard = false; privateValues = emptyMap(); onBack() }) { Text("Выйти без сохранения") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Продолжить редактирование") } })
}

private val adminLabels = mapOf(
    "id" to "Номер", "user_id" to "Пользователь", "username" to "Логин", "email" to "Почта", "phone" to "Телефон", "address" to "Адрес",
    "first_name" to "Имя", "last_name" to "Фамилия", "customer_name" to "Покупатель", "title" to "Название", "name" to "Название", "message" to "Сообщение", "text" to "Текст", "status" to "Статус", "order_id" to "Заказ", "created_at" to "Создано", "updated_at" to "Обновлено", "paid_at" to "Оплачено", "expires_at" to "Истекает", "is_active" to "Включено", "is_admin" to "Администратор", "is_read" to "Прочитано", "items" to "Состав", "total_kopecks" to "Итого", "price_kopecks" to "Цена", "amount_kopecks" to "Сумма", "quantity" to "Количество", "count" to "Количество", "total" to "Всего", "total_count" to "Всего", "users" to "Пользователи", "orders" to "Заказы", "payments" to "Платежи", "products" to "Товары", "statistics" to "Статистика", "summary" to "Итоги", "plan" to "Тариф", "tier" to "Уровень", "premium" to "PREMIUM", "error" to "Ошибка", "result" to "Результат", "reason" to "Причина", "action" to "Действие", "event" to "Событие", "event_type" to "Тип события", "ip" to "IP-адрес", "client_ip" to "IP-адрес", "actor_user_id" to "Администратор", "target_user_id" to "Пользователь", "details" to "Подробности", "payload" to "Данные", "table" to "Раздел данных", "rows" to "Записи", "tables" to "Разделы", "payment_id" to "Номер платежа", "payment_url" to "Ссылка оплаты", "tbank_status" to "Статус банка", "delivery_details" to "Доставка", "delivery_point" to "Пункт выдачи", "full_address" to "Полный адрес", "digital_delivery_email" to "Почта для книги", "sausages_kopecks" to "Колбаски", "stock" to "Остаток", "rating" to "Оценка", "comment" to "Комментарий", "answer" to "Ответ", "code" to "Код", "label" to "Название", "description" to "Описание", "days" to "Дней", "limit" to "Лимит", "offset" to "Смещение", "period" to "Период", "since" to "С", "until" to "До", "recent_orders" to "Последние заказы", "recent_users" to "Новые пользователи", "content" to "Материалы", "attention" to "Требует внимания", "metrics" to "Показатели", "date" to "Дата", "data" to "Данные", "enabled" to "Включено", "registered_at" to "Регистрация", "last_seen_at" to "Последнее посещение", "subscription" to "Подписка", "referral" to "Приглашения", "rewards" to "Бонусы"
)
fun adminLabel(key: String): String = adminLabels[key] ?: key.replace('_', ' ')

@Composable fun AdminJsonDetails(value: JSONObject) {
    value.keys().forEach { key ->
        val item = value.opt(key)
        when (item) {
            is JSONObject -> SectionCard(adminLabel(key)) { AdminJsonDetails(item) }
            is JSONArray -> if (item.length() > 0) SectionCard(adminLabel(key)) {
                for (index in 0 until item.length()) when (val row = item.opt(index)) {
                    is JSONObject -> { if (index > 0) HorizontalDivider(); AdminJsonDetails(row) }
                    else -> AdminCopyField("${index + 1}", row.toString())
                }
            }
            null, JSONObject.NULL -> Unit
            else -> AdminCopyField(adminLabel(key), if (key.endsWith("_kopecks") && item is Number) money(item.toLong()) else if (item is Boolean) if (item) "Да" else "Нет" else item.toString())
        }
    }
}

@Composable fun AdminUploadButton(api: ApiClient, path: String, title: String, mimeTypes: Array<String> = arrayOf("*/*"), fields: Map<String, String> = emptyMap(), onBusyChanged: (Boolean) -> Unit = {}, onUploaded: (JSONObject) -> Unit = {}) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }; var result by remember { mutableStateOf<JSONObject?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) { busy = true; onBusyChanged(true); error = ""; scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val filename = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "upload"
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(65536)
                        while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size().toLong() + count <= 100L * 1024 * 1024) { "Файл больше 100 МБ." }; output.write(buffer, 0, count) }
                        output.toByteArray()
                    } ?: error("Не удалось открыть файл.")
                    api.upload(path, filename, resolver.getType(uri) ?: "application/octet-stream", bytes, fields)
                }
                result = response; onUploaded(response)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message.orEmpty() }
            finally { busy = false; onBusyChanged(false) }
        } }
    }
    OutlinedButton(enabled = !busy, onClick = { picker.launch(mimeTypes) }) { Text(if (busy) "Загружаем…" else title) }
    ErrorMessage(error)
    result?.let { Text("Файл загружен.", style = MaterialTheme.typography.bodySmall) }
}

@Composable fun AdminDownloadButton(api: ApiClient, path: String, title: String, filename: String, body: JSONObject? = null) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    OutlinedButton(enabled = !busy, onClick = { busy = true; error = ""; scope.launch {
        try {
            val (bytes, mime) = api.downloadFile(path, body)
            val file = withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "shared").apply { mkdirs() }
                File(directory, filename.replace(Regex("[^\\p{L}\\p{N}._-]"), "_")).apply { writeBytes(bytes) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime.substringBefore(';'); putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Сохранить или отправить файл"))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty() }
        finally { busy = false }
    } }) { Text(if (busy) "Получаем файл…" else title) }
    ErrorMessage(error)
}
