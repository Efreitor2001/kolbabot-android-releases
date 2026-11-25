package ru.kolbabot.app

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.ByteArrayOutputStream
import java.net.URI

/** Interprets the calculator's form and report templates as Compose controls. */
internal class TsehRenderer(
    private val snapshot: TsehSnapshot,
    private val editGeneration: Int,
    private val onClick: (Element) -> Unit,
    private val onField: (Element, String, Boolean, String) -> Unit,
    private val onLink: (String) -> Unit,
) {
    @Composable
    fun Children(children: List<Element>, location: String) {
        children.forEachIndexed { index, element ->
            val identity = listOf(element.id(), element.attr("data-product"), element.attr("data-pid"),
                element.attr("data-key"), element.attr("data-path"), element.attr("data-launchdone"))
                .filter(String::isNotBlank).joinToString("-").ifBlank { index.toString() }
            key(location, identity, index) { Node(element, "$location/$identity") }
        }
    }

    @Composable
    private fun Node(node: Element, location: String, usePart: Boolean = true) {
        if (node.hasClass("print-only") || node.hasAttr("hidden")) return
        val dynamic = if (usePart) dynamicPart(node) else null
        if (dynamic?.optBoolean("hidden") == true ||
            dynamic == null && node.attr("style").replace(" ", "").contains("display:none")) return

        val dynamicHtml = dynamic?.optString("html").orEmpty()
        val dynamicText = dynamic?.optString("text").orEmpty()
        if (dynamicHtml.isNotBlank() || dynamicText.isNotBlank()) {
            val hydrated = remember(node, dynamicHtml, dynamicText) {
                node.clone().empty().run { if (dynamicHtml.isBlank()) text(dynamicText) else html(dynamicHtml) }
            }
            Node(hydrated, location, usePart = false)
            return
        }
        val classes = node.classNames()
        when (node.normalName()) {
            "button" -> if (node.text().isNotBlank()) {
                TsehButton(node.text(), node.hasClass("btn-primary"), onClick = { onClick(node) })
            }
            "label" -> {
                val checkbox = node.selectFirst("input[type=checkbox]")
                if (checkbox != null) Field(checkbox, node.ownText().ifBlank { node.text() }, location)
                else if (node.attr("for") == "importJson") {
                    TsehButton(node.text()) { onClick(Element("button").attr("id", "importJson")) }
                } else Text(node.text(), style = MaterialTheme.typography.labelMedium)
            }
            "input", "select" -> Field(node, node.attr("aria-label"), location)
            "details" -> {
                val summary = node.children().firstOrNull { it.normalName() == "summary" }
                var expanded by rememberSaveable(location) { mutableStateOf(node.hasAttr("open")) }
                Surface(shape = RoundedCornerShape(12.dp), color = SiteColors.Soft,
                    border = BorderStroke(1.dp, SiteColors.Border), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (expanded) "▾" else "▸", color = SiteColors.Brand)
                            Text(summary?.text().orEmpty(), style = MaterialTheme.typography.titleSmall, color = SiteColors.Brand)
                        }
                        if (expanded) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Children(node.children().filter { it != summary }, location)
                        }
                    }
                }
            }
            "a" -> {
                val target = node.attr("href")
                if (target.startsWith("https://") || target.startsWith("http://")) TextButton({
                    if (runCatching { URI(target).host }.getOrNull() != null) onLink(target)
                }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(node.text()) }
            }
            "table" -> TsehTable(node)
            "hr" -> HorizontalDivider(color = SiteColors.Border)
            "h1", "h2", "h3", "h4" -> Text(node.text(), style = when (node.normalName()) {
                "h1" -> MaterialTheme.typography.displaySmall
                "h2" -> MaterialTheme.typography.headlineSmall
                "h3" -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }, fontWeight = FontWeight.ExtraBold)
            "ul", "ol" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                node.children().forEachIndexed { index, item ->
                    Text((if (node.normalName() == "ol") "${index + 1}. " else "• ") + item.text())
                }
            }
            "div", "section" -> when {
                "field" in classes -> {
                    val input = node.children().firstOrNull { it.normalName() in setOf("input", "select") }
                    val labelOverride = input?.takeIf { it.attr("data-key") == "rawKg" }?.let {
                        snapshot.part("[data-pid=\"${it.attr("data-pid")}\"][data-key=\"rawKg\"]:label")?.optString("text")
                    }
                    if (input != null) {
                        val label = node.children().firstOrNull { it.normalName() == "label" && it.selectFirst("input") == null }
                        Field(input, labelOverride ?: label?.text().orEmpty(), location)
                        Children(node.children().filter { it != label && it != input }, "$location/help")
                    } else Children(node.children(), location)
                }
                classes.any { it in panelStyles } -> TsehPanel(when {
                    classes.any { it in warningStyles } -> MaterialTheme.colorScheme.errorContainer
                    classes.any { it in highlightStyles } -> SiteColors.AccentSoft
                    "soft" in classes -> SiteColors.Soft
                    else -> SiteColors.Surface
                }) { Content(node, location) }
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Content(node, location) }
            }
            "script", "style", "summary" -> Unit
            else -> if (node.text().isNotBlank()) RichText(node)
        }
    }

    @Composable
    private fun Content(node: Element, location: String) {
        if (node.children().all { it.normalName() in inlineTags } && node.text().isNotBlank()) {
            RichText(node)
        } else {
            if (node.ownText().isNotBlank()) Text(node.ownText(), style = MaterialTheme.typography.bodyMedium)
            Children(node.children(), location)
        }
    }

    @Composable
    private fun Field(node: Element, label: String, location: String) {
        val inputKey = "$location/${tsehDataSet(node)}"
        val type = node.attr("type")
        if (type == "file" || type == "hidden") return
        val path = node.attr("data-path")
        val stored = path.takeIf(String::isNotBlank)?.let(snapshot::field)
        if (type == "checkbox") {
            val checked = stored as? Boolean ?: node.hasAttr("checked")
            Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) {
                onField(node, "", it, "change")
            }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(checked, onCheckedChange = null)
                Text(label)
            }
        } else if (node.normalName() == "select") {
            val options = node.select("option")
            val selected = stored?.toString()?.takeIf { it.isNotBlank() }
                ?: options.firstOrNull { it.hasAttr("selected") }?.attr("value")
                ?: options.firstOrNull()?.attr("value").orEmpty()
            SiteSelect(label, selected, options.map { it.attr("value") to it.text() },
                { onField(node, it, false, "change") }, enabled = !node.hasAttr("disabled"))
        } else {
            // Keep the cursor and the latest keystroke while the calculator works in the background.
            var value by remember(inputKey, editGeneration) { mutableStateOf(stored?.toString() ?: node.attr("value")) }
            val keyboard = when (node.attr("inputmode")) {
                "numeric" -> KeyboardType.Number
                "decimal" -> KeyboardType.Decimal
                else -> KeyboardType.Text
            }
            OutlinedTextField(value, { input ->
                value = input
                onField(node, input, false, "input")
            }, Modifier.fillMaxWidth(), singleLine = true, enabled = !node.hasAttr("disabled"),
                label = if (label.isBlank()) null else { { Text(label) } },
                placeholder = { Text(node.attr("placeholder")) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboard))
        }
    }

    private fun dynamicPart(node: Element): org.json.JSONObject? {
        val id = node.id()
        if (id.isNotBlank()) snapshot.part(id)?.let { return it }
        return listOf("data-sale-summary", "data-channel-est").firstNotNullOfOrNull { attr ->
            if (node.hasAttr(attr)) snapshot.part("[$attr=\"${node.attr(attr)}\"]") else null
        }
    }

    private companion object {
        val warningStyles = setOf("prod-warning", "warning")
        val highlightStyles = setOf("callout", "scenario-output", "tax-summary-note", "patent-banner", "sale-summary")
        val panelStyles = warningStyles + highlightStyles +
            setOf("block", "stat", "metric", "budgetrow", "routeitem", "rec", "channel", "tax-option", "tax-breakdown")
        val inlineTags = setOf("b", "strong", "em", "i", "span", "small", "br")
    }
}

