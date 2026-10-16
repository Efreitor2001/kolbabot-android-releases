package ru.kolbabot.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

internal object AdminUserPickerContract {
    private fun text(user: JSONObject, key: String) = user.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim().orEmpty()
    fun label(user: JSONObject): String {
        val name = listOf(text(user, "first_name"), text(user, "last_name")).filter { it.isNotBlank() }.joinToString(" ")
        val username = text(user, "username")
        return listOf(name, username, text(user, "email"), "#${user.optLong("id")}")
            .filter { it.isNotBlank() }.distinct().joinToString(" · ")
    }
    fun matches(user: JSONObject, query: String): Boolean = query.trim().let { value ->
        value.isBlank() || listOf("id", "username", "email", "phone", "first_name", "last_name")
            .any { text(user, it).contains(value, ignoreCase = true) } ||
            "${text(user, "first_name")} ${text(user, "last_name")}".contains(value, ignoreCase = true)
    }
    fun lookupId(query: String): Long? = query.trim().removePrefix("#").toLongOrNull()?.takeIf { it > 0 }
}

/** One picker for profiles, recipients and journal filters. Only a selected server ID is submitted. */
@Composable
internal fun AdminUserPicker(api: ApiClient, selectedId: Long?, onSelected: (Long?) -> Unit,
    label: String = "Пользователь", enabled: Boolean = true, allowEmpty: Boolean = false,
    emptyLabel: String = "Все пользователи", tag: String = "admin-user-picker") {
    val owner = api.userId
    var users by remember(api, owner) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember(api, owner) { mutableStateOf(true) }
    var error by remember(api, owner) { mutableStateOf("") }
    var revision by remember(api, owner) { mutableIntStateOf(0) }
    var expanded by remember(api, owner) { mutableStateOf(false) }
    var query by remember(api, owner) { mutableStateOf("") }
    var visibleCount by remember(query) { mutableIntStateOf(50) }
    var extraUser by remember(api, owner, selectedId) { mutableStateOf<JSONObject?>(null) }
    var lookupUser by remember(api, owner, query) { mutableStateOf<JSONObject?>(null) }
    var lookupBusy by remember(api, owner, query) { mutableStateOf(false) }
    var lookupError by remember(api, owner, query) { mutableStateOf("") }
    LaunchedEffect(api, owner, revision) {
        loading = true; error = ""
        try {
            val result = api.get("/api/admin/users?limit=2000").items("items").filter { it.optLong("id") > 0 }.distinctBy { it.optLong("id") }
            if (api.userId == owner) users = result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (api.userId == owner) error = failure.message ?: "Не удалось загрузить пользователей." }
        finally { loading = false }
    }
    // A copied personal notification can refer to a user outside the first server page.
    LaunchedEffect(api, owner, selectedId, users, loading) {
        if (loading || selectedId == null || selectedId <= 0 || users.any { it.optLong("id") == selectedId }) return@LaunchedEffect
        try {
            val user = AdminPeopleContract.userFromDossier(api.get("/api/admin/users/$selectedId/dossier"))
            if (api.userId == owner && user.optLong("id") == selectedId) extraUser = user
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the original ID; never silently pick a different recipient. */ }
    }
    val lookupId = AdminUserPickerContract.lookupId(query)
    LaunchedEffect(api, owner, query, expanded, users) {
        if (!expanded || lookupId == null || users.any { it.optLong("id") == lookupId }) return@LaunchedEffect
        delay(350)
        lookupBusy = true
        try {
            val user = AdminPeopleContract.userFromDossier(api.get("/api/admin/users/$lookupId/dossier"))
            if (api.userId == owner && user.optLong("id") == lookupId) lookupUser = user
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { lookupError = failure.message ?: "Не удалось найти пользователя." }
        finally { lookupBusy = false }
    }
    val available = (users + listOfNotNull(extraUser, lookupUser)).distinctBy { it.optLong("id") }
    val selected = available.firstOrNull { it.optLong("id") == selectedId }
    val filtered = available.filter { AdminUserPickerContract.matches(it, query) || (lookupId != null && it.optLong("id") == lookupId) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val menuWidth = maxWidth
            OutlinedButton(enabled = enabled, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
                Text("$label: ${selected?.let(AdminUserPickerContract::label) ?: selectedId?.takeIf { it > 0 }?.let { "#$it" } ?: if (allowEmpty) emptyLabel else "Выберите пользователя"} ▾")
            }
            DropdownMenu(expanded && enabled, { expanded = false; query = "" }, modifier = Modifier.width(menuWidth).heightIn(max = 420.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Имя, логин, почта, телефон или ID") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("$tag-search"))
                if (allowEmpty) DropdownMenuItem(text = { Text(emptyLabel) }, onClick = {
                    if (api.userId == owner) onSelected(null)
                    expanded = false; query = ""
                })
                if (loading || lookupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotBlank()) {
                    Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                    DropdownMenuItem(text = { Text("Повторить загрузку") }, onClick = { revision++ })
                }
                if (lookupError.isNotBlank()) Text(lookupError, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                filtered.take(visibleCount).forEach { user ->
                    DropdownMenuItem(text = { Text(AdminUserPickerContract.label(user)) }, modifier = Modifier.testTag("$tag-user-${user.optLong("id")}"), onClick = {
                        if (api.userId == owner) { extraUser = user; onSelected(user.optLong("id")) }
                        expanded = false; query = ""
                    })
                }
                if (filtered.size > visibleCount) DropdownMenuItem(text = { Text("Показать ещё (${filtered.size - visibleCount})") }, onClick = { visibleCount += 50 })
                if (!loading && !lookupBusy && filtered.isEmpty() && error.isBlank()) Text("Пользователи не найдены.", Modifier.padding(12.dp))
                if (users.size >= 2000) Text("Загружены первые 2000 пользователей. Остальных можно найти по ID.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                DropdownMenuItem(text = { Text("Обновить список") }, onClick = { revision++ })
            }
        }
        if (!expanded && loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!expanded && error.isNotBlank()) {
            ErrorMessage(error)
            TextButton(onClick = { revision++ }, enabled = enabled) { Text("Повторить загрузку пользователей") }
        }
    }
}
