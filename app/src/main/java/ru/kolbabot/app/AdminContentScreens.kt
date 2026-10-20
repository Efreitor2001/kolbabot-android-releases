package ru.kolbabot.app

import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BulletSpan
import android.text.style.AlignmentSpan
import android.text.style.ImageSpan
import android.text.style.ParagraphStyle
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.UUID

private fun acPath(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
private fun acError(error: Exception): String { if (error is CancellationException) throw error; return error.message ?: "Не удалось сохранить изменения." }
private fun acText(row: JSONObject, key: String): String = if (row.isNull(key)) "" else row.optString(key)
private fun acBool(row: JSONObject, key: String, default: Boolean = false): Boolean = when (val raw = row.opt(key)) { is Boolean -> raw; is Number -> raw.toInt() != 0; is String -> raw == "true" || raw == "1"; else -> default }
private fun acArray(rows: List<JSONObject>): JSONArray = JSONArray().apply { rows.forEach(::put) }
private fun acUrl(api: ApiClient, path: String): String = runCatching { URI(api.baseUrl).resolve(if (path.startsWith("/", false) || path.startsWith("http")) path else "/data/$path").toString() }.getOrDefault("")
private fun acSlug(value: String): String {
    val letters = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
    val latin = listOf("a","b","v","g","d","e","e","zh","z","i","y","k","l","m","n","o","p","r","s","t","u","f","h","ts","ch","sh","sch","","y","","e","yu","ya")
    return buildString { value.lowercase().forEach { char -> val i = letters.indexOf(char); append(if (i >= 0) latin[i] else char.toString()) } }.replace(Regex("[^a-z0-9_-]+"), "-").trim('-').take(100)
}

@Composable
fun AdminContentScreen(api: ApiClient, section: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var bootstrap by remember(section) { mutableStateOf<JSONObject?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by rememberSaveable(section) { mutableStateOf("") }
    var editor by rememberSaveable(section) { mutableStateOf("") }
    var selected by rememberSaveable(section) { mutableStateOf("{}") }
    var groupKey by rememberSaveable(section) { mutableStateOf("") }
    // Keep catalog selection above the loading branch, which is removed while refreshing.
    var catalogCategoryKey by rememberSaveable(section) { mutableStateOf("") }
    var catalogSausageCategoryKey by rememberSaveable(section) { mutableStateOf("") }
    var catalogSausageKey by rememberSaveable(section) { mutableStateOf("") }
    var deletion by remember { mutableStateOf<JSONObject?>(null) }
    val title = mapOf("catalog" to "Категории и сорта", "handbook" to "Редактор справочника", "articles" to "Редактор статей", "recipes" to "Рецептуры: текст и ингредиенты", "faq" to "Редактор FAQ", "equipment" to "Оборудование")[section] ?: "Материалы"
    LaunchedEffect(reload, section) {
        loading = true; error = ""
        try { bootstrap = api.get("/api/admin/bootstrap") }
        catch (e: Exception) { error = acError(e) } finally { loading = false }
    }
    fun done() { editor = ""; bootstrap = null; notice = "Изменения сохранены."; reload++ }
    fun edit(kind: String, row: JSONObject = JSONObject()) { selected = row.toString(); editor = kind; notice = "" }
    fun askDelete(label: String, path: String, body: JSONObject = JSONObject()) { deletion = JSONObject().put("label", label).put("path", path).put("body", body) }
    val data = bootstrap
    val row = remember(selected) { JSONObject(selected) }
    deletion?.let { pending ->
        AlertDialog(onDismissRequest = { if (!loading) deletion = null }, title = { Text("Подтвердите удаление") }, text = { Text(pending.getString("label")) }, confirmButton = {
            TextButton(enabled = !loading, onClick = {
                scope.launch {
                    loading = true; error = ""
                    try { api.delete(pending.getString("path"), pending.getJSONObject("body")); deletion = null; selected = "{}"; editor = ""; bootstrap = null; notice = "Удалено."; reload++ }
                    catch (e: Exception) { error = acError(e); deletion = null } finally { loading = false }
                }
            }) { Text("Удалить") }
        }, dismissButton = { TextButton(enabled = !loading, onClick = { deletion = null }) { Text("Отмена") } })
    }
    @Composable fun Editor() {
        if (data == null || editor.isBlank()) return
        val categories = data.items("categories").map { it.optString("slug") to it.optString("title") }
        when (editor) {
            "category-new", "category-edit" -> AdminEditForm(if (editor == "category-new") "Добавить категорию" else "Изменить категорию", (if (editor == "category-new") listOf(AdminField("slug", "Код в адресе (латиница)", required = true)) else emptyList()) + AdminField("title", "Название категории", required = true), row, { editor = "" }, embedded = true) { payload ->
                if (editor == "category-new") { payload.put("slug", acSlug(payload.optString("slug"))); api.post("/api/admin/categories", payload) }
                else api.patch("/api/admin/categories/${acPath(row.getString("slug"))}", payload)
                done()
            }
            "sausage-new", "sausage-edit" -> {
                val fresh = editor == "sausage-new"
                val fields = (if (fresh) listOf(AdminField("category_slug", "Категория", "choice", true, categories)) else emptyList()) + listOf(AdminField(if (fresh) "display_name" else "new_display_name", "Название сорта", required = true), AdminField(if (fresh) "sausage_slug" else "new_sausage_slug", "Код в адресе (латиница)", required = true))
                AdminEditForm(if (fresh) "Добавить сорт" else "Изменить сорт", fields, row, { editor = "" }, embedded = true) { payload ->
                    if (fresh) api.post("/api/admin/sausages", payload.put("sausage_slug", acSlug(payload.optString("sausage_slug"))))
                    else api.patch("/api/admin/sausages", payload.put("category_slug", row.getString("category_slug")).put("old_sausage_slug", row.getString("old_sausage_slug")).put("new_sausage_slug", acSlug(payload.optString("new_sausage_slug"))))
                    done()
                }
            }
            "handbook-new", "handbook-new-section", "handbook-edit" -> {
                val existing = editor == "handbook-edit"
                val newSection = editor == "handbook-new-section"
                val fields = (if (!existing) listOf(AdminField("section", "Раздел", if (newSection) "text" else "choice", true, if (newSection) emptyList() else data.items("handbook_sections").map { it.optString("key") to it.optString("title") }), AdminField("key", "Код статьи (латиница)", required = true)) else emptyList()) +
                    listOf(AdminField("section_title", "Название раздела", required = newSection), AdminField("title", "Заголовок статьи", required = true), AdminField("text", "Текст статьи", "multiline", true), AdminField("link", "Дополнительная ссылка"))
                AdminEditForm(if (existing) "Изменить статью справочника" else "Новая статья справочника", fields, row, { editor = "" }, embedded = true) { payload ->
                    val group = if (existing) row.getString("section") else acSlug(payload.getString("section"))
                    val article = if (existing) row.getString("key") else acSlug(payload.getString("key"))
                    require(group.isNotBlank() && article.isNotBlank()) { "Укажите коды раздела и статьи." }
                    api.put("/api/admin/handbook/${acPath(group)}/${acPath(article)}", payload)
                    done()
                }
            }
            "faq-new", "faq-edit" -> AdminEditForm("Вопрос и ответ", listOf(AdminField("question", "Вопрос", "multiline", true), AdminField("answer", "Ответ", "multiline", true)), row, { editor = "" }, embedded = true) { payload ->
                if (row.optInt("id") > 0) api.patch("/api/admin/faq/${row.optInt("id")}", payload) else api.post("/api/admin/faq", payload)
                done()
            }
            "equipment" -> AdminEquipmentEditor(api, row, { editor = "" }, ::done)
            "article" -> AdminArticleEditor(api, row, { editor = "" }, ::done)
            "comments" -> AdminArticleModeration(api, row.getInt("id"), { editor = "" })
            "recipe" -> AdminRecipeEditor(api, row, data, { editor = "" }, ::done)
        }
    }
    if (editor == "comments" && row.optInt("id") > 0) {
        BackHandler { editor = "article" }
        AdminArticleModeration(api, row.optInt("id")) { editor = "article" }
        return
    }
    Page(title, onBack = onBack) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorMessage(error)
        if (notice.isNotBlank()) Text(notice, color = SiteColors.Accent)
        if (data == null) return@Page
        when (section) {
            "catalog" -> {
                AcCatalogInline(api, data, !loading, onChanged = ::done, onError = { error = it },
                    onNewCategory = { edit("category-new") }, onNewSausage = { category -> edit("sausage-new", JSONObject().put("category_slug", category)) },
                    onDelete = ::askDelete,
                    categoryKey = catalogCategoryKey, onCategorySelected = { catalogCategoryKey = it },
                    sausageCategoryKey = catalogSausageCategoryKey, onSausageCategorySelected = { catalogSausageCategoryKey = it; catalogSausageKey = "" },
                    sausageKey = catalogSausageKey, onSausageSelected = { catalogSausageKey = it })
                if (editor.isNotBlank()) key(editor, selected) { Editor() }
            }
            "handbook", "recipes" -> {
                val handbook = section == "handbook"
                val groups = data.items(if (handbook) "handbook_sections" else "categories")
                val groupId = if (handbook) "key" else "slug"
                val group = groups.firstOrNull { it.optString(groupId) == groupKey } ?: groups.firstOrNull()
                val entries = group?.items(if (handbook) "articles" else "sausages").orEmpty()
                val entryId = if (handbook) "key" else "slug"
                val entryTitle = if (handbook) "title" else "display_name"
                fun rowFor(entry: JSONObject): JSONObject = JSONObject(entry.toString()).apply {
                    if (handbook) { put("section", group?.optString("key")); put("section_title", group?.optString("title")) }
                    else put("category_slug", group?.optString("slug"))
                }
                LaunchedEffect(data, groupKey, editor) {
                    if (editor.isBlank() && entries.isNotEmpty()) {
                        val entry = entries.firstOrNull { it.optString(entryId) == row.optString(entryId) } ?: entries.first()
                        selected = rowFor(entry).toString(); editor = if (handbook) "handbook-edit" else "recipe"
                    }
                }
                SiteSelect(if (handbook) "Раздел" else "Категория", group?.optString(groupId).orEmpty(), groups.map { it.optString(groupId) to it.optString("title") }, {
                    groupKey = it; selected = "{}"; editor = ""
                })
                SiteSelect(if (handbook) "Статья" else "Сорт", row.optString(entryId), entries.map { it.optString(entryId) to it.optString(entryTitle) }, { key ->
                    entries.firstOrNull { it.optString(entryId) == key }?.let { edit(if (handbook) "handbook-edit" else "recipe", rowFor(it)) }
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { edit(if (handbook) "handbook-new" else "recipe", JSONObject().put(if (handbook) "section" else "category_slug", group?.optString(groupId).orEmpty())) }) { Text(if (handbook) "Новая" else "Новый рецепт") }
                    if (handbook && row.optString("key").isNotBlank()) TextButton(onClick = { askDelete("Удалить статью «${row.optString("title")}»?", "/api/admin/handbook/${acPath(row.optString("section"))}/${acPath(row.optString("key"))}") }) { Text("Удалить") }
                }
                if (handbook) TextButton(onClick = { edit("handbook-new-section") }) { Text("Создать раздел и статью") }
                key(editor, selected) { CompositionLocalProvider(LocalAcInline provides true) { Editor() } }
            }
            else -> {
                val items = data.items(section)
                val kind = when (section) { "faq" -> "faq-edit"; "equipment" -> "equipment"; else -> "article" }
                val titleKey = if (section == "faq") "question" else "title"
                LaunchedEffect(data, editor) {
                    if (editor.isBlank() && items.isNotEmpty()) {
                        selected = (items.firstOrNull { it.optInt("id") == row.optInt("id") } ?: items.first()).toString(); editor = kind
                    }
                }
                SiteSelect(when (section) { "faq" -> "Запись FAQ"; "equipment" -> "Позиция оборудования"; else -> "Статья" }, row.optInt("id").takeIf { it > 0 }?.toString().orEmpty(), items.map { it.optInt("id").toString() to it.optString(titleKey) }, { id ->
                    items.firstOrNull { it.optInt("id").toString() == id }?.let { edit(kind, it) }
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { edit(if (section == "faq") "faq-new" else kind, if (section == "equipment") JSONObject().put("is_active", true).put("sort_order", 100).put("buy_label", "Купить") else JSONObject()) }) { Text(if (section == "articles") "Новая статья" else "Новая") }
                    if (row.optInt("id") > 0) TextButton(onClick = { askDelete("Удалить «${row.optString(titleKey)}»?", "/api/admin/$section/${row.optInt("id")}") }) { Text("Удалить") }
                    if (section == "articles" && row.optInt("id") > 0) TextButton(onClick = { editor = "comments" }) { Text("Комментарии (${row.optInt("comments_count")})") }
                }
                key(editor, selected) { CompositionLocalProvider(LocalAcInline provides true) { Editor() } }
                if (section == "faq") AdminUploadButton(api, "/api/admin/import/faq-excel", "Импорт FAQ из Excel", arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"), onUploaded = { result -> notice = result.optString("result", "Импорт завершён."); reload++ })
            }
        }
    }
}

private val LocalAcInline = staticCompositionLocalOf { false }

@Composable private fun AcEditorPage(title: String, subtitle: String = "", onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    if (LocalAcInline.current) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    else Page(title, subtitle, onBack, content = content)
}

@Composable private fun AcCatalogInline(api: ApiClient, data: JSONObject, enabled: Boolean, onChanged: () -> Unit, onError: (String) -> Unit,
    onNewCategory: () -> Unit, onNewSausage: (String) -> Unit, onDelete: (String, String, JSONObject) -> Unit,
    categoryKey: String, onCategorySelected: (String) -> Unit,
    sausageCategoryKey: String, onSausageCategorySelected: (String) -> Unit,
    sausageKey: String, onSausageSelected: (String) -> Unit) {
    val categories = data.items("categories")
    val category = categories.firstOrNull { it.optString("slug") == categoryKey } ?: categories.firstOrNull()
    val sausageCategory = categories.firstOrNull { it.optString("slug") == sausageCategoryKey } ?: categories.firstOrNull()
    val sausages = sausageCategory?.items("sausages").orEmpty()
    val sausage = sausages.firstOrNull { it.optString("slug") == sausageKey } ?: sausages.firstOrNull()
    var categoryTitle by remember(data, category?.optString("slug")) { mutableStateOf(category?.optString("title").orEmpty()) }
    var sausageTitle by remember(data, sausage?.optString("slug")) { mutableStateOf(sausage?.optString("display_name").orEmpty()) }
    var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    fun save(block: suspend () -> Unit) { busy = true; scope.launch { try { block(); onChanged() } catch (failure: Exception) { onError(acError(failure)) } finally { busy = false } } }
    SiteSelect("Категория", category?.optString("slug").orEmpty(), categories.map { it.optString("slug") to it.optString("title") }, onCategorySelected, enabled = enabled && !busy)
    AcField("Название категории", categoryTitle, enabled && !busy) { categoryTitle = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onNewCategory, enabled = enabled && !busy) { Text("Новая") }
        Button(enabled = enabled && !busy && category != null && categoryTitle.isNotBlank(), onClick = { save { api.patch("/api/admin/categories/${acPath(category!!.getString("slug"))}", JSONObject().put("title", categoryTitle.trim())) } }) { Text("Сохранить") }
        TextButton(enabled = enabled && !busy && category != null, onClick = { onDelete("Удалить категорию «${category?.optString("title") }»?", "/api/admin/categories/${acPath(category!!.getString("slug"))}", JSONObject()) }) { Text("Удалить") }
    }
    HorizontalDivider()
    SiteSelect("Категория для сорта", sausageCategory?.optString("slug").orEmpty(), categories.map { it.optString("slug") to it.optString("title") }, onSausageCategorySelected, enabled = enabled && !busy)
    SiteSelect("Сорт", sausage?.optString("slug").orEmpty(), sausages.map { it.optString("slug") to it.optString("display_name") }, onSausageSelected, enabled = enabled && !busy)
    AcField("Название сорта", sausageTitle, enabled && !busy) { sausageTitle = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onNewSausage(sausageCategory?.optString("slug").orEmpty()) }, enabled = enabled && !busy && sausageCategory != null) { Text("Новый") }
        Button(enabled = enabled && !busy && sausage != null && sausageTitle.isNotBlank(), onClick = { save { api.patch("/api/admin/sausages", JSONObject().put("category_slug", sausageCategory!!.getString("slug")).put("old_sausage_slug", sausage!!.getString("slug")).put("new_sausage_slug", sausage.getString("slug")).put("new_display_name", sausageTitle.trim())) } }) { Text("Сохранить") }
        TextButton(enabled = enabled && !busy && sausage != null, onClick = { onDelete("Удалить сорт «${sausage?.optString("display_name") }»?", "/api/admin/sausages", JSONObject().put("category_slug", sausageCategory!!.getString("slug")).put("sausage_slug", sausage!!.getString("slug"))) }) { Text("Удалить") }
    }
}

