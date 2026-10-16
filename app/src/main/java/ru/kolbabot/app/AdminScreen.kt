package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable fun AdminScreen(api: ApiClient, onBack: () -> Unit) {
    CompositionLocalProvider(LocalSiteAdmin provides true) {
        AdminAccountScreen(api, onBack)
    }
}

@Composable private fun AdminAccountScreen(api: ApiClient, onBack: () -> Unit) {
    val enteredUserId = rememberSaveable { api.userId }
    val identity = rememberVerifiedAccount(api, enteredUserId)
    val canAdmin = identity.isAdmin(api, enteredUserId)
    val currentBack by rememberUpdatedState(onBack)
    val stateHolder = rememberSaveableStateHolder()
    LaunchedEffect(identity.checked, canAdmin, api.userId) {
        if ((identity.checked && !canAdmin) || api.userId != enteredUserId || enteredUserId <= 0) currentBack()
    }
    if (canAdmin) stateHolder.SaveableStateProvider(enteredUserId) {
        AuthorizedAdminScreen(api, identity.user!!, onBack)
    } else Page("Админ", onBack = onBack) {
        if (!identity.checked) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (identity.error.isNotBlank()) {
            ErrorMessage(identity.error)
            OutlinedButton(onClick = identity.refresh) { Text("Обновить аккаунт") }
        }
    }
}

@Composable private fun AuthorizedAdminScreen(api: ApiClient, user: JSONObject, onBack: () -> Unit) {
    var section by rememberSaveable { mutableStateOf("dashboard") }
    val holder = rememberSaveableStateHolder()
    val back = { section = "dashboard" }
    BackHandler(section != "dashboard") { back() }
    val sections = listOf("dashboard" to "Дашборд", "catalog" to "Каталог", "handbook" to "Справочник",
        "articles" to "Статьи", "recipes" to "Рецепты", "faq" to "FAQ", "equipment" to "Оборудование",
        "store" to "Магазин", "carts" to "Корзины", "orders" to "Заказы", "reviews" to "Отзывы",
        "support" to "Поддержка", "notifications" to "Оповещения", "users" to "Пользователи",
        "db" to "БД", "security" to "Безопасность", "tools" to "Инструменты")
    val siteChrome = LocalSiteChrome.current
    var bootstrap by remember(api) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(api) {
        try { bootstrap = api.get("/api/bootstrap") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Counts remain unavailable; section requests report their own failures. */ }
    }
    val chrome: @Composable () -> Unit = { AdminChrome(user, bootstrap, siteChrome, section, sections) { section = it } }
    CompositionLocalProvider(LocalAdminChrome provides chrome) {
        holder.SaveableStateProvider(section) {
        when (section) {
            "orders" -> Column { TextButton(onClick = { section = "digital" }) { Text("Электронные книги") }; Box(Modifier.weight(1f)) { AdminOrdersScreen(api, back) } }
            "banners" -> AdminBannersScreen(api, back)
            "catalog", "handbook", "articles", "recipes", "faq", "equipment" -> AdminContentScreen(api, section, back)
            "users", "support", "notifications" -> AdminPeopleScreen(api, section, back)
            "dashboard" -> AdminDashboardScreen(api, onBack, { section = it }, user.optString("username").ifBlank { user.optString("first_name", "администратор") })
            "security", "db", "tools" -> AdminToolsScreen(api, section, back)
            "carts", "reviews", "digital" -> AdminCommerceScreen(api, section, back)
            "store" -> AdminStoreScreen(api, back) { section = it }
            "2fa" -> AdminTwoFactorSetup(api, back)
            else -> AdminToolsScreen(api, "dashboard", onBack)
        }
        }
    }
}

@Composable private fun AdminTwoFactorSetup(api: ApiClient, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var setup by remember { mutableStateOf<JSONObject?>(null) }
    var code by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    Page("Двухфакторный вход", onBack = onBack) {
        Text("Добавьте ключ в приложение одноразовых кодов. Резервный код сохраните в надёжном месте — он понадобится при потере доступа к телефону.")
        ErrorMessage(error)
        if (setup == null) Button(enabled = !busy, onClick = { busy = true; scope.launch {
            try { setup = api.post("/api/auth/admin/2fa/setup/start") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message.orEmpty() }
            finally { busy = false }
        } }) { Text("Получить ключ") }
        setup?.let { data ->
            AdminCopyField("Ключ для приложения кодов", data.optString("secret"))
            AdminCopyField("Резервный код", data.optString("backup_code"))
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Код из приложения") }, singleLine = true)
            Button(enabled = !busy && code.length == 6, onClick = { busy = true; error = ""; scope.launch {
                try { api.post("/api/auth/admin/2fa/setup/confirm", JSONObject().put("code", code)); setup = null; onBack() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message.orEmpty() }
                finally { busy = false }
            } }) { Text("Подключить") }
        }
    }
}
