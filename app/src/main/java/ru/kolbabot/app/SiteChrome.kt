package ru.kolbabot.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.json.JSONObject

internal class SiteChromeState {
    var analyticsEnabled by mutableStateOf(false)
    var socialLinks by mutableStateOf<JSONObject?>(null)
    var cartCount by mutableIntStateOf(0)
    var unreadNotifications by mutableIntStateOf(0)
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

/** Mobile product-header from static/index.html. */
@Composable internal fun SiteChrome(state: SiteChromeState, scrollOffset: () -> Int = { 0 }) {
    var menu by remember { mutableStateOf(false) }
    val user = state.user
    val name = user?.optString("username").orEmpty().ifBlank {
        listOf(user?.optString("first_name"), user?.optString("last_name")).filterNotNull().joinToString(" ").trim()
    }.ifBlank { "Пользователь" }
    val compact = LocalConfiguration.current.screenWidthDp <= 430
    Surface(Modifier.fillMaxWidth().zIndex(1f).offset { IntOffset(0, scrollOffset()) }, color = SiteColors.BackgroundTop) {
        Column {
            FestiveHeader {
                Row(Modifier.fillMaxWidth().heightIn(min = 67.dp).padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.clickable { state.onNavigate("store") }, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SiteMascot(38.dp)
                        if (!compact) {
                            Text("Колбабот", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                    FilledTonalButton({ state.onNavigate("tseh") }, shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SiteColors.AccentSoft, contentColor = SiteColors.Brand)) {
                        Text("Цех", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp, Alignment.End)) {
                        SiteThemeButton(iconWidth = if (compact) 36.dp else 38.dp)
                        Button(state.onCart, shape = RoundedCornerShape(11.dp),
                            modifier = (if (compact) Modifier.weight(1f) else Modifier).height(40.dp).semantics {
                                contentDescription = "Открыть корзину. Товаров: ${state.cartCount}"
                            }, contentPadding = PaddingValues(horizontal = if (compact) 6.dp else 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SiteColors.AddToCart, contentColor = SiteColors.CartInk)) {
                            Icon(painterResource(R.drawable.site_nav_store), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Корзина · ${state.cartCount}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (user == null) {
                            Button(state.onAccount, Modifier.height(42.dp), shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SiteColors.Action, contentColor = Color.White)) {
                                Text("Войти", fontSize = 13.sp)
                            }
                        } else {
                            IconButton(state.onNotifications, Modifier.size(if (compact) 36.dp else 38.dp, 40.dp)) {
                                SiteNotificationIcon(state.unreadNotifications)
                            }
                            Box {
                                OutlinedIconButton({ menu = true }, Modifier.size(if (compact) 36.dp else 40.dp, 40.dp), shape = RoundedCornerShape(11.dp)) {
                                    Icon(painterResource(R.drawable.site_nav_account), "Меню аккаунта", Modifier.size(18.dp), tint = SiteColors.Ink)
                                }
                                DropdownMenu(menu, { menu = false }) {
                                    Text("$name · ${siteTierLabel(user, state.access)}", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = SiteColors.Muted)
                                    DropdownMenuItem({ Text("Личный кабинет") }, { menu = false; state.onAccount() })
                                    if (user.optBoolean("is_admin")) DropdownMenuItem({ Text("Управление сайтом") }, { menu = false; state.onNavigate("admin") })
                                    DropdownMenuItem({ Text("Выйти") }, { menu = false; state.onLogout() }, enabled = !state.busy)
                                }
                            }
                        }
                    }
                }
            }
            SiteSocialLinks(state.socialLinks)
            HorizontalDivider(color = SiteColors.Border)
            if (state.error.isNotBlank()) ErrorMessage(state.error)
        }
    }
}

@Composable internal fun SiteNotificationIcon(count: Int) {
    BadgedBox(badge = {
        if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) }
    }) {
        val label = if (count > 0) "Уведомления. Непрочитанных: $count" else "Уведомления"
        Icon(painterResource(R.drawable.ic_stat_notification), label, Modifier.size(19.dp), tint = SiteColors.Ink)
    }
}

private data class SiteSocial(val key: String, val name: String, val icon: Int, val fill: Long)

private val siteSocials = listOf(
    SiteSocial("vk", "ВКонтакте", R.drawable.site_social_vk, 0xFF0077FF),
    SiteSocial("max", "MAX", R.drawable.site_social_max, 0x00000000),
    SiteSocial("youtube", "YouTube", R.drawable.site_social_youtube, 0xFFFF0033),
    SiteSocial("instagram", "Instagram", R.drawable.site_social_instagram, 0xFFC13584),
    SiteSocial("telegram", "Telegram", R.drawable.site_social_telegram, 0xFF229ED9),
    SiteSocial("facebook", "Facebook", R.drawable.site_social_facebook, 0xFF1877F2),
    SiteSocial("tiktok", "TikTok", R.drawable.site_social_tiktok, 0xFF111111),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SiteSocialLinks(links: JSONObject?) {
    val visible = siteSocials.mapNotNull { item ->
        val url = links?.optString(item.key)?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
        item to url
    }
    if (visible.isEmpty()) return
    val context = LocalContext.current
    FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { (item, url) ->
            Surface({ openLink(context, url) }, Modifier.size(30.dp), shape = RoundedCornerShape(9.dp), color = Color(item.fill)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(item.icon), item.name, Modifier.size(if (item.key == "max") 30.dp else 17.dp),
                        tint = if (item.key == "max") Color.Unspecified else Color.White)
                }
            }
        }
    }
}

private fun siteTierLabel(user: JSONObject?, access: JSONObject?): String {
    if (user == null) return "Демо"
    if (access == null) return "—"
    if (access.optBoolean("is_admin")) return "ADMIN"
    if (listOf("access_tier", "paid_tier", "donut_tier").any { access.optString(it).equals("premium", true) }) return "PREMIUM"
    return if (access.optBoolean("is_member")) "Базовый уровень" else "Без доступа"
}