@Composable
private fun AcField(label: String, value: String, enabled: Boolean = true, multiline: Boolean = false, numeric: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = !multiline, minLines = if (multiline) 4 else 1, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable
private fun AcChoice(label: String, selected: String, choices: List<Pair<String, String>>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    SiteSelect(label, selected, choices, onSelect, enabled = enabled)
}

@Composable
private fun AcToggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, Modifier.weight(1f)); Checkbox(checked, onChange, enabled = enabled) }
}

@Composable
private fun AcPhoto(api: ApiClient, label: String, uploadPath: String, path: String, enabled: Boolean, onBusyChanged: (Boolean) -> Unit = {}, onChange: (String) -> Unit) {
    var clear by remember { mutableStateOf(false) }
    SectionCard(label) {
        if (path.isNotBlank()) {
            NetworkImage(acUrl(api, path), label, Modifier.fillMaxWidth().height(180.dp))
            TextButton(onClick = { clear = true }, enabled = enabled) { Text("Убрать изображение") }
        }
        if (enabled) AdminUploadButton(api, uploadPath, "Выбрать изображение", arrayOf("image/*"), onBusyChanged = onBusyChanged, onUploaded = { uploaded -> onChange(uploaded.getString("photo_path")) })
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Убрать изображение?") }, text = { Text("Изменение будет применено после сохранения формы.") }, confirmButton = { TextButton(onClick = { onChange(""); clear = false }) { Text("Убрать") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Отмена") } })
}

