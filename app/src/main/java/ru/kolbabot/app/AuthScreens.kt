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
import java.net.URI

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

@Composable fun AuthScreen(api: ApiClient, onAuthenticated: (JSONObject) -> Unit) {
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
    var flow by remember { mutableStateOf<JSONObject?>(null) }
    var verifier by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun navigate(next: String) { mode = next; error = ""; code = ""; challenge = ""; notice = ""; password = ""; confirm = ""; flow = null; verifier = "" }
    fun back() {
        if (busy) return
        if (flow != null) { flow = null; verifier = ""; notice = "" }
        else navigate(when (mode) { "register_code" -> "register"; "reset_code" -> "reset"; else -> "login" })
    }
    BackHandler(mode != "login" || flow != null) { back() }
    LaunchedEffect(flow) {
        val pending = flow ?: return@LaunchedEffect
        val started = android.os.SystemClock.elapsedRealtime()
        while (android.os.SystemClock.elapsedRealtime() - started < 300000) {
            delay(2500)
            try {
                val answer = api.post("/api/mobile/auth/exchange", JSONObject().put("flow_id", pending.getString("flow_id")).put("code_verifier", verifier))
                val user = answer.optJSONObject("user")
                if (user != null) { flow = null; onAuthenticated(user); return@LaunchedEffect }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: ApiException) { error = failure.message; flow = null; return@LaunchedEffect }
            catch (_: java.io.IOException) { notice = "Ожидаем соединения…" }
        }
        flow = null; error = "Время ожидания истекло. Начните вход заново."
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
                        else { password = ""; onAuthenticated(result.getJSONObject("user")) }
                    }
                    "2fa" -> {
                        val result = api.post("/api/auth/login/2fa", JSONObject().put("challenge_token", challenge).put("code", code.trim()))
                        onAuthenticated(result.getJSONObject("user")); password = ""
                    }
                    "register" -> {
                        require(pd && cookie && adult) { "Отметьте согласия перед регистрацией." }
                        require(password == confirm) { "Пароли не совпадают." }
                        api.post("/api/auth/register", JSONObject().put("username", username.trim()).put("email", email.trim())
                            .put("password", password).put("confirm_password", confirm).put("pd_consent", pd)
                            .put("cookie_consent", cookie).put("age_consent", adult).put("referral_code", referral.trim()))
                        mode = "register_code"; password = ""; confirm = ""; notice = "Код отправлен на $email"
                    }
                    "register_code" -> {
                        val result = api.post("/api/auth/register/confirm", JSONObject().put("username", username.trim()).put("email", email.trim()).put("code", code.trim()))
                        onAuthenticated(result.getJSONObject("user"))
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
    Page(when(mode) { "register", "register_code" -> "Начнём знакомство"; "reset", "reset_code" -> "Вернём доступ"; "2fa" -> "Подтверждение входа"; else -> "Добро пожаловать" },
        subtitle = when(mode) {
            "register", "register_code" -> "Создайте единый кабинет для расчётов, заказов и подключённых способов входа."
            "reset", "reset_code" -> "Получите код на email и задайте новый пароль — это займёт пару минут."
            else -> "Войдите удобным способом — ваши данные и заказы будут под рукой."
        }, onBack = if (mode != "login" || flow != null) ({ back() }) else null) {
        if (notice.isNotBlank()) Text(notice)
        ErrorMessage(error)
        if (flow != null) {
            SectionCard("Подтвердите вход в браузере") {
                Text("Сверьте код: ${flow!!.optString("display_code")}", style = MaterialTheme.typography.headlineSmall)
                Text("Войдите привычным способом и разрешите вход в Android-приложение. Затем вернитесь сюда.")
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = { openLink(context, flow!!.getString("authorization_url")) }) { Text("Вернуться к подтверждению") }
                TextButton(onClick = { back() }) { Text("Отменить") }
            }
        } else SectionCard {
            if (mode in listOf("login", "register")) OutlinedTextField(username, { username = it }, enabled = !busy, label = { Text("Логин") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (mode in listOf("register", "reset")) OutlinedTextField(email, { email = it }, enabled = !busy, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            if (mode in listOf("login", "register", "reset_code")) OutlinedTextField(password, { password = it }, enabled = !busy, label = { Text(if (mode == "reset_code") "Новый пароль" else "Пароль") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (mode in listOf("register", "reset_code")) OutlinedTextField(confirm, { confirm = it }, enabled = !busy, label = { Text(if (mode == "reset_code") "Повторите новый пароль" else "Повторите пароль") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (mode in listOf("2fa", "register_code", "reset_code")) OutlinedTextField(code, { code = it }, enabled = !busy, label = { Text(if (mode == "2fa") "Код 2FA или резервный код" else "Код из письма") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (mode == "register") {
                OutlinedTextField(referral, { referral = it }, enabled = !busy, label = { Text("Код друга, если есть") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ConsentRow("Согласен на обработку персональных данных", pd, { pd = it }, "/android/privacy")
                ConsentRow("Принимаю политику cookie", cookie, { cookie = it }, "/cookie-policy")
                ConsentRow("Мне исполнилось 18 лет", adult, { adult = it })
            }
            Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Подождите…" else when(mode) { "register" -> "Отправить код"; "reset" -> "Отправить код сброса"; "reset_code" -> "Сбросить пароль"; "register_code" -> "Подтвердить и зарегистрироваться"; "2fa" -> "Подтвердить"; else -> "Войти" })
            }
            if (mode == "login") {
                TextButton(enabled = !busy, onClick = { navigate("reset") }) { Text("Восстановление") }
                OutlinedButton(enabled = !busy, onClick = { navigate("register") }, modifier = Modifier.fillMaxWidth()) { Text("Регистрация") }
                HorizontalDivider()
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true; error = ""
                    scope.launch {
                        try {
                            verifier = api.newVerifier()
                            val result = api.post("/api/mobile/auth/start", JSONObject().put("code_challenge", ApiContract.challenge(verifier)).put("code_challenge_method", "S256"))
                            val authorization = URI(api.baseUrl).resolve(result.getString("authorization_url"))
                            require(authorization.scheme == "https" && authorization.host == URI(api.baseUrl).host && authorization.path == "/mobile/auth/approve")
                            result.put("authorization_url", authorization.toString()); flow = result
                            openLink(context, authorization.toString())
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = if (failure is ApiException && failure.status == 404) "Для входа через Google, VK, Mail.ru и ОК нужно установить обновление мобильного входа на сервер. Вход по логину уже доступен." else failure.message ?: "Не удалось открыть вход." }
                        finally { busy = false }
                    }
                }) { Text("Войти через Google / VK / Mail.ru / ОК") }
            }
        }
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
