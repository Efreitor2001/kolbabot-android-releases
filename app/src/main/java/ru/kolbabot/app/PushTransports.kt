package ru.kolbabot.app

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.HmsMessaging
import com.huawei.hms.push.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** All store APKs use the available device service; branding never chooses a token provider. */
internal object PushTransports {
    const val FIREBASE = "firebase"
    const val HUAWEI = "huawei"
    private var huaweiConfigLoaded = false
    private val huaweiTokens = MutableStateFlow("")

    private fun firebase(context: Context): FirebaseApp? {
        if (!BuildConfig.HAS_FIREBASE_CONFIG) return null
        return runCatching { FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context) }.getOrNull()
    }

    @Synchronized private fun huawei(context: Context): AGConnectServicesConfig? {
        if (!BuildConfig.HAS_HUAWEI_CONFIG) return null
        return runCatching {
            val configuration = AGConnectServicesConfig.fromContext(context)
            if (!huaweiConfigLoaded) {
                // Generated allowlist contains public client configuration, never OAuth secrets.
                val publicConfig = context.assets.open("kolbabot-huawei.json").bufferedReader().use { JSONObject(it.readText()) }
                val client = publicConfig.getJSONObject("client")
                check(client.optString("package_name") == context.packageName)
                check(client.optString("app_id").matches(Regex("[0-9]{5,64}")))
                // setParam works even if an SDK content provider has already read AGConnect.
                // overlayWith is lazy and would not replace an already initialized reader.
                listOf("app_id", "package_name", "cp_id", "project_id", "api_key", "product_id", "client_id").forEach { key ->
                    if (client.has(key)) configuration.setParam("client/$key", client.optString(key))
                }
                listOf("configuration_version", "region").forEach { key ->
                    if (publicConfig.has(key)) configuration.setParam(key, publicConfig.optString(key))
                }
                publicConfig.optJSONObject("agcgw")?.let { gateway ->
                    listOf("url", "backurl").forEach { key -> if (gateway.has(key)) configuration.setParam("agcgw/$key", gateway.optString(key)) }
                }
                check(configuration.getString("client/package_name") == context.packageName)
                check(configuration.getString("client/app_id").orEmpty().matches(Regex("[0-9]{5,64}")))
                huaweiConfigLoaded = true
            }
            configuration
        }.getOrNull()
    }

    private fun googleAvailable(context: Context): Boolean = runCatching {
        GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)
    private fun huaweiAvailable(context: Context): Boolean = runCatching {
        HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(context) == 0
    }.getOrDefault(false)

    fun available(context: Context, provider: String): Boolean = when (provider) {
        FIREBASE -> googleAvailable(context) && firebase(context) != null
        HUAWEI -> huaweiAvailable(context) && huawei(context) != null
        else -> false
    }

    fun select(context: Context): String? = when {
        available(context, FIREBASE) -> FIREBASE
        available(context, HUAWEI) -> HUAWEI
        else -> null
    }

    fun problem(context: Context): String = when {
        select(context) != null -> ""
        !BuildConfig.HAS_FIREBASE_CONFIG && !BuildConfig.HAS_HUAWEI_CONFIG -> "Push-уведомления пока не подключены в этой сборке приложения."
        huaweiAvailable(context) && huawei(context) == null -> "Уведомления Huawei ещё не подключены: отсутствуют настройки приложения Huawei."
        googleAvailable(context) && firebase(context) == null -> "Уведомления Google ещё не подключены в этой сборке приложения."
        else -> "Для push-уведомлений нужны работающие сервисы Google Play или Huawei HMS Core."
    }

    fun configured(provider: String): Boolean = when (provider) {
        FIREBASE -> BuildConfig.HAS_FIREBASE_CONFIG
        HUAWEI -> BuildConfig.HAS_HUAWEI_CONFIG
        else -> false
    }

    fun clientId(context: Context, provider: String): String = when (provider) {
        FIREBASE -> firebase(context)?.options?.projectId.orEmpty()
        HUAWEI -> huawei(context)?.getString("client/app_id").orEmpty()
        else -> ""
    }

    fun autoInit(context: Context, provider: String, enabled: Boolean) {
        when (provider) {
            FIREBASE -> if (firebase(context) != null) FirebaseMessaging.getInstance().isAutoInitEnabled = enabled
            HUAWEI -> if (huawei(context) != null) HmsMessaging.getInstance(context).isAutoInitEnabled = enabled
        }
    }

    suspend fun token(context: Context, provider: String): String = when (provider) {
        FIREBASE -> FirebaseMessaging.getInstance().token.awaitPushToken()
        HUAWEI -> {
            val appId = clientId(context, provider)
            check(appId.isNotBlank()) { "Настройки уведомлений Huawei отсутствуют." }
            val direct = withContext(Dispatchers.IO) { HmsInstanceId.getInstance(context).getToken(appId, "HCM") }.orEmpty()
            // Older EMUI versions deliver the requested token through onNewToken instead.
            direct.ifBlank { withTimeout(20_000) { huaweiTokens.first { it.isNotBlank() } } }
        }
        else -> error("Неизвестная служба уведомлений.")
    }

    fun huaweiTokenChanged(token: String) { if (token.isNotBlank()) huaweiTokens.value = token }

    internal fun huaweiData(raw: String): Map<String, String>? = runCatching {
        require(raw.toByteArray(Charsets.UTF_8).size <= 4096)
        val data = JSONObject(raw)
        data.keys().asSequence().associateWith { key -> data.get(key) as String }
    }.getOrNull()
}

private suspend fun <T> Task<T>.awaitPushToken(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { completed ->
        if (continuation.isActive) {
            if (completed.isSuccessful) continuation.resume(completed.result)
            else continuation.resumeWithException(completed.exception ?: IllegalStateException("Не удалось подключить push-уведомления."))
        }
    }
}

class KolbaHuaweiMessagingService : HmsMessageService() {
    override fun onNewToken(token: String) {
        PushTransports.huaweiTokenChanged(token)
        onNativePushToken(this, PushTransports.HUAWEI, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Backend only sends data messages: native account/opt-in checks control display.
        val data = PushTransports.huaweiData(message.data.orEmpty()) ?: return
        receiveNativePush(this, PushTransports.HUAWEI, data)
    }
}