@Composable
private fun AdminEquipmentEditor(api: ApiClient, initial: JSONObject, onBack: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(initial.optInt("id")) { mutableStateOf(initial.toString()) }
    var busy by remember { mutableStateOf(false) }
    var uploads by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val data = JSONObject(draft)
    fun set(key: String, value: Any) { draft = JSONObject(draft).put(key, value).toString() }
    AcEditorPage(if (initial.optInt("id") > 0) "Редактор оборудования" else "Новое оборудование", onBack = onBack) {
        AcField("Название", acText(data, "title"), !busy) { set("title", it) }
        AcField("Название категории", acText(data, "category_name"), !busy) { set("category_name", it); if (initial.optInt("id") == 0 && acText(data, "category_slug").isBlank()) set("category_slug", acSlug(it)) }
        AcField("Код категории (латиница)", acText(data, "category_slug"), !busy) { set("category_slug", it) }
        AcField("Описание", acText(data, "description"), !busy, multiline = true) { set("description", it) }
        AcField("Ссылка покупки", acText(data, "buy_link"), !busy) { set("buy_link", it) }
        AcField("Текст кнопки", acText(data, "buy_label").ifBlank { "Купить" }, !busy) { set("buy_label", it) }
        AcField("Порядок показа", acText(data, "sort_order"), !busy, numeric = true) { set("sort_order", it) }
        AcToggle("Показывать пользователям", acBool(data, "is_active", true), !busy) { set("is_active", it) }
        AcPhoto(api, "Фото оборудования", "/api/admin/equipment/photo", acText(data, "photo_path"), !busy, { uploads = (uploads + if (it) 1 else -1).coerceAtLeast(0) }) { set("photo_path", it) }
        AcField("ID изображения Telegram (если используется)", acText(data, "photo_id"), !busy) { set("photo_id", it) }
        if (error.isNotBlank()) ErrorMessage(error)
        Button(enabled = !busy && uploads == 0, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                busy = true; error = ""
                try {
                    val payload = JSONObject(draft)
                    listOf("title", "category_name", "category_slug", "description").forEach { require(payload.optString(it).isNotBlank()) { "Заполните название, категорию и описание." } }
                    payload.put("sort_order", payload.optString("sort_order").toIntOrNull() ?: 100).put("is_active", acBool(payload, "is_active", true)).put("category_slug", acSlug(payload.getString("category_slug")))
                    if (initial.optInt("id") > 0) api.patch("/api/admin/equipment/${initial.getInt("id")}", payload) else api.post("/api/admin/equipment", payload)
                    onSaved()
                } catch (e: Exception) { error = acError(e) } finally { busy = false }
            }
        }) { Text(if (busy) "Сохранение…" else "Сохранить оборудование") }
    }
}

