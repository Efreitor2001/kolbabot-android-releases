package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ChatStatus(val key: String, val userLabel: String, val adminLabel: String) {
    Open("open", "Обращение открыто", "Требует ответа"),
    Working("in_progress", "В работе", "В работе"),
    Answered("answered", "Ответ получен", "Отвечено");

    fun label(admin: Boolean) = if (admin) adminLabel else userLabel

    @Composable
    fun tint(): Color {
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        return when (this) {
            Open -> if (dark) Color(0xFFFFD581) else Color(0xFF87520B)
            Working -> if (dark) Color(0xFF9CDAFF) else Color(0xFF155A83)
            Answered -> if (dark) Color(0xFF83E7BC) else Color(0xFF146B53)
        }
    }
}

private fun JSONObject.chatStatus() = ChatStatus.entries.firstOrNull { it.key == optString("status") } ?: ChatStatus.Open
private fun chatQuery(value: String) = URLEncoder.encode(value.trim(), "UTF-8")

private val supportZone = ZoneId.of("Europe/Moscow")
private val supportClock = DateTimeFormatter.ofPattern("HH:mm")
private val supportDate = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
private val supportOldDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

private fun chatTime(value: String): ZonedDateTime? {
    val iso = value.trim().replaceFirst(' ', 'T')
    val offset = if (Regex("[+-]\\d{2}$").containsMatchIn(iso)) "$iso:00" else iso
    return runCatching { OffsetDateTime.parse(offset).atZoneSameInstant(supportZone) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso).atZone(supportZone) }.getOrNull()
}

private fun chatDay(date: LocalDate): String {
    val today = LocalDate.now(supportZone)
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(if (date.year == today.year) supportDate else supportOldDate)
    }
}

private suspend fun chatRequest(onError: (String) -> Unit, action: suspend () -> Unit) {
    try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onError(failure.message.orEmpty().ifBlank { "Не удалось загрузить чат." })
    }
}

@Composable
fun SupportChatScreen(api: ApiClient, admin: Boolean = false, onBack: () -> Unit) {
    val url = if (admin) "/api/admin/support/chats" else "/api/support/chats"
    var selected by rememberSaveable(api.userId, admin) { mutableLongStateOf(0) }
    var anchor by rememberSaveable(api.userId, admin) { mutableLongStateOf(0) }
    var creating by rememberSaveable(api.userId, admin) { mutableStateOf(false) }
    var filter by rememberSaveable(api.userId, admin) { mutableStateOf("") }
    var query by rememberSaveable(api.userId, admin) { mutableStateOf("") }
    var showSearch by rememberSaveable(api.userId, admin) { mutableStateOf(false) }
    val drafts = remember(api.userId, admin) { mutableStateMapOf<Long, String>() }
    var festive by remember(api) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(api, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                chatRequest({}) {
                    val appearance = api.get("/api/site/appearance")
                    if (appearance.has("new_year_enabled")) festive = appearance.getBoolean("new_year_enabled")
                }
                delay(60_000)
            }
        }
    }
    val inChat = selected > 0 || creating
    val closeChat = {
        selected = 0
        anchor = 0
        creating = false
    }
    BackHandler {
        when {
            inChat -> closeChat()
            showSearch -> { showSearch = false; query = "" }
            else -> onBack()
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        if (inChat) {
            key(selected, creating) {
                SupportThread(
                    api = api, url = url, admin = admin, ticket = selected, initialAnchor = anchor, festive = festive,
                    draft = drafts[selected].orEmpty(), setDraft = { drafts[selected] = it },
                    onAnchor = { anchor = it }, onBack = closeChat,
                    onCreated = { id -> selected = id; anchor = 0; creating = false },
                )
            }
        } else {
            SupportChatList(
                api, url, admin, filter, { filter = it }, query, { query = it }, showSearch,
                { showSearch = !showSearch; if (!showSearch) query = "" }, onBack,
                onNew = { creating = true },
                onOpen = { chat -> selected = chat.optLong("id"); anchor = chat.optLong("match_message_id") },
            )
        }
    }
}

