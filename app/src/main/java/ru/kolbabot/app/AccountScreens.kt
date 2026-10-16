package ru.kolbabot.app

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

@Composable fun AccountScreen(api: ApiClient, user: JSONObject, openNotifications: Boolean = false, onNotificationsOpened: () -> Unit = {},
                              initialSection: String = "", onSectionOpened: () -> Unit = {},
                              initialOrderId: String = "", onOrderOpened: () -> Unit = {},
                              onSectionChanged: (String) -> Unit = {}, onLogout: () -> Unit) {
    val accountId = user.optLong("id")
    val identity = rememberVerifiedAccount(api, accountId)
    var route by rememberSaveable(accountId) { mutableStateOf("account") }
    var routeOwner by rememberSaveable { mutableLongStateOf(accountId) }
    LaunchedEffect(accountId) {
        if (routeOwner != accountId) { routeOwner = accountId; route = "account" }
        // Discard an administrative route saved by an older app version.
        if (route in setOf("admin", "banners")) route = "account"
    }
    LaunchedEffect(openNotifications) {
        if (openNotifications) { route = "notifications"; onNotificationsOpened() }
    }
    LaunchedEffect(initialSection) {
        when (initialSection) {
            "support", "notifications", "orders", "account" -> { route = initialSection; onSectionOpened() }
            "paywall" -> { route = "subscription"; onSectionOpened() }
        }
    }
    LaunchedEffect(initialOrderId) { if (initialOrderId.isNotBlank()) route = "orders" }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var logoutConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLogout by rememberUpdatedState(onLogout)
    val identityMatches = accountId > 0 && api.userId == accountId && identity.user?.optLong("id") == accountId
    LaunchedEffect(identity.checked, identity.error, identityMatches) {
        if (identity.checked && !identityMatches && identity.error.isBlank()) currentLogout()
    }
    BackHandler(route != "account") { route = "account" }
    if (!identityMatches) {
        Page("Личный кабинет") {
            if (!identity.checked) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (identity.error.isNotBlank()) {
                ErrorMessage(identity.error)
                OutlinedButton(onClick = identity.refresh) { Text("Обновить аккаунт") }
            } else {
                Text("Войдите в аккаунт, чтобы открыть личный кабинет.")
                Button(onClick = onLogout) { Text("Войти") }
            }
        }
        return
    }
    val visibleRoute = if (routeOwner == accountId && route !in setOf("admin", "banners")) route else "account"
    val currentSectionChanged by rememberUpdatedState(onSectionChanged)
    LaunchedEffect(visibleRoute) {
        currentSectionChanged(when (visibleRoute) {
            "support" -> "support"
            "subscription" -> "paywall"
            else -> "account"
        })
    }
    when (visibleRoute) {
        "orders" -> Column { TextButton(onClick = { route = "account" }) { Text("‹ Кабинет") }; Box(Modifier.weight(1f)) { OrdersScreen(api, initialOrderId, onOrderOpened) } }
        "support" -> Column { TextButton(onClick = { route = "account" }) { Text("‹ Кабинет") }; Box(Modifier.weight(1f)) { SupportScreen(api) } }
        "notifications" -> NotificationsScreen(api) { route = "account" }
        "subscription" -> SubscriptionScreen(api) { route = "account" }
        "email" -> EmailScreen(api, identity.user!!, onConfirmed = identity.refresh) { route = "account" }
        "connections" -> LinkedAccountsScreen(api, identity.user ?: user, { route = "account" })
        else -> Page("Личный кабинет") {
            val current = identity.user!!
            if (!identity.checked) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (identity.error.isNotBlank()) {
                ErrorMessage(identity.error)
                OutlinedButton(onClick = identity.refresh) { Text("Обновить аккаунт") }
            }
            SectionCard(current.optString("username", "Ваш аккаунт")) {
                val email = current.optString("email").takeUnless { it == "null" }.orEmpty()
                if (email.isNotBlank()) Text(email)
                Text("Покупки, подписка и колбаски синхронизированы с сайтом.")
            }
            ErrorMessage(error)
            SectionCard("Покупки и доступ") {
                AccountLink("История заказов", "Статусы, состав и сумма ваших заказов.") { route = "orders" }
                AccountLink("Ваш доступ", "Статус, оплата и автопродление") { route = "subscription" }
                AccountLink("Уведомления", "Сообщения и изменения заказов") { route = "notifications" }
            }
            SectionCard("Аккаунт и помощь") {
                AccountLink("Привязать email", "Отправим шестизначный код и добавим адрес только после подтверждения.") { route = "email" }
                AccountLink("Способы входа", "Google, VK ID, Mail.ru и Одноклассники") { route = "connections" }
                AccountLink("Поддержка", "Написать нам и посмотреть ответы") { route = "support" }
            }
            AccountProfileDetails(api, current)
            val referral = current.optJSONObject("referral_program")
            NotificationPreferences(api)
            if (referral != null && referral.optBoolean("is_active") && referral.optString("registration_path").isNotBlank()) SectionCard("Приведи друга") {
                Text("Приглашено: ${referral.optInt("invited_count")} · Начислено: ${money(referral.optLong("earned_kopecks"))}")
                Text("Ваш код: ${referral.optString("code")}")
                OutlinedButton(onClick = {
                    val link = api.baseUrl + referral.optString("registration_path")
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Присоединяйтесь к Колбаботу: $link") }, "Пригласить друга"))
                }) { Text("Поделиться приглашением") }
            }
            SectionCard("О приложении") {
                Text("Колбабот ${BuildConfig.VERSION_NAME}")
                TextButton(onClick = { openLink(context, "/android/privacy") }) { Text("Политика конфиденциальности") }
                TextButton(onClick = { openLink(context, "/android/delete-account") }) { Text("Удалить аккаунт и данные") }
                TextButton(onClick = { openLink(context, "/public-offer") }) { Text("Публичная оферта") }
                TextButton(onClick = { openLink(context, "/payment-credentials") }) { Text("Реквизиты и оплата") }
            }
            OutlinedButton(enabled = !busy, onClick = { logoutConfirmation = true }) { Text("Выйти") }
        }
    }
    if (logoutConfirmation) AlertDialog(onDismissRequest = { logoutConfirmation = false }, title = { Text("Выйти из аккаунта?") },
        text = { Text("Заказы и данные останутся на сервере. Для доступа нужно будет войти снова.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; scope.launch {
                try { disablePush(context, api); api.post("/api/auth/logout"); cancelNotificationSync(context); api.clearSession(); onLogout() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { error = e.message.orEmpty() }
                finally { busy = false; logoutConfirmation = false }
            }
        }) { Text("Выйти") } }, dismissButton = { TextButton(onClick = { logoutConfirmation = false }) { Text("Отмена") } })
}