@Composable
private fun AdminArticleEditor(api: ApiClient, initial: JSONObject, onBack: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(initial.optInt("id")) { mutableStateOf(initial.toString()) }
    var busy by remember { mutableStateOf(false) }
    var uploads by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    val data = JSONObject(draft)
    fun set(key: String, value: Any) { draft = JSONObject(draft).put(key, value).toString() }
    AcEditorPage(if (initial.optInt("id") > 0) "Редактор статьи" else "Новая статья", onBack = onBack) {
        AcField("Заголовок", acText(data, "title"), !busy) { set("title", it) }
        AcField("Код в адресе (можно оставить пустым)", acText(data, "slug"), !busy) { set("slug", it) }
        AcField("Краткое описание", acText(data, "excerpt"), !busy, multiline = true) { set("excerpt", it) }
        AcPhoto(api, "Обложка статьи", "/api/admin/articles/image", acText(data, "cover_image_path"), !busy, { uploads = (uploads + if (it) 1 else -1).coerceAtLeast(0) }) { set("cover_image_path", it) }
        AcPhoto(api, "Изображение карточки", "/api/admin/articles/image", acText(data, "card_image_path"), !busy, { uploads = (uploads + if (it) 1 else -1).coerceAtLeast(0) }) { set("card_image_path", it) }
        Text("Текст статьи", style = MaterialTheme.typography.titleLarge)
        AcArticleTextEditor(api, acText(data, "content_html"), !busy, { uploads = (uploads + if (it) 1 else -1).coerceAtLeast(0) }) { set("content_html", it) }
        AcToggle("Доступ только PREMIUM", acBool(data, "is_premium"), !busy) { set("is_premium", it) }
        AcToggle("Опубликована", acBool(data, "is_published"), !busy) { set("is_published", it) }
        Text("Первая публикация отправляет пользователям уведомление о новой статье.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { preview = !preview }) { Text(if (preview) "Скрыть предпросмотр" else "Предпросмотр") }
        if (preview) SectionCard(acText(data, "title")) {
            Text(plainText(acText(data, "content_html")))
            Regex("<img\\b[^>]*src=[\"']([^\"']+)", RegexOption.IGNORE_CASE).findAll(acText(data, "content_html")).forEach { match -> NetworkImage(acUrl(api, match.groupValues[1]), "Иллюстрация статьи", Modifier.fillMaxWidth().height(180.dp)) }
        }
        if (error.isNotBlank()) ErrorMessage(error)
        Button(enabled = !busy && uploads == 0, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                busy = true; error = ""
                try {
                    val payload = JSONObject(draft)
                    require(payload.optString("title").trim().length >= 3) { "Заголовок должен содержать минимум 3 символа." }
                    require(plainText(payload.optString("content_html")).trim().length >= 3) { "Добавьте текст статьи." }
                    payload.put("is_premium", acBool(payload, "is_premium")).put("is_published", acBool(payload, "is_published"))
                    if (initial.optInt("id") > 0) api.put("/api/admin/articles/${initial.getInt("id")}", payload) else api.post("/api/admin/articles", payload)
                    onSaved()
                } catch (e: Exception) { error = acError(e) } finally { busy = false }
            }
        }) { Text(if (busy) "Сохранение…" else "Сохранить статью") }
    }
}