@Composable
private fun SupportChatList(
    api: ApiClient, url: String, admin: Boolean, filter: String, onFilter: (String) -> Unit,
    query: String, onQuery: (String) -> Unit, searching: Boolean, onToggleSearch: () -> Unit,
    onBack: () -> Unit, onNew: () -> Unit, onOpen: (JSONObject) -> Unit,
) {
    var chats by remember(filter, query) { mutableStateOf(emptyList<JSONObject>()) }
    var hasMore by remember(filter, query) { mutableStateOf(false) }
    var busy by remember(filter, query) { mutableStateOf(true) }
    var error by remember(filter, query) { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    suspend fun load(append: Boolean = false, silent: Boolean = false) {
        if (!silent) { busy = true; error = "" }
        try {
            chatRequest({ if (!silent) error = it }) {
                val data = api.get("$url?status=$filter&q=${chatQuery(query)}&offset=${if (append) chats.size else 0}")
                val page = data.items("items")
                chats = if (append) (chats + page).distinctBy { it.optLong("id") } else page
                hasMore = data.optBoolean("has_more")
            }
        } finally {
            if (!silent) busy = false
        }
    }
    LaunchedEffect(api, filter, query, revision, lifecycle) {
        if (query.isNotBlank()) delay(250)
        load()
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(15_000)
                if (!busy && chats.size <= 40 && query.isBlank()) load(silent = true)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatIconButton("Назад", "back", onBack)
            Text(
                if (admin) "Обращения поддержки" else "Поддержка",
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
            if (admin) ChatIconButton("Поиск по чатам", "search", onToggleSearch)
            else ChatIconButton("Новое обращение", "add", onNew)
        }
        if (admin) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(ChatStatus.Open, ChatStatus.Working).forEach { status ->
                        SupportStatusFilter(status, filter, onFilter, Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(filter.isBlank(), { onFilter("") }, { Text("Все", style = MaterialTheme.typography.labelSmall) })
                    SupportStatusFilter(ChatStatus.Answered, filter, onFilter)
                }
            }
            if (searching) ChatSearchField(query, onQuery, "ID, имя или сообщение", onToggleSearch)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (busy && chats.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) {
            ErrorMessage(error)
            TextButton({ revision++ }) { Text("Повторить") }
        }
        if (!busy && error.isBlank() && chats.isEmpty()) {
            Text(
                if (query.isNotBlank() || filter.isNotBlank()) "Чаты не найдены." else "Чатов пока нет.",
                Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(chats, key = { it.optLong("id") }) { chat ->
                Row(
                    Modifier.fillMaxWidth().testTag("support-chat-${chat.optLong("id")}")
                        .clickable { onOpen(chat) }.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatUserId(chat.optLong("user_id"))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                chat.optString("title"), Modifier.weight(1f), maxLines = 1,
                                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold,
                            )
                            val date = chatTime(chat.optString("updated_at"))
                            if (date != null) Text(
                                if (date.toLocalDate() == LocalDate.now(supportZone)) date.format(supportClock) else chatDay(date.toLocalDate()),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            chat.optString("match_text").takeUnless { it.isBlank() || it == "null" } ?: chat.optString("preview"),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(3.dp))
                        ChatStatusBadge(chat.chatStatus(), admin)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (hasMore) item {
                TextButton(onClick = { scope.launch { load(append = true) } }, enabled = !busy) { Text("Ещё чаты") }
            }
        }
    }
}

@Composable
private fun SupportStatusFilter(status: ChatStatus, filter: String, onFilter: (String) -> Unit, modifier: Modifier = Modifier) {
    val color = status.tint()
    FilterChip(
        filter == status.key,
        { onFilter(if (filter == status.key) "" else status.key) },
        { Text(status.adminLabel, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = color.copy(alpha = 0.10f), labelColor = color,
            selectedContainerColor = color.copy(alpha = 0.22f), selectedLabelColor = color,
        ),
    )
}

@Composable
private fun SupportThread(
    api: ApiClient, url: String, admin: Boolean, ticket: Long, initialAnchor: Long, festive: Boolean,
    draft: String, setDraft: (String) -> Unit, onAnchor: (Long) -> Unit, onBack: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val listState = rememberLazyListState()
    var chat by remember { mutableStateOf<JSONObject?>(null) }
    var messages by remember { mutableStateOf(emptyList<JSONObject>()) }
    var older by remember { mutableStateOf(false) }
    var newer by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(ticket > 0) }
    var sending by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var innerSearch by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val isNew = ticket == 0L
    val searchActive = innerSearch && query.isNotBlank()
    val path = "$url/$ticket"
    BackHandler(enabled = innerSearch) {
        innerSearch = false
        query = ""
    }

    suspend fun load(before: Long = 0, after: Long = 0, around: Long = 0, silent: Boolean = false) {
        if (isNew) return
        if (!silent) { loading = true; error = "" }
        try {
            chatRequest({ if (!silent) error = it }) {
                val data = api.get("$path?before=$before&after=$after&around=$around&q=${chatQuery(if (searchActive) query else "")}")
                chat = data.optJSONObject("chat")
                val page = data.items("items")
                val follow = messages.isEmpty() || (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= messages.lastIndex - 1
                if (after != 0L && page.isEmpty() && silent) return@chatRequest
                messages = when {
                    before > 0 -> page + messages
                    after > 0 -> messages + page
                    else -> page
                }.distinctBy { it.optLong("id") }
                if (after == 0L) older = data.optBoolean("has_older")
                if (before == 0L) newer = data.optBoolean("has_newer")
                if (messages.isNotEmpty()) {
                    val index = when {
                        around > 0 -> messages.indexOfFirst { it.optLong("id") == around }.coerceAtLeast(0)
                        searchActive && before == 0L -> 0
                        before > 0 -> (listState.firstVisibleItemIndex + page.size + if (older) 0 else -1).coerceAtLeast(0)
                        after == 0L || follow -> messages.lastIndex
                        else -> -1
                    }
                    if (index >= 0) listState.requestScrollToItem(index + if (older) 1 else 0)
                }
            }
        } finally {
            if (!silent) loading = false
        }
    }
    LaunchedEffect(ticket, query, initialAnchor, revision, lifecycle) {
        if (isNew) return@LaunchedEffect
        if (searchActive) delay(250)
        load(around = if (searchActive) 0 else initialAnchor)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(15_000)
                if (!loading && !sending && !searchActive && !newer) {
                    load(after = messages.lastOrNull()?.optLong("id") ?: 0L, silent = true)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatIconButton("К списку чатов", "back", onBack)
            chat?.let {
                ChatUserId(it.optLong("user_id"), compact = true)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    chat?.optString("title") ?: if (isNew) "Новое обращение" else "Поддержка",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold,
                )
                chat?.let { ChatStatusBadge(it.chatStatus(), admin) }
            }
            if (!isNew) Box {
                ChatIconButton("Меню чата", "menu", { menu = true })
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    if (admin) DropdownMenuItem({ Text("Поиск в чате") }, { menu = false; innerSearch = true })
                    DropdownMenuItem({ Text("Обновить чат") }, {
                        menu = false
                        query = ""
                        innerSearch = false
                        onAnchor(0)
                        revision++
                    })
                    if (admin) {
                        HorizontalDivider()
                        ChatStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                { ChatStatusBadge(status, true) },
                                {
                                    menu = false
                                    sending = true
                                    scope.launch {
                                        try {
                                            chatRequest({ error = it }) {
                                                val data = api.patch("$path/status", JSONObject().put("status", status.key))
                                                chat = chat?.let { JSONObject(it.toString()).put("status", data.optString("status")) }
                                            }
                                        } finally { sending = false }
                                    }
                                },
                                enabled = !sending && chat != null && status != chat?.chatStatus(),
                            )
                        }
                    }
                }
            }
        }
        if (innerSearch) ChatSearchField(query, { query = it }, "Найти сообщение") {
            innerSearch = false
            query = ""
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (loading && messages.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.Bottom),
        ) {
            if (older && messages.isNotEmpty()) item("older") {
                TextButton(
                    onClick = { scope.launch { load(before = messages.first().optLong("id")) } }, enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Более ранние сообщения") }
            }
            if (messages.isEmpty() && searchActive && !loading && error.isBlank()) item {
                Text("Совпадений нет.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(messages, key = { _, message -> message.optLong("id") }) { index, message ->
                val date = chatTime(message.optString("created_at"))
                val previous = messages.getOrNull(index - 1)?.let { chatTime(it.optString("created_at")) }
                val messageId = message.optLong("id")
                if (date != null && date.toLocalDate() != previous?.toLocalDate()) {
                    Box(Modifier.fillMaxWidth().padding(5.dp), contentAlignment = Alignment.Center) {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp)) {
                            Text(
                                chatDay(date.toLocalDate()), Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                ChatBubble(
                    message.optString("body"), date?.format(supportClock).orEmpty(),
                    message.optString("sender_role") == if (admin) "admin" else "user", festive, initialAnchor == messageId,
                    onClick = if (searchActive) { {
                        query = ""
                        innerSearch = false
                        onAnchor(messageId)
                    } } else null,
                )
            }
            if (newer && messages.isNotEmpty()) item("newer") {
                TextButton(
                    onClick = { scope.launch { load(after = messages.last().optLong("id")) } }, enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Более поздние сообщения") }
            }
        }
        if (error.isNotBlank()) {
            ErrorMessage(error)
            if (chat == null && !isNew) TextButton({ revision++ }) { Text("Повторить") }
        }
        if (!searchActive) {
            Row(
                Modifier.fillMaxWidth().imePadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    draft, { if (it.length <= 5000) setDraft(it) },
                    Modifier.weight(1f), label = { Text(if (isNew) "Опишите ваш вопрос" else "Сообщение") },
                    maxLines = 4, enabled = !sending,
                )
                ChatIconButton("Отправить", "send", {
                    sending = true
                    error = ""
                    scope.launch {
                        try {
                            chatRequest({ error = it }) {
                                val body = JSONObject().put("text", draft.trim())
                                val result = api.post(if (isNew) "/api/support/tickets" else "$path/messages", body)
                                setDraft("")
                                if (isNew) onCreated(result.optLong("ticket_id"))
                                else {
                                    onAnchor(0)
                                    revision++
                                }
                            }
                        } finally { sending = false }
                    }
                }, enabled = !sending && (isNew || chat != null) && draft.trim().length >= if (isNew) 5 else 1)
            }
        }
    }
}

@Composable
private fun ChatBubble(body: String, time: String, own: Boolean, festive: Boolean, selected: Boolean, onClick: (() -> Unit)?) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fill = when {
        festive && dark && own -> Color(0xFF302B50)
        festive && dark -> Color(0xFF1E3939)
        festive && own -> Color(0xFFEFF1FF)
        festive -> Color(0xFFE9F7F2)
        dark && own -> Color(0xFF28334E)
        dark -> Color(0xFF1E3B39)
        own -> Color(0xFFEDF0FF)
        else -> Color(0xFFEEF9F5)
    }
    val border = when {
        selected -> MaterialTheme.colorScheme.primary
        festive && dark && own -> Color(0xFF887BB7)
        festive && dark -> Color(0xFF598E80)
        festive && own -> Color(0xFFAAA9DB)
        festive -> Color(0xFF79B5A6)
        dark && own -> Color(0xFF6074AC)
        dark -> Color(0xFF4C8F82)
        own -> Color(0xFFA9B6ED)
        else -> Color(0xFF8CC5B8)
    }
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(5.dp, if (own) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (own) ChatClock(time)
        Surface(
            modifier = Modifier.widthIn(max = 360.dp).weight(1f, fill = false),
            shape = if (own) RoundedCornerShape(13.dp, 13.dp, 3.dp, 13.dp) else RoundedCornerShape(13.dp, 13.dp, 13.dp, 3.dp),
            color = fill, contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        ) { Text(body, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.bodyMedium) }
        if (!own) ChatClock(time)
    }
}

@Composable
private fun ChatClock(time: String) {
    if (time.isNotEmpty()) Text(time, Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChatStatusBadge(status: ChatStatus, admin: Boolean) {
    val color = status.tint()
    Surface(color = color.copy(alpha = 0.13f), shape = RoundedCornerShape(7.dp)) {
        Text(status.label(admin), Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ChatUserId(id: Long, compact: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.size(if (compact) 42.dp else 50.dp, if (compact) 37.dp else 46.dp), contentAlignment = Alignment.Center) {
            Text("ID $id", Modifier.padding(horizontal = 2.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun ChatSearchField(value: String, onValue: (String) -> Unit, label: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value, { if (it.length <= 160) onValue(it) }, Modifier.weight(1f), label = { Text(label) }, singleLine = true)
        ChatIconButton("Закрыть поиск", "close", onClose)
    }
}

@Composable
private fun ChatIconButton(label: String, icon: String, onClick: () -> Unit, enabled: Boolean = true) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    IconButton(onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        Canvas(Modifier.size(22.dp)) {
            val unit = size.width / 24
            fun point(x: Int, y: Int) = Offset(x * unit, y * unit)
            fun line(x1: Int, y1: Int, x2: Int, y2: Int) =
                drawLine(color, point(x1, y1), point(x2, y2), strokeWidth = 1.8f * unit, cap = StrokeCap.Round)
            when (icon) {
                "back" -> { line(4, 12, 20, 12); line(4, 12, 11, 5); line(4, 12, 11, 19) }
                "search" -> { drawCircle(color, 6 * unit, point(10, 10), style = Stroke(1.8f * unit)); line(15, 15, 21, 21) }
                "add" -> { line(5, 12, 19, 12); line(12, 5, 12, 19) }
                "close" -> { line(6, 6, 18, 18); line(6, 18, 18, 6) }
                "menu" -> for (y in listOf(5, 12, 19)) drawCircle(color, 1.65f * unit, point(12, y))
                "send" -> {
                    drawPath(Path().apply {
                        moveTo(3 * unit, 4 * unit); lineTo(22 * unit, 12 * unit); lineTo(3 * unit, 20 * unit)
                        lineTo(6 * unit, 12 * unit); close()
                    }, color, style = Stroke(1.7f * unit))
                    line(6, 12, 22, 12)
                }
            }
        }
    }
}
