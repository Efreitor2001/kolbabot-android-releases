package ru.kolbabot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.net.URLEncoder

@Composable
internal fun OzonPickupPicker(api: ApiClient, selected: JSONObject?, onDismiss: () -> Unit, onSelect: (JSONObject) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var status by remember { mutableStateOf("Введите город, улицу или номер ПВЗ либо выберите точку на карте.") }
    var searchAttempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var manualAddress by rememberSaveable { mutableStateOf("") }
    var manualName by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val mapHeight = (LocalConfiguration.current.screenHeightDp * 0.44f).coerceIn(280f, 390f).dp
    LaunchedEffect(query, searchAttempt) {
        results = emptyList()
        loading = false
        if (query.trim().length < 2) {
            status = "Введите минимум два символа или выберите точку на карте."
            return@LaunchedEffect
        }
        status = "Ищем по мере ввода…"
        delay(280)
        loading = true
        try {
            val found = api.get("${OzonDelivery.API}points?q=${URLEncoder.encode(query.trim(), "UTF-8")}&limit=40")
            ensureActive()
            results = found.items("items").filter(OzonDelivery::validPoint)
            status = if (results.isEmpty()) "Ничего не найдено. Уточните город или улицу." else "Найдено: ${results.size}"
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = e.message ?: "Не удалось загрузить пункты Ozon. Повторите поиск." }
        finally { loading = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Ozon Delivery", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Выберите пункт выдачи", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onDismiss) { Text("Закрыть") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (manualOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(manualAddress, { manualAddress = it.take(500) }, Modifier.fillMaxWidth(),
                            label = { Text("Адрес ПВЗ Ozon") }, minLines = 3)
                        OutlinedTextField(manualName, { manualName = it.take(200) }, Modifier.fillMaxWidth(),
                            label = { Text("Название или номер ПВЗ (необязательно)") }, singleLine = true)
                        Text("Укажите адрес так, как он указан в Ozon. Такой ПВЗ будет обработан вручную.", style = MaterialTheme.typography.bodySmall)
                        Button({ onSelect(JSONObject().put("checkout_mode", "manual").put("full_address", manualAddress.trim())
                            .put("name", manualName.trim().ifBlank { "ПВЗ Ozon" })) }, enabled = manualAddress.trim().length >= 5) { Text("Использовать этот ПВЗ") }
                        OutlinedButton({ manualOpen = false }) { Text("Отмена") }
                    }
                    return@Column
                }
                OzonMap(api, selected, Modifier.fillMaxWidth().height(mapHeight).testTag("ozonPickupMap"), onSelect) { points ->
                    results = points
                    status = "В этом месте ${points.size} ПВЗ. Выберите нужный в списке."
                    keyboard?.hide()
                }
                OutlinedTextField(query, { query = it.take(240) }, Modifier.fillMaxWidth().testTag("ozonPointSearch"),
                    label = { Text("Город, улица или номер ПВЗ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchAttempt++; keyboard?.hide() }))
                Button({ searchAttempt++; keyboard?.hide() }, enabled = query.trim().length >= 2 && !loading) { Text("Найти") }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (results.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("ozonPointResults"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { point ->
                        Card(onClick = { onSelect(point) }, modifier = Modifier.fillMaxWidth().testTag("ozonPoint_${point.optLong("delivery_point_id")}"),
                            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(OzonDelivery.pointName(point), style = MaterialTheme.typography.titleSmall)
                                Text(point.optString("full_address"), style = MaterialTheme.typography.bodyMedium)
                                val type = if (point.optString("type") == "postamat") "Постамат" else "ПВЗ"
                                val storage = point.optInt("storage_period_days")
                                Text(type + if (storage > 0) " · хранение $storage дн." else "", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                OutlinedButton({ manualOpen = true }) { Text("Моего ПВЗ нет в списке") }
                }
            }
        }
    }
}