private data class AcHeadingSpan(val level: Int) : ParagraphStyle

private fun acStyledArticle(html: String): Spanned {
    val result = android.text.SpannableStringBuilder(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT, Html.ImageGetter { ColorDrawable(0xFFCFD8CE.toInt()).apply { setBounds(0, 0, 160, 100) } }, null))
    var offset = 0
    Regex("<h([234])\\b[^>]*>(.*?)</h\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).forEach { heading ->
        val label = Html.fromHtml(heading.groupValues[2], Html.FROM_HTML_MODE_COMPACT).toString().trim()
        val start = result.toString().indexOf(label, offset)
        if (label.isNotEmpty() && start >= 0) { result.setSpan(AcHeadingSpan(heading.groupValues[1].toInt()), start, start + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); offset = start + label.length }
    }
    return result
}

/** Serializes supported native spans to the server's allowed tags, including headings and images. */
private fun acArticleHtml(text: Spanned): String {
    fun inline(start: Int, end: Int): String = buildString {
        var position = start
        while (position < end) {
            val next = text.nextSpanTransition(position, end, Any::class.java).coerceAtLeast(position + 1)
            val spans = text.getSpans(position, position + 1, Any::class.java)
            val picture = spans.filterIsInstance<ImageSpan>().firstOrNull()
            if (picture != null) {
                append("<img src=\"").append(Html.escapeHtml(picture.source.orEmpty())).append("\">")
            } else {
                val styles = spans.filterIsInstance<StyleSpan>().fold(0) { mask, item -> mask or item.style }
                val tags = buildList<Pair<String, String>> {
                    spans.filterIsInstance<URLSpan>().firstOrNull()?.let { add("<a href=\"${Html.escapeHtml(it.url)}\">" to "</a>") }
                    if (styles and Typeface.BOLD != 0) add("<strong>" to "</strong>")
                    if (styles and Typeface.ITALIC != 0) add("<em>" to "</em>")
                    if (spans.any { it is UnderlineSpan }) add("<u>" to "</u>")
                    if (spans.any { it is StrikethroughSpan }) add("<s>" to "</s>")
                }
                tags.forEach { append(it.first) }
                append(Html.escapeHtml(text.subSequence(position, next).toString()))
                tags.asReversed().forEach { append(it.second) }
            }
            position = next
        }
    }
    return buildString {
        var start = 0
        var listOpen = false
        while (start < text.length) {
            val end = text.toString().indexOf('\n', start).let { if (it < 0) text.length else it }
            val styles = text.getSpans(start, if (end > start) start + 1 else start, Any::class.java)
            val bullet = styles.any { it is BulletSpan }
            if (bullet && !listOpen) { append("<ul>"); listOpen = true }
            if (!bullet && listOpen) { append("</ul>"); listOpen = false }
            val heading = styles.filterIsInstance<AcHeadingSpan>().lastOrNull()?.level
            val tag = when { bullet -> "li"; heading != null -> "h${heading.coerceIn(2, 4)}"; else -> "p" }
            val quoted = styles.any { it is QuoteSpan }
            if (quoted) append("<blockquote>")
            append('<').append(tag)
            styles.filterIsInstance<AlignmentSpan>().firstOrNull()?.let { span ->
                val align = when (span.alignment) { android.text.Layout.Alignment.ALIGN_CENTER -> "center"; android.text.Layout.Alignment.ALIGN_OPPOSITE -> "right"; else -> "left" }
                append(" style=\"text-align: ").append(align).append("\"")
            }
            append('>').append(if (start == end) "<br>" else inline(start, end)).append("</").append(tag).append('>')
            if (quoted) append("</blockquote>")
            start = end + 1
        }
        if (listOpen) append("</ul>")
    }
}

