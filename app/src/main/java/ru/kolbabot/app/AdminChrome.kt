package ru.kolbabot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import org.json.JSONObject

internal val LocalAdminChrome = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }
internal val LocalAdminScrollOffset = staticCompositionLocalOf<() -> Int> { { 0 } }

private val adminNavigation = listOf(
    "" to listOf("dashboard" to "Дашборд"),
    "Продажи" to listOf("orders" to "Заказы", "store" to "Магазин", "carts" to "Корзины", "reviews" to "Отзывы"),
    "Материалы" to listOf("recipes" to "Рецепты", "articles" to "Статьи", "handbook" to "Справочник",
        "equipment" to "Оборудование", "faq" to "FAQ", "catalog" to "Каталог"),
    "Клиенты" to listOf("users" to "Пользователи", "support" to "Поддержка", "notifications" to "Оповещения"),
    "Система" to listOf("security" to "Безопасность", "tools" to "Инструменты", "db" to "БД"),
)

@Composable
internal fun AdminChrome(user: JSONObject, state: SiteChromeState?, section: String, onSite: () -> Unit, onSection: (String) -> Unit) {
    val scrollOffset = LocalAdminScrollOffset.current
    var navigationOpen by remember { mutableStateOf(false) }
    var accountMenuOpen by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().zIndex(1f).offset { IntOffset(0, scrollOffset()) },
        color = SiteColors.Surface, contentColor = SiteColors.Ink) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ navigationOpen = true }, Modifier.size(42.dp).testTag("admin-menu-open"),
                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(11.dp)) {
                    Text("☰", fontSize = 20.sp, modifier = Modifier.semantics { contentDescription = "Открыть меню админки" })
                }
                Row(Modifier.weight(1f).clickable(onClick = onSite), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SiteMascot(38.dp)
                    if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp > 360) {
                        Text("Колбабот", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
                SiteThemeButton()
                if (state != null) {
                    IconButton(state.onNotifications, Modifier.size(38.dp, 42.dp)) {
                        Icon(painterResource(R.drawable.ic_stat_notification), "Уведомления", Modifier.size(19.dp), tint = SiteColors.Ink)
                    }
                    Box {
                        OutlinedIconButton({ accountMenuOpen = true }, Modifier.size(40.dp), shape = RoundedCornerShape(11.dp)) {
                            Icon(painterResource(R.drawable.site_nav_account), "Меню аккаунта", Modifier.size(18.dp), tint = SiteColors.Ink)
                        }
                        DropdownMenu(accountMenuOpen, { accountMenuOpen = false }) {
                            Text(user.optString("username", "Администратор") + " · ADMIN", Modifier.padding(12.dp), color = SiteColors.Muted)
                            DropdownMenuItem({ Text("Перейти на сайт") }, { accountMenuOpen = false; onSite() })
                            DropdownMenuItem({ Text("Выйти") }, { accountMenuOpen = false; state.onLogout() }, enabled = !state.busy)
                        }
                    }
                }
            }
            HorizontalDivider(color = SiteColors.Border)
            state?.error?.takeIf(String::isNotBlank)?.let { ErrorMessage(it) }
        }
    }
    if (navigationOpen) AdminNavigationDrawer(section, onDismiss = { navigationOpen = false },
        onSite = { navigationOpen = false; onSite() },
        onSection = { navigationOpen = false; onSection(it) })
}

@Composable
private fun AdminNavigationDrawer(section: String, onDismiss: () -> Unit, onSite: () -> Unit, onSection: (String) -> Unit) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = minOf(280.dp, maxWidth - 40.dp)
            Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
            Surface(Modifier.width(width).fillMaxHeight(), color = SiteColors.Surface, contentColor = SiteColors.Ink) {
                Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Управление сайтом", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = SiteColors.Muted)
                        OutlinedButton(onDismiss, Modifier.size(38.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, SiteColors.CartBorder), colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SiteColors.CartIconBackground, contentColor = SiteColors.CartIcon)) {
                            Text("×", fontSize = 21.sp, modifier = Modifier.semantics { contentDescription = "Закрыть меню админки" })
                        }
                    }
                    adminNavigation.forEach { (label, entries) ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (label.isNotBlank()) Text(label.uppercase(), Modifier.padding(horizontal = 12.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, color = SiteColors.Muted)
                            entries.forEach { (key, title) ->
                                val selected = key == section
                                TextButton({ onSection(key) }, Modifier.fillMaxWidth().heightIn(min = 42.dp).testTag("admin-tab-$key"),
                                    shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (selected) SiteColors.AccentSoft else Color.Transparent,
                                        contentColor = if (selected) SiteColors.Brand else SiteColors.Muted)) {
                                    Text(title, Modifier.fillMaxWidth().padding(horizontal = 2.dp), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = SiteColors.Border)
                    TextButton(onSite) { Text("Перейти на сайт") }
                }
            }
        }
    }
}
