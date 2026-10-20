package ru.kolbabot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import org.json.JSONObject

internal class SiteChromeState {
    var cartCount by mutableIntStateOf(0)
    var openCartRequest by mutableIntStateOf(0)
    var user by mutableStateOf<JSONObject?>(null)
    var access by mutableStateOf<JSONObject?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")
    var onAccount: () -> Unit = {}
    var onLogout: () -> Unit = {}
    var onCart: () -> Unit = {}
    var onNotifications: () -> Unit = {}
    var onNavigate: (String) -> Unit = {}
}

internal val LocalSiteChrome = staticCompositionLocalOf<SiteChromeState?> { null }

/** Native rendering of index.html product-header and its mobile nav-actions. */
@Composable internal fun SiteChrome(state: SiteChromeState, scrollOffset: () -> Int = { 0 }) {
    var heroHeight by remember { mutableIntStateOf(0) }
    var metaHeight by remember { mutableIntStateOf(0) }
    val headerGaps = with(LocalDensity.current) { 14.dp.roundToPx() }
    val user = state.user
    val name = user?.optString("username").orEmpty().ifBlank {
        listOf(user?.optString("first_name"), user?.optString("last_name")).filterNotNull().joinToString(" ").trim()
    }.ifBlank { if (user == null) "Гость" else "Пользователь" }
    Column(Modifier.zIndex(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(Modifier.onSizeChanged { heroHeight = it.height }, shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .94f),
            border = BorderStroke(1.dp, SiteColors.CustomerBorder), shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("ДОМАШНИЕ РЕЦЕПТЫ И МАГАЗИН", fontSize = 10.sp, lineHeight = 14.sp,
                        letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold, color = SiteColors.Muted, maxLines = 1)
                    Text("Колбабот", style = TextStyle(brush = Brush.linearGradient(
                        0f to Color(0xFF1E3A8A), .45f to Color(0xFF3C61D3), 1f to Color(0xFF1B937C)),
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 23.2.sp, lineHeight = 24.36.sp))
                    Spacer(Modifier.height(4.dp))
                    Text(if (user == null) "Изучайте разделы без входа. Для оплаты, сохранения и кабинета войдите в аккаунт."
                        else "Привет, $name. Сайт готов к работе.", fontSize = 11.sp, lineHeight = 15.sp,
                        color = Color(0xFF687286), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Image(painterResource(R.drawable.site_robot), "Логотип сайта", Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
            }
        }
        Surface(Modifier.onSizeChanged { metaHeight = it.height }, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .94f), border = BorderStroke(1.dp, SiteColors.CustomerBorder)) {
            Row(Modifier.fillMaxWidth().padding(5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HeaderBadge(name, Modifier.weight(1f))
                HeaderBadge(siteTierLabel(user, state.access))
                androidx.compose.material3.TextButton(onClick = if (user == null) state.onAccount else state.onLogout,
                    enabled = !state.busy, shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp),
                    modifier = Modifier.heightIn(min = 32.dp), colors = ButtonDefaults.textButtonColors(containerColor = Color(0xFFEEF2FF), contentColor = SiteColors.Brand)) {
                    Text(if (user == null) "Войти" else "Выйти", fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
        Surface(Modifier.offset { IntOffset(0, (scrollOffset() - heroHeight - metaHeight - headerGaps).coerceAtLeast(0)) },
            shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .96f), border = BorderStroke(1.dp, SiteColors.CustomerBorder)) {
            Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(state.onCart, Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SiteColors.Accent.copy(alpha = 0.18f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SiteColors.Accent.copy(alpha = 0.09f), contentColor = Color(0xFF116A59))) {
                    Text("Корзина: ${state.cartCount}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                if (user != null) OutlinedButton(state.onNotifications, Modifier.size(44.dp), shape = RoundedCornerShape(50), contentPadding = PaddingValues(10.dp)) {
                    Icon(painterResource(R.drawable.ic_stat_notification), "Уведомления", Modifier.size(22.dp))
                }
            }
        }
        if (state.error.isNotBlank()) ErrorMessage(state.error)
    }
}

@Composable private fun HeaderBadge(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(11.dp), color = Color(0xFFF4F6FA)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 7.dp), fontWeight = FontWeight.Bold,
            fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun siteTierLabel(user: JSONObject?, access: JSONObject?): String {
    if (user == null) return "Демо"
    if (access == null) return "—"
    if (access.optBoolean("is_admin")) return "ADMIN"
    if (listOf("access_tier", "paid_tier", "donut_tier").any { access.optString(it).equals("premium", true) }) return "PREMIUM"
    return if (access.optBoolean("is_member")) "Базовый уровень" else "Без доступа"
}