/** Keeps existing HTML unchanged until the administrator edits the native styled document. */
@Composable
private fun AcArticleTextEditor(api: ApiClient, html: String, enabled: Boolean, onBusyChanged: (Boolean) -> Unit, onChange: (String) -> Unit) {
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    var editor by remember { mutableStateOf<EditText?>(null) }
    val change by rememberUpdatedState(onChange)
    var lastWritten by remember { mutableStateOf(html) }
    var internal by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    fun output() { editor?.let { view -> lastWritten = acArticleHtml(view.text); change(lastWritten) } }
    fun applySpan(span: Any) {
        val view = editor ?: return
        val start = minOf(view.selectionStart, view.selectionEnd).coerceAtLeast(0)
        val end = maxOf(view.selectionStart, view.selectionEnd).coerceAtLeast(start)
        if (start < end) { view.text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); output() }
    }
    Text("Выделите фрагмент для форматирования. Изображения в редакторе обозначены серыми блоками; реальные фотографии видны в предпросмотре.", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(enabled = enabled, onClick = { applySpan(StyleSpan(Typeface.BOLD)) }) { Text("Жирный") }
        TextButton(enabled = enabled, onClick = { applySpan(StyleSpan(Typeface.ITALIC)) }) { Text("Курсив") }
        TextButton(enabled = enabled, onClick = { applySpan(UnderlineSpan()) }) { Text("Подчеркнуть") }
        TextButton(enabled = enabled, onClick = {
            val view = editor
            if (view != null) {
                val start = minOf(view.selectionStart, view.selectionEnd).coerceAtLeast(0)
                val end = maxOf(view.selectionStart, view.selectionEnd).coerceAtLeast(start)
                if (start < end) { applySpan(AcHeadingSpan(2)); applySpan(RelativeSizeSpan(1.5f)); applySpan(StyleSpan(Typeface.BOLD)) }
            }
        }) { Text("Заголовок") }
        TextButton(enabled = enabled, onClick = {
            val view = editor
            if (view != null) {
                val text = view.text
                val position = view.selectionStart.coerceAtLeast(0)
                val start = text.toString().lastIndexOf('\n', (position - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                val end = text.toString().indexOf('\n', view.selectionEnd.coerceAtLeast(position)).let { if (it < 0) text.length else it + 1 }
                if (start < end) { text.setSpan(BulletSpan(16), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); output() }
            }
        }) { Text("Список") }
        TextButton(enabled = enabled, onClick = { linkDialog = true }) { Text("Ссылка") }
        TextButton(enabled = enabled, onClick = {
            val view = editor
            if (view != null) {
                val start = minOf(view.selectionStart, view.selectionEnd).coerceAtLeast(0)
                val end = maxOf(view.selectionStart, view.selectionEnd).coerceAtLeast(start)
                view.text.getSpans(start, end, Any::class.java).filter { it is StyleSpan || it is UnderlineSpan || it is BulletSpan || it is URLSpan || it is AcHeadingSpan || it is RelativeSizeSpan }.forEach { view.text.removeSpan(it) }
                output()
            }
        }) { Text("Без оформления") }
    }
    AndroidView(factory = { nativeContext ->
        EditText(nativeContext).apply {
            minLines = 10; textSize = 16f; gravity = android.view.Gravity.TOP
            typeface = ResourcesCompat.getFont(nativeContext, R.font.manrope_regular)
            includeFontPadding = false
            setLineSpacing(0f, 1.65f)
            val density = resources.displayMetrics.density
            val inset = (16 * density).toInt()
            setPadding(inset, inset, inset, inset)
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 12 * density
                setStroke(density.toInt().coerceAtLeast(1), SiteColors.Border.toArgb())
            }
            backgroundTintList = null
            setSingleLine(false); setTextColor(foreground)
            setText(acStyledArticle(html))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { if (!internal) { lastWritten = acArticleHtml(text); change(lastWritten) } }
            })
            editor = this
        }
    }, update = { view ->
        view.isEnabled = enabled; view.setTextColor(foreground)
        if (html != lastWritten) {
            internal = true
            view.setText(acStyledArticle(html))
            lastWritten = html; internal = false
        }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp))
    if (enabled) AdminUploadButton(api, "/api/admin/articles/image", "Вставить изображение в текст", arrayOf("image/*"), onBusyChanged = onBusyChanged, onUploaded = { result ->
        val source = result.optString("photo_url").ifBlank { "/data/${result.optString("photo_path")}" }
        val view = editor
        if (view != null) {
            val picture = SpannableString("\n\uFFFC\n")
            val drawable = ColorDrawable(0xFFCFD8CE.toInt()).apply { setBounds(0, 0, 160, 100) }
            picture.setSpan(ImageSpan(drawable, source), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            view.text.insert(view.selectionStart.coerceIn(0, view.text.length), picture)
            output()
        }
    })
    if (linkDialog) AlertDialog(onDismissRequest = { linkDialog = false }, title = { Text("Вставить ссылку") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AcField("Текст ссылки", linkText) { linkText = it }
        AcField("Адрес https://…", linkUrl) { linkUrl = it }
    } }, confirmButton = { TextButton(enabled = linkText.isNotBlank() && (linkUrl.startsWith("https://") || linkUrl.startsWith("http://") || linkUrl.startsWith("/")), onClick = {
        editor?.let { view ->
            val link = SpannableString(linkText).apply { setSpan(URLSpan(linkUrl.trim()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            view.text.insert(view.selectionStart.coerceIn(0, view.text.length), link); output()
        }
        linkDialog = false; linkText = ""; linkUrl = ""
    }) { Text("Вставить") } }, dismissButton = { TextButton(onClick = { linkDialog = false }) { Text("Отмена") } })
}

@Composable
private fun AdminArticleModeration(api: ApiClient, id: Int, onBack: () -> Unit) {
    var pending by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    RemotePage("Комментарии статьи", api, "/api/articles/$id", onBack) { data, reload ->
        Text(data.optJSONObject("article")?.optString("title").orEmpty(), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = reload, enabled = !busy) { Text("Обновить") }
        if (error.isNotBlank()) ErrorMessage(error)
        if (data.items("comments").isEmpty()) Text("Комментариев пока нет.")
        data.items("comments").forEach { comment -> AdminListEntry("content-comment-${comment.optInt("id")}", comment.optString("nickname"),
            acText(comment, "created_at") + if (comment.optBoolean("is_deleted")) " · Комментарий удалён" else "") {
            Text(if (comment.optBoolean("is_deleted")) "Комментарий удалён" else comment.optString("text"))
            if (!comment.optBoolean("is_deleted")) TextButton(enabled = !busy, onClick = { pending = comment }) { Text("Удалить комментарий") }
        } }
        pending?.let { comment -> AlertDialog(onDismissRequest = { if (!busy) pending = null }, title = { Text("Удалить комментарий?") }, text = { Text(comment.optString("text")) }, confirmButton = {
            TextButton(enabled = !busy, onClick = { scope.launch { busy = true; error = ""; try { api.delete("/api/articles/$id/comments/${comment.getInt("id")}"); pending = null; reload() } catch (e: Exception) { error = acError(e); pending = null } finally { busy = false } } }) { Text("Удалить") }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("Отмена") } }) }
    }
}

private fun acIngredient(row: JSONObject): JSONObject = JSONObject().put("full_name", acText(row, "full_name"))
    .put("quantity", if (row.has("quantity")) acText(row, "quantity") else acText(row, "value"))
    .put("short_label", acText(row, "short_label").ifBlank { acText(row, "short_key") }.ifBlank { acText(row, "full_name") })
    .put("link", acText(row, "link")).put("product_id", row.optLong("product_id"))
    .put("new_price", acText(row, "new_price"))
    .put("option_key", acText(row, "option_key"))
    .put("options", acArray(row.items("options").map(::acIngredient)))

@Composable
private fun AdminRecipeEditor(api: ApiClient, selected: JSONObject, bootstrap: JSONObject, onBack: () -> Unit, onSaved: () -> Unit) {
    val existingSlug = selected.optString("slug")
    val isNew = existingSlug.isBlank()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(existingSlug) { mutableStateOf(if (isNew) JSONObject(selected.toString()).put("ingredients", JSONArray()).toString() else "") }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var uploads by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf("") }
    var ingredientIndex by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(existingSlug, reload) {
        if (!isNew && draft.isBlank()) {
            loading = true
            try {
                val recipe = api.get("/api/recipes/${acPath(existingSlug)}")
                val rows = acArray(recipe.items("ingredients").map(::acIngredient))
                draft = JSONObject(recipe.toString()).put("photo_path", acText(recipe, "recipe_photo_path")).put("photo_id", acText(recipe, "recipe_photo_id")).put("ingredients", rows).toString()
            } catch (e: Exception) { error = acError(e) } finally { loading = false }
        }
    }
    val data = if (draft.isBlank()) JSONObject() else JSONObject(draft)
    fun set(key: String, value: Any) { draft = JSONObject(draft.ifBlank { "{}" }).put(key, value).toString() }
    fun save() {
        scope.launch {
            busy = true; error = ""
            try {
                val payload = JSONObject(draft)
                val slug = if (isNew) acSlug(payload.optString("slug").ifBlank { payload.optString("display_name") }) else existingSlug
                require(slug.isNotBlank() && payload.optString("display_name").isNotBlank() && payload.optString("category_slug").isNotBlank()) { "Укажите название и категорию рецепта." }
                val ingredients = payload.items("ingredients")
                val names = HashSet<String>()
                val prices = JSONArray()
                ingredients.forEachIndexed { index, item ->
                    require(item.optString("full_name").isNotBlank() && names.add(item.optString("full_name").trim())) { "У ингредиента ${index + 1} пустое или повторяющееся название." }
                    val options = item.items("options")
                    require(options.size != 1) { "Для «${item.optString("full_name") }» нужно минимум два варианта либо ни одного." }
                    val rows = if (options.isEmpty()) listOf(item) else options
                    rows.forEach { option ->
                        require(option.optString("full_name").isNotBlank() && option.optString("quantity").isNotBlank()) { "Заполните название и количество каждого ингредиента и варианта." }
                        if (option.optString("short_label").isBlank()) option.put("short_label", option.getString("full_name"))
                        val price = option.optString("new_price")
                        if (price.isNotBlank()) {
                            val number = price.replace(',', '.').toDoubleOrNull()
                            require(number != null && number.isFinite() && number >= 0) { "Цена ингредиента должна быть неотрицательным числом." }
                            prices.put(JSONObject().put("price_key", option.getString("short_label")).put("price", number))
                        }
                    }
                }
                payload.put("create_recipe", isNew).put("replace_ingredients", true).put("ingredient_prices", prices)
                api.put("/api/admin/recipes/${acPath(slug)}", payload)
                onSaved()
            } catch (e: Exception) { error = acError(e) } finally { busy = false }
        }
    }
    if (ingredientIndex >= 0 && draft.isNotBlank()) {
        val ingredients = data.items("ingredients")
        val initial = ingredients.getOrNull(ingredientIndex) ?: JSONObject()
        BackHandler { ingredientIndex = -1 }
        AdminIngredientEditor(initial, bootstrap.items("store_products"), { ingredientIndex = -1 }) { value ->
            val updated = JSONObject(draft).items("ingredients").toMutableList()
            if (ingredientIndex < updated.size) updated[ingredientIndex] = value else updated.add(value)
            set("ingredients", acArray(updated)); ingredientIndex = -1
        }
        return
    }
    AcEditorPage(if (isNew) "Новый рецепт" else "Редактор рецепта", onBack = onBack) {
        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) ErrorMessage(error)
        if (draft.isBlank()) { OutlinedButton(enabled = !loading, onClick = { reload++ }) { Text("Повторить загрузку") }; return@AcEditorPage }
        AcField("Название рецепта", acText(data, "display_name"), !busy) { set("display_name", it) }
        if (isNew) {
            AcField("Код в адресе (можно оставить пустым)", acText(data, "slug"), !busy) { set("slug", it) }
            AcChoice("Категория", acText(data, "category_slug"), bootstrap.items("categories").map { it.optString("slug") to it.optString("title") }, !busy) { set("category_slug", it) }
        } else Text("Категория: ${bootstrap.items("categories").firstOrNull { it.optString("slug") == data.optString("category_slug") }?.optString("title") ?: data.optString("category_slug")}")
        AcField("Технология приготовления", acText(data, "recipe_text"), !busy, multiline = true) { set("recipe_text", it) }
        if (acText(data, "recipe_text").isNotBlank()) TextButton(enabled = !busy, onClick = { pending = "text" }) { Text("Очистить текст") }
        AcPhoto(api, "Фото рецепта", "/api/admin/recipes/photo", acText(data, "photo_path"), !busy, { uploads = (uploads + if (it) 1 else -1).coerceAtLeast(0) }) { set("photo_path", it); if (it.isBlank()) set("photo_id", "") }
        AcField("ID изображения Telegram (если используется)", acText(data, "photo_id"), !busy) { set("photo_id", it) }
        Text("Ингредиенты на 1 кг", style = MaterialTheme.typography.titleLarge)
        val ingredients = data.items("ingredients")
        if (ingredients.isEmpty()) Text("Состав ещё не добавлен.")
        ingredients.forEachIndexed { index, item ->
            SectionCard(item.optString("full_name")) {
                if (item.items("options").isNotEmpty()) Text("Варианты: " + item.items("options").joinToString { it.optString("full_name") }) else Text("Количество: ${item.optString("quantity")}")
                if (item.optLong("product_id") > 0) Text("Товар: ${bootstrap.items("store_products").firstOrNull { it.optLong("id") == item.optLong("product_id") }?.optString("title") ?: "#${item.optLong("product_id")}"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = { ingredientIndex = index }) { Text("Изменить") }
                    TextButton(enabled = !busy, onClick = { pending = "ingredient:$index" }) { Text("Удалить") }
                }
            }
        }
        OutlinedButton(enabled = !busy, onClick = { ingredientIndex = ingredients.size }) { Text("Добавить ингредиент") }
        Button(enabled = !busy && uploads == 0, modifier = Modifier.fillMaxWidth(), onClick = { save() }) { Text(if (busy) "Сохранение…" else "Сохранить рецепт") }
    }
    if (pending.isNotBlank()) AlertDialog(onDismissRequest = { pending = "" }, title = { Text(if (pending == "text") "Очистить текст рецепта?" else "Удалить ингредиент?") }, text = { Text("Изменение применится после сохранения рецепта.") }, confirmButton = {
        TextButton(onClick = {
            if (pending == "text") set("recipe_text", "")
            else { val index = pending.substringAfter(':').toInt(); val items = JSONObject(draft).items("ingredients").toMutableList(); if (index in items.indices) items.removeAt(index); set("ingredients", acArray(items)) }
            pending = ""
        }) { Text("Удалить") }
    }, dismissButton = { TextButton(onClick = { pending = "" }) { Text("Отмена") } })
}

