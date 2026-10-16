package ru.kolbabot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

private val bannerKinds = linkedMapOf(
    "sausages" to "Колбаски за покупки",
    "referral" to "Пригласи друга",
    "review-bonus" to "Колбаски за отзывы",
    "custom" to "Произвольный баннер"
)

private fun bannerFailure(failure: Exception): String {
    if (failure is CancellationException) throw failure
    return if (failure is ApiException && failure.status == 404) "Редактор баннеров ещё не доступен на сервере. Сначала опубликуйте обновление сайта." else failure.message ?: "Не удалось загрузить баннеры. Повторите попытку."
}

private fun bannerList(source: String): List<JSONObject> {
    val array = JSONArray(source)
    return (0 until array.length()).map { array.getJSONObject(it) }
}

private fun bannerJson(items: List<JSONObject>): String = JSONArray().apply {
    items.forEachIndexed { index, item -> put(JSONObject(item.toString()).put("sort_order", (index + 1) * 10)) }
}.toString()

private fun validBannerUrl(raw: String, image: Boolean): Boolean {
    if (raw.isBlank()) return true
    if (raw.length > 2048 || raw.any { it.isWhitespace() || it == '\\' || it.code < 32 }) return false
    if (raw.startsWith("/") && !raw.startsWith("//")) return true
    if (!image && raw.startsWith("#")) return true
    return runCatching { URI(raw).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
}

@Composable
fun AdminBannersScreen(api: ApiClient, onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loaded by rememberSaveable { mutableStateOf(false) }
    var revision by rememberSaveable { mutableStateOf("") }
    var original by rememberSaveable { mutableStateOf("[]") }
    var draft by rememberSaveable { mutableStateOf("[]") }
    var selected by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var conflict by rememberSaveable { mutableStateOf(false) }
    var latest by remember { mutableStateOf<JSONObject?>(null) }
    var confirmReload by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var removeId by remember { mutableStateOf("") }
    val dirty = loaded && draft != original

    fun applyServer(data: JSONObject) {
        require(data.has("banners") && data.optJSONArray("banners") != null && data.optString("revision").isNotBlank()) { "Сервер вернул неполные данные баннеров." }
        val items = data.items("banners").sortedBy { it.optInt("sort_order") }
        draft = bannerJson(items)
        original = draft
        revision = data.getString("revision")
        loaded = true; conflict = false; latest = null
        if (items.none { it.optString("id") == selected }) selected = ""
    }

    fun loadServer() {
        scope.launch {
            busy = true; error = ""; notice = ""
            try { applyServer(api.get("/api/admin/store/banners")) }
            catch (e: Exception) { error = bannerFailure(e) } finally { busy = false }
        }
    }

    fun edit(id: String, field: String, value: Any) {
        draft = bannerJson(bannerList(draft).map { item -> if (item.optString("id") == id) JSONObject(item.toString()).put(field, value) else item })
        notice = ""
    }

    fun leave() { if (dirty) confirmLeave = true else onBack?.invoke() }
    BackHandler(onBack != null) { leave() }
    LaunchedEffect(api.userId) { if (!loaded) loadServer() }

    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("Выйти без сохранения?") }, text = { Text("Изменения баннеров ещё не опубликованы.") }, confirmButton = {
        TextButton(onClick = { confirmLeave = false; onBack?.invoke() }) { Text("Выйти") }
    }, dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Продолжить редактирование") } })

    if (confirmReload) AlertDialog(onDismissRequest = { confirmReload = false }, title = { Text("Загрузить баннеры с сервера?") }, text = { Text("Текущий черновик будет заменён опубликованной версией. При необходимости сначала скопируйте черновик.") }, confirmButton = {
        TextButton(onClick = { confirmReload = false; val data = latest; if (data != null) applyServer(data) else loadServer() }) { Text("Загрузить") }
    }, dismissButton = { TextButton(onClick = { confirmReload = false }) { Text("Отмена") } })

    if (removeId.isNotBlank()) AlertDialog(onDismissRequest = { removeId = "" }, title = { Text("Удалить баннер?") }, text = { Text("Он исчезнет с сайта и из приложения после сохранения списка.") }, confirmButton = {
        TextButton(onClick = { draft = bannerJson(bannerList(draft).filter { it.optString("id") != removeId }); if (selected == removeId) selected = ""; removeId = ""; notice = "" }) { Text("Удалить") }
    }, dismissButton = { TextButton(onClick = { removeId = "" }) { Text("Отмена") } })

    Page("Баннеры магазина", "Общий список для сайта и приложения", if (onBack == null) null else ({ leave() })) {
        Text("Порядок карточек здесь определяет порядок в карусели. Изменения появятся на сайте и в приложении после сохранения.")
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) ErrorMessage(error)
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
        if (!loaded) {
            if (!busy) Button(onClick = { loadServer() }) { Text("Загрузить баннеры") }
            return@Page
        }
        if (dirty) Text("Есть несохранённые изменения", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
        if (conflict) SectionCard("Список изменён другим администратором") {
            Text("Ваш черновик сохранён на этом экране. Чтобы не затереть чужие изменения, сохранение остановлено. Скопируйте черновик, загрузите свежую версию и повторите нужные правки.")
            latest?.let { Text("Сейчас на сервере баннеров: ${it.items("banners").size}") }
            OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Черновик баннеров", draft)); notice = "Черновик скопирован." }) { Text("Скопировать мой черновик") }
            Button(enabled = !busy, onClick = { confirmReload = true }) { Text("Загрузить свежую версию") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && dirty && !conflict, modifier = Modifier.weight(1f), onClick = {
                scope.launch {
                    busy = true; error = ""; notice = ""
                    try {
                        val items = bannerList(draft)
                        require(items.size <= 20) { "Допускается не более 20 баннеров." }
                        items.forEachIndexed { index, item ->
                            require(item.optString("title").isNotBlank()) { "Заполните заголовок баннера ${index + 1}." }
                            require(validBannerUrl(item.optString("image_url"), true)) { "Проверьте ссылку на изображение баннера ${index + 1}: нужен HTTPS или путь /…" }
                            require(validBannerUrl(item.optString("link_url"), false)) { "Проверьте ссылку кнопки баннера ${index + 1}: нужен HTTPS, путь /… или #раздел." }
                        }
                        val response = api.put("/api/admin/store/banners", JSONObject().put("revision", revision).put("banners", JSONArray(draft)))
                        applyServer(response)
                        notice = "Баннеры сохранены для сайта и приложения."
                    } catch (e: Exception) {
                        if (e is ApiException && e.status == 409) {
                            conflict = true
                            try { latest = api.get("/api/admin/store/banners") }
                            catch (refreshFailure: Exception) { error = bannerFailure(refreshFailure) }
                        } else error = bannerFailure(e)
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Подождите…" else "Сохранить") }
            OutlinedButton(enabled = !busy, onClick = { if (dirty) confirmReload = true else loadServer() }) { Text("Обновить") }
        }
        val items = bannerList(draft)
        if (items.isEmpty()) Text("Баннеров нет. После сохранения пустого списка карусель будет скрыта.")
        items.forEachIndexed { index, item ->
            val id = item.optString("id")
            key(id) {
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${item.optString("title").ifBlank { "Новый баннер" }}", style = MaterialTheme.typography.titleMedium)
                            Text(bannerKinds[item.optString("kind")] ?: item.optString("kind"), style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = item.optBoolean("is_active", true), enabled = !busy, onCheckedChange = { edit(id, "is_active", it) })
                    }
                    Text(if (item.optBoolean("is_active", true)) "Включён" else "Скрыт", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy && index > 0, onClick = { val reordered = items.toMutableList(); val moved = reordered.removeAt(index); reordered.add(index - 1, moved); draft = bannerJson(reordered) }) { Text("↑") }
                        OutlinedButton(enabled = !busy && index < items.lastIndex, onClick = { val reordered = items.toMutableList(); val moved = reordered.removeAt(index); reordered.add(index + 1, moved); draft = bannerJson(reordered) }) { Text("↓") }
                        TextButton(onClick = { selected = if (selected == id) "" else id }) { Text(if (selected == id) "Свернуть" else "Редактировать") }
                    }
                    if (selected == id) {
                        var kindsOpen by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(enabled = !busy, onClick = { kindsOpen = true }) { Text("Тип: ${bannerKinds[item.optString("kind")] ?: "Произвольный"} ▾") }
                            DropdownMenu(kindsOpen, { kindsOpen = false }) { bannerKinds.forEach { (kind, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { edit(id, "kind", kind); kindsOpen = false }) } }
                        }
                        Text("Баннеры акций показываются только когда включена соответствующая акция. Произвольный баннер не зависит от настроек акций.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(item.optString("title"), { if (it.length <= 160) edit(id, "title", it) }, label = { Text("Заголовок") }, supportingText = { Text("${item.optString("title").length}/160") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        OutlinedTextField(item.optString("text"), { if (it.length <= 1600) edit(id, "text", it) }, label = { Text("Текст") }, minLines = 3, supportingText = { Text("${item.optString("text").length}/1600") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        OutlinedTextField(item.optString("image_url"), { if (it.length <= 2048) edit(id, "image_url", it.trim()) }, label = { Text("Ссылка на изображение") }, supportingText = { Text("https://… или /data/…") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        OutlinedTextField(item.optString("link_url"), { if (it.length <= 2048) edit(id, "link_url", it.trim()) }, label = { Text("Ссылка кнопки") }, supportingText = { Text("https://…, /… или #раздел") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        OutlinedTextField(item.optString("link_text"), { if (it.length <= 80) edit(id, "link_text", it) }, label = { Text("Текст кнопки") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                        Text("Автоподстановка из настроек акций: {standard_percent}, {premium_percent}, {max_redemption_percent}, {referral_percent}, {referral_max_sausages}, {review_bonus_sausages}, {review_min_chars}.", style = MaterialTheme.typography.bodySmall)
                        Text("Предпросмотр", style = MaterialTheme.typography.titleSmall)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (item.optString("image_url").isNotBlank()) NetworkImage(runCatching { URI(api.baseUrl).resolve(item.optString("image_url")).toString() }.getOrDefault(""), item.optString("title"), Modifier.fillMaxWidth().height(180.dp))
                                Text(item.optString("title"), style = MaterialTheme.typography.titleLarge)
                                Text(item.optString("text"))
                                if (item.optString("link_url").isNotBlank()) Text(item.optString("link_text").ifBlank { "Подробнее" } + " →", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Text("В опубликованном баннере значения в фигурных скобках заменятся актуальными настройками акции.", style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = !busy, onClick = { removeId = id }) { Text("Удалить баннер", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        OutlinedButton(enabled = !busy && items.size < 20, modifier = Modifier.fillMaxWidth(), onClick = {
            val id = UUID.randomUUID().toString()
            val created = JSONObject().put("id", id).put("kind", "custom").put("title", "").put("text", "").put("image_url", "").put("link_url", "").put("link_text", "Подробнее").put("is_active", true)
            draft = bannerJson(items + created); selected = id; notice = ""
        }) { Text("Добавить баннер (${items.size}/20)") }
    }
}
