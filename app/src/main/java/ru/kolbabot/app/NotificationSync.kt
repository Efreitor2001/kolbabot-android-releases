package ru.kolbabot.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

internal const val OPEN_NOTIFICATIONS_EXTRA = "kolbabot_open_notifications"
private const val LEGACY_SYNC_NAME = "kolbabot_notification_sync"
private const val PUSH_PREFERENCES = "kolbabot_push_v1"
private const val REGISTRATION_WORK = "kolbabot_push_registration"
private const val CHANNEL_ID = "account_updates"
private const val DESIRED_USER = "desired_user"
private const val BOUND_USER = "bound_user"
private const val LATEST_TOKEN = "latest_token"
private const val REGISTERED_TOKEN = "registered_token"
private const val STARTUP_PERMISSION_REQUESTED = "startup_notification_permission_requested"
private val pushStateLock = Any()
private val registrationMutex = Mutex()
private fun pushPreferences(context: Context) = context.getSharedPreferences(PUSH_PREFERENCES, Context.MODE_PRIVATE)
private fun desiredUser(context: Context) = pushPreferences(context).getLong(DESIRED_USER, 0)
private fun boundProvider(context: Context) = pushPreferences(context).getString("provider", PushTransports.FIREBASE).orEmpty()
private fun registeredFor(context: Context, uid: Long): Boolean {
    val prefs = pushPreferences(context)
    return uid > 0 && prefs.getLong(DESIRED_USER, 0) == uid && prefs.getLong(BOUND_USER, 0) == uid &&
        !prefs.getBoolean("registration_pending", false) &&
        prefs.getString(REGISTERED_TOKEN, "").orEmpty().let { it.isNotBlank() && it == prefs.getString(LATEST_TOKEN, "") }
}
private fun notificationsAllowed(context: Context): Boolean =
    (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

internal object PushConsentContract {
    fun shouldRegister(userId: Long, currentUserId: Long, enabled: Boolean, allowed: Boolean): Boolean =
        userId > 0 && currentUserId == userId && enabled && allowed

    fun permissionResultBelongsTo(requestUserId: Long, currentUserId: Long, screenUserId: Long): Boolean =
        requestUserId > 0 && requestUserId == currentUserId && requestUserId == screenUserId
}

private fun pushEnabled(context: Context, uid: Long): Boolean =
    uid > 0 && pushPreferences(context).getBoolean("notifications_enabled_$uid", true)

private fun setPushEnabled(context: Context, uid: Long, enabled: Boolean) {
    if (uid > 0) pushPreferences(context).edit().putBoolean("notifications_enabled_$uid", enabled).apply()
}

internal data class AccountPush(val userId: Long, val notificationId: Long, val title: String, val body: String)
internal object PushContract {
    fun parse(data: Map<String, String>): AccountPush? {
        val uid = data["user_id"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val id = data["notification_id"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val title = data["title"].orEmpty().trim()
        val body = data["body"].orEmpty().trim()
        if (title.isBlank() && body.isBlank()) return null
        return AccountPush(uid, id, title.take(160), body.take(1500))
    }
    fun canDisplay(message: AccountPush, desired: Long, bound: Long, currentSessionUser: Long): Boolean =
        desired > 0 && message.userId == desired && message.userId == bound &&
            (currentSessionUser == 0L || currentSessionUser == message.userId)
    fun matchesProvider(incoming: String, registered: String): Boolean =
        incoming in setOf("firebase", "huawei") && incoming == registered
    fun nextSeen(previous: List<String>, key: String): List<String> = (previous.filterNot { it == key } + key).takeLast(512)
}

private fun localPushProblem(context: Context): String = PushTransports.problem(context)

/** Called at application startup; old periodic message polling is never resumed. */
fun initializePush(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(LEGACY_SYNC_NAME)
    context.getSharedPreferences(LEGACY_SYNC_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    // A temporary Play Services/HMS update must not revoke an existing opt-in.
    if (!PushTransports.configured(boundProvider(context))) cancelNotificationSync(context)
    else if (desiredUser(context) > 0) schedulePushRegistration(context)
}

/** Called after a confirmed session change, never for the initial cold-start userId=0. */
fun onPushAccountChanged(context: Context, userId: Long) {
    if (desiredUser(context) > 0 && desiredUser(context) != userId) cancelNotificationSync(context)
}

/** Immediate local privacy boundary: queued messages from the previous account are discarded. */
fun cancelNotificationSync(context: Context) {
    synchronized(pushStateLock) {
        val prefs = pushPreferences(context)
        prefs.edit().remove(DESIRED_USER).remove(BOUND_USER).remove(REGISTERED_TOKEN)
            .remove("registration_error").remove("registration_pending")
            .putLong("generation", prefs.getLong("generation", 0) + 1).commit()
        NotificationManagerCompat.from(context).cancelAll()
    }
    WorkManager.getInstance(context).cancelUniqueWork(REGISTRATION_WORK)
    WorkManager.getInstance(context).cancelUniqueWork(LEGACY_SYNC_NAME)
    listOf(PushTransports.FIREBASE, PushTransports.HUAWEI).forEach { provider ->
        runCatching { PushTransports.autoInit(context, provider, false) }
    }
}

/** Unbinds the exact token while the old session still exists; local opt-out does not depend on the network. */
suspend fun disablePush(context: Context, api: ApiClient) {
    val provider = boundProvider(context)
    val binding = synchronized(pushStateLock) {
        val prefs = pushPreferences(context)
        Triple(prefs.getLong(BOUND_USER, 0), prefs.getString(REGISTERED_TOKEN, "").orEmpty(), prefs.getString("installation_id", "").orEmpty())
    }
    cancelNotificationSync(context)
    if (binding.first <= 0 || binding.first != api.userId || binding.second.isBlank() || binding.third.isBlank()) return
    try {
        withTimeoutOrNull(5_000) { api.delete("/api/android/push/token", JSONObject().put("token", binding.second).put("installation_id", binding.third)
            .apply { if (provider != PushTransports.FIREBASE) put("provider", provider) }) }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { /* Stale server deliveries are blocked by the local recipient check. */ }
}

private suspend fun verifyPushServer(context: Context, api: ApiClient,
                                     provider: String = if (desiredUser(context) > 0) boundProvider(context) else PushTransports.select(context).orEmpty()) {
    check(localPushProblem(context).isBlank()) { localPushProblem(context) }
    check(PushTransports.available(context, provider)) { "Служба уведомлений устройства временно недоступна. Проверьте сервисы Google Play или Huawei HMS Core." }
    val config = api.get(if (provider == PushTransports.HUAWEI) "/api/android/push/config?provider=huawei" else "/api/android/push/config")
    check(config.optBoolean("enabled")) { "Служба push-уведомлений пока не подключена на сервере." }
    val clientId = PushTransports.clientId(context, provider)
    val key = if (provider == PushTransports.HUAWEI) "app_id" else "project_id"
    check(clientId.isNotBlank() && config.optString(key) == clientId) { "Настройки уведомлений приложения и сервера не совпадают." }
}

private suspend fun enablePush(context: Context, api: ApiClient, uid: Long) = registrationMutex.withLock {
    check(uid > 0 && api.userId == uid) { "Войдите в аккаунт ещё раз." }
    check(pushEnabled(context, uid)) { "Уведомления выключены в кабинете." }
    check(notificationsAllowed(context)) { "Разрешите уведомления в настройках Android." }
    val provider = PushTransports.select(context) ?: error(localPushProblem(context))
    val prefs = pushPreferences(context)
    val generation = synchronized(pushStateLock) {
        val next = prefs.getLong("generation", 0) + 1
        val install = prefs.getString("installation_id", "").orEmpty().ifBlank { UUID.randomUUID().toString() }
        check(prefs.edit().putLong(DESIRED_USER, uid).remove(BOUND_USER).remove(REGISTERED_TOKEN)
            .remove("registration_error").putBoolean("registration_pending", true).remove(LATEST_TOKEN).putString("provider", provider)
            .putLong("generation", next).putString("installation_id", install).commit()) { "Не удалось сохранить настройку уведомлений." }
        next
    }
    // Keep the account preference across temporary server/token failures. No SDK
    // token request starts until the server confirms the matching provider.
    verifyPushServer(context, api, provider)
    check(api.userId == uid && desiredUser(context) == uid && prefs.getLong("generation", 0) == generation) { "Аккаунт изменился. Включите уведомления заново." }
    PushTransports.autoInit(context, provider, true)
    val token = withTimeoutOrNull(25_000) { PushTransports.token(context, provider) }
        ?: error("Служба уведомлений не ответила. Подключение будет повторено при доступном интернете.")
    synchronized(pushStateLock) {
        check(desiredUser(context) == uid && prefs.getLong("generation", 0) == generation && api.userId == uid) { "Аккаунт изменился. Включите уведомления заново." }
        check(prefs.edit().putString(LATEST_TOKEN, token).commit()) { "Не удалось сохранить настройку уведомлений." }
    }
    registerCurrentToken(context, api, uid, generation, token)
}

private suspend fun registerCurrentToken(context: Context, api: ApiClient, uid: Long, generation: Long, token: String) {
    val prefs = pushPreferences(context)
    check(token.isNotBlank()) { "Служба уведомлений не выдала адрес устройства." }
    check(desiredUser(context) == uid && api.userId == uid && prefs.getLong("generation", 0) == generation) { "Аккаунт изменился." }
    val install = prefs.getString("installation_id", "").orEmpty()
    val provider = boundProvider(context)
    check(install.isNotBlank()) { "Включите уведомления заново." }
    val result = api.post("/api/android/push/token", JSONObject().put("token", token).put("installation_id", install)
        .apply { if (provider != PushTransports.FIREBASE) put("provider", provider) })
    check(result.optBoolean("registered")) { "Сервер не подтвердил подключение уведомлений." }
    synchronized(pushStateLock) {
        check(desiredUser(context) == uid && api.userId == uid && prefs.getLong("generation", 0) == generation) { "Аккаунт изменился." }
        check(prefs.edit().putLong(BOUND_USER, uid).putString(REGISTERED_TOKEN, token)
            .remove("registration_error").remove("registration_pending").commit()) { "Не удалось сохранить настройку уведомлений." }
    }
}

private fun schedulePushRegistration(context: Context) {
    if (desiredUser(context) <= 0 || !PushTransports.configured(boundProvider(context))) return
    pushPreferences(context).edit().remove("registration_error").putBoolean("registration_pending", true).apply()
    val work = OneTimeWorkRequestBuilder<PushTokenRegistrationWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
    WorkManager.getInstance(context).enqueueUniqueWork(REGISTRATION_WORK, ExistingWorkPolicy.REPLACE, work)
}

/** WorkManager retries token registration only. Messages arrive through FCM or Huawei Push. */
class PushTokenRegistrationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = registrationMutex.withLock {
        val context = applicationContext
        val uid = desiredUser(context)
        if (uid <= 0 || !pushEnabled(context, uid) || !notificationsAllowed(context) || localPushProblem(context).isNotBlank()) return@withLock Result.success()
        val api = (context as? KolbaApplication)?.api ?: return@withLock Result.failure()
        try {
            val me = api.get("/api/auth/me").optJSONObject("user")?.optLong("id") ?: 0
            if (me != uid || desiredUser(context) != uid) {
                if (desiredUser(context) == uid) cancelNotificationSync(context)
                return@withLock Result.success()
            }
            val provider = boundProvider(context)
            verifyPushServer(context, api, provider)
            val prefs = pushPreferences(context)
            val generation = prefs.getLong("generation", 0)
            if (api.userId != uid || desiredUser(context) != uid) return@withLock Result.success()
            PushTransports.autoInit(context, provider, true)
            val token = withTimeoutOrNull(25_000) { PushTransports.token(context, provider) }
                ?: error("Push registration timed out.")
            synchronized(pushStateLock) {
                if (api.userId != uid || desiredUser(context) != uid || prefs.getLong("generation", 0) != generation) return@withLock Result.success()
                check(prefs.edit().putString(LATEST_TOKEN, token).commit()) { "Не удалось сохранить настройку уведомлений." }
            }
            // An idempotent server acknowledgement also restores a binding lost
            // on the server; OS permission or an old local flag is insufficient.
            registerCurrentToken(context, api, uid, generation, token)
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ApiException) {
            if (failure.status in listOf(401, 403)) cancelNotificationSync(context)
            if (failure.status >= 500 && runAttemptCount < 8) Result.retry() else registrationFailed(context, uid)
        } catch (_: Exception) { if (runAttemptCount < 8 && desiredUser(context) == uid) Result.retry() else registrationFailed(context, uid) }
    }
    private fun registrationFailed(context: Context, uid: Long): Result {
        synchronized(pushStateLock) {
            if (desiredUser(context) == uid) pushPreferences(context).edit()
                .putString("registration_error", "Не удалось подключить устройство к уведомлениям. Проверьте интернет и повторите подключение.").apply()
        }
        return Result.failure()
    }
}

class KolbaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        onNativePushToken(this, PushTransports.FIREBASE, token)
    }
    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only messages prevent SDK auto-display from bypassing the recipient check.
        if (message.notification != null) return
        receiveNativePush(this, PushTransports.FIREBASE, message.data)
    }
}

internal fun onNativePushToken(context: Context, provider: String, token: String) {
    if (token.isBlank() || !PushTransports.configured(provider)) return
    synchronized(pushStateLock) {
        if (!PushContract.matchesProvider(provider, boundProvider(context))) return
        pushPreferences(context).edit().putString(LATEST_TOKEN, token).commit()
    }
    schedulePushRegistration(context)
}

internal fun receiveNativePush(context: Context, provider: String, data: Map<String, String>) {
    if (!PushTransports.configured(provider) || !notificationsAllowed(context)) return
    val value = PushContract.parse(data) ?: return
    if (!pushEnabled(context, value.userId)) return
    synchronized(pushStateLock) {
        if (!PushContract.matchesProvider(provider, boundProvider(context))) return
        val prefs = pushPreferences(context)
        val currentUid = (context.applicationContext as? KolbaApplication)?.api?.userId ?: 0
        if (!PushContract.canDisplay(value, prefs.getLong(DESIRED_USER, 0), prefs.getLong(BOUND_USER, 0), currentUid)) return
        val seen = runCatching { JSONArray(prefs.getString("seen", "[]")) }.getOrDefault(JSONArray())
        val previous = (0 until seen.length()).map { seen.optString(it) }
        val key = "${value.userId}:${value.notificationId}"
        if (key in previous) return
        if (!prefs.edit().putString("seen", JSONArray(PushContract.nextSeen(previous, key)).toString()).commit()) return
        showAccountPush(context, value)
    }
}

private fun showAccountPush(context: Context, message: AccountPush) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Новости аккаунта и заказов", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Новые сообщения о заказах, поддержке и аккаунте Колбабота."
        lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
    })
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(OPEN_NOTIFICATIONS_EXTRA, true)
    }
    val pending = PendingIntent.getActivity(context, message.userId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val body = plainText(message.body)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_notification).setContentTitle(plainText(message.title).ifBlank { "Колбабот" })
        .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setCategory(NotificationCompat.CATEGORY_STATUS).build()
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    NotificationManagerCompat.from(context).notify("push_${message.userId}", message.notificationId.hashCode(), notification)
}

