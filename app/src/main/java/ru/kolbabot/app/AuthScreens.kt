package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

@Composable fun ConsentRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, link: String = "") {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked, onCheckedChange = onChecked)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (link.isNotBlank()) TextButton(onClick = { openLink(context, link) }, contentPadding = PaddingValues(0.dp)) { Text("Открыть документ") }
        }
    }
}

@Composable fun AuthScreen(api: ApiClient, onOpenBrowser: ((String) -> Unit)? = null, onAuthenticated: (JSONObject) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("login") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf("") }
    var pd by rememberSaveable { mutableStateOf(false) }
    var cookie by rememberSaveable { mutableStateOf(false) }
    var adult by rememberSaveable { mutableStateOf(false) }
    var referral by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pendingStore = remember(api) { MobileSignInStore(context, api.baseUrl) }
    var flow by remember(api) { mutableStateOf(pendingStore.read()) }
    var googleAvailable by remember { mutableStateOf(false) }
    var vkAvailable by remember { mutableStateOf(false) }
    var providersLoading by remember { mutableStateOf(true) }
    var oauthPd by rememberSaveable { mutableStateOf(false) }
    var oauthCookie by rememberSaveable { mutableStateOf(false) }
    var oauthAdult by rememberSaveable { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val authenticated by rememberUpdatedState(onAuthenticated)
    val launchBrowser by rememberUpdatedState(onOpenBrowser ?: { url: String -> openSignInBrowser(context, url) })
    fun clearFlow() { pendingStore.clear(); flow = null }
    LaunchedEffect(api) {
        try { googleAvailable = api.get("/api/auth/google/config").optBoolean("enabled") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Password login and the site fallback stay available. */ }
        try { vkAvailable = api.get("/api/auth/vk/config").optBoolean("enabled") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        providersLoading = false
    }
    fun navigate(next: String) { mode = next; error = ""; code = ""; challenge = ""; notice = ""; password = ""; confirm = ""; clearFlow() }
    fun back() {
        if (busy) return
        if (flow != null) { clearFlow(); notice = "" }
        else navigate(when (mode) { "register_code" -> "register"; "reset_code" -> "reset"; else -> "login" })
    }
    BackHandler(mode != "login" || flow != null) { back() }
    LaunchedEffect(flow?.flowId, lifecycleState) {
        val pending = flow ?: return@LaunchedEffect
        if (lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
        while (System.currentTimeMillis() < pending.expiresAt) {
            try {
                val answer = api.post("/api/mobile/auth/exchange", JSONObject()
                    .put("flow_id", pending.flowId).put("code_verifier", pending.verifier))
                val user = answer.optJSONObject("user")
                if (user != null) { clearFlow(); authenticated(user); return@LaunchedEffect }
                notice = "После подтверждения закройте окно браузера — вход завершится здесь."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: ApiException) {
                if (failure.status in setOf(400, 401, 403, 404, 409, 410)) {
                    error = failure.message; clearFlow(); return@LaunchedEffect
                }
                notice = "Не удалось проверить вход. Повторим при восстановлении соединения."
            }
            catch (_: java.io.IOException) { notice = "Ожидаем соединения…" }
            delay(pending.intervalMillis)
        }
        clearFlow(); error = "Время ожидания истекло. Начните вход заново."
    }
    fun startQuickSignIn(provider: String) {
        if (busy) return
        if (provider == "google" && !(oauthPd && oauthCookie && oauthAdult)) {
            error = "Отметьте согласия перед входом через Google."; return
        }
        busy = true; error = ""; notice = ""
        scope.launch {
            try {
                val verifier = api.newVerifier()
                val response = api.post("/api/mobile/auth/start", JSONObject()
                    .put("code_challenge", ApiContract.challenge(verifier)).put("code_challenge_method", "S256"))
                val pending = MobileSignIn.start(api.baseUrl, response, verifier, provider, System.currentTimeMillis())
                pendingStore.write(pending); flow = pending
                launchBrowser(pending.browserUrl(api.baseUrl))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = if (failure is ApiException && failure.status == 404)
                    "Быстрый вход сейчас недоступен. Войдите по логину и паролю."
                    else failure.message ?: "Не удалось открыть браузер. Попробуйте ещё раз."
            } finally { busy = false }
        }
    }
    fun submit() {
        if (busy) return
        busy = true; error = ""; notice = ""
        scope.launch {
            try {
                when (mode) {
                    "login" -> {
                        val result = api.post("/api/auth/login", JSONObject().put("username", username.trim()).put("password", password))
                        if (result.optBoolean("requires_2fa")) { challenge = result.getString("challenge_token"); mode = "2fa"; code = "" }
                        else { password = ""; clearFlow(); onAuthenticated(result.getJSONObject("user")) }
                    }
                    "2fa" -> {
                        val result = api.post("/api/auth/login/2fa", JSONObject().put("challenge_token", challenge).put("code", code.trim()))
                        clearFlow(); onAuthenticated(result.getJSONObject("user")); password = ""
                    }
                    "register" -> {
                        require(pd && cookie && adult) { "Отметьте согласия перед регистрацией." }
                        require(password == confirm) { "Пароли не совпадают." }
                        api.post("/api/auth/register", JSONObject().put("username", username.trim()).put("email", email.trim())
                            .put("password", password).put("confirm_password", confirm).put("pd_consent", pd)
                            .put("cookie_consent", cookie).put("age_consent", adult).put("referral_code", referral.trim()))
                        mode = "register_code"; notice = "Код отправлен на email. Введите его для завершения регистрации."
                    }
                    "register_code" -> {
                        val result = api.post("/api/auth/register/confirm", JSONObject().put("username", username.trim()).put("email", email.trim()).put("code", code.trim()))
                        clearFlow(); onAuthenticated(result.getJSONObject("user"))
                    }
                    "reset" -> {
                        api.post("/api/auth/password-reset/request", JSONObject().put("email", email.trim()))
                        mode = "reset_code"; notice = "Если адрес зарегистрирован, письмо с кодом придёт на $email."
                    }
                    "reset_code" -> {
                        require(password == confirm) { "Пароли не совпадают." }
                        api.post("/api/auth/password-reset/confirm", JSONObject().put("email", email.trim()).put("code", code.trim())
                            .put("new_password", password).put("confirm_password", confirm))
                        navigate("login"); notice = "Пароль изменён. Войдите с новым паролем."
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = failure.message ?: "Не удалось выполнить запрос."
                if (mode == "2fa") { mode = "login"; error += " Войдите заново, чтобы получить новую попытку 2FA." }
            } finally { busy = false }
        }
    }
    AuthLayout(when(mode) { "register", "register_code" -> "Начнём знакомство"; "reset", "reset_code" -> "Вернём доступ"; "2fa" -> "Подтверждение входа"; else -> "Добро пожаловать" },
        subtitle = when(mode) {
            "register", "register_code" -> "Создайте единый кабинет для расчётов, заказов и подключённых способов входа."
            "reset", "reset_code" -> "Получите код на email и задайте новый пароль — это займёт пару минут."
            else -> "Войдите удобным способом — ваши данные и заказы будут под рукой."
        }, mode = mode, enabled = !busy, onMode = ::navigate) {
        if (flow != null) {
            SectionCard("Подтвердите вход в браузере") {
                Text("Сверьте код: ${flow!!.displayCode}", style = MaterialTheme.typography.headlineSmall)
                Text("Войдите привычным способом и разрешите вход в Android-приложение. Затем вернитесь сюда.")
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = { runCatching { launchBrowser(flow!!.authorizationUrl) }.onFailure { error = "Не удалось открыть браузер." } }) { Text("Вернуться к подтверждению") }
                TextButton(onClick = { back() }) { Text("Отменить") }
            }
        } else {
        if (mode == "login") AuthQuickPanel {
            Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Быстрый вход", fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = SiteColors.Ink)
                Text("Google, VK ID, Mail.ru или Одноклассники", fontSize = 10.sp, lineHeight = 13.5.sp, color = SiteColors.Muted)
            }
            if (providersLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (googleAvailable) {
                AuthConsents {
                    AuthConsentRow("Я принимаю ", "согласие на обработку персональных данных",
                        " и разрешаю получить ФИО, email, телефон и источник входа из выбранного аккаунта",
                        oauthPd, { oauthPd = it }, "/privacy-policy", "oauth-pd")
                    AuthConsentRow("Я принимаю ", "политику использования cookie", "", oauthCookie,
                        { oauthCookie = it }, "/cookie-policy", "oauth-cookie")
                    AuthConsentRow("Мне исполнилось 18 лет", "", "", oauthAdult, { oauthAdult = it }, tag = "oauth-adult")
                }
                OutlinedButton(onClick = { startQuickSignIn("google") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("auth-google"),
                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF1D2A4E).copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White, contentColor = Color(0xFF1F1F1F))) {
                    Image(painterResource(R.drawable.sign_in_google), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp)); Text("Войти через Google", fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif, fontSize = 14.sp, letterSpacing = 0.25.sp)
                }
            }
            if (vkAvailable) {
                OutlinedButton(onClick = { startQuickSignIn("site") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("auth-site"), border = null, colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF0077FF), contentColor = Color.White)) {
                    Text("VK ID, Mail.ru и Одноклассники", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Text("Выбор сервиса откроется в браузере", fontSize = 10.sp, color = SiteColors.Muted)
            }
            if (!providersLoading && !googleAvailable && !vkAvailable) {
                OutlinedButton(onClick = { startQuickSignIn("site") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("auth-site")) {
                    Text("Открыть быстрый вход")
                }
            }
        }
        if (mode == "login") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = Color(0xFF1D2A4E).copy(alpha = 0.09f))
            Text("ИЛИ ПО ЛОГИНУ И ПАРОЛЮ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A92A2))
            HorizontalDivider(Modifier.weight(1f), color = Color(0xFF1D2A4E).copy(alpha = 0.09f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mode in listOf("login", "register", "register_code")) OutlinedTextField(username, { username = it }, enabled = !busy, label = { Text("Логин") }, placeholder = { Text(if (mode == "login") "Ваш логин" else "Как к вам обращаться") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (mode in listOf("register", "register_code", "reset", "reset_code")) OutlinedTextField(email, { email = it }, enabled = !busy, label = { Text("Email") }, placeholder = { Text("name@example.ru") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            if (mode == "reset_code") AuthCodeField(code, { code = it }, "Код из email", "12345678", !busy)
            if (mode in listOf("login", "register", "register_code", "reset_code")) OutlinedTextField(password, { password = it }, enabled = !busy, label = { Text(if (mode == "reset_code") "Новый пароль" else "Пароль") }, placeholder = { if (mode != "reset_code") Text(if (mode == "login") "Введите пароль" else "Не менее 6 символов") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (mode in listOf("register", "register_code", "reset_code")) OutlinedTextField(confirm, { confirm = it }, enabled = !busy, label = { Text(if (mode == "reset_code") "Повторите новый пароль" else "Повторите пароль") }, placeholder = { if (mode != "reset_code") Text("Введите пароль ещё раз") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (mode == "register_code") AuthCodeField(code, { code = it }, "Код подтверждения из email", "123456", !busy)
            if (mode == "2fa") OutlinedTextField(code, { code = it }, enabled = !busy, label = { Text("Код 2FA или резервный код") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (mode == "register") {
                AuthConsents {
                    AuthConsentRow("Я принимаю ", "согласие на обработку персональных данных", "", pd, { pd = it }, "/privacy-policy", "register-pd")
                    AuthConsentRow("Я принимаю ", "политику использования cookie", "", cookie, { cookie = it }, "/cookie-policy", "register-cookie")
                    AuthConsentRow("Мне исполнилось 18 лет", "", "", adult, { adult = it }, tag = "register-adult")
                }
            }
            Button(onClick = ::submit, enabled = !busy, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).background(
                    Brush.linearGradient(listOf(SiteColors.Brand, Color(0xFF6552D8))), RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)) {
                Text(if (busy) "Подождите…" else when(mode) { "register" -> "Отправить код"; "reset" -> "Отправить код сброса"; "reset_code" -> "Сбросить пароль"; "register_code" -> "Подтвердить и зарегистрироваться"; "2fa" -> "Подтвердить"; else -> "Войти" })
            }
        }
    }
        AuthResult(error.ifBlank { notice.ifBlank { when {
            mode.startsWith("register") -> "Введите email. Без подтверждения почты регистрация не завершится."
            mode.startsWith("reset") -> "Введите email, получите код и задайте новый пароль."
            providersLoading -> "После входа откроется основной сайт."
            googleAvailable || vkAvailable -> "Войдите через Google, VK ID, Mail.ru, Одноклассники или по логину и паролю."
            else -> "Введите логин и пароль."
        } } }, isError = error.isNotBlank())
        AuthFooter()
    }
}

@Composable private fun AuthCodeField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, enabled: Boolean) {
    OutlinedTextField(value, onValueChange, enabled = enabled, label = { Text(label) }, placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable private fun AuthResult(message: String, isError: Boolean) {
    val accent = if (isError) Color(0xFFC12525) else SiteColors.Brand
    Surface(Modifier.fillMaxWidth().heightIn(min = 43.dp), shape = RoundedCornerShape(13.dp),
        color = accent.copy(alpha = if (isError) .05f else .045f), border = BorderStroke(1.dp, accent.copy(alpha = if (isError) .16f else .1f))) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(Modifier.size(17.dp), shape = RoundedCornerShape(50), color = accent.copy(alpha = .12f)) {
                Box(contentAlignment = Alignment.Center) { Text(if (isError) "!" else "i", color = accent,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 11.sp, lineHeight = 17.sp) }
            }
            Text(message, fontSize = 11.sp, lineHeight = 15.95.sp, color = if (isError) accent else Color(0xFF657084), modifier = Modifier.weight(1f))
        }
    }
}

/** Native layout of auth.html and the final auth-page CSS, including its mobile order. */
@Composable private fun AuthLayout(title: String, subtitle: String, mode: String, enabled: Boolean,
    onMode: (String) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val selected = when (mode) { "register", "register_code" -> "register"; "reset", "reset_code" -> "reset"; else -> "login" }
    Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFF8F9FC), Color(0xFFF2F1EC))))
        .verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(Modifier.fillMaxWidth(), color = Color.Transparent, shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .3f)), shadowElevation = 10.dp) {
            Column(Modifier.authBrandBackground()
                .heightIn(min = 150.dp).padding(19.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Surface(Modifier.size(36.dp), color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))) {
                        Box(contentAlignment = Alignment.Center) { Text("К", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                    Text("Колбабот", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("Домашнее производство — увереннее", fontSize = 24.8.sp, lineHeight = 26.78.sp, letterSpacing = (-.62).sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Color.White,
                    modifier = Modifier.widthIn(max = 310.dp))
            }
        }
        Surface(Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.96f), contentColor = SiteColors.Ink,
            shape = RoundedCornerShape(22.dp), shadowElevation = 4.dp, border = BorderStroke(1.dp, Color(0xFF202B48).copy(alpha = 0.1f))) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(when(selected) { "register" -> "НОВЫЙ АККАУНТ"; "reset" -> "ВОССТАНОВЛЕНИЕ"; else -> "ЛИЧНЫЙ КАБИНЕТ" },
                        fontSize = 12.sp, lineHeight = 16.8.sp, fontWeight = FontWeight.Bold, letterSpacing = .96.sp, color = SiteColors.Brand)
                    Spacer(Modifier.height(5.dp))
                    Text(title, fontSize = 24.8.sp, lineHeight = 28.52.sp, letterSpacing = .248.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = SiteColors.Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(subtitle, fontSize = 13.sp, lineHeight = 20.15.sp, color = SiteColors.Muted)
                }
                Surface(color = Color(0xFFF2F4F8), shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, Color(0xFF1D2A4E).copy(alpha = 0.07f))) {
                    Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("login" to "Вход", "register" to "Регистрация", "reset" to "Восстановление").forEach { (key, caption) ->
                            Surface(Modifier.weight(1f).testTag("auth-tab-$key").selectable(selected == key, enabled,
                                role = Role.Tab, onClick = { onMode(key) }),
                                shape = RoundedCornerShape(11.dp), color = if (selected == key) Color.White else Color.Transparent,
                                border = if (selected == key) BorderStroke(1.dp, SiteColors.Brand.copy(alpha = 0.08f)) else null) {
                                Box(Modifier.heightIn(min = 42.dp).padding(horizontal = 4.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(caption, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center, color = if (selected == key) SiteColors.Brand else Color(0xFF687286))
                                }
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalSiteAuth provides true) { content() }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** The same three CSS gradients and three decorative rings as auth-brand-panel. */
private fun Modifier.authBrandBackground() = drawWithCache {
    val base = Brush.linearGradient(0f to Color(0xFF365BD0), .58f to Color(0xFF6550D4), 1f to Color(0xFF187F72))
    val light = Brush.radialGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent),
        center = Offset(size.width * .85f, size.height * .08f), radius = size.width * .32f)
    val green = Brush.radialGradient(listOf(Color(0xFF1B937C).copy(alpha = .5f), Color.Transparent),
        center = Offset(size.width * .12f, size.height * .88f), radius = size.width * .4f)
    onDrawBehind {
        drawRect(base); drawRect(green); drawRect(light)
        val ring = Color.White.copy(alpha = .16f)
        drawCircle(ring, 145.dp.toPx(), Offset(size.width - 50.dp.toPx(), 40.dp.toPx()), style = Stroke(1.dp.toPx()))
        drawCircle(ring, 77.5.dp.toPx(), Offset(size.width - 52.5.dp.toPx(), size.height - 157.5.dp.toPx()), style = Stroke(1.dp.toPx()))
        val glowCenter = Offset(15.dp.toPx(), size.height * .28f + 105.dp.toPx())
        drawCircle(Color.White.copy(alpha = .04f), 105.dp.toPx(), glowCenter)
        drawCircle(ring, 105.dp.toPx(), glowCenter, style = Stroke(1.dp.toPx()))
    }
}

@Composable private fun AuthQuickPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.Transparent,
        border = BorderStroke(1.dp, SiteColors.Brand.copy(alpha = 0.1f))) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(SiteColors.Brand.copy(alpha = 0.04f),
            SiteColors.Accent.copy(alpha = 0.025f)))).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable private fun AuthConsents(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color(0xFF1D2A4E).copy(alpha = 0.07f))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable private fun AuthConsentRow(prefix: String, linkText: String, suffix: String, checked: Boolean,
    onChecked: (Boolean) -> Unit, link: String = "", tag: String = "") {
    val context = LocalContext.current
    val text = buildAnnotatedString {
        append(prefix)
        if (linkText.isNotEmpty()) withLink(LinkAnnotation.Clickable(link,
            TextLinkStyles(style = SpanStyle(color = SiteColors.Brand, fontWeight = FontWeight.Bold))) { openLink(context, link) }) {
            append(linkText)
        }
        append(suffix)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Checkbox(checked, onChecked, modifier = Modifier.size(20.dp).testTag(tag),
                colors = CheckboxDefaults.colors(checkedColor = SiteColors.Accent, uncheckedColor = Color(0xFF87909F)))
        }
        Text(text, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp, color = Color(0xFF505C70), modifier = Modifier.weight(1f))
    }
}

@Composable private fun AuthFooter() {
    val context = LocalContext.current
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(buildAnnotatedString {
            withLink(LinkAnnotation.Clickable("privacy", TextLinkStyles(SpanStyle(color = SiteColors.Brand))) { openLink(context, "/privacy-policy") }) { append("Персональные данные") }
            append(" · ")
            withLink(LinkAnnotation.Clickable("cookie", TextLinkStyles(SpanStyle(color = SiteColors.Brand))) { openLink(context, "/cookie-policy") }) { append("Cookie") }
        }, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(" · ", fontSize = 10.sp, color = SiteColors.Muted)
        Text("Вернуться на сайт", Modifier.testTag("auth-return-home").clickable(role = Role.Button) { openLink(context, "/") },
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SiteColors.Brand)
    }
}

@Composable fun ConsentScreen(api: ApiClient, onDone: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Page("Согласие на обработку данных") {
        ConsentRow("Согласен на обработку персональных данных", accepted, { accepted = it }, "/android/privacy")
        ErrorMessage(error)
        Button(enabled = accepted && !busy, onClick = { busy = true; scope.launch {
            try { api.post("/api/auth/legal/pd-consent", JSONObject().put("pd_consent", true)); onDone() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = e.message.orEmpty() } finally { busy = false }
        } }) { Text("Продолжить") }
    }
}
