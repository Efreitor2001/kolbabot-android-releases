package ru.kolbabot.app

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal data class SiteOrderStatus(val label: String, val foreground: Long, val background: Long, val hint: String = "")

/** Labels: static/app.js storeOrderStatusText/customerOrderStatus; palette: styles.css 4491–4493. */
internal fun siteOrderStatus(status: String, customer: Boolean = false): SiteOrderStatus {
    val label = when (status) {
        "new" -> "Новый"; "payment_pending" -> "Ожидает оплаты"
        "paid" -> if (customer) "Ожидает сборки" else "Оплачен"
        "ready" -> "Готов"; "cancelled" -> "Отменён"
        "refunded" -> if (customer) "Возврат" else "Отменён / возврат"
        "payment_failed" -> "Ошибка оплаты"; "payment_expired" -> "Просрочен"
        else -> status.ifBlank { "—" }
    }
    val hint = if (!customer) "" else when (status) {
        "paid" -> "Оплата получена, скоро начнём сборку"
        "ready" -> "Заказ готов к выдаче или отправке"
        "cancelled" -> "Заказ отменён администратором"
        "refunded" -> "Заказ отменён администратором, оформлен возврат"
        else -> ""
    }
    return when (status) {
        "ready" -> SiteOrderStatus(label, 0xFF166534, 0xFFDCFCE7, hint)
        "cancelled", "refunded", "payment_failed", "payment_expired" -> SiteOrderStatus(label, 0xFF991B1B, 0xFFFEE2E2, hint)
        else -> SiteOrderStatus(label, 0xFF92400E, 0xFFFEF3C7, hint)
    }
}

@Composable internal fun OrderCopyText(value: String, text: String = value, modifier: Modifier = Modifier,
    emphasis: Boolean = false, small: Boolean = false) {
    val context = LocalContext.current
    Text(text, modifier.clickable(enabled = value.isNotBlank(), onClickLabel = "Скопировать") {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Заказ", value))
    }, style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis,
        color = if (small) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
}

@Composable internal fun OrderStatusPill(status: String, customer: Boolean = false, modifier: Modifier = Modifier) {
    val value = siteOrderStatus(status, customer)
    val context = LocalContext.current
    Surface(modifier.clickable(onClickLabel = "Скопировать статус") {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Статус", value.label))
    }, shape = RoundedCornerShape(999.dp), color = Color(value.background), contentColor = Color(value.foreground)) {
        Text(value.label, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** A compact list item; recipient, products and actions are shown only after opening the order. */
@Composable internal fun OrderCompactRow(order: JSONObject, customer: Boolean, onOpen: () -> Unit) {
    val id = order.optString("order_id")
    Surface(Modifier.fillMaxWidth().testTag("order-row-$id"), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderCopyText(id, "Заказ № $id", Modifier.weight(1f), emphasis = true)
                TextButton(onClick = onOpen, modifier = Modifier.testTag("open-order-$id"), contentPadding = PaddingValues(4.dp)) { Text("Подробнее") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                OrderStatusPill(order.optString("status"), customer, Modifier.weight(1f, fill = false).testTag("order-status-$id"))
                Spacer(Modifier.width(8.dp))
                OrderCopyText(money(order.optLong("total_kopecks")), emphasis = true)
            }
            if (order.optString("created_at").isNotBlank()) OrderCopyText(order.optString("created_at"), small = true)
            if (!customer && order.optString("subscription_tier_at_checkout") == "premium")
                Text("PREMIUM • ПРИОРИТЕТ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