@Composable private fun AccountLink(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun NotificationsScreen(api: ApiClient, onBack: () -> Unit) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var expandedId by rememberSaveable { mutableLongStateOf(0) }
    RemotePage("Уведомления", api, "/api/notifications", onBack) { data, reload ->
        BackHandler(expandedId > 0) { expandedId = 0 }
        Text("Непрочитанных: ${data.optInt("unread_count")}")
        ErrorMessage(error)
        fun action(path: String) { if (!busy) { busy = true; scope.launch {
            try { api.post(path); reload() } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = e.message.orEmpty() } finally { busy = false }
        } } }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(enabled = !busy, onClick = { action("/api/notifications/read-all") }) { Text("Прочитать все") }
            TextButton(enabled = !busy, onClick = { action("/api/notifications/clear-read") }) { Text("Очистить") }
        }
        val items = data.items("items")
        if (items.isEmpty()) Text("Здесь появятся сообщения о ваших заказах и новости.")
        items.forEach { item ->
            val id = item.optLong("id")
            val unread = !item.optBoolean("is_read")
            SiteDisclosure(item.optString("title"), expandedId == id, { expandedId = if (expandedId == id) 0 else id },
                summary = (if (unread) "Новое · " else "") + item.optString("message"),
                accent = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                bodyPadding = 14.dp, bodySpacing = 8.dp) {
            Text(item.optString("message")); Text(item.optString("created_at"), style = MaterialTheme.typography.labelSmall)
            if (!item.optBoolean("is_read")) TextButton(enabled = !busy, onClick = { action("/api/notifications/${item.optLong("id")}/read") }) { Text("Прочитано") }
            if (item.optString("action_url").isNotBlank()) TextButton(onClick = { openLink(context, item.optString("action_url")) }) { Text("Подробнее") }
        } }
    }
}

