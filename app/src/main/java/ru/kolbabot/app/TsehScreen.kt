package ru.kolbabot.app

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream

private data class TsehCommand(val action: String, val payload: String = "null")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TsehScreen(api: ApiClient, userId: Long, onStore: (Long) -> Unit, onSignIn: () -> Unit, onSupport: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var reload by remember { mutableIntStateOf(0) }
    var document by remember(api, userId) { mutableStateOf<TsehDocument?>(null) }
    var snapshot by remember(api, userId) { mutableStateOf<TsehSnapshot?>(null) }
    var error by remember(api, userId) { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var navigatorOffset by remember { mutableIntStateOf(0) }
    var editGeneration by remember { mutableIntStateOf(0) }
    var confirmDemo by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<TsehFile?>(null) }
    val commands = remember(api, userId, reload) { Channel<TsehCommand>(Channel.UNLIMITED) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file = pendingFile
        pendingFile = null
        val uri = result.data?.data
        if (uri != null && file != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Не удалось открыть файл для записи.")
                    output.use { it.write(file.bytes) }
                }
                Toast.makeText(context, "Файл сохранён", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Не удалось сохранить файл." }
        }
    }
    fun saveFile(file: TsehFile) {
        if (pendingFile != null) return
        pendingFile = file
        save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = file.mime.substringBefore(';')
            putExtra(Intent.EXTRA_TITLE, file.name)
        })
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= TSEH_PROJECT_LIMIT) { "Файл слишком большой." }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    } ?: error("Не удалось открыть проект.")
                }
                commands.send(TsehCommand("load", JSONObject.quote(text)))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (problem: Exception) { error = problem.message.orEmpty() }
        }
    }

    val tsehUpdates = liveRevision(api, "tseh", "orders", "catalog")
    var seenTsehUpdates by remember(api, userId) { mutableStateOf(tsehUpdates) }
    LaunchedEffect(tsehUpdates, document != null) {
        val active = document ?: return@LaunchedEffect
        if (seenTsehUpdates == tsehUpdates) return@LaunchedEffect
        seenTsehUpdates = tsehUpdates
        try {
            val next = loadTsehDocument(api)
            if (active.hasAccess != next.hasAccess) reload++ else document = next
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { /* Preserve the active calculator and its draft. */ }
    }
    LaunchedEffect(api, userId, reload) {
        snapshot = null
        document = null
        error = ""
        try {
            val loaded = loadTsehDocument(api)
            document = loaded
            if (!loaded.hasAccess) return@LaunchedEffect
            val (source, mime) = api.downloadFile("/tseh/calculator.js")
            require(mime.contains("javascript") && source.size <= 500_000) { "Не удалось загрузить формулы калькулятора." }
            TsehEngine.create(context, source.toString(Charsets.UTF_8)).use { engine ->
                snapshot = engine.command("init")
                for ((action, payload) in commands) {
                    try {
                        val previousStep = snapshot?.step
                        val updated = engine.command(action, payload)
                        snapshot = updated
                        if (action == "load" || action == "click" && JSONObject(payload).optString("id") == "demoBtn") editGeneration++
                        error = ""
                        if (action == "load") launch { scroll.scrollTo(0) }
                        else if (updated.step != previousStep) launch { scroll.scrollTo(navigatorOffset) }
                        val file = updated.export()
                        file?.let(::saveFile)
                        if (updated.toast.isNotBlank() && file == null) {
                            Toast.makeText(context, updated.toast, Toast.LENGTH_LONG).show()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = "Не удалось выполнить действие в калькуляторе."
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message.orEmpty().ifBlank { "Не удалось открыть «Цех на бумаге»." } }
    }
    DisposableEffect(commands) { onDispose { commands.close() } }

    fun download(path: String, name: String) {
        if (working) return
        scope.launch {
            working = true
            try {
                val (bytes, mime) = api.downloadFile(path)
                require(mime.startsWith("application/pdf")) { "Сервер не вернул PDF." }
                saveFile(TsehFile(name, "application/pdf", bytes))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message.orEmpty() }
            finally { working = false }
        }
    }
    fun click(node: Element) {
        when (node.id()) {
            "uploadSide", "importJson" -> open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            "demoBtn" -> confirmDemo = true
            "exportPdf" -> {
                val current = snapshot ?: return
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.Default) { tsehReportPdf(current) }
                        saveFile(TsehFile("Цех-на-бумаге.pdf", "application/pdf", bytes))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "Не удалось подготовить PDF." }
                }
            }
            else -> commands.trySend(TsehCommand("click", JSONObject().put("id", node.id()).put("dataset", tsehDataSet(node)).toString()))
        }
    }
    val current = snapshot
    BackHandler(enabled = current != null && current.step > 0) {
        commands.trySend(TsehCommand("step", ((current?.step ?: 1) - 1).toString()))
    }
    Column(Modifier.fillMaxSize().background(SiteColors.BackgroundTop).verticalScroll(scroll)
        .padding(horizontal = if (current == null) 16.dp else 14.dp, vertical = if (current == null) 16.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FestiveHeader {
            if (current == null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (LocalNewYear.current) SiteMascot(40.dp, Modifier.clickable { onStore(0) })
                    Text("Колбабот", Modifier.weight(1f).clickable { onStore(0) },
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
                    val dark = LocalSiteDark.current
                    val toggle = LocalToggleTheme.current
                    OutlinedButton(toggle, shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = SiteColors.Soft, contentColor = SiteColors.Ink),
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text(if (dark) "Светлая тема" else "Тёмная тема", fontSize = 13.sp, fontWeight = FontWeight.Normal)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { commands.trySend(TsehCommand("step", "0")) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (LocalNewYear.current) SiteMascot(43.dp)
                        else Box(Modifier.size(43.dp).background(SiteColors.Action, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center) {
                            Text("К", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Цех на бумаге", style = MaterialTheme.typography.titleLarge)
                            Text("КолбаБот · финансовый путеводитель для открытия дела",
                                style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
                        }
                    }
                    val dark = LocalSiteDark.current
                    val toggle = LocalToggleTheme.current
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TsehToolbarButton("В магазин") { onStore(0) }
                        TsehToolbarButton("Книга", enabled = !working) { download("/tseh/book", "tseh_book_5_9.pdf") }
                        TsehToolbarButton("Инструкция", enabled = !working) { download("/tseh/guide", "tseh_guide_4_4.pdf") }
                        TsehToolbarButton(if (dark) "Светлая тема" else "Тёмная тема", onClick = toggle)
                        TsehToolbarButton("Сохранить проект") { click(Element("button").attr("id", "saveTop")) }
                    }
                }
            }
        }
        ErrorMessage(error)
        if (document == null || document?.hasAccess == true && current == null) {
            if (error.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth())
            else OutlinedButton({ reload++ }) { Text("Повторить") }
        } else if (document?.hasAccess == false) {
            val landing = document!!
            TsehLanding(landing, userId > 0, onStore, onSignIn, onSupport) { password ->
                api.post("/api/tseh/activate", JSONObject().put("password", password.trim()))
                reload++
            }
        } else if (current != null) {
            val loaded = document!!
            val renderer = TsehRenderer(current, editGeneration, ::click, { node, value, checked, event ->
                val payload = JSONObject().put("dataset", tsehDataSet(node)).put("id", node.id())
                    .put("value", value).put("checked", checked).put("type", node.attr("type")).put("event", event)
                commands.trySend(TsehCommand("field", payload.toString()))
            }, { url -> openBrowser(context, url) })
            loaded.hero?.let { hero ->
                TsehPanel(SiteColors.AccentSoft, padding = 22) {
                    renderer.Children(hero.children(), "hero")
                }
            }
            val narrow = LocalConfiguration.current.screenWidthDp <= 640
            val columns = if (narrow) 2 else 4
            Column(Modifier.onGloballyPositioned { navigatorOffset = it.positionInParent().y.roundToInt() },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Продукция", "Расходы", "Планы", "Результат").chunked(columns).forEachIndexed { row, options ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEachIndexed { column, title ->
                            val index = row * columns + column
                            TsehStepButton(title, index + 1, current.step == index, Modifier.weight(1f)) {
                                commands.trySend(TsehCommand("step", index.toString()))
                            }
                        }
                    }
                }
            }
            TsehPanel {
                renderer.Children(loaded.stage(current.step), "step-${current.step}")
            }
            loaded.sidebar?.let { panel -> TsehPanel { renderer.Children(panel.children(), "side") } }
            Text(loaded.footer, style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        }
    }
    if (confirmDemo) AlertDialog(
        onDismissRequest = { confirmDemo = false },
        title = { Text("Загрузить демонстрационный пример?") },
        text = { Text("Текущие введённые данные будут заменены.") },
        confirmButton = { TextButton({
            confirmDemo = false
            commands.trySend(TsehCommand("click", JSONObject().put("id", "demoBtn").toString()))
        }) { Text("Загрузить") } },
        dismissButton = { TextButton({ confirmDemo = false }) { Text("Отмена") } },
    )
}

@Composable
private fun TsehLanding(document: TsehDocument, authenticated: Boolean, onStore: (Long) -> Unit, onSignIn: () -> Unit,
                        onSupport: () -> Unit, activate: suspend (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
    val title = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp, lineHeight = 29.sp)
    val main = document.html.selectFirst("main")
    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp),
            color = SiteColors.Surface, border = BorderStroke(1.dp, SiteColors.Border), shadowElevation = 2.dp) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                main?.selectFirst(".tseh-kicker")?.let {
                    Text(it.text(), Modifier.padding(top = 16.dp), color = SiteColors.Brand, style = body, fontWeight = FontWeight.Bold)
                }
                Text(main?.selectFirst("h1")?.text() ?: "Цех на бумаге", style = title)
                main?.selectFirst("h1 + p")?.let { Text(it.text(), style = body) }
                main?.select("ul > li")?.takeIf { it.isNotEmpty() }?.let { list ->
                    Column(Modifier.padding(start = 20.dp)) {
                        list.forEach { TsehLandingListItem("•", it.text(), body, SiteColors.Ink) }
                    }
                }
                if (document.productId > 0) {
                    Button({ onStore(document.productId) }, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 55.dp)) {
                        Text(document.priceLabel, style = body, fontWeight = FontWeight.Bold)
                    }
                    main?.selectFirst(".tseh-purchase p")?.let { Text(it.text(), style = body, color = SiteColors.Muted) }
                } else {
                    Surface(color = SiteColors.Soft, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SiteColors.Border)) {
                        Text("Продажа пока не открыта. Цена появится здесь после начала продаж.", Modifier.padding(16.dp),
                            style = body, color = SiteColors.Muted)
                    }
                }
                main?.selectFirst(".tseh-purchase + h2")?.let { Text(it.text(), Modifier.padding(top = 14.dp), style = title) }
                main?.select(".tseh-purchase-steps li")?.let { steps ->
                    Column(Modifier.padding(start = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        steps.forEachIndexed { index, step -> TsehLandingListItem("${index + 1}.", step.text(), body, SiteColors.Muted) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = SiteColors.Border)
                if (authenticated) {
                    Text("Уже купили?", style = title)
                    OutlinedTextField(password, { password = it.take(24) }, Modifier.fillMaxWidth(),
                        singleLine = true, enabled = !busy, label = { Text("Одноразовый пароль из письма после покупки") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
                    Button(enabled = password.trim().length == 24 && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        busy = true
                        error = ""
                        scope.launch {
                            try { activate(password) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message.orEmpty() }
                            finally { busy = false }
                        }
                    }) { Text("Открыть доступ навсегда") }
                    ErrorMessage(error)
                    TsehLandingNote("Активируйте в том аккаунте, с которого оформлена покупка. Если письмо потерялось, обратитесь в ",
                        "поддержку магазина", body, onSupport)
                } else TsehButton("Войти в аккаунт КолбаБота", primary = true, onClick = onSignIn)
                TsehLandingNote("После первой активации здесь сразу откроется калькулятор. Условия покупки — в ",
                    "публичной оферте", body) { openLink(context, "/public-offer") }
            }
        }
    }
}

