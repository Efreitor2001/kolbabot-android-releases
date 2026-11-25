package ru.kolbabot.app

import android.content.Context
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal const val TSEH_PROJECT_LIMIT = 2_000_000

internal data class TsehFile(val name: String, val mime: String, val bytes: ByteArray)

internal class TsehSnapshot(raw: String) {
    private val result = JSONObject(raw)
    val step = result.getInt("step")
    private val data = result.getJSONObject("data")
    private val parts = result.getJSONObject("parts")
    val toast: String get() = parts.optJSONObject("toast")?.optString("text").orEmpty()
    fun part(id: String): JSONObject? = parts.optJSONObject(id)
    fun field(path: String): Any? {
        val (group, key) = path.split('.', limit = 2)
        return data.optJSONObject(group)?.opt(key)?.takeUnless { it == JSONObject.NULL }
    }

    fun export(): TsehFile? {
        val file = result.optJSONObject("file") ?: return null
        val parts = file.getJSONArray("parts")
        val output = java.io.ByteArrayOutputStream()
        for (i in 0 until parts.length()) {
            when (val part = parts.get(i)) {
                is String -> output.write(part.toByteArray(Charsets.UTF_8))
                is org.json.JSONArray -> for (j in 0 until part.length()) output.write(part.getInt(j))
            }
        }
        return TsehFile(file.getString("name"), file.getString("type"), output.toByteArray())
    }
}

internal fun tsehDataSet(node: Element): JSONObject = JSONObject().apply {
    node.attributes().filter { it.key.startsWith("data-") }.forEach { attribute ->
        val words = attribute.key.removePrefix("data-").split('-')
        val key = words.first() + words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        put(key, attribute.value)
    }
}

internal class TsehEngine private constructor(private val runtime: QuickJs) : AutoCloseable {
    suspend fun command(action: String, payload: String = "null"): TsehSnapshot = withContext(Dispatchers.Default) {
        val expression = "nativeCommand(${JSONObject.quote(action)}, ${JSONObject.quote(payload)})"
        // QuickJS drains the import's microtasks before evaluation returns. Read the
        // resulting state separately because the import returns a JavaScript Promise.
        val rendered = if (action == "load") {
            runtime.evaluate<Any?>("$expression; void 0;")
            runtime.evaluate<String>("nativeSnapshot()")
        } else runtime.evaluate<String>(expression)
        TsehSnapshot(rendered)
    }

    override fun close() = runtime.close()

    companion object {
        suspend fun create(context: Context, source: String): TsehEngine = withContext(Dispatchers.Default) {
            require(source.length in 1..500_000) { "Не удалось открыть версию калькулятора." }
            val runtime = QuickJs.create(Dispatchers.Default)
            try {
                runtime.memoryLimit = 48L * 1024 * 1024
                runtime.evaluationTimeoutMillis = 2_000
                fun adapter(file: String) = context.assets.open("tseh/$file.js").bufferedReader().use { it.readText() }
                // QuickJS exposes no app functions, files, Android objects or network access.
                runtime.evaluate<Any?>(listOf(adapter("bridge"), source, adapter("actions"), "void 0;").joinToString("\n"), "tseh.js")
                TsehEngine(runtime)
            } catch (failure: Throwable) {
                runtime.close()
                throw failure
            }
        }
    }
}

internal class TsehDocument(val html: Document) {
    val hasAccess = html.selectFirst("main.panel > #step1") != null
    val buy = html.selectFirst("#tsehBuyLink")
    val productId = buy?.let { nativeDestination(it.attr("href"))?.productId } ?: 0L
    val priceLabel = buy?.text().orEmpty()

    fun stage(step: Int) = html.getElementById("step${step + 1}")?.children().orEmpty()
    val hero get() = html.selectFirst(".hero > div")
    val sidebar get() = html.selectFirst(".side > .panel")
    val footer get() = html.selectFirst("footer.privacy")?.text().orEmpty()
}

internal suspend fun loadTsehDocument(api: ApiClient): TsehDocument = withContext(Dispatchers.Default) {
    val (bytes, mime) = api.downloadFile("/tseh")
    require(mime.startsWith("text/html") && bytes.size in 1..400_000) { "Не удалось загрузить «Цех на бумаге»." }
    val html = Jsoup.parse(bytes.toString(Charsets.UTF_8))
    require(html.title().contains("Цех на бумаге", ignoreCase = true)) { "Сейчас «Цех на бумаге» недоступен." }
    TsehDocument(html)
}