/** A narrow platform boundary keeps consent/permission UI tests free of real push registrations. */
internal interface PushSettingsPlatform {
    fun allowed(context: Context): Boolean
    fun needsPermission(context: Context): Boolean
    fun problem(context: Context): String
    suspend fun verify(context: Context, api: ApiClient)
    suspend fun enable(context: Context, api: ApiClient, uid: Long)
    suspend fun disable(context: Context, api: ApiClient)
    fun retry(context: Context)
}

private object NativePushSettingsPlatform : PushSettingsPlatform {
    override fun allowed(context: Context) = notificationsAllowed(context)
    override fun needsPermission(context: Context) = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    override fun problem(context: Context) = localPushProblem(context)
    override suspend fun verify(context: Context, api: ApiClient) = verifyPushServer(context, api)
    override suspend fun enable(context: Context, api: ApiClient, uid: Long) { enablePush(context, api, uid) }
    override suspend fun disable(context: Context, api: ApiClient) = disablePush(context, api)
    override fun retry(context: Context) = schedulePushRegistration(context)
}

/** Ask on launch, even for a guest. Bind a device only after the account is verified. */
@Composable internal fun NotificationStartup(api: ApiClient, verifiedUserId: Long,
    platform: PushSettingsPlatform = NativePushSettingsPlatform,
    requestPermission: (((Boolean) -> Unit) -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    var allowed by remember { mutableStateOf(platform.allowed(context)) }
    var enabled by remember(verifiedUserId) { mutableStateOf(pushEnabled(context, verifiedUserId)) }
    var availability by remember { mutableStateOf(platform.problem(context)) }
    var connecting by remember(api, verifiedUserId) { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = platform.allowed(context)
    }
    LaunchedEffect(Unit) {
        val prefs = pushPreferences(context)
        if (platform.needsPermission(context) && !prefs.getBoolean(STARTUP_PERMISSION_REQUESTED, false)) {
            // Persist before launching so rotation or another startup cannot repeat a refusal.
            prefs.edit().putBoolean(STARTUP_PERMISSION_REQUESTED, true).apply()
            if (requestPermission != null) requestPermission { allowed = platform.allowed(context) }
            else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    DisposableEffect(verifiedUserId) {
        val prefs = pushPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "notifications_enabled_$verifiedUserId") enabled = pushEnabled(context, verifiedUserId)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    OnResume {
        allowed = platform.allowed(context); availability = platform.problem(context)
        if (!connecting && PushConsentContract.shouldRegister(verifiedUserId, api.userId, enabled, allowed) &&
            desiredUser(context) == verifiedUserId && !registeredFor(context, verifiedUserId)) platform.retry(context)
    }
    LaunchedEffect(api, verifiedUserId, enabled, allowed, availability) {
        if (!PushConsentContract.shouldRegister(verifiedUserId, api.userId, enabled, allowed) || availability.isNotBlank()) return@LaunchedEffect
        if (desiredUser(context) == verifiedUserId) {
            if (!registeredFor(context, verifiedUserId)) platform.retry(context)
            return@LaunchedEffect
        }
        connecting = true
        try { platform.enable(context, api, verifiedUserId) }
        catch (cancelled: CancellationException) {
            if (api.userId == verifiedUserId && pushEnabled(context, verifiedUserId) && desiredUser(context) == verifiedUserId) platform.retry(context)
            throw cancelled
        } catch (failure: Exception) {
            if (api.userId == verifiedUserId && pushEnabled(context, verifiedUserId) && desiredUser(context) == verifiedUserId) {
                pushPreferences(context).edit().putString("registration_error", failure.message ?: "Не удалось подключить уведомления.").apply()
                platform.retry(context)
            }
        } finally { connecting = false }
    }
}

@Composable internal fun NotificationPreferences(api: ApiClient, platform: PushSettingsPlatform = NativePushSettingsPlatform,
    requestPermission: (((Boolean) -> Unit) -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    val uid = api.userId
    val scope = rememberCoroutineScope()
    var enabled by remember(uid) { mutableStateOf(pushEnabled(context, uid)) }
    var desired by remember(uid) { mutableStateOf(desiredUser(context) == uid && uid > 0) }
    var registered by remember(uid) { mutableStateOf(registeredFor(context, uid)) }
    var allowed by remember(uid) { mutableStateOf(platform.allowed(context)) }
    var busy by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf("") }
    var registrationError by remember(uid) { mutableStateOf(pushPreferences(context).getString("registration_error", "").orEmpty()) }
    var localAvailability by remember(uid) { mutableStateOf(platform.problem(context)) }
    var availability by remember(uid) { mutableStateOf("") }
    var serverReady by remember(uid) { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var permissionUser by rememberSaveable(uid) { mutableLongStateOf(0) }
    val pendingPermission = permissionUser > 0
    DisposableEffect(uid) {
        val prefs = pushPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            enabled = pushEnabled(context, uid)
            desired = desiredUser(context) == uid && uid > 0
            registered = registeredFor(context, uid)
            registrationError = prefs.getString("registration_error", "").orEmpty()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    OnResume {
        revision++; enabled = pushEnabled(context, uid); desired = desiredUser(context) == uid && uid > 0
        registered = registeredFor(context, uid); allowed = platform.allowed(context)
        if (desired && allowed && api.userId == uid && !registered) platform.retry(context)
    }
    LaunchedEffect(uid, revision) {
        localAvailability = platform.problem(context); availability = localAvailability; serverReady = false
        if (localAvailability.isBlank() && uid > 0) {
            try { platform.verify(context, api); if (api.userId == uid) serverReady = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { availability = failure.message ?: "Не удалось проверить службу уведомлений." }
        }
    }
    fun enable() {
        if (busy || uid <= 0 || api.userId != uid) return
        busy = true; error = ""
        scope.launch {
            try {
                platform.enable(context, api, uid)
                if (api.userId == uid) { desired = desiredUser(context) == uid; registered = registeredFor(context, uid); serverReady = true; availability = "" }
            } catch (cancelled: CancellationException) {
                if (api.userId == uid && desiredUser(context) == uid) platform.retry(context)
                throw cancelled
            } catch (failure: Exception) {
                // Permission and the account preference survive a temporary outage;
                // a binding is reported only after the POST has been acknowledged.
                desired = api.userId == uid && desiredUser(context) == uid
                registered = registeredFor(context, uid); serverReady = false
                error = failure.message ?: "Не удалось подключить уведомления."
                if (desired) platform.retry(context)
            } finally { busy = false; allowed = platform.allowed(context) }
        }
    }
    fun permissionResult(granted: Boolean) {
        val requestedFor = permissionUser; permissionUser = 0
        if (!PushConsentContract.permissionResultBelongsTo(requestedFor, api.userId, uid)) return
        allowed = platform.allowed(context)
        if (granted && allowed) enable() else error = "Уведомления не разрешены. Вы сможете включить их позже в кабинете."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), ::permissionResult)
    fun requestEnable() {
        if (busy || pendingPermission || api.userId != uid || uid <= 0) return
        setPushEnabled(context, uid, true); enabled = true; error = ""
        if (platform.needsPermission(context)) {
            permissionUser = uid
            if (requestPermission != null) requestPermission(::permissionResult) else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (platform.allowed(context)) enable()
        else error = "Android запрещает уведомления. Разрешите их в настройках и повторите подключение."
    }
    val connected = enabled && desired && registered && allowed && serverReady
    val status = when {
        !enabled -> "Уведомления приложения выключены"
        !allowed -> "Ожидаем разрешение Android"
        busy -> "Подключаем уведомления…"
        connected -> "Подключено к серверу для этого аккаунта"
        else -> "Подключение к серверу не завершено"
    }
    val details: @Composable ColumnScope.() -> Unit = {
        Text("Новые сообщения с сайта, заказы, ответы поддержки и новости будут приходить на телефон. Для доставки используется Google или Huawei. Уведомления можно выключить в кабинете.", style = MaterialTheme.typography.bodySmall)
        Text(if (allowed) "Android: уведомления разрешены" else "Android: уведомления пока не разрешены", Modifier.testTag("push-os-status"))
        Text(status, Modifier.testTag("push-server-status"))
        if (availability.isNotBlank()) Text(availability)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorMessage(error)
        if (desired && registrationError.isNotBlank()) ErrorMessage(registrationError)
        if (desired && !connected && !busy && !pendingPermission) Text("Выбор сохранён. Подключение будет повторено при доступном интернете; разрешение Android само по себе не подтверждает доставку.", style = MaterialTheme.typography.bodySmall)
        if (!allowed) TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Настройки уведомлений Android") }
    }
    SectionCard("Уведомления на телефоне") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Получать уведомления", Modifier.weight(1f))
            Switch(checked = enabled, enabled = !busy && !pendingPermission && uid > 0, modifier = Modifier.testTag("push-opt-in"), onCheckedChange = { checked ->
                if (!checked) {
                    setPushEnabled(context, uid, false); enabled = false
                    busy = true; error = ""
                    scope.launch { try { platform.disable(context, api); desired = false; registered = false } finally { busy = false } }
                } else requestEnable()
            })
        }
        details()
        if (enabled && !connected) TextButton(enabled = !busy && !pendingPermission && localAvailability.isBlank(), onClick = ::requestEnable) {
            Text(if (allowed) "Повторить подключение" else "Разрешить уведомления")
        }
        if (!serverReady && localAvailability.isBlank()) TextButton(enabled = !busy, onClick = { revision++ }) { Text("Проверить подключение") }
    }
}