@Composable
private fun TsehLandingListItem(marker: String, label: String, style: TextStyle, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(marker, Modifier.widthIn(min = 14.dp), style = style, color = color)
        Text(label, style = style, color = color)
    }
}

@Composable
private fun TsehLandingNote(lead: String, label: String, style: TextStyle, onClick: () -> Unit) {
    val link = SpanStyle(color = SiteColors.Brand, textDecoration = TextDecoration.Underline)
    Text(buildAnnotatedString {
        append(lead)
        withLink(LinkAnnotation.Clickable(label, TextLinkStyles(link)) { onClick() }) { append(label) }
        append('.')
    }, style = style, color = SiteColors.Muted)
}

@Composable
private fun TsehToolbarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled, shape = RoundedCornerShape(11.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun TsehStepButton(title: String, step: Int, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val surface = if (active) SiteColors.Action else SiteColors.Surface
    val ink = if (active) Color.White else SiteColors.Muted
    Surface(onClick, modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(11.dp), color = surface,
        contentColor = ink, border = BorderStroke(1.dp, if (active) SiteColors.Action else SiteColors.Border)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(24.dp).background(if (active) Color.White.copy(alpha = .18f) else SiteColors.BackgroundTop, CircleShape),
                contentAlignment = Alignment.Center) {
                Text(step.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun TsehPanel(color: Color = SiteColors.Surface, padding: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = color, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, SiteColors.Border)) {
        Column(Modifier.padding(padding.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
internal fun TsehButton(label: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val target = modifier.heightIn(min = 46.dp)
    if (primary) Button(onClick, target, shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = SiteColors.Action, contentColor = Color.White)) { Text(label) }
    else OutlinedButton(onClick, target, shape = shape, border = BorderStroke(1.dp, SiteColors.Border)) { Text(label, color = SiteColors.Ink) }
}
