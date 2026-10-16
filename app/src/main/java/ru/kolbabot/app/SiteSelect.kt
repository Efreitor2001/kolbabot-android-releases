package ru.kolbabot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Native equivalent of the website's label + select, without a separate picker screen. */
@Composable
internal fun SiteSelect(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val admin = LocalSiteAdmin.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SiteColors.Ink)
        Box(Modifier.fillMaxWidth()) {
            Surface(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("site-select-$label")
                    .semantics { stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто" },
                shape = RoundedCornerShape(if (admin) 10.dp else 12.dp),
                color = if (admin) Color.White else SiteColors.InputBackground,
                border = BorderStroke(1.dp, if (admin) SiteColors.Border else SiteColors.CustomerBorder)) {
                Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(options.firstOrNull { it.first == value }?.second ?: "Выберите…", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, color = if (enabled) SiteColors.Ink else SiteColors.Muted)
                    Text("▾", color = SiteColors.Muted)
                }
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(text = { Text(text) }, modifier = Modifier.testTag("site-option-$key"),
                        onClick = { expanded = false; onSelected(key) },
                        trailingIcon = if (key == value) ({ Text("✓", color = SiteColors.Brand) }) else null)
                }
            }
        }
    }
}
