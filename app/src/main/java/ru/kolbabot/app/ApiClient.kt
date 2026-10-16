package ru.kolbabot.app

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KolbaApplication : Application() {
    val api by lazy { ApiClient(this) }
    override fun onCreate() { super.onCreate(); initializePush(this) }
}

class ApiException(val status: Int, override val message: String, val details: JSONObject = JSONObject()) : IOException(message)

fun JSONObject.items(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

internal object ApiContract {
    fun endpoint(base: String, path: String): URI {
        require(path.startsWith("/") && !path.startsWith("//") && !path.contains('\\')) { "Invalid API path" }
        val origin = URI(base)
        val result = origin.resolve(path)
        require(result.scheme == "https" && result.host == origin.host && result.port == origin.port && result.userInfo == null) { "Invalid API origin" }
        return result
    }
    fun unwrap(status: Int, source: String): JSONObject {
        val envelope = try { JSONObject(source) } catch (_: Exception) {
            throw ApiException(status, if (status == 404) "Сервер пока не поддерживает эту функцию." else "Сервер временно недоступен. Попробуйте ещё раз.")
        }
        if (status !in 200..299 || !envelope.optBoolean("ok")) {
            throw ApiException(status, envelope.optString("error", "Ошибка сервера ($status)"), envelope.optJSONObject("details") ?: JSONObject())
        }
        return when (val data = envelope.opt("data")) {
            is JSONObject -> data
            is JSONArray -> JSONObject().put("items", data)
            else -> JSONObject()
        }
    }
    fun challenge(verifier: String): String = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}

/** The app stores only a revocable site session, encrypted with a device-bound key. */
private class SessionVault(context: Context) {
    private val preferences = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("kolbabot_session_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("kolbabot_session_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): String? = try {
        preferences.getString("value", null)?.let {
            val bytes = Base64.decode(it, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    } catch (_: Exception) { clear(); null }
    fun write(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        preferences.edit().putString("value", Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)).apply()
    }
    fun clear() { preferences.edit().clear().apply() }
}

open class ApiClient(context: Context, val baseUrl: String = BuildConfig.BASE_URL) {
    private val applicationContext = context.applicationContext
    private val vault = SessionVault(context.applicationContext)
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val gate = Mutex()
    private var csrf = ""
    private val expired = MutableStateFlow(0)
    val sessionExpiry = expired.asStateFlow()
    @Volatile open var userId: Long = 0
        protected set(value) { field = value; onPushAccountChanged(applicationContext, value) }
    val authenticated: Boolean get() = userId > 0

    init {
        runCatching {
            val saved = JSONObject(vault.read() ?: "{}")
            if (saved.optString("origin") == baseUrl) {
                val now = System.currentTimeMillis()
                saved.items("cookies").forEach { item ->
                    val remaining = (item.optLong("expires") - now) / 1000
                    if (remaining > 0) cookies.cookieStore.add(URI(baseUrl), HttpCookie(item.getString("name"), item.getString("value")).apply {
                        path = item.optString("path", "/"); secure = true; isHttpOnly = true; maxAge = remaining; version = 0
                    })
                }
            }
        }.onFailure { vault.clear() }
    }

    private fun saveSession() {
        val now = System.currentTimeMillis()
        val list = JSONArray()
        cookies.cookieStore.cookies.filter { !it.hasExpired() && it.isHttpOnly }.forEach { cookie ->
            // Persistent site cookies have Max-Age; session-only cookies deliberately remain memory-only.
            if (cookie.maxAge > 0) list.put(JSONObject().put("name", cookie.name).put("value", cookie.value)
                .put("path", cookie.path ?: "/").put("expires", now + cookie.maxAge * 1000))
        }
        if (list.length() == 0) vault.clear() else vault.write(JSONObject().put("origin", baseUrl).put("cookies", list).toString())
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) { gate.withLock {
        cookies.cookieStore.removeAll(); csrf = ""; userId = 0; vault.clear()
    } }

    open suspend fun get(path: String): JSONObject = request("GET", path)
    open suspend fun post(path: String, body: JSONObject = JSONObject(), headers: Map<String, String> = emptyMap()): JSONObject = request("POST", path, body, headers)
    open suspend fun put(path: String, body: JSONObject = JSONObject(), headers: Map<String, String> = emptyMap()): JSONObject = request("PUT", path, body, headers)
    open suspend fun patch(path: String, body: JSONObject = JSONObject(), headers: Map<String, String> = emptyMap()): JSONObject = request("PATCH", path, body, headers)
    open suspend fun delete(path: String, body: JSONObject = JSONObject(), headers: Map<String, String> = emptyMap()): JSONObject = request("DELETE", path, body, headers)

    private suspend fun request(method: String, path: String, body: JSONObject? = null, headers: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        gate.withLock {
            val mobile = path.startsWith("/api/mobile/auth/")
            val anonymousAuth = mobile || path in setOf("/api/auth/login", "/api/auth/login/2fa", "/api/auth/register", "/api/auth/register/confirm", "/api/auth/password-reset/request", "/api/auth/password-reset/confirm")
            fun execute(): JSONObject {
                val response = transport(method, path, body, headers, anonymous = anonymousAuth)
                val data = ApiContract.unwrap(response.status, String(response.bytes, Charsets.UTF_8))
                if (response.csrf.isNotBlank()) csrf = response.csrf
                else if (data.optString("csrf_token").isNotBlank()) csrf = data.optString("csrf_token")
                if (path.substringBefore('?') in setOf("/api/auth/me", "/api/auth/login", "/api/auth/login/2fa", "/api/auth/register/confirm", "/api/mobile/auth/exchange") && data.has("user"))
                    userId = data.optJSONObject("user")?.optLong("id") ?: 0
                return data
            }
            try { execute() } catch (error: ApiException) {
                if (error.status == 401 && authenticated && !path.startsWith("/api/auth/login")) {
                    cookies.cookieStore.removeAll(); csrf = ""; userId = 0; vault.clear()
                    expired.value++
                }
                // Only a rejected CSRF request can be replayed; uncertain payments are never retried.
                if (!anonymousAuth && method != "GET" && error.status == 403 && error.message.contains("csrf", true)) {
                    val fresh = transport("GET", "/api/auth/me", null, emptyMap())
                    val me = ApiContract.unwrap(fresh.status, String(fresh.bytes, Charsets.UTF_8))
                    csrf = fresh.csrf.ifBlank { me.optString("csrf_token") }
                    execute()
                } else throw error
            }
        }
    }

    suspend fun download(path: String, body: JSONObject): ByteArray = withContext(Dispatchers.IO) { gate.withLock {
        val response = transport("POST", path, body, emptyMap(), maxBytes = 30 * 1024 * 1024)
        if (response.status !in 200..299 || !response.contentType.contains("application/pdf")) {
            ApiContract.unwrap(response.status, String(response.bytes, Charsets.UTF_8))
            throw ApiException(response.status, "Сервер не вернул PDF.")
        }
        response.bytes
    } }

    open suspend fun downloadFile(path: String, body: JSONObject? = null): Pair<ByteArray, String> = withContext(Dispatchers.IO) { gate.withLock {
        val response = transport(if (body == null) "GET" else "POST", path, body, emptyMap(), maxBytes = 100 * 1024 * 1024)
        if (response.status !in 200..299) ApiContract.unwrap(response.status, String(response.bytes, Charsets.UTF_8))
        Pair(response.bytes, response.contentType.ifBlank { "application/octet-stream" })
    } }

    open suspend fun upload(path: String, fileName: String, mimeType: String, bytes: ByteArray, fields: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) { gate.withLock {
        require(bytes.isNotEmpty() && bytes.size <= 100 * 1024 * 1024) { "Выберите непустой файл размером до 100 МБ." }
        val multipart = MultipartPayload(fileName, mimeType, bytes, fields)
        fun execute(): JSONObject {
            val response = transport("POST", path, null, emptyMap(), multipart = multipart)
            return ApiContract.unwrap(response.status, String(response.bytes, Charsets.UTF_8))
        }
        try { execute() } catch (failure: ApiException) {
            if (failure.status == 403 && failure.message.contains("csrf", true)) {
                val fresh = transport("GET", "/api/auth/me", null, emptyMap())
                val me = ApiContract.unwrap(fresh.status, String(fresh.bytes, Charsets.UTF_8))
                csrf = fresh.csrf.ifBlank { me.optString("csrf_token") }
                execute()
            } else throw failure
        }
    } }

    private data class Response(val status: Int, val bytes: ByteArray, val csrf: String, val contentType: String)
    private data class MultipartPayload(val fileName: String, val mimeType: String, val bytes: ByteArray, val fields: Map<String, String>)
    private fun transport(method: String, path: String, body: JSONObject?, headers: Map<String, String>, anonymous: Boolean = false, maxBytes: Int = 8 * 1024 * 1024, multipart: MultipartPayload? = null): Response {
        val uri = ApiContract.endpoint(baseUrl, path)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = if (path.startsWith("/api/admin/store/") && (path.contains("/book") || path.contains("digital-book") || path.contains("digital-sales"))) 210000 else 30000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "KolbaBotAndroid")
            connection.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME)
            connection.setRequestProperty("Origin", baseUrl)
            if (!anonymous) {
                cookies.get(uri, emptyMap()).forEach { (name, values) -> connection.setRequestProperty(name, values.joinToString("; ")) }
                if (method != "GET" && csrf.isNotBlank()) connection.setRequestProperty("X-CSRF-Token", csrf)
            }
            headers.forEach { (name, value) ->
                require(name.equals("Idempotency-Key", true) || name.equals("X-Idempotency-Key", true))
                connection.setRequestProperty(name, value)
            }
            if (multipart != null) {
                val boundary = "KolbaBot-" + java.util.UUID.randomUUID().toString()
                val filename = multipart.fileName.replace(Regex("[\\r\\n\"\\\\]"), "_").take(200)
                val mime = multipart.mimeType.takeIf { it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) } ?: "application/octet-stream"
                val prefix = buildString {
                    multipart.fields.forEach { (key, value) ->
                        require(key.matches(Regex("[a-zA-Z0-9_-]+")))
                        append("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n")
                    }
                    append("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: $mime\r\n\r\n")
                }.toByteArray(Charsets.UTF_8)
                val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.US_ASCII)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.setFixedLengthStreamingMode(prefix.size.toLong() + multipart.bytes.size + suffix.size)
                connection.outputStream.use { it.write(prefix); it.write(multipart.bytes); it.write(suffix) }
            } else if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val receivesSession = path in setOf("/api/mobile/auth/exchange", "/api/auth/login", "/api/auth/login/2fa", "/api/auth/register/confirm") && status in setOf(200, 201)
            if (!anonymous || receivesSession) {
                if (anonymous && receivesSession) cookies.cookieStore.removeAll()
                val responseHeaders = connection.headerFields.filterKeys { it != null }
                cookies.put(uri, responseHeaders)
                if (responseHeaders.keys.any { it.equals("Set-Cookie", true) }) saveSession()
            }
            val headerCsrf = connection.getHeaderField("X-CSRF-Token") ?: ""
            if (!anonymous && headerCsrf.isNotBlank()) csrf = headerCsrf
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } >= 0) {
                    if (output.size() + count > maxBytes) throw IOException("Ответ сервера слишком большой.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: ByteArray(0)
            if (bytes.size > maxBytes) throw IOException("Ответ сервера слишком большой.")
            if (status in 300..399) throw ApiException(status, "Адрес сервера изменился. Обновите приложение.")
            return Response(status, bytes, headerCsrf, connection.contentType ?: "")
        } catch (error: java.net.SocketTimeoutException) {
            throw IOException("Сервер не ответил вовремя. Если вы оформляли оплату, проверьте заказ перед повторной попыткой.", error)
        } catch (error: java.net.UnknownHostException) {
            throw IOException("Нет соединения с сервером. Проверьте интернет.", error)
        } finally { connection.disconnect() }
    }

    fun newVerifier(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let {
        Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
