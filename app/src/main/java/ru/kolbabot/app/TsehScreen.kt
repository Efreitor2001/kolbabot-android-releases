package ru.kolbabot.app

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    Column(Modifier.fillMaxSize().background(SiteColors.BackgroundTop).verticalScroll(scroll).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FestiveHeader {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ onStore(0) }, contentPadding = PaddingValues(horizontal = 1.dp, vertical = 4.dp)) {
                    SiteMascot(34.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (current == null) "Колбабот" else "Цех на бумаге", color = SiteColors.Ink, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.weight(1f))
                SiteThemeButton(showLabel = true)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onStore(0) }) { Text("В магазин") }
                OutlinedButton({ download("/tseh/book", "tseh_book_5_9.pdf") }, enabled = !working) { Text("Книга") }
                OutlinedButton({ download("/tseh/guide", "tseh_guide_4_4.pdf") }, enabled = !working) { Text("Инструкция") }
                OutlinedButton({ click(Element("button").attr("id", "saveTop")) }) { Text("Сохранить проект") }
            }
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
            Column(Modifier.onGloballyPositioned { navigatorOffset = it.positionInParent().y.roundToInt() },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Продукция", "Расходы", "Планы", "Результат").chunked(2).forEachIndexed { row, options ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEachIndexed { column, title ->
                            val index = row * 2 + column
                            val selected = current.step == index
                            TsehButton("${index + 1}  $title", selected, Modifier.weight(1f)) {
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
    Text("Программа для ремесленников", color = SiteColors.Brand, style = MaterialTheme.typography.labelMedium)
    Text("Цех на бумаге", style = MaterialTheme.typography.displaySmall)
    Text("Оцените себестоимость, прибыль и стартовый капитал своего производства.")
    Text("• Книга-путеводитель 5.9 и инструкция в PDF\n• Калькулятор 4.4, проекты JSON, экспорт Excel и PDF\n• Доступ навсегда на вашем аккаунте с любого устройства")
    TsehPanel {
        if (document.productId > 0) {
            TsehButton(document.priceLabel, primary = true) { onStore(document.productId) }
            Text("Откроется карточка программы в магазине. Добавьте её в корзину и оплатите заказ.",
                style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        } else Text("Продажа пока не открыта. Цена появится здесь после начала продаж.")
    }
    Text("Как получить доступ", style = MaterialTheme.typography.headlineSmall)
    Text("1. Купите программу в магазине КолбаБота.\n2. Получите книгу, инструкцию и одноразовый пароль на почту.\n3. Введите пароль здесь один раз — Цех навсегда откроется на вашем аккаунте.")
    HorizontalDivider(color = SiteColors.Border)
    if (authenticated) {
        Text("Уже купили?", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(password, { password = it.take(24) }, Modifier.fillMaxWidth(),
            singleLine = true, enabled = !busy, label = { Text("Одноразовый пароль из письма после покупки") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
        Button(enabled = password.trim().length == 24 && !busy, onClick = {
            busy = true
            error = ""
            scope.launch {
                try { activate(password) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message.orEmpty() }
                finally { busy = false }
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = SiteColors.Action, contentColor = Color.White)) {
            Text("Открыть доступ навсегда")
        }
        ErrorMessage(error)
        Text("Активируйте в том аккаунте, с которого оформлена покупка.", color = SiteColors.Muted)
        TextButton(onSupport) { Text("Поддержка магазина") }
    } else TsehButton("Войти в аккаунт КолбаБота", primary = true, onClick = onSignIn)
    Text("После первой активации здесь сразу откроется калькулятор.", color = SiteColors.Muted)
    TextButton({ openLink(context, "/public-offer") }) { Text("Публичная оферта") }
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
