package ru.kolbabot.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Opt-in, real FCM transport probe. Run the whole class in ONE instrumentation invocation:
 * -e live_push true -e class ru.kolbabot.app.FirebaseDeliveryProbeTest
 *
 * a_prepare returns after writing externalFiles/live-push-probe.json. While b_verify waits,
 * send that file's data-only payload/token through the real FCM server API. The app is on HOME.
 * No receiver is invoked manually. Both normal tests skip unless live_push=true.
 * The token is never printed; cleanup removes its temporary file. A separate result JSON has
 * only notification evidence and whether notification permission was originally granted.
 * If permission was initially denied, the operator may revoke it AFTER instrumentation exits;
 * revoking a runtime permission during instrumentation can terminate the test process.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FirebaseDeliveryProbeTest {
    @Test fun a_prepareLivePushProbe() {
        requireLiveProbe()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        assumeTrue("Install the APK built with its Firebase configuration first.", BuildConfig.HAS_FIREBASE_CONFIG)
        val application = context as KolbaApplication
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        assumeTrue("Use an isolated emulator with push disabled for real accounts.",
            prefs.getLong("desired_user", 0) == 0L && prefs.getLong("bound_user", 0) == 0L)
        val firebase = FirebaseApp.getInstance()
        val projectId = firebase.options.projectId.orEmpty()
        assertTrue("Firebase project is not configured.", projectId.isNotBlank())
        val messaging = FirebaseMessaging.getInstance()
        val apiField = KolbaApplication::class.java.getDeclaredField("api\$delegate").apply { isAccessible = true }
        val originalApi = apiField.get(application)
        val originalPreferences = prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.filterIsInstance<String>().toSet() else value }
        val directory = context.getExternalFilesDir(null) ?: throw AssertionError("External test directory is unavailable.")
        val tokenFile = File(directory, "live-push-probe.json")
        val resultFile = File(directory, "live-push-probe-result.json")
        val permissionWasGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val fixture = ProbeFixture(context, prefs, originalPreferences, application, apiField, originalApi,
            messaging.isAutoInitEnabled, tokenFile, resultFile, permissionWasGranted)
        activeFixture = fixture
        try {
            // All site calls made by a token-rotation worker stay inside this synthetic ApiClient.
            // The real FCM SDK and receiver remain unchanged, including their recipient checks.
            apiField.set(application, lazy<ApiClient> { ProbeApi(context, projectId) })
            cancelRegistration(context)
            if (Build.VERSION.SDK_INT >= 33 && !permissionWasGranted) {
                shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            }
            assertTrue("Android notification permission is required for this probe.",
                Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            val manager = context.getSystemService(NotificationManager::class.java)
            assertTrue("Enable notifications for this emulator app before running the probe.", manager.areNotificationsEnabled())
            messaging.isAutoInitEnabled = true
            val token = Tasks.await(messaging.token, 60, TimeUnit.SECONDS)
            assertTrue("FCM did not issue a registration token.", token.isNotBlank())
            val nonce = UUID.randomUUID().toString()
            val id = System.currentTimeMillis()
            val installation = UUID.randomUUID().toString()
            val title = "KOLBABOT_FCM_PROBE_$nonce"
            val body = "FCM transport verified: $nonce"
            fixture.notificationId = id
            fixture.expectedTitle = title
            fixture.expectedBody = body
            assertTrue("Could not save the isolated probe binding.", prefs.edit()
                .putLong("desired_user", PROBE_USER).putLong("bound_user", PROBE_USER)
                .putString("latest_token", token).putString("registered_token", token)
                .putString("provider", PushTransports.FIREBASE)
                .putString("installation_id", installation).putLong("generation", prefs.getLong("generation", 0) + 1)
                .remove("registration_error").putString("seen", "[]").commit())
            if (resultFile.exists()) check(resultFile.delete()) { "Could not remove stale probe result." }
            writeJson(tokenFile, JSONObject().put("ready", true).put("project_id", projectId).put("token", token)
                .put("installation_id", installation).put("nonce", nonce).put("created_at_epoch_ms", id)
                .put("data", JSONObject().put("user_id", PROBE_USER.toString()).put("notification_id", id.toString())
                    .put("title", title).put("body", body).put("action_url", "/#notifications")))
            // Delivery must work while the app has no foreground activity.
            shell("input keyevent KEYCODE_HOME")
            fixture.ready = true
        } catch (failure: Throwable) {
            cleanupProbe()
            throw failure
        }
    }

    @Test fun b_verifyLivePushDelivery() {
        requireLiveProbe()
        val fixture = activeFixture
        assumeTrue("Run both methods in this class in the same instrumentation invocation.", fixture?.ready == true)
        fixture!!
        val timeout = InstrumentationRegistry.getArguments().getString("live_push_timeout_seconds")
            ?.toLongOrNull()?.coerceIn(30, 600) ?: 180L
        val deadline = SystemClock.elapsedRealtime() + timeout * 1000
        val manager = fixture.context.getSystemService(NotificationManager::class.java)
        try {
            var actual: android.service.notification.StatusBarNotification? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                actual = manager.activeNotifications.firstOrNull { posted ->
                    posted.tag == "push_$PROBE_USER" && posted.id == fixture.notificationId.hashCode() &&
                        posted.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == fixture.expectedTitle
                }
                if (actual != null) break
                SystemClock.sleep(200)
            }
            assertNotNull("The real FCM notification did not arrive before the probe timeout. No receiver was invoked manually.", actual)
            assertEquals("The delivered notification body differs from the nonce payload.", fixture.expectedBody,
                actual!!.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            assertNotNull("The notification must open the native inbox.", actual.notification.contentIntent)
            writeJson(fixture.resultFile, JSONObject().put("received", true).put("transport", "FCM")
                .put("user_id", PROBE_USER).put("notification_id", fixture.notificationId)
                .put("observed_title", fixture.expectedTitle).put("observed_body", fixture.expectedBody)
                .put("posted_at_epoch_ms", actual.postTime).put("verified_at_epoch_ms", System.currentTimeMillis())
                .put("notification_permission_originally_granted", fixture.permissionWasGranted))
            val visibleFor = InstrumentationRegistry.getArguments().getString("live_push_keep_visible_ms")?.toLongOrNull()?.coerceIn(0, 30_000) ?: 0L
            if (visibleFor > 0) SystemClock.sleep(visibleFor)
        } finally { cleanupProbe() }
    }

    companion object {
        private const val PREFS = "kolbabot_push_v1"
        private const val PROBE_USER = 900000999L
        private var activeFixture: ProbeFixture? = null

        private fun requireLiveProbe() {
            assumeTrue("Real FCM delivery probes run only with -e live_push true.",
                InstrumentationRegistry.getArguments().getString("live_push") == "true")
        }

        private fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }

        private fun cancelRegistration(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork("kolbabot_push_registration").result.get(10, TimeUnit.SECONDS)
            work.cancelUniqueWork("kolbabot_notification_sync").result.get(10, TimeUnit.SECONDS)
        }

        private fun writeJson(file: File, value: JSONObject) {
            FileOutputStream(file).use { output -> output.write(value.toString().toByteArray(Charsets.UTF_8)); output.fd.sync() }
        }

        @AfterClass @JvmStatic fun cleanupProbe() {
            val fixture = activeFixture ?: return
            activeFixture = null
            try {
                // Clear the temporary recipient first, before stopping workers or restoring the API.
                fixture.preferences.edit().remove("desired_user").remove("bound_user").remove("registered_token").commit()
                cancelRegistration(fixture.context)
                fixture.context.getSystemService(NotificationManager::class.java).cancel("push_$PROBE_USER", fixture.notificationId.hashCode())
                FirebaseMessaging.getInstance().isAutoInitEnabled = fixture.originalAutoInit
            } finally {
                fixture.apiField.set(fixture.application, fixture.originalApi)
                val edit = fixture.preferences.edit().clear()
                fixture.originalPreferences.forEach { (key, value) -> when (value) {
                    is String -> edit.putString(key, value)
                    is Long -> edit.putLong(key, value)
                    is Int -> edit.putInt(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                } }
                check(edit.commit()) { "Could not restore pre-probe preferences." }
                if (fixture.tokenFile.exists()) check(fixture.tokenFile.delete()) { "Could not remove the temporary FCM token file." }
            }
        }
    }
}