@Composable
private fun AdminIngredientEditor(initial: JSONObject, products: List<JSONObject>, onBack: () -> Unit, onSave: (JSONObject) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(acIngredient(initial).toString()) }
    var error by remember { mutableStateOf("") }
    var removeOption by remember { mutableIntStateOf(-1) }
    val data = JSONObject(draft)
    fun set(key: String, value: Any) { draft = JSONObject(draft).put(key, value).toString() }
    fun setOption(index: Int, key: String, value: Any) { val current = JSONObject(draft); current.getJSONArray("options").getJSONObject(index).put(key, value); draft = current.toString() }
    val productOptions = listOf("0" to "Не привязывать к магазину") + products.map { it.optLong("id").toString() to (it.optString("title") + " · " + money(it.optLong("price_kopecks"))) }
    AcEditorPage("Ингредиент", "Количество указывается на 1 кг рецепта", onBack) {
        AcField("Название ингредиента или группы вариантов", acText(data, "full_name")) { set("full_name", it) }
        AcField("Количество или текст (например, 20 или «по вкусу»)", acText(data, "quantity")) { set("quantity", it) }
        AcField("Ключ цены", acText(data, "short_label")) { set("short_label", it) }
        Text("Ключ цены должен совпадать с используемым в калькуляторе, например «Указать цену за 1кг свинины». Для массы количество в граммах, для жидкости — мл, для оболочки — метры.", style = MaterialTheme.typography.bodySmall)
        AcField("Новая базовая цена, ₽ (необязательно)", acText(data, "new_price"), numeric = true) { set("new_price", it) }
        AcField("Ссылка на ингредиент", acText(data, "link")) { set("link", it) }
        AcChoice("Товар для корзины", data.optLong("product_id").toString(), productOptions) { set("product_id", it.toLong()) }
        val options = data.items("options")
        options.forEachIndexed { index, option ->
            SectionCard("Вариант ${index + 1}") {
                AcField("Название варианта", acText(option, "full_name")) { setOption(index, "full_name", it) }
                AcField("Количество варианта на 1 кг", acText(option, "quantity")) { setOption(index, "quantity", it) }
                AcField("Ключ цены варианта", acText(option, "short_label")) { setOption(index, "short_label", it) }
                AcField("Новая цена варианта, ₽ (необязательно)", acText(option, "new_price"), numeric = true) { setOption(index, "new_price", it) }
                AcField("Ссылка варианта", acText(option, "link")) { setOption(index, "link", it) }
                AcChoice("Товар варианта", option.optLong("product_id").toString(), productOptions) { setOption(index, "product_id", it.toLong()) }
                TextButton(onClick = { removeOption = index }) { Text("Удалить вариант") }
            }
        }
        OutlinedButton(onClick = {
            val items = JSONObject(draft).items("options").toMutableList()
            if (items.isEmpty()) items.add(acIngredient(JSONObject(draft)).put("option_key", UUID.randomUUID().toString()).put("options", JSONArray()))
            items.add(acIngredient(JSONObject()).put("option_key", UUID.randomUUID().toString()))
            set("options", acArray(items))
        }) { Text(if (options.isEmpty()) "Добавить выбор из двух вариантов" else "Добавить вариант") }
        if (error.isNotBlank()) ErrorMessage(error)
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            try {
                val payload = JSONObject(draft)
                require(payload.optString("full_name").isNotBlank()) { "Укажите название ингредиента." }
                val variants = payload.items("options")
                require(variants.size != 1) { "Добавьте второй вариант или удалите единственный." }
                (if (variants.isEmpty()) listOf(payload) else variants).forEach { item ->
                    require(item.optString("full_name").isNotBlank() && item.optString("quantity").isNotBlank()) { "Заполните названия и количество." }
                    if (item.optString("short_label").isBlank()) item.put("short_label", item.optString("full_name"))
                }
                onSave(payload)
            } catch (e: Exception) { error = acError(e) }
        }) { Text("Применить к рецепту") }
    }
    if (removeOption >= 0) AlertDialog(onDismissRequest = { removeOption = -1 }, title = { Text("Удалить вариант?") }, text = { Text("Для выбора ингредиента должно остаться не меньше двух вариантов. Можно удалить все варианты и оставить обычный ингредиент.") }, confirmButton = { TextButton(onClick = { val items = JSONObject(draft).items("options").toMutableList(); items.removeAt(removeOption); set("options", acArray(items)); removeOption = -1 }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { removeOption = -1 }) { Text("Отмена") } })
}
