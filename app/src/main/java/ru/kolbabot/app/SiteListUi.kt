package ru.kolbabot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The site's FAQ +/- control, applied to native category and ingredient lists. */
@Composable
internal fun SiteDisclosure(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String = "",
    count: Int? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    bodyPadding: Dp = 0.dp,
    bodySpacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val admin = LocalSiteAdmin.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(if (admin) 10.dp else 15.dp),
        color = Color.White.copy(alpha = if (admin) 0.45f else 1f),
        border = BorderStroke(1.dp, if (admin) SiteColors.Border else Color(0xFF1D2A4E).copy(alpha = 0.08f))) {
        Column {
            Row(modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто" }
                .heightIn(min = if (admin) 44.dp else 56.dp).padding(horizontal = if (admin) 10.dp else 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (admin) Text(if (expanded) "▾" else "▸", color = SiteColors.Ink)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (count != null) SiteListBadge(count.toString(), accent)
                if (!admin) Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.08f)) {
                    Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) {
                        Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleMedium, color = accent)
                    }
                }
            }
            if (expanded) {
                HorizontalDivider(color = SiteColors.Border)
                Column(Modifier.fillMaxWidth().background(if (admin) Color.Transparent else Color(0xFFFBFCFE))
                    .padding(bodyPadding), verticalArrangement = Arrangement.spacedBy(bodySpacing), content = content)
            }
        }
    }
}

/** Compact image-and-text list rows follow .article-list-card in static/styles.css. */
@Composable
internal fun SiteListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    meta: String = "",
    framed: Boolean = true,
    thumbnail: (@Composable () -> Unit)? = null,
    badges: @Composable RowScope.() -> Unit = {},
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(if (framed) 14.dp else 0.dp),
        color = Color.White.copy(alpha = if (framed) 0.7f else 1f),
        border = if (framed) BorderStroke(1.dp, SiteColors.Border) else null) {
        Row(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp).padding(if (framed) 10.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            thumbnail?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = badges)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SiteListBadge(text: String, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = accent)
    }
}