private class ProbeFixture(
    val context: Context,
    val preferences: SharedPreferences,
    val originalPreferences: Map<String, Any?>,
    val application: KolbaApplication,
    val apiField: Field,
    val originalApi: Any?,
    val originalAutoInit: Boolean,
    val tokenFile: File,
    val resultFile: File,
    val permissionWasGranted: Boolean,
) {
    var ready = false
    var notificationId = 0L
    var expectedTitle = ""
    var expectedBody = ""
}

/** Even an FCM token-refresh callback can only hit these local fixtures during the probe. */
private class ProbeApi(context: Context, private val projectId: String) : ApiClient(context, "https://push-probe.invalid") {
    init { userId = 900000999L }
    override suspend fun get(path: String): JSONObject = when (path) {
        "/api/auth/me" -> JSONObject().put("user", JSONObject().put("id", userId).put("is_admin", false))
        "/api/android/push/config" -> JSONObject().put("enabled", true).put("project_id", projectId)
        else -> throw AssertionError("Site access is forbidden during the isolated FCM probe.")
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        check(path == "/api/android/push/token") { "Site mutations are forbidden during the isolated FCM probe." }
        return JSONObject().put("registered", true)
    }
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Site mutations are forbidden during the isolated FCM probe.")
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Site mutations are forbidden during the isolated FCM probe.")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Site mutations are forbidden during the isolated FCM probe.")
}
