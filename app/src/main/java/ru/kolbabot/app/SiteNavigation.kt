package ru.kolbabot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

internal data class SiteNavigationItem(val section: String, val label: String, val symbol: String = "")

// Labels, order, glyphs and vector paths are copied from static/index.html mobileBottomNav.
internal val siteBottomNavigation = listOf(
    SiteNavigationItem("store", "Магазин"),
    SiteNavigationItem("recipes", "Рецепты", "≡"),
    SiteNavigationItem("custom", "Расчёт", "∑"),
    SiteNavigationItem("account", "Кабинет"),
    SiteNavigationItem("more", "Ещё", "•••"),
)

internal val siteMoreNavigation = listOf(
    SiteNavigationItem("store", "Магазин"),
    SiteNavigationItem("recipes", "Рецепты"),
    SiteNavigationItem("custom", "Свой расчёт"),
    SiteNavigationItem("handbook", "Справочник"),
    SiteNavigationItem("articles", "Статьи"),
    SiteNavigationItem("equipment", "Оборудование"),
    SiteNavigationItem("faq", "FAQ"),
    SiteNavigationItem("support", "Поддержка"),
    SiteNavigationItem("account", "Кабинет"),
)

@Composable internal fun SiteBottomNavigation(section: String, moreOpen: Boolean, onSelect: (String) -> Unit) {
    val primary = setOf("store", "recipes", "custom", "account")
    Surface(color = Color.White.copy(alpha = 0.96f), shadowElevation = 8.dp) {
        Column {
            HorizontalDivider(color = Color(0xFF0F172A).copy(alpha = 0.1f))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 5.dp)) {
                siteBottomNavigation.forEach { item ->
                    val selected = if (item.section == "more") moreOpen || section !in primary else !moreOpen && section == item.section
                    val color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF596273)
                    Column(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        .selectable(selected, role = Role.Tab, onClick = { onSelect(item.section) })
                        .testTag("site-bottom-${item.section}").padding(horizontal = 2.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                      Box(Modifier.heightIn(min = 24.dp), contentAlignment = Alignment.Center) {
                    when (item.section) {
                        "store" -> Icon(painterResource(R.drawable.site_nav_store), null, Modifier.size(24.dp), tint = color)
                        "account" -> Icon(painterResource(R.drawable.site_nav_account), null, Modifier.size(24.dp), tint = color)
                        else -> Text(item.symbol, color = color, fontSize = 23.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                      }
                      Spacer(Modifier.height(2.dp))
                      Text(item.label, color = color, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SiteNavigationSheet(api: ApiClient, expectedUserId: Long, section: String,
                                             onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    // A restored sheet cannot reuse a saved role. This is the same fresh, lifecycle-aware
    // identity check used by the account and admin screens, which recheck on entry again.
    val identity = rememberVerifiedAccount(api, expectedUserId)
    val sameAccount = identity.checked && expectedUserId > 0 && api.userId == expectedUserId && identity.user?.optLong("id") == expectedUserId
    val canAdmin = identity.isAdmin(api, expectedUserId)
    var showAccess by remember(api, expectedUserId) { mutableStateOf(false) }
    LaunchedEffect(identity.user, sameAccount) {
        showAccess = false
        if (sameAccount) {
            try {
                val access = api.get("/api/access/status")
                // The site's openPaywall exposes this item only when access is missing.
                showAccess = api.userId == expectedUserId && access.has("can_use_app") && !access.optBoolean("can_use_app")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Unknown access never creates a privileged navigation entry. */ }
        }
    }
    val items = siteMoreNavigation +
        (if (sameAccount && showAccess) listOf(SiteNavigationItem("paywall", "Доступ")) else emptyList()) +
        (if (canAdmin) listOf(SiteNavigationItem("admin", "Админ")) else emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null,
        containerColor = Color.White, scrimColor = Color(0xFF0F172A).copy(alpha = 0.48f), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = minOf((LocalConfiguration.current.screenHeightDp * 0.78f).dp, 620.dp)).padding(horizontal = 14.dp, vertical = 16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(42.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD5D9E2)))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Навигация", style = MaterialTheme.typography.labelMedium)
                    Text("Все разделы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "Закрыть меню" }) { Text("×", fontSize = 24.sp) }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                val columns = if (LocalConfiguration.current.screenWidthDp <= 420) 1 else 2
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { item ->
                                OutlinedButton(onClick = { onSelect(item.section) },
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("site-more-${item.section}"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (section == item.section) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color(0xFF0F172A).copy(alpha = 0.12f)),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (section == item.section) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color(0xFFF6F7FA),
                                        contentColor = if (section == item.section) MaterialTheme.colorScheme.primary else Color(0xFF2C2D30)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(item.label, Modifier.fillMaxWidth(), fontSize = 14.sp)
                                }
                            }
                            if (row.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
