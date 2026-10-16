package ru.kolbabot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.roundToLong

private fun contentPath(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
private fun contentUrl(api: ApiClient, value: String): String = runCatching { URI(api.baseUrl).resolve(value).toString() }.getOrDefault(value)
private fun contentMoney(rubles: Double): String = money((rubles * 100).roundToLong())
private fun unitLabel(unit: String): String = when (unit) { "m" -> "м"; "ml" -> "мл"; "l" -> "л"; "pcs" -> "шт"; "kg" -> "кг"; else -> "г" }
private fun contentFailure(error: Exception): String { if (error is CancellationException) throw error; return error.message ?: "Не удалось выполнить запрос. Проверьте соединение и повторите." }

@Composable
fun RecipesScreen(api: ApiClient, onOpenProduct: ((Long) -> Unit)? = null) {
    var selected by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf(false) }
    var expandedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    if (custom) {
        BackHandler { custom = false }
        CustomCalculatorContent(api, onBack = { custom = false })
    } else if (selected.isNotEmpty()) {
        BackHandler { selected = "" }
        RecipeDetail(api, selected, onOpenProduct) { selected = "" }
    } else {
        RemotePage("Готовые рецепты", api, "/api/categories") { data, _ ->
            SectionCard("Что готовим?") {
                OutlinedTextField(search, { search = it }, label = { Text("Поиск по всем рецептам") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { custom = true }) { Text("Свой расчёт") }
            }
            val categories = data.items("items")
            val query = search.trim()
            val openCategory = expandedCategory ?: categories.firstOrNull()?.optString("slug")
            var foldedSearchGroups by rememberSaveable(query) { mutableStateOf(listOf<String>()) }
            var found = false
            categories.forEach { category ->
                val recipes = category.items("items").filter {
                    query.isBlank() || (it.optString("display_name") + " " + it.optString("slug") + " " + category.optString("title")).contains(query, true)
                }
                if (recipes.isNotEmpty()) {
                    found = true
                    val categorySlug = category.optString("slug")
                    // Search reads the complete API catalogue; folding a category never filters it out.
                    SiteDisclosure(category.optString("title"), expanded = if (query.isNotBlank()) categorySlug !in foldedSearchGroups else openCategory == categorySlug,
                        onToggle = {
                            if (query.isNotBlank()) foldedSearchGroups = if (categorySlug in foldedSearchGroups) foldedSearchGroups - categorySlug else foldedSearchGroups + categorySlug
                            else expandedCategory = if (openCategory == categorySlug) "" else categorySlug
                        },
                        count = recipes.size, modifier = Modifier.testTag("recipe-category-$categorySlug")) {
                        recipes.forEachIndexed { index, recipe ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SiteListRow(recipe.optString("display_name"), onClick = { selected = recipe.optString("slug") },
                                framed = false, modifier = Modifier.testTag("recipe-${recipe.optString("slug")}"))
                        }
                    }
                }
            }
            if (!found) Text(if (categories.isEmpty()) "Рецепты пока не опубликованы." else "По вашему запросу ничего не найдено.")
        }
    }
}

@Composable
private fun RecipeDetail(api: ApiClient, slug: String, onOpenProduct: ((Long) -> Unit)?, onBack: () -> Unit) {
    RemotePage("Рецепт", api, "/api/recipes/${contentPath(slug)}", onBack) { recipe, _ ->
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var weight by rememberSaveable(slug) { mutableStateOf("1") }
        var calculation by remember(slug) { mutableStateOf<JSONObject?>(null) }
        var calculatedRequest by remember(slug) { mutableStateOf<JSONObject?>(null) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        val choices = remember(slug, recipe) { mutableStateMapOf<String, String>().apply {
            recipe.items("ingredients").forEach { ingredient ->
                if (ingredient.optString("selected_option_key").isNotBlank()) put(ingredient.optString("full_name"), ingredient.optString("selected_option_key"))
            }
        } }
        val title = recipe.optString("display_name", "Рецепт")
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(recipe.optString("category_name"), style = MaterialTheme.typography.labelLarge)
        if (recipe.optString("recipe_photo_url").isNotBlank()) NetworkImage(contentUrl(api, recipe.optString("recipe_photo_url")), title, Modifier.fillMaxWidth().height(220.dp))
        var showPreparation by rememberSaveable(slug) { mutableStateOf(false) }
        SiteDisclosure("Приготовление", expanded = showPreparation, onToggle = { showPreparation = !showPreparation },
            summary = if (showPreparation) "" else plainText(recipe.optString("recipe_text")).take(160),
            bodyPadding = 14.dp, bodySpacing = 10.dp) {
            val text = recipe.optString("recipe_text")
            if (text.isBlank()) Text("Описание приготовления пока не добавлено.") else ContentHtml(api, text)
        }
        val ingredients = recipe.items("ingredients")
        if (ingredients.isNotEmpty()) SectionCard("Состав на 1 кг") {
            ingredients.forEach { ingredient ->
                key(ingredient.optString("full_name")) {
                    val options = ingredient.items("options")
                    Text(ingredient.optString("full_name"), style = MaterialTheme.typography.titleSmall)
                    if (options.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        val selected = options.firstOrNull { it.optString("option_key") == choices[ingredient.optString("full_name")] } ?: options.first()
                        Box {
                            OutlinedButton(onClick = { expanded = true }, enabled = !busy) { Text(selected.optString("full_name") + " ▾") }
                            DropdownMenu(expanded, { expanded = false }) {
                                options.forEach { option ->
                                    DropdownMenuItem(text = { Text(option.optString("full_name")) }, onClick = {
                                        expanded = false
                                        val ingredientName = ingredient.optString("full_name")
                                        val nextKey = option.optString("option_key")
                                        if (nextKey != selected.optString("option_key")) scope.launch {
                                            busy = true; error = ""; message = ""
                                            val nextChoices = JSONObject(choices.toMap()).put(ingredientName, nextKey)
                                            val previousRequest = calculatedRequest
                                            try {
                                                api.post("/api/recipes/${contentPath(slug)}/choices", JSONObject().put("choices", nextChoices))
                                                choices[ingredientName] = nextKey
                                                calculation = null; calculatedRequest = null
                                                message = "Выбор ингредиента сохранён."
                                                if (previousRequest != null) {
                                                    val request = JSONObject(previousRequest.toString()).put("choices", nextChoices)
                                                    calculation = api.post("/api/calc/recipe", request)
                                                    calculatedRequest = request
                                                }
                                            } catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
                                        }
                                    })
                                }
                            }
                        }
                        Text(selected.optString("value") + if (selected.optBoolean("is_numeric")) " ${unitLabel(selected.optString("amount_unit"))}" else "")
                        if (selected.optLong("product_id") > 0 && onOpenProduct != null) TextButton(enabled = !busy, onClick = { onOpenProduct(selected.optLong("product_id")) }) { Text("Открыть товар в магазине") }
                    } else Text(ingredient.optString("value") + if (ingredient.optBoolean("is_numeric")) " ${unitLabel(ingredient.optString("amount_unit"))}" else "")
                    if (options.isEmpty() && ingredient.optLong("product_id") > 0 && onOpenProduct != null) TextButton(enabled = !busy, onClick = { onOpenProduct(ingredient.optLong("product_id")) }) { Text("Открыть товар в магазине") }
                    HorizontalDivider()
                }
            }
        }
        SectionCard("Калькулятор") {
            OutlinedTextField(weight, { weight = it; calculation = null; calculatedRequest = null }, label = { Text("Вес партии (кг)") }, supportingText = { Text("Целое число от 1 кг") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Button(enabled = !busy && weight.toIntOrNull()?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    busy = true; error = ""; message = ""
                    try {
                        val request = JSONObject().put("sausage_slug", slug).put("weight_kg", weight.toInt()).put("choices", JSONObject(choices.toMap()))
                        calculation = api.post("/api/calc/recipe", request)
                        calculatedRequest = request
                    } catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
                }
            }) { Text(if (busy) "Выполняется…" else "Рассчитать") }
        }
        if (error.isNotBlank()) ErrorMessage(error)
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        calculation?.let { result ->
            CalculationResult(result, onOpenProduct)
            val cartItems = result.items("items").filter { it.optJSONObject("store_product") != null && it.optInt("cart_quantity") > 0 }
            if (cartItems.isNotEmpty()) SectionCard("Ингредиенты из магазина") {
                cartItems.forEach { item -> Text("${item.optJSONObject("store_product")?.optString("title")} × ${item.optInt("cart_quantity")}") }
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true; error = ""; message = ""
                        try {
                            val quantities = linkedMapOf<Int, Int>()
                            api.get("/api/store/cart").items("items").forEach { quantities[it.optInt("product_id")] = it.optInt("quantity") }
                            cartItems.forEach { item ->
                                val id = item.optJSONObject("store_product")!!.optInt("id")
                                quantities[id] = ((quantities[id] ?: 0) + item.optInt("cart_quantity")).coerceAtMost(999)
                            }
                            val array = JSONArray()
                            quantities.filterKeys { it > 0 }.forEach { (id, quantity) -> array.put(JSONObject().put("product_id", id).put("quantity", quantity)) }
                            api.put("/api/store/cart", JSONObject().put("items", array))
                            message = "Ингредиенты добавлены в корзину. Откройте магазин для оформления."
                        } catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
                    }
                }) { Text("Добавить ингредиенты в корзину") }
            }
            RecipePriceEditor(api, result, enabled = !busy, onSaved = {
                scope.launch {
                    busy = true; error = ""
                    try { calculatedRequest?.let { calculation = api.post("/api/calc/recipe", it) } }
                    catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
                }
            })
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    busy = true; error = ""
                    try { calculatedRequest?.let { sharePdf(context, api.download("/api/print/recipe-pdf", it), "recipe.pdf") } }
                    catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
                }
            }) { Text("Сохранить или отправить PDF") }
        }
    }
}

