package ru.kolbabot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable fun AdminScreen(api: ApiClient, onBack: () -> Unit) {
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
    var section by rememberSaveable { mutableStateOf("") }
    val holder = rememberSaveableStateHolder()
    val back = { section = "" }
    BackHandler(section.isNotBlank()) { back() }
    holder.SaveableStateProvider(section) {
        when (section) {
            "orders" -> AdminOrdersScreen(api, back)
            "banners" -> AdminBannersScreen(api, back)
            "catalog", "handbook", "articles", "recipes", "faq", "equipment" -> AdminContentScreen(api, section, back)
            "users", "support", "notifications" -> AdminPeopleScreen(api, section, back)
            "dashboard", "security", "db", "tools" -> AdminToolsScreen(api, section, back)
            "carts", "reviews", "digital" -> AdminCommerceScreen(api, section, back)
            "store" -> AdminStoreScreen(api, back) { section = it }
            "2fa" -> AdminTwoFactorSetup(api, back)
            else -> Page("Админ-редактор", onBack = onBack) {
                    if (!user.optBoolean("admin_2fa_enabled")) SectionCard("Защита администратора") {
                        Text("Для доступа к административным действиям подключите приложение с одноразовыми кодами.")
                        Button(onClick = { section = "2fa" }) { Text("Настроить двухфакторный вход") }
                    }
                    Text("Те же данные и права доступа, что в админке сайта.")
                    val sections = listOf(
                        Triple("dashboard", "Обзор", "Продажи, пользователи и события"),
                        Triple("orders", "Заказы", "Статусы, возвраты и копирование данных"),
                        Triple("store", "Магазин", "Товары, варианты, категории, доставка и акции"),
                        Triple("banners", "Баннеры", "Общая карусель сайта и приложения"),
                        Triple("digital", "Электронные книги", "Файлы, версии и отправка покупателям"),
                        Triple("carts", "Корзины", "Собранные корзины покупателей"),
                        Triple("reviews", "Отзывы", "Оценки и модерация"),
                        Triple("catalog", "Категории и сорта", "Каталог рецептов"),
                        Triple("recipes", "Рецепты", "Состав, варианты ингредиентов, текст и фото"),
                        Triple("handbook", "Справочник", "Разделы и материалы"),
                        Triple("articles", "Статьи", "Публикации, изображения и комментарии"),
                        Triple("faq", "Частые вопросы", "Вопросы и ответы"),
                        Triple("equipment", "Оборудование", "Карточки и фотографии"),
                        Triple("support", "Поддержка", "Обращения и ответы"),
                        Triple("notifications", "Оповещения", "Общие и личные сообщения"),
                        Triple("users", "Пользователи", "Профили, доступ, бонусы и безопасность"),
                        Triple("db", "Журнал базы данных", "События и действия"),
                        Triple("security", "Безопасность", "Журнал безопасности"),
                        Triple("tools", "Инструменты и настройки", "Аналитика, тарифы, оформление, импорт и обслуживание")
                    )
                    val groups = listOf(
                        "Продажи и магазин" to setOf("dashboard", "orders", "store", "banners", "digital", "carts", "reviews"),
                        "Материалы" to setOf("catalog", "recipes", "handbook", "articles", "faq", "equipment"),
                        "Общение и пользователи" to setOf("support", "notifications", "users"),
                        "Управление" to setOf("db", "security", "tools")
                    )
                    groups.forEach { (group, keys) -> SectionCard(group) {
                        sections.filter { it.first in keys }.forEach { (key, title, description) ->
                            OutlinedCard(onClick = { section = key }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(title, style = MaterialTheme.typography.titleMedium)
                                        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    } }
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
