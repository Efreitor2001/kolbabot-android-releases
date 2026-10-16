package ru.kolbabot.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

private fun accountText(user: JSONObject, key: String): String = if (user.isNull(key)) "" else user.optString(key)
private fun accountFailure(error: Exception): String { if (error is CancellationException) throw error; return error.message ?: "Не удалось обновить данные." }
private fun providerLabel(code: String): String = when (code.lowercase().replace('-', '_')) {
    "google" -> "Google"
    "vk", "vkid" -> "VK ID"
    "mail", "mail_ru" -> "Mail.ru"
    "ok", "ok_ru" -> "Одноклассники"
    "", "local" -> "Email и пароль"
    else -> code
}

/** Profile fields and rewards are read from the existing account and server cart contracts. */
@Composable
fun AccountProfileDetails(api: ApiClient, user: JSONObject) {
    val profileId = user.optLong("id")
    val sessionId = api.userId
    var rewards by remember(profileId, sessionId) { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    OnResume { refresh++ }
    LaunchedEffect(api, profileId, sessionId, refresh) {
        if (profileId <= 0 || sessionId != profileId) return@LaunchedEffect
        error = ""
        try {
            val cart = api.get("/api/store/cart")
            if (api.userId == profileId && cart.optLong("user_id", profileId) == profileId) {
                rewards = cart.optJSONObject("rewards") ?: JSONObject()
            }
        }
        catch (e: Exception) { error = accountFailure(e) }
    }
    if (profileId <= 0 || sessionId != profileId) return
    SectionCard("Данные профиля") {
        val fullName = listOf(accountText(user, "first_name"), accountText(user, "last_name")).filter(String::isNotBlank).joinToString(" ")
        if (fullName.isNotBlank()) Text(fullName, style = MaterialTheme.typography.titleMedium)
        val email = accountText(user, "email")
        Text("Email: " + if (email.isBlank()) "не привязан" else email)
        if (email.isNotBlank()) Text(if (user.optBoolean("email_verified")) "Email подтверждён" else "Email ещё не подтверждён", style = MaterialTheme.typography.bodySmall)
        Text("Телефон: ${accountText(user, "phone").ifBlank { "не указан" }}")
        Text("Основной вход: ${accountText(user, "oauth_provider_label").ifBlank { providerLabel(accountText(user, "oauth_provider")) }}")
        HorizontalDivider()
        Text("Баланс колбасок", style = MaterialTheme.typography.labelLarge)
        rewards?.let {
            val count = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply { maximumFractionDigits = 2 }.format(it.optLong("sausages_kopecks") / 100.0)
            Text("$count колбасок", style = MaterialTheme.typography.headlineSmall)
        } ?: if (error.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth()) else Unit
        ErrorMessage(error)
        TextButton(onClick = { refresh++ }) { Text("Обновить баланс") }
    }
}

/** Place immediately after the promo text field; payment still uses the same entered promo code. */
@Composable
fun SubscriptionPromoPreview(api: ApiClient, promoCode: String, enabled: Boolean = true, onValidated: (JSONObject?) -> Unit = {}) {
    val code = promoCode.trim().uppercase(Locale.ROOT)
    val latestCode by rememberUpdatedState(code)
    val report by rememberUpdatedState(onValidated)
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    var validatedCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(code) { preview = null; validatedCode = ""; error = ""; report(null) }
    OutlinedButton(enabled = enabled && !busy && code.isNotBlank(), onClick = {
        val requestedCode = code
        busy = true; error = ""; preview = null; validatedCode = ""; report(null)
        scope.launch {
            try {
                val result = api.post("/api/payments/tbank/preview-promo", JSONObject().put("promo_code", requestedCode))
                require(result.optString("tier") == "premium") { "Этот промокод не относится к PREMIUM." }
                if (latestCode == requestedCode) { preview = result; validatedCode = requestedCode; report(result) }
            } catch (e: Exception) {
                val message = accountFailure(e)
                if (latestCode == requestedCode) error = message
            } finally { busy = false }
        }
    }) { Text(if (busy) "Проверяем промокод…" else "Проверить промокод") }
    ErrorMessage(error)
    if (validatedCode == code) preview?.let { result ->
        val first = result.optDouble("first_month_amount_rub", result.optDouble("amount_rub"))
        val regular = result.optDouble("regular_amount_rub", first)
        val duration = result.optInt("duration_days", 30)
        SectionCard("Промокод $validatedCode") {
            Text("Первый период: ${money((first * 100).roundToLong())} за $duration дней", style = MaterialTheme.typography.titleMedium)
            Text("Далее: ${money((regular * 100).roundToLong())} каждые $duration дней")
            Text("Скидка по промокоду применяется только к первому платежу.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The server binds the browser approval to the initiating native account. */
@Composable
fun LinkedAccountsScreen(api: ApiClient, user: JSONObject, onBack: () -> Unit, onLink: ((String) -> Unit)? = null) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun startLink(provider: String) {
        if (linking || !consent) return
        if (onLink != null) { onLink(provider); return }
        linking = true; error = ""; notice = ""
        scope.launch {
            try {
                val flow = api.post("/api/mobile/account-link/start", JSONObject()
                    .put("provider", if (provider == "google") "google" else "vk").put("profile_consent", true))
                val origin = URI(api.baseUrl)
                val target = origin.resolve(flow.getString("authorization_url"))
                require(target.scheme == "https" && target.host == origin.host && target.port == origin.port && target.userInfo == null && target.path.startsWith("/mobile/account-link")) { "Сервер вернул некорректную ссылку подтверждения." }
                openBrowser(context, target.toString())
                notice = "Завершите привязку в браузере под этим же аккаунтом. Подтверждение действительно ${flow.optInt("expires_in", 300) / 60} минут."
            } catch (e: Exception) { error = accountFailure(e) }
            finally { linking = false }
        }
    }
    OnResume { refresh++ }
    LaunchedEffect(api, refresh) {
        loading = true; error = ""
        try { data = api.get("/api/account/oauth/connections") }
        catch (e: Exception) { error = accountFailure(e) }
        finally { loading = false }
    }
    Page("Способы входа", "Один аккаунт Колбабота для всех способов входа", onBack) {
        Text("Аккаунт: ${accountText(user, "username")}")
        Text(accountText(user, "email"))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorMessage(error)
        if (notice.isNotBlank()) Text(notice)
        OutlinedButton(enabled = !loading && !linking, onClick = { refresh++ }) { Text("Обновить подключения") }
        ConsentRow("Разрешаю получить идентификатор, имя, фамилию, email и телефон внешнего аккаунта для привязки", consent, { consent = it })
        Text("Подтверждение выполняется в браузере. После возврата список подключений обновится автоматически.", style = MaterialTheme.typography.bodySmall)
        data?.items("connections")?.forEach { connection ->
            val code = connection.optString("code")
            val connected = connection.optBoolean("connected")
            val available = connection.optBoolean("enabled", true)
            SectionCard(connection.optString("label").ifBlank { providerLabel(code) }) {
                Text(if (connected) "Подключён к вашему аккаунту" else if (available) "Ещё не подключён" else "Сервис сейчас недоступен")
                if (connected && accountText(connection, "email").isNotBlank()) Text(connection.getString("email"))
                if (connected && accountText(connection, "connected_at").isNotBlank()) Text("Подключён: ${connection.getString("connected_at").replace('T', ' ').take(16)}", style = MaterialTheme.typography.bodySmall)
                if (!connected && available) OutlinedButton(enabled = consent && !loading && !linking, onClick = { startLink(code) }) { Text("Привязать ${connection.optString("label").ifBlank { providerLabel(code) }}") }
            }
        }
    }
}