@Composable
private fun RecipePriceEditor(api: ApiClient, calculation: JSONObject, enabled: Boolean, onSaved: () -> Unit) {
    val editable = calculation.items("items").filter { !it.optBoolean("is_text") && it.optString("short_key").isNotBlank() && it.optInt("product_id") <= 0 }.distinctBy { it.optString("short_key") }
    if (editable.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val prices = remember(calculation) { mutableStateMapOf<String, String>().apply { editable.forEach { put(it.optString("short_key"), it.optString("unit_price", "0")) } } }
    val scope = rememberCoroutineScope()
    SectionCard {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть свои цены" else "Указать свои цены на ингредиенты") }
        if (expanded) {
            Text("Цены сохраняются в вашем аккаунте. Для товаров магазина используется актуальная цена магазина.", style = MaterialTheme.typography.bodySmall)
            editable.forEach { item ->
                val priceKey = item.optString("short_key")
                OutlinedTextField(prices[priceKey] ?: "", { prices[priceKey] = it }, label = { Text(item.optString("full_name")) }, suffix = { Text("₽/${unitLabel(item.optString("price_unit"))}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled && !saving, modifier = Modifier.fillMaxWidth())
            }
            if (error.isNotBlank()) ErrorMessage(error)
            Button(enabled = enabled && !saving, onClick = {
                scope.launch {
                    saving = true; error = ""
                    try {
                        val data = JSONArray()
                        prices.forEach { (key, raw) ->
                            val value = raw.replace(',', '.').toDoubleOrNull()
                            require(value != null && value.isFinite() && value >= 0) { "Укажите корректную цену для каждого ингредиента." }
                            data.put(JSONObject().put("price_key", key).put("price", value))
                        }
                        api.post("/api/user-prices/bulk", JSONObject().put("prices", data))
                        onSaved()
                    } catch (e: Exception) { error = contentFailure(e) } finally { saving = false }
                }
            }) { Text(if (saving) "Сохранение…" else "Сохранить и пересчитать") }
        }
    }
}

@Composable
private fun CalculationResult(data: JSONObject, onOpenProduct: ((Long) -> Unit)? = null) {
    SectionCard("Результат на ${data.optString("weight_kg")} кг") {
        Text(contentMoney(data.optDouble("total_cost", 0.0)), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("Себестоимость ингредиентов", style = MaterialTheme.typography.bodySmall)
        data.items("items").forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    val productId = item.optJSONObject("store_product")?.optLong("id") ?: item.optLong("product_id")
                    if (productId > 0 && onOpenProduct != null) TextButton(onClick = { onOpenProduct(productId) }, contentPadding = PaddingValues(0.dp)) {
                        Text(item.optString("full_name", item.optString("name")), style = MaterialTheme.typography.titleSmall)
                    } else Text(item.optString("full_name", item.optString("name")), style = MaterialTheme.typography.titleSmall)
                    Text(if (item.optBoolean("is_text")) item.optString("raw_value") else item.optString("amount_text"))
                }
                if (!item.optBoolean("is_text")) Text(contentMoney(item.optDouble("cost", 0.0)))
            }
            HorizontalDivider()
        }
    }
}

private data class CustomIngredient(val id: String = UUID.randomUUID().toString(), val name: String = "", val quantity: String = "", val unit: String = "г", val price: String = "")

private val ingredientDraftSaver = Saver<SnapshotStateList<CustomIngredient>, String>(
    save = { rows -> JSONArray().apply { rows.forEach { item -> put(JSONObject().put("id", item.id).put("name", item.name).put("quantity", item.quantity).put("unit", item.unit).put("price", item.price)) } }.toString() },
    restore = { saved ->
        val array = JSONArray(saved)
        (0 until array.length()).map { index -> array.getJSONObject(index).let { CustomIngredient(it.getString("id"), it.getString("name"), it.getString("quantity"), it.getString("unit"), it.getString("price")) } }.toMutableStateList()
    }
)

@Composable
fun CustomCalculatorScreen(api: ApiClient) = CustomCalculatorContent(api)

@Composable
private fun CustomCalculatorContent(api: ApiClient, onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var weight by rememberSaveable { mutableStateOf("1") }
    val rows = rememberSaveable(saver = ingredientDraftSaver) { mutableStateListOf(CustomIngredient()) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var lastRequest by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var expandedIngredient by rememberSaveable { mutableStateOf(rows.first().id) }
    Page("Свой состав", "Соберите собственную рецептуру и сразу увидьте вес ингредиентов и себестоимость.", onBack) {
        SectionCard("Ингредиенты на 1 кг") {
            OutlinedTextField(weight, { weight = it; result = null }, label = { Text("Вес партии (кг)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text("Название · граммы · цена", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEachIndexed { index, ingredient ->
            key(ingredient.id) {
                fun update(value: CustomIngredient) { val position = rows.indexOfFirst { it.id == ingredient.id }; if (position >= 0) rows[position] = value; result = null }
                val summary = listOfNotNull(
                    ingredient.quantity.takeIf { it.isNotBlank() }?.let { "$it ${ingredient.unit}" },
                    ingredient.price.takeIf { it.isNotBlank() }?.let { "$it ₽/${if (ingredient.unit in listOf("мл", "л")) "л" else "кг"}" }
                ).joinToString(" · ")
                SiteDisclosure(ingredient.name.ifBlank { "Ингредиент ${index + 1}" },
                    expanded = expandedIngredient == ingredient.id,
                    onToggle = { expandedIngredient = if (expandedIngredient == ingredient.id) "" else ingredient.id },
                    summary = summary, accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("custom-ingredient-${index + 1}"), bodyPadding = 12.dp, bodySpacing = 10.dp) {
                    OutlinedTextField(ingredient.name, { update(ingredient.copy(name = it)) }, label = { Text("Название") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ingredient.quantity, { update(ingredient.copy(quantity = it)) }, label = { Text("Количество на 1 кг") }, suffix = { Text(ingredient.unit) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("г", "кг", "мл", "л").forEach { unit -> FilterChip(selected = ingredient.unit == unit, onClick = { update(ingredient.copy(unit = unit)) }, enabled = !busy, label = { Text(unit) }) } }
                    OutlinedTextField(ingredient.price, { update(ingredient.copy(price = it)) }, label = { Text("Цена за ${if (ingredient.unit in listOf("мл", "л")) "1 л" else "1 кг"}, ₽") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (rows.size > 1) TextButton(enabled = !busy, onClick = {
                        rows.remove(ingredient); expandedIngredient = rows.getOrNull(index.coerceAtMost(rows.lastIndex))?.id ?: ""; result = null
                    }) { Text("Удалить ингредиент") }
                }
            }
        }
        OutlinedButton(onClick = { val next = CustomIngredient(); rows.add(next); expandedIngredient = next.id; result = null }, enabled = !busy && rows.size < 100, modifier = Modifier.fillMaxWidth()) { Text("+ Добавить") }
        if (error.isNotBlank()) ErrorMessage(error)
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                busy = true; error = ""
                try {
                    val kg = weight.replace(',', '.').toDoubleOrNull()
                    require(kg != null && kg.isFinite() && kg > 0) { "Укажите вес партии больше нуля." }
                    val lines = rows.mapIndexed { i, item ->
                        if (item.name.isBlank() || item.name.contains('|') || item.name.contains('\n')) expandedIngredient = item.id
                        require(item.name.isNotBlank() && !item.name.contains('|') && !item.name.contains('\n')) { "Укажите название ингредиента ${i + 1} без символа | и переноса строки." }
                        val quantity = item.quantity.replace(',', '.').toDoubleOrNull()
                        val price = item.price.replace(',', '.').toDoubleOrNull()
                        if (quantity == null || !quantity.isFinite() || quantity <= 0 || price == null || !price.isFinite() || price < 0) expandedIngredient = item.id
                        require(quantity != null && quantity.isFinite() && quantity > 0 && price != null && price.isFinite() && price >= 0) { "Проверьте количество и цену ингредиента ${i + 1}." }
                        "${item.name.trim()} | $quantity ${item.unit} | $price"
                    }
                    val request = JSONObject().put("weight_kg", kg).put("lines", JSONArray(lines))
                    result = api.post("/api/calc/custom", request)
                    lastRequest = request
                } catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
            }
        }) { Text(if (busy) "Выполняется…" else "Рассчитать состав") }
        result?.let { calculation ->
            CalculationResult(calculation)
            OutlinedButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true; error = ""
                    try { lastRequest?.let { sharePdf(context, api.download("/api/print/custom-pdf", it), "custom-recipe.pdf") } }
                    catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить или отправить PDF") }
        }
    }
}

@Composable
fun KnowledgeScreen(api: ApiClient, onOpenAccount: (() -> Unit)? = null,
                    initialArticleId: Long = 0, onArticleOpened: () -> Unit = {},
                    initialSection: String = "", onSectionOpened: () -> Unit = {},
                    onSectionChanged: (String) -> Unit = {}) {
    var section by rememberSaveable { mutableStateOf("Статьи") }
    var articleId by rememberSaveable { mutableStateOf(0) }
    var handbookSection by rememberSaveable { mutableStateOf("") }
    var handbookArticle by rememberSaveable { mutableStateOf("") }
    var equipmentId by rememberSaveable { mutableStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var expandedHandbookSection by rememberSaveable { mutableStateOf<String?>(null) }
    val currentSectionChanged by rememberUpdatedState(onSectionChanged)
    LaunchedEffect(section) {
        currentSectionChanged(when (section) {
            "Справочник" -> "handbook"
            "Оборудование" -> "equipment"
            "Вопросы" -> "faq"
            else -> "articles"
        })
    }
    LaunchedEffect(initialArticleId) {
        if (initialArticleId > 0 && initialArticleId <= Int.MAX_VALUE) {
            articleId = initialArticleId.toInt(); handbookArticle = ""; equipmentId = 0; section = "Статьи"
            onArticleOpened()
        }
    }
    LaunchedEffect(initialSection) {
        val requested = mapOf("articles" to "Статьи", "handbook" to "Справочник", "equipment" to "Оборудование", "faq" to "Вопросы")[initialSection]
        if (requested != null) {
            section = requested; search = ""
            if (initialArticleId <= 0) articleId = 0
            handbookArticle = ""; equipmentId = 0; onSectionOpened()
        }
    }
    when {
        articleId > 0 -> { BackHandler { articleId = 0 }; ArticleDetail(api, articleId, onOpenAccount) { articleId = 0 } }
        handbookArticle.isNotBlank() -> { BackHandler { handbookArticle = "" }; HandbookDetail(api, handbookSection, handbookArticle) { handbookArticle = "" } }
        equipmentId > 0 -> { BackHandler { equipmentId = 0 }; EquipmentDetail(api, equipmentId) { equipmentId = 0 } }
        else -> {
            val path = when (section) { "Справочник" -> "/api/handbook"; "Оборудование" -> "/api/equipment"; "Вопросы" -> "/api/faq"; else -> "/api/articles" }
            val heading = when (section) { "Справочник" -> "Справочник технолога"; "Вопросы" -> "Частые вопросы"; else -> section }
            RemotePage(heading, api, path) { data, _ ->
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Статьи", "Справочник", "Оборудование", "Вопросы").forEach { tab -> FilterChip(selected = section == tab, onClick = { section = tab; search = "" }, label = { Text(if (tab == "Вопросы") "FAQ" else tab) }) }
                }
                OutlinedTextField(search, { search = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val items = data.items("items")
                val query = search.trim()
                var foldedSearchGroups by rememberSaveable(section, query) { mutableStateOf(listOf<String>()) }
                var found = false
                when (section) {
                    "Справочник" -> {
                        Text("Выберите материал", style = MaterialTheme.typography.titleMedium)
                        val openSection = expandedHandbookSection ?: items.firstOrNull()?.optString("key")
                        items.forEach { group ->
                            val articles = group.items("articles").filter { query.isBlank() || (it.optString("title") + " " + it.optString("text") + " " + group.optString("title")).contains(query, true) }
                            if (articles.isNotEmpty()) {
                                found = true
                                val groupKey = group.optString("key")
                                SiteDisclosure(group.optString("title"), count = articles.size,
                                    expanded = if (query.isNotBlank()) groupKey !in foldedSearchGroups else openSection == groupKey,
                                    onToggle = {
                                        if (query.isNotBlank()) foldedSearchGroups = if (groupKey in foldedSearchGroups) foldedSearchGroups - groupKey else foldedSearchGroups + groupKey
                                        else expandedHandbookSection = if (openSection == groupKey) "" else groupKey
                                    }, modifier = Modifier.testTag("handbook-section-$groupKey")) {
                                    articles.forEachIndexed { index, article ->
                                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        SiteListRow(article.optString("title"), framed = false,
                                            modifier = Modifier.testTag("handbook-article-${article.optString("key")}"),
                                            onClick = { handbookSection = groupKey; handbookArticle = article.optString("key") })
                                    }
                                }
                            }
                        }
                    }
                    "Вопросы" -> items.filter { query.isBlank() || (it.optString("question") + " " + it.optString("answer")).contains(query, true) }.forEach { faq ->
                        found = true
                        key(faq.optInt("id")) {
                            var expanded by rememberSaveable(query) { mutableStateOf(query.isNotBlank()) }
                            SiteDisclosure(faq.optString("question"), expanded = expanded, onToggle = { expanded = !expanded },
                                modifier = Modifier.testTag("faq-question-${faq.optInt("id")}"), bodyPadding = 14.dp, bodySpacing = 10.dp) {
                                Column(Modifier.testTag("faq-answer-${faq.optInt("id")}")) { ContentHtml(api, faq.optString("answer")) }
                            }
                        }
                    }
                    "Оборудование" -> items.filter { query.isBlank() || (it.optString("title") + " " + it.optString("category_name") + " " + it.optString("description")).contains(query, true) }.forEach { item ->
                        found = true
                        SiteListRow(item.optString("title"), onClick = { equipmentId = item.optInt("id") },
                            modifier = Modifier.testTag("equipment-${item.optInt("id")}"),
                            subtitle = plainText(item.optString("description")),
                            thumbnail = if (item.optString("photo_url").isNotBlank()) ({
                                NetworkImage(contentUrl(api, item.optString("photo_url")), item.optString("title"), Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)))
                            }) else null,
                            badges = { if (item.optString("category_name").isNotBlank()) SiteListBadge(item.optString("category_name"), MaterialTheme.colorScheme.tertiary) })
                    }
                    else -> items.filter { query.isBlank() || (it.optString("title") + " " + it.optString("excerpt")).contains(query, true) }.forEach { article ->
                        found = true
                        val imageUrl = article.optString("card_image_url").ifBlank { article.optString("cover_image_url") }
                        SiteListRow(article.optString("title"), onClick = { articleId = article.optInt("id") },
                            modifier = Modifier.testTag("article-${article.optInt("id")}"),
                            subtitle = plainText(article.optString("excerpt")),
                            meta = "👍 ${article.optInt("likes")}  ·  💬 ${article.optInt("comments_count")}",
                            thumbnail = if (imageUrl.isNotBlank()) ({
                                NetworkImage(contentUrl(api, imageUrl), article.optString("title"), Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)))
                            }) else null,
                            badges = {
                                if (article.optBoolean("is_premium")) SiteListBadge("PREMIUM", MaterialTheme.colorScheme.secondary)
                                if (article.optBoolean("is_new")) SiteListBadge("NEW", MaterialTheme.colorScheme.error)
                            })
                    }
                }
                if (!found) Text(if (search.isBlank()) "В этом разделе пока нет материалов." else "По вашему запросу ничего не найдено.")
            }
        }
    }
}

@Composable
private fun HandbookDetail(api: ApiClient, section: String, article: String, onBack: () -> Unit) {
    val context = LocalContext.current
    RemotePage("Справочник", api, "/api/handbook/${contentPath(section)}/${contentPath(article)}", onBack) { data, _ ->
        Text(data.optString("title"), style = MaterialTheme.typography.headlineMedium)
        ContentHtml(api, data.optString("text"))
        if (data.optString("link").isNotBlank()) OutlinedButton(onClick = { openLink(context, contentUrl(api, data.optString("link"))) }) { Text("Открыть ссылку") }
    }
}

@Composable
private fun EquipmentDetail(api: ApiClient, id: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    RemotePage("Оборудование", api, "/api/equipment/$id", onBack) { item, _ ->
        Text(item.optString("title"), style = MaterialTheme.typography.headlineMedium)
        Text(item.optString("category_name"), style = MaterialTheme.typography.labelLarge)
        if (item.optString("photo_url").isNotBlank()) NetworkImage(contentUrl(api, item.optString("photo_url")), item.optString("title"), Modifier.fillMaxWidth().height(240.dp))
        ContentHtml(api, item.optString("description"))
        if (item.optString("buy_link").isNotBlank()) Button(onClick = { openLink(context, contentUrl(api, item.optString("buy_link"))) }, modifier = Modifier.fillMaxWidth()) { Text(item.optString("buy_label", "Купить")) }
    }
}

@Composable
private fun ArticleDetail(api: ApiClient, articleId: Int, onOpenAccount: (() -> Unit)?, onBack: () -> Unit) {
    val context = LocalContext.current
    RemotePage("Статья", api, "/api/articles/$articleId?mark_read=1", onBack) { data, refresh ->
        val article = data.optJSONObject("article") ?: JSONObject()
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        var commentText by rememberSaveable(articleId) { mutableStateOf("") }
        var replyId by rememberSaveable(articleId) { mutableStateOf(0) }
        var replyName by rememberSaveable(articleId) { mutableStateOf("") }
        var editId by rememberSaveable(articleId) { mutableStateOf(0) }
        var editText by rememberSaveable(articleId) { mutableStateOf("") }
        var deleteId by remember { mutableStateOf(0) }
        fun react(path: String, reaction: Int) {
            scope.launch {
                busy = true; error = ""
                try { api.put(path, JSONObject().put("reaction", reaction)); refresh() }
                catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
            }
        }
        if (deleteId > 0) AlertDialog(onDismissRequest = { if (!busy) deleteId = 0 }, title = { Text("Удалить комментарий?") }, text = { Text("Комментарий будет удалён из обсуждения на всех устройствах.") }, confirmButton = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true; error = ""
                    try { api.delete("/api/articles/$articleId/comments/$deleteId"); deleteId = 0; refresh() }
                    catch (e: Exception) { error = contentFailure(e); deleteId = 0 } finally { busy = false }
                }
            }) { Text("Удалить") }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { deleteId = 0 }) { Text("Отмена") } })
        Text(article.optString("title"), style = MaterialTheme.typography.headlineMedium)
        Text(article.optString("author_nickname") + " · " + article.optString("published_at").take(10), style = MaterialTheme.typography.bodySmall)
        var shareNotice by remember { mutableStateOf("") }
        val shareUrl = contentUrl(api, "/?article=$articleId#articles")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, article.optString("title"))
                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                }, "Поделиться статьёй"))
            }) { Text("Поделиться") }
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(article.optString("title"), shareUrl))
                shareNotice = "Ссылка скопирована."
            }) { Text("Скопировать ссылку") }
        }
        if (shareNotice.isNotBlank()) Text(shareNotice, style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy, onClick = refresh) { Text("Обновить статью и комментарии") }
        if (article.optString("cover_image_url").isNotBlank()) NetworkImage(contentUrl(api, article.optString("cover_image_url")), article.optString("title"), Modifier.fillMaxWidth().height(220.dp))
        if (data.optBoolean("premium_locked")) {
            SectionCard("Материал PREMIUM") {
                Text(plainText(article.optString("excerpt")))
                Text("Статья доступна с подпиской PREMIUM. Войдите в аккаунт с действующей подпиской или откройте раздел «Кабинет» для подключения.")
                if (onOpenAccount != null) Button(onClick = onOpenAccount) { Text("Открыть кабинет и подписку") }
            }
        } else {
            ContentHtml(api, article.optString("content_html"))
            val canInteract = data.optBoolean("can_interact")
            ReactionButtons(article, canInteract && !busy) { react("/api/articles/$articleId/reaction", it) }
            if (error.isNotBlank()) ErrorMessage(error)
            SectionCard("Комментарии") {
                if (!canInteract) {
                    Text("Войдите в аккаунт в разделе «Кабинет», чтобы оценивать статьи и оставлять комментарии.")
                    if (onOpenAccount != null) TextButton(onClick = onOpenAccount) { Text("Войти в аккаунт") }
                }
                val comments = data.items("comments")
                if (comments.isEmpty()) Text("Комментариев пока нет.")
                comments.forEach { comment ->
                    key(comment.optInt("id")) {
                        if (comment.optInt("parent_comment_id") > 0) {
                            val parent = comments.firstOrNull { it.optInt("id") == comment.optInt("parent_comment_id") }
                            Text("↳ Ответ: ${parent?.optString("nickname") ?: "комментарий #${comment.optInt("parent_comment_id")}"}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (comment.optBoolean("is_deleted")) Text("Комментарий удалён", style = MaterialTheme.typography.bodySmall) else {
                            Text(comment.optString("nickname", "Пользователь") + if (comment.optBoolean("is_admin")) " · ADMIN" else if (comment.optBoolean("is_premium")) " · PREMIUM" else "", style = MaterialTheme.typography.titleSmall)
                            Text(comment.optString("created_at").replace('T', ' ').take(16), style = MaterialTheme.typography.labelSmall)
                            if (editId == comment.optInt("id")) {
                                OutlinedTextField(editText, { if (it.length <= 5000) editText = it }, label = { Text("Редактирование комментария") }, minLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !busy && editText.trim().length >= 2, onClick = {
                                        scope.launch {
                                            busy = true; error = ""
                                            try { api.patch("/api/articles/$articleId/comments/$editId", JSONObject().put("text", editText.trim())); editId = 0; editText = ""; refresh() }
                                            catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
                                        }
                                    }) { Text("Сохранить") }
                                    TextButton(enabled = !busy, onClick = { editId = 0 }) { Text("Отмена") }
                                }
                            } else Text(comment.optString("text"))
                            ReactionButtons(comment, canInteract && !busy) { react("/api/articles/$articleId/comments/${comment.optInt("id")}/reaction", it) }
                            if (canInteract) TextButton(enabled = !busy, onClick = { replyId = comment.optInt("id"); replyName = comment.optString("nickname") }) { Text("Ответить") }
                            if (canInteract && comment.optLong("user_id") == api.userId) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !busy, onClick = { editId = comment.optInt("id"); editText = comment.optString("text") }) { Text("Изменить") }
                                TextButton(enabled = !busy, onClick = { deleteId = comment.optInt("id") }) { Text("Удалить") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (canInteract) {
                    if (replyId > 0) Row {
                        Text("Ответ пользователю $replyName", modifier = Modifier.weight(1f))
                        TextButton(onClick = { replyId = 0; replyName = "" }) { Text("Отмена") }
                    }
                    OutlinedTextField(commentText, { if (it.length <= 5000) commentText = it }, label = { Text("Ваш комментарий") }, minLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    Button(enabled = !busy && commentText.trim().length >= 2, onClick = {
                        scope.launch {
                            busy = true; error = ""
                            try {
                                api.post("/api/articles/$articleId/comments", JSONObject().put("text", commentText.trim()).put("parent_comment_id", replyId))
                                commentText = ""; replyId = 0; replyName = ""; refresh()
                            } catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
                        }
                    }) { Text(if (busy) "Отправка…" else "Отправить комментарий") }
                }
            }
        }
    }
}

@Composable
private fun ReactionButtons(item: JSONObject, enabled: Boolean, onReaction: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = item.optInt("my_reaction") == 1, enabled = enabled, onClick = { onReaction(1) }, label = { Text("👍 ${item.optInt("likes")}") })
        FilterChip(selected = item.optInt("my_reaction") == -1, enabled = enabled, onClick = { onReaction(-1) }, label = { Text("👎 ${item.optInt("dislikes")}") })
    }
}

