package ru.kolbabot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal val LocalAdminChrome = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }
internal val LocalAdminScrollOffset = staticCompositionLocalOf<() -> Int> { { 0 } }

/** admin.html header and editor tabs share the page scroll, including on the dashboard. */
@Composable internal fun AdminChrome(user: JSONObject, bootstrap: JSONObject?, state: SiteChromeState?,
    section: String, sections: List<Pair<String, String>>, onSection: (String) -> Unit) {
    val scrollOffset = LocalAdminScrollOffset.current
    var heroHeight by remember { mutableIntStateOf(0) }
    var metaHeight by remember { mutableIntStateOf(0) }
    val headerGaps = with(LocalDensity.current) { 20.dp.roundToPx() }
    val name = user.optString("username").ifBlank {
        listOf(user.optString("first_name"), user.optString("last_name")).filter { it.isNotBlank() }.joinToString(" ")
    }.ifBlank { "Пользователь" }
    val greetingName = user.optString("username").ifBlank { user.optString("first_name") }
    Column(Modifier.zIndex(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdminChromeSurface(Modifier.onSizeChanged { heroHeight = it.height }) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("АДМИНКА", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .96.sp, color = SiteColors.Muted)
                Text("Колбабот Admin", style = TextStyle(brush = Brush.linearGradient(
                    0f to Color(0xFF1E3A8A), .45f to Color(0xFF3C61D3), 1f to Color(0xFF1B937C)),
                    fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold))
                Text("Привет${greetingName.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()}. Сайт готов к работе.", color = SiteColors.Muted)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Категорий" to bootstrap?.opt("categories_count"), "Оборудование" to bootstrap?.opt("equipment_count"),
                        "FAQ" to bootstrap?.opt("faq_count"), "Справочник" to bootstrap?.optJSONArray("handbook_sections")?.length()).forEach { (title, count) ->
                        Surface(shape = RoundedCornerShape(50), color = SiteColors.BackgroundTop, border = BorderStroke(1.dp, SiteColors.Border)) {
                            Text("$title: ${count ?: "—"}", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        AdminChromeSurface(Modifier.onSizeChanged { metaHeight = it.height }) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdminMeta("Пользователь", name, Modifier.weight(1f))
                    AdminMeta("Роль", "Администратор", Modifier.weight(1f))
                    AdminMeta("Уровень", "ADMIN", Modifier.weight(1f))
                }
                if (state != null) OutlinedButton(state.onLogout, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.busy) { Text("Выйти") }
            }
        }
        // app.js hides every site tab except admin on /admin; keep the actual runtime view.
        AdminChromeSurface(Modifier.zIndex(1f).offset { IntOffset(0, (scrollOffset() - heroHeight - metaHeight - headerGaps).coerceAtLeast(0)) }) {
            Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = true, onClick = {}, label = { Text("Админ") }, modifier = Modifier.widthIn(min = 132.dp))
                if (state != null) OutlinedButton(state.onNotifications, Modifier.size(44.dp), shape = RoundedCornerShape(50), contentPadding = PaddingValues(10.dp)) {
                    Icon(painterResource(R.drawable.ic_stat_notification), "Уведомления", Modifier.size(22.dp))
                }
            }
        }
        AdminChromeSurface {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Админ-редактор", fontFamily = FontFamily.Serif, fontSize = 16.8.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sections.forEach { (key, title) ->
                        FilterChip(selected = section == key, onClick = { onSection(key) }, modifier = Modifier.testTag("admin-tab-$key"), label = { Text(title) })
                    }
                }
            }
        }
        if (!state?.error.isNullOrBlank()) ErrorMessage(state!!.error)
    }
}

@Composable private fun AdminChromeSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .94f),
        border = BorderStroke(1.dp, SiteColors.Border), content = content)
}

@Composable private fun AdminMeta(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = SiteColors.BackgroundTop, border = BorderStroke(1.dp, SiteColors.Border)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 12.sp, color = SiteColors.Muted)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
