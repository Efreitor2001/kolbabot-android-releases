package ru.kolbabot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun AccountProfileDetails(api: ApiClient, user: JSONObject, content: @Composable ColumnScope.() -> Unit = {}) {
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
    SectionCard("Ваши данные") {
        val fullName = listOf(accountText(user, "first_name"), accountText(user, "last_name")).filter(String::isNotBlank).joinToString(" ")
            .ifBlank { accountText(user, "username").ifBlank { "Участник Колбабота" } }
        val initials = fullName.split(Regex("\\s+")).filter(String::isNotBlank).take(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT)
        SiteListBadge("Аккаунт активен", SiteColors.Accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.Transparent,
                border = BorderStroke(1.dp, SiteColors.Brand.copy(alpha = 0.14f))) {
                Box(Modifier.size(62.dp).background(Brush.linearGradient(listOf(SiteColors.Brand.copy(alpha = 0.14f), SiteColors.Brand2.copy(alpha = 0.11f)))),
                    contentAlignment = Alignment.Center) {
                    Text(initials, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = SiteColors.Brand)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(fullName, style = MaterialTheme.typography.titleMedium)
                Text(if (user.optBoolean("is_admin")) "Администратор" else "Участник Колбабота", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
            }
        }
        val email = accountText(user, "email")
        Column {
            AccountProfileFact("Email", email.ifBlank { "Не привязан" }, if (email.isBlank()) "" else if (user.optBoolean("email_verified")) "Email подтверждён" else "Email ещё не подтверждён")
            AccountProfileFact("Телефон", accountText(user, "phone").ifBlank { "Не указан" })
            AccountProfileFact("Основной вход", accountText(user, "oauth_provider_label").ifBlank { providerLabel(accountText(user, "oauth_provider")) })
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), color = SiteColors.Accent.copy(alpha = 0.065f),
            border = BorderStroke(1.dp, SiteColors.Accent.copy(alpha = 0.14f))) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(shape = RoundedCornerShape(11.dp), color = SiteColors.Accent) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Text("К", color = Color.White, fontWeight = FontWeight.ExtraBold) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Баланс колбасок", style = MaterialTheme.typography.labelSmall, color = SiteColors.Muted)
                    rewards?.let {
                        val count = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply { maximumFractionDigits = 2 }.format(it.optLong("sausages_kopecks") / 100.0)
                        Text("$count колбасок", style = MaterialTheme.typography.titleSmall)
                    } ?: if (error.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth()) else Text("Не удалось загрузить баланс", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        ErrorMessage(error)
        TextButton(onClick = { refresh++ }) { Text("Обновить баланс") }
        content()
    }
}

/** Same label/value rows as .account-profile-facts on the website. */
@Composable
private fun AccountProfileFact(label: String, value: String, detail: String = "") {
    HorizontalDivider(color = SiteColors.CustomerBorder)
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = SiteColors.Muted, textAlign = TextAlign.End)
        }
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
fun LinkedAccountsScreen(api: ApiClient, user: JSONObject, onBack: () -> Unit, embedded: Boolean = false, onLink: ((String) -> Unit)? = null) {
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
    val content: @Composable ColumnScope.() -> Unit = {
        Text("Подключённые аккаунты", style = MaterialTheme.typography.titleMedium)
        Text("Аккаунт: ${accountText(user, "username")}")
        Text(accountText(user, "email"))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorMessage(error)
        if (notice.isNotBlank()) Text(notice)
        OutlinedButton(enabled = !loading && !linking, onClick = { refresh++ }) { Text("Обновить подключения") }
        data?.items("connections")?.forEach { connection ->
            val code = connection.optString("code")
            val connected = connection.optBoolean("connected")
            val available = connection.optBoolean("enabled", true)
            SectionCard(connection.optString("label").ifBlank { providerLabel(code) }) {
                Text(if (connected) "Подключён к вашему аккаунту" else if (available) "Ещё не подключён" else "Сервис сейчас недоступен")
                if (connected && accountText(connection, "email").isNotBlank()) Text(connection.getString("email"))
                if (connected && accountText(connection, "connected_at").isNotBlank()) Text("Подключён: ${connection.getString("connected_at").replace('T', ' ').take(16)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        ConsentRow("Разрешаю получить идентификатор, имя, фамилию, email и телефон внешнего аккаунта для привязки", consent, { consent = it })
        data?.items("connections")?.filter { !it.optBoolean("connected") && it.optBoolean("enabled", true) }?.forEach { connection ->
            val code = connection.optString("code")
            OutlinedButton(enabled = consent && !loading && !linking, onClick = { startLink(code) }) { Text("Привязать ${connection.optString("label").ifBlank { providerLabel(code) }}") }
        }
        Text("Подтверждение выполняется в браузере. После возврата список подключений обновится автоматически.", style = MaterialTheme.typography.bodySmall)
        Text("Если внешний аккаунт уже принадлежит другому пользователю, мы не переносим его автоматически — так безопаснее.", style = MaterialTheme.typography.bodySmall, color = SiteColors.Muted)
    }
    if (embedded) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    else Page("Способы входа", "Один аккаунт Колбабота для всех способов входа", onBack, content = content)

}