@Composable
fun SupportScreen(api: ApiClient) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    RemotePage("Поддержка", api, "/api/support/tickets/my") { data, refresh ->
        SectionCard("Чем помочь?") {
            Text("Опишите ситуацию своими словами. Ответ появится здесь же, в истории обращений.")
            OutlinedTextField(text, { if (it.length <= 5000) text = it }, label = { Text("Сообщение") }, supportingText = { Text("${text.length}/5000") }, minLines = 4, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Button(enabled = !busy && text.trim().length >= 5, onClick = {
                scope.launch {
                    busy = true; error = ""; message = ""
                    try {
                        val response = api.post("/api/support/tickets", JSONObject().put("text", text.trim()))
                        message = "Обращение №${response.optInt("ticket_id")} отправлено."
                        text = ""; refresh()
                    } catch (e: Exception) { error = contentFailure(e) } finally { busy = false }
                }
            }) { Text(if (busy) "Отправка…" else "Отправить") }
            if (error.isNotBlank()) ErrorMessage(error)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Мои обращения", style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = !busy, onClick = refresh) { Text("Обновить") }
        }
        val tickets = data.items("items")
        if (tickets.isEmpty()) Text("У вас пока нет обращений.")
        tickets.forEach { ticket ->
            SectionCard("№${ticket.optInt("id")} · ${if (ticket.optBoolean("is_answered")) "Есть ответ" else "Ожидает ответа"}") {
                Text(ticket.optString("created_at").replace('T', ' ').take(16), style = MaterialTheme.typography.bodySmall)
                Text(ticket.optString("question_text"))
                if (ticket.optString("answer_text").isNotBlank()) {
                    HorizontalDivider()
                    Text("Ответ поддержки", style = MaterialTheme.typography.titleSmall)
                    Text(ticket.optString("answer_text"))
                }
            }
        }
    }
}