@Composable internal fun EmailScreen(api: ApiClient, user: JSONObject, onConfirmed: () -> Unit = {}, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(user.optString("email").takeUnless { it == "null" }.orEmpty()) }
    var code by remember { mutableStateOf("") }; var sent by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }; var notice by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun sendCode() {
        if (busy) return
        val address = email.trim().lowercase(Locale.ROOT)
        if (address.isBlank()) return
        busy = true; error = ""; notice = ""
        scope.launch {
            try {
                val result = api.post("/api/account/email/start", JSONObject().put("email", address))
                email = result.optString("email").ifBlank { address }; code = ""; sent = true
                notice = "Код отправлен на $email. Он действует ${result.optInt("expires_in_minutes", 30)} минут."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = e.message.orEmpty() }
            finally { busy = false }
        }
    }
    Page("Привязать email", onBack = {
        if (!busy) {
            if (sent) { sent = false; code = ""; notice = ""; error = "" } else onBack()
        }
    }) {
        Text("На этот адрес приходят подтверждения и приобретённые книги.")
        ErrorMessage(error); if (notice.isNotBlank()) Text(notice)
        OutlinedTextField(email, { email = it }, enabled = !busy && !sent, singleLine = true, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        if (!sent) Button(enabled = !busy && email.isNotBlank(), onClick = ::sendCode) { Text("Получить код") }
        if (sent) {
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, enabled = !busy, label = { Text("Код из письма") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && code.length == 6, onClick = { busy = true; error = ""; scope.launch {
            try {
                api.post("/api/account/email/confirm", JSONObject().put("email", email.trim().lowercase(Locale.ROOT)).put("code", code))
                notice = "Адрес подтверждён."; sent = false; code = ""; onConfirmed()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = e.message.orEmpty() } finally { busy = false }
            } }) { Text("Подтвердить email") }
            OutlinedButton(enabled = !busy, onClick = ::sendCode) { Text("Отправить снова") }
            TextButton(enabled = !busy, onClick = { sent = false; code = ""; notice = ""; error = "" }) { Text("Изменить адрес") }
        }
    }
}

