package ru.kolbabot.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.time.Instant

internal class VisitorAnalytics(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("first_party_analytics", Context.MODE_PRIVATE)
    private val changed = MutableStateFlow(read().consent)
    val consent = changed.asStateFlow()
    private val state = VisitorAnalyticsState(read(), persist = { value ->
        val editor = preferences.edit().clear().putString("consent", value.consent.name)
        if (value.consent == AnalyticsConsent.GRANTED) editor
            .putString("installation_id", value.installationId).putString("guest_token", value.guestToken)
            .putLong("account_id", value.accountId).putString("delivered", value.delivered)
            .putBoolean("guest_reset", value.guestReset)
        editor.apply()
    })
    private val sending = Mutex()

    private fun read() = AnalyticsSnapshot(
        runCatching { AnalyticsConsent.valueOf(preferences.getString("consent", "").orEmpty()) }.getOrDefault(AnalyticsConsent.UNDECIDED),
        preferences.getString("installation_id", "").orEmpty(),
        preferences.getString("guest_token", "").orEmpty(),
        preferences.getLong("account_id", -1),
        preferences.getString("delivered", "").orEmpty(),
        preferences.getBoolean("guest_reset", false),
    )

    fun choose(value: AnalyticsConsent) { state.choose(value); changed.value = value }
    fun confirmAccount(accountId: Long) { state.confirmAccount(accountId) }

    suspend fun visit(api: ApiClient) {
        if (!sending.tryLock()) return
        try {
            val visit = state.prepare(Instant.now()) ?: return
            val response = api.reportAnalytics(visit) { state.isCurrent(visit) } ?: return
            val token = response.optString("guest_token").takeUnless { it == "null" }
            if (response.optBoolean("ignored") || (visit.accountId == 0L && token.isNullOrBlank())) state.ignored(visit)
            else state.delivered(visit, token)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Analytics never changes the visible application state. */ }
        finally { sending.unlock() }
    }
}

@Composable internal fun VisitorAnalyticsLifecycle(api: ApiClient, active: Boolean, identityRevision: Int) {
    val owner = LocalLifecycleOwner.current
    val consent by api.analytics.consent.collectAsState()
    LaunchedEffect(api, owner, active, consent, identityRevision) {
        if (!active || consent != AnalyticsConsent.GRANTED) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                api.analytics.visit(api)
                delay(60_000)
            }
        }
    }
}

@Composable internal fun AnalyticsConsentCard(api: ApiClient, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val choice by api.analytics.consent.collectAsState()
    Surface(Modifier.fillMaxWidth().testTag("analytics-consent"), tonalElevation = 3.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Статистика посещений", style = MaterialTheme.typography.titleSmall)
            Text("Разрешить Колбаботу учитывать посещения приложения? Используем случайный идентификатор установки, а после входа — аккаунт. Данные остаются на сервере Колбабота. Отказ не ограничивает функции; выбор можно изменить в меню «Ещё».",
                style = MaterialTheme.typography.bodySmall)
            if (choice != AnalyticsConsent.UNDECIDED) Text(
                if (choice == AnalyticsConsent.GRANTED) "Сбор разрешён" else "Сбор отключён",
                style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { api.analytics.choose(AnalyticsConsent.DENIED); onClose?.invoke() }, modifier = Modifier.weight(1f)) {
                    Text(if (choice == AnalyticsConsent.GRANTED) "Отозвать" else "Отказаться")
                }
                OutlinedButton(onClick = { api.analytics.choose(AnalyticsConsent.GRANTED); onClose?.invoke() }, modifier = Modifier.weight(1f),
                    enabled = choice != AnalyticsConsent.GRANTED) { Text("Разрешить") }
            }
            TextButton(onClick = { openBrowser(context, api.baseUrl + "/android/privacy") }) { Text("Политика конфиденциальности") }
        }
    }
}

internal fun analyticsRows(data: JSONObject): List<Pair<String, List<Long>>> {
    val periods = data.getJSONObject("periods")
    return listOf("day" to "Сегодня", "week" to "7 дней", "month" to "30 дней", "all" to "Всё время").map { (key, label) ->
        val values = periods.getJSONObject(key)
        label to listOf("total", "web", "android").map { values.getLong(it).also { count -> require(count >= 0) } }
    }
}