@Composable
private fun RichText(node: Element) {
    val muted = node.normalName() == "small" || node.hasClass("help") || node.hasClass("footnote")
    Text(remember(node) { tsehText(node) },
        style = if (muted) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = if (node.hasClass("eyebrow")) SiteColors.Brand else if (muted) SiteColors.Muted else SiteColors.Ink)
}

private fun tsehText(node: Element): AnnotatedString = buildAnnotatedString {
    fun appendNode(child: Node) {
        when (child) {
            is TextNode -> append(child.text())
            is Element -> {
                if (child.normalName() == "br") {
                    append("\n")
                    return
                }
                val tag = child.normalName()
                if (tag in setOf("b", "strong")) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                if (tag in setOf("i", "em")) pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                child.childNodes().forEach(::appendNode)
                if (tag in setOf("b", "strong", "i", "em")) pop()
            }
        }
    }
    node.childNodes().forEach(::appendNode)
}

@Composable
private fun TsehTable(node: Element) {
    val rows = node.select("tr")
    val columns = rows.maxOfOrNull { it.childrenSize() } ?: return
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        rows.forEach { row ->
            val heading = row.children().any { it.normalName() == "th" }
            Row(Modifier.width((columns * 118).dp).background(if (heading) SiteColors.Soft else SiteColors.Surface)) {
                row.children().forEach { cell ->
                    Text(cell.text(), Modifier.width(118.dp).padding(8.dp), style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal)
                }
            }
            HorizontalDivider(Modifier.width((columns * 118).dp), color = SiteColors.Border)
        }
    }
}