/** Native Android text and images preserve article formatting without embedding a browser. */
@Composable
private fun ContentHtml(api: ApiClient, source: String) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary
    val imagePattern = remember { Regex("<img\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE) }
    val pieces = remember(source) {
        buildList<Pair<Boolean, String>> {
            var offset = 0
            imagePattern.findAll(source).forEach { match ->
                if (match.range.first > offset) add(false to source.substring(offset, match.range.first))
                add(true to match.groupValues[1])
                offset = match.range.last + 1
            }
            if (offset < source.length) add(false to source.substring(offset))
        }
    }
    pieces.forEach { (image, text) ->
        if (image) NetworkImage(contentUrl(api, text), "Иллюстрация", Modifier.fillMaxWidth().height(220.dp))
        else if (text.isNotBlank()) {
            val formatted = remember(text, api.baseUrl) {
                val html = if (text.contains(Regex("<\\s*(p|div|br|h[1-6]|ul|ol|a|b|strong|em|i|table)\\b", RegexOption.IGNORE_CASE))) text else Html.escapeHtml(text).replace("\n", "<br>")
                SpannableStringBuilder(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)).apply {
                    getSpans(0, length, URLSpan::class.java).forEach { link ->
                        val start = getSpanStart(link)
                        val end = getSpanEnd(link)
                        removeSpan(link)
                        setSpan(object : ClickableSpan() { override fun onClick(widget: View) { openLink(context, contentUrl(api, link.url)) } }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            AndroidView(factory = { context -> TextView(context).apply {
                textSize = 16f
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(4f, 1f)
            } }, update = { view ->
                view.text = formatted
                view.setTextColor(Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
                view.setLinkTextColor(Color.argb(255, (linkColor.red * 255).toInt(), (linkColor.green * 255).toInt(), (linkColor.blue * 255).toInt()))
            }, modifier = Modifier.fillMaxWidth())
        }
    }
}