@Composable internal fun SubscriptionScreen(api: ApiClient, onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("subscription_payment", Context.MODE_PRIVATE) }
    val key = "order_${api.userId}"
    var pending by rememberSaveable { mutableStateOf(preferences.getString(key, "").orEmpty()) }
    var paymentUrl by rememberSaveable { mutableStateOf(preferences.getString(key + "_url", "").orEmpty()) }
    var billing by remember { mutableStateOf<JSONObject?>(null) }
    var offers by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }; var notice by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var promo by rememberSaveable { mutableStateOf("") }; var offerConsent by rememberSaveable { mutableStateOf(false) }
    var subscriptionConsent by rememberSaveable { mutableStateOf(false) }; var requisitesConsent by rememberSaveable { mutableStateOf(false) }
    var privacyConsent by rememberSaveable { mutableStateOf(false) }; var cancelConfirm by remember { mutableStateOf(false) }
    var recoveryConfirm by remember { mutableStateOf(false) }
    var supportConfirmed by remember { mutableStateOf(false) }
    var recoveryStatus by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    fun clearPending() {
        preferences.edit().remove(key).remove(key + "_url").remove(key + "_request").commit()
        pending = ""; paymentUrl = ""
    }
    OnResume { refresh++ }
    LaunchedEffect(refresh) {
        error = ""
        try {
            billing = api.get("/api/account/billing"); offers = api.get("/api/bootstrap").optJSONObject("payments")
            if (pending == "uncertain") {
                val lastOrder = billing?.optString("last_order_id")?.takeUnless { it.isBlank() || it == "null" }
                recoveryStatus = if (lastOrder == null) "Сервер пока не показывает оплаченный заказ подписки. Это не подтверждает отсутствие незавершённого платежа." else {
                    val last = api.get("/api/payments/tbank/status?order_id=${URLEncoder.encode(lastOrder, "UTF-8")}")
                    "Последний заказ подписки на сервере: $lastOrder · ${last.optString("status")}."
                }
                if (billing?.optString("status") == "active" && billing?.optString("tier") == "premium") {
                    notice = "Сервер подтверждает активную PREMIUM. Повторная оплата не требуется."
                    clearPending()
                }
            }
            if (pending.isNotBlank() && pending != "uncertain") {
                val result = api.get("/api/payments/tbank/status?order_id=${URLEncoder.encode(pending, "UTF-8")}")
                notice = if (result.optBoolean("is_success")) "Оплата подтверждена." else "Статус оплаты: ${result.optString("status")}"
                if (result.optBoolean("is_final")) { clearPending(); billing = api.get("/api/account/billing") }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { error = e.message.orEmpty() }
    }
    Page("Доступ", onBack = onBack) {
        ErrorMessage(error); if (notice.isNotBlank()) Text(notice)
        val account = billing
        if (account == null && error.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth())
        account?.let { SectionCard("Ваш доступ") {
            Text(if (it.optString("status") == "active") "${it.optString("tier").uppercase()} · активна" else "Нет активной подписки")
            val until = it.optString("active_until").takeUnless { value -> value == "null" }.orEmpty()
            if (until.isNotBlank()) Text("Действует до $until")
            Text(if (it.optBoolean("autopay_enabled")) "Автопродление включено" else "Автопродление отключено")
            val card = it.optString("card_mask").takeUnless { value -> value == "null" }.orEmpty()
            val method = when {
                it.optString("autopay_method") == "sbp" && it.optBoolean("has_sbp") -> "СБП"
                card.isNotBlank() -> "Карта $card"
                it.optBoolean("has_rebill") -> "Банковская карта"
                else -> "Не сохранён"
            }
            Text("Способ оплаты: $method")
            if (it.optString("status") == "active" && it.optString("tier") == "premium") {
                Text("PREMIUM уже активна. Повторная оплата доступна после окончания текущего периода.", style = MaterialTheme.typography.bodySmall)
            }
            if (it.optBoolean("autopay_enabled")) OutlinedButton(enabled = !busy, onClick = { cancelConfirm = true }) { Text("Отключить автопродление") }
        } }
        if (pending.isNotBlank()) SectionCard("Проверка оплаты") {
            Text("Запрос отправлен в банк. Проверьте результат перед новой оплатой.")
            if (paymentUrl.isNotBlank()) Button(onClick = { openLink(context, paymentUrl) }, enabled = !busy) { Text("Продолжить оплату") }
            Button(onClick = { refresh++ }) { Text("Проверить статус") }
            if (pending == "uncertain") {
                Text("Ответ на запрос потерян. Перед новой попыткой проверьте кабинет на сайте или обратитесь в поддержку.")
                if (recoveryStatus.isNotBlank()) Text(recoveryStatus)
                TextButton(onClick = { openBrowser(context, api.baseUrl + "/#account") }) { Text("Проверить кабинет на сайте") }
                TextButton(onClick = { openLink(context, api.baseUrl + "/#support") }) { Text("Обратиться в поддержку") }
                OutlinedButton(onClick = { supportConfirmed = false; recoveryConfirm = true }) { Text("Поддержка завершила проверку") }
            }
        }
        offers?.items("plans")?.forEach { plan -> SectionCard(plan.optString("title", "PREMIUM")) {
            Text("${money(plan.optLong("amount_kopecks"))} / ${plan.optInt("duration_days")} дней", style = MaterialTheme.typography.headlineSmall)
            Text(plan.optString("description"))
            plan.optJSONArray("features")?.let { features -> for (i in 0 until features.length()) Text("• ${features.optString(i)}") }
        } }
        if (offers?.optBoolean("enabled") == true && pending.isBlank() && billing != null && !(billing?.optString("status") == "active" && billing?.optString("tier") == "premium")) SectionCard("Оформить PREMIUM") {
            OutlinedTextField(promo, { promo = it }, label = { Text("Промокод") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SubscriptionPromoPreview(api, promo, enabled = !busy)
            ConsentRow("Принимаю публичную оферту", offerConsent, { offerConsent = it }, "/public-offer")
            ConsentRow("Принимаю условия подписки и регулярных списаний", subscriptionConsent, { subscriptionConsent = it }, "/subscription-agreement")
            ConsentRow("Ознакомлен с реквизитами и условиями оплаты", requisitesConsent, { requisitesConsent = it }, "/payment-credentials")
            ConsentRow("Принимаю политику конфиденциальности", privacyConsent, { privacyConsent = it }, "/android/privacy")
            Button(enabled = !busy && offerConsent && subscriptionConsent && requisitesConsent && privacyConsent, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; error = ""
                val request = JSONObject().put("tier", "premium").put("promo_code", promo.trim().uppercase(Locale.ROOT))
                    .put("accept_public_offer", true).put("accept_subscription_agreement", true).put("accept_payment_credentials", true).put("accept_privacy_policy", true)
                scope.launch {
                    try {
                        require(preferences.edit().putString(key, "uncertain").putString(key + "_request", request.toString()).remove(key + "_url").commit()) {
                            "Не удалось сохранить состояние платежа. Освободите память устройства и попробуйте снова."
                        }
                        pending = "uncertain"; paymentUrl = ""
                        val result = api.post("/api/payments/tbank/init", request)
                        pending = result.optString("order_id").ifBlank { "uncertain" }
                        paymentUrl = result.optString("payment_url")
                        preferences.edit().putString(key, pending).putString(key + "_url", paymentUrl).commit()
                        if (result.optBoolean("pending_reconciliation")) notice = "Банк проверяет платёж. Повторно оплачивать не нужно."
                        else paymentUrl.takeIf { it.isNotBlank() }?.let { openLink(context, it) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) {
                        error = e.message.orEmpty()
                        if (e is ApiException && e.status in listOf(400, 401, 403, 422, 429)) clearPending()
                    } finally { busy = false }
                }
            }) { Text("Перейти к оплате в Т-Банке") }
        }
        OutlinedButton(onClick = { refresh++ }) { Text("Обновить") }
    }
    if (recoveryConfirm) AlertDialog(onDismissRequest = { recoveryConfirm = false }, title = { Text("Завершить проверку оплаты?") },
        text = { Column {
            Text("Новая попытка доступна только после проверки предыдущей оплаты. Простое отсутствие списания в истории банка не исключает платёж в обработке.")
            ConsentRow("Поддержка подтвердила, что незавершённого платежа нет и можно начать оплату заново", supportConfirmed, { supportConfirmed = it })
        } },
        confirmButton = { TextButton(enabled = supportConfirmed, onClick = { clearPending(); recoveryConfirm = false; notice = "Отметка проверки снята. Вы можете начать новую оплату." }) { Text("Завершить проверку") } },
        dismissButton = { TextButton(onClick = { recoveryConfirm = false }) { Text("Отмена") } })
    if (cancelConfirm) AlertDialog(onDismissRequest = { cancelConfirm = false }, title = { Text("Отключить автопродление?") },
        text = { Text("PREMIUM сохранится до конца оплаченного периода.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
            try { api.post("/api/account/autopay/disable"); refresh++; cancelConfirm = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = e.message.orEmpty(); cancelConfirm = false } finally { busy = false }
        } }) { Text("Отключить") } }, dismissButton = { TextButton(onClick = { cancelConfirm = false }) { Text("Отмена") } })
}