internal fun tsehReportPdf(snapshot: TsehSnapshot): ByteArray {
    val report = Jsoup.parseBodyFragment(snapshot.part("report")?.optString("html").orEmpty())
    report.select("button,input,select,.no-print").remove()
    require(report.body().childrenSize() > 0)
    val blocks = buildList {
        add("КолбаБот · Цех на бумаге" to true)
        add(snapshot.part("reportSubtitle")?.optString("text").orEmpty() to false)
        fun walk(node: Element) {
            when (node.normalName()) {
                "tr" -> add(node.children().joinToString("   │   ") { it.text() } to false)
                "h1", "h2", "h3", "h4", "summary" -> add(node.text() to true)
                "p", "li", "small" -> add(node.text() to false)
                else -> if (node.children().all { it.normalName() in setOf("span", "strong", "b", "i", "br") }) {
                    add(node.text() to false)
                } else {
                    if (node.ownText().isNotBlank()) add(node.ownText() to false)
                    node.children().forEach(::walk)
                }
            }
        }
        report.body().children().forEach(::walk)
    }
    val pdf = PdfDocument()
    try {
        val width = 595
        val height = 842
        val margin = 36
        var number = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, number).create())
        var y = margin
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        blocks.filter { it.first.isNotBlank() }.forEach { (text, heading) ->
            paint.textSize = if (heading) 13f else 9.5f
            paint.typeface = if (heading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width - 2 * margin)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            for (line in 0 until layout.lineCount) {
                val lineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
                if (y + lineHeight > height - margin) {
                    pdf.finishPage(page)
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, ++number).create())
                    y = margin
                }
                val content = text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd()
                page.canvas.drawText(content, margin.toFloat(), (y + layout.getLineBaseline(line) - layout.getLineTop(line)).toFloat(), paint)
                y += lineHeight
            }
            y += if (heading) 6 else 3
        }
        pdf.finishPage(page)
        return ByteArrayOutputStream().use { pdf.writeTo(it); it.toByteArray() }
    } finally {
        pdf.close()
    }
}
