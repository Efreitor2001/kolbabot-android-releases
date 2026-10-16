package ru.kolbabot.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

private fun storeArray(items: List<JSONObject>) = JSONArray().also { a -> items.forEach(a::put) }
private fun cartRequest(items: List<JSONObject>) = JSONObject().put("items", storeArray(items.map {
    JSONObject().put("product_id", it.optLong("product_id", it.optLong("id")))
        .put("quantity", it.optInt("quantity", 1))
}))
private fun storeFailure(error: Throwable): String {
    if (error is CancellationException) throw error
    return error.message ?: "Не удалось связаться с магазином. Повторите попытку."
}
private fun photoUrl(api: ApiClient, row: JSONObject): String {
    val raw = row.optString("photo_url")
    return if (raw.startsWith("/")) api.baseUrl.trimEnd('/') + raw else raw
}

@Composable
fun StoreScreen(api: ApiClient, initialProductId: Long = 0, onProductOpened: () -> Unit = {}, onNavigate: (String) -> Unit = {}) {
    var route by rememberSaveable { mutableStateOf("catalog") }
    var cartOrigin by rememberSaveable { mutableStateOf("catalog") }
    var productId by rememberSaveable { mutableLongStateOf(0L) }
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var visibleCount by rememberSaveable { mutableIntStateOf(30) }
    var revision by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storeCart = remember(api) { StoreCart(context, api) }
    LaunchedEffect(initialProductId) {
        if (initialProductId > 0) { productId = initialProductId; route = "product"; onProductOpened() }
    }
    LaunchedEffect(api, revision) {
        error = ""
        try { catalog = api.get("/api/store") } catch (e: Exception) { error = storeFailure(e) }
    }
    fun back() {
        route = if (route == "cart") cartOrigin else "catalog"
        if (route == "catalog") productId = 0
        revision++
    }
    fun openCart() { cartOrigin = route; route = "cart" }
    BackHandler(route != "catalog") { back() }
    fun addProduct(id: Long) {
        if (busy) return
        busy = true; message = ""; error = ""
        scope.launch {
            try {
                val products = catalog ?: api.get("/api/store").also { catalog = it }
                storeCart.add(id, products)
                message = "Товар добавлен в корзину"
            } catch (e: Exception) { error = storeFailure(e) } finally { busy = false }
        }
    }
    when (route) {
        "product" -> {
            ProductScreen(api, productId, busy, message, error, ::back,
                { productId = it; message = ""; error = "" }, ::addProduct, ::openCart, catalog?.optJSONObject("review_bonus_settings"))
            return
        }
        "cart" -> { CartScreen(api, catalog, ::back) { onNavigate("account") }; return }
    }
    Page("Магазин", "Ингредиенты, оболочки и полезные инструменты — в одном каталоге.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(::openCart, Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
                Icon(painterResource(R.drawable.site_nav_store), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text("Корзина")
            }
            OutlinedButton({ revision++ }) { Text("Обновить") }
        }
        if (error.isNotBlank()) ErrorMessage(error)
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        val data = catalog
        if (data == null && error.isBlank()) { CircularProgressIndicator(); return@Page }
        if (data == null) return@Page
        StoreBannerCarousel(api, data, onNavigate)
        if (data.optBoolean("premium_active")) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
            Text("Ваша скидка PREMIUM: ${data.optInt("premium_discount_percent")}%", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        OutlinedTextField(search, { search = it; visibleCount = 30 }, Modifier.fillMaxWidth(),
            label = { Text("Поиск") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(category.isEmpty(), { category = ""; visibleCount = 30 }, { Text("Все") }, colors = storeChipColors())
            data.items("categories").forEach { cat ->
                val id = cat.optString("id", cat.optString("category_id"))
                FilterChip(category == id, { category = id; visibleCount = 30 }, { Text(cat.optString("title", cat.optString("name"))) }, colors = storeChipColors())
            }
        }
        val filtered = remember(data, category, search) { visibleStoreProducts(data.items("products"), category, search) }
        Text("Найдено: ${filtered.size}", style = MaterialTheme.typography.labelLarge)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth > 388.dp && LocalDensity.current.fontScale <= 1.3f) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filtered.take(visibleCount).chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { representative ->
                            key(variantGroup(representative).ifBlank { representative.optString("id") }, category, search) {
                                StoreProductCard(api, representative, data.items("products"), busy, Modifier.weight(1f), grid = columns > 1,
                                    onOpen = { productId = it; route = "product" }, onAdd = ::addProduct)
                            }
                        }
                        if (row.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (filtered.isEmpty()) SectionCard { Text("По вашему запросу ничего не найдено.") }
        if (filtered.size > visibleCount) OutlinedButton({ visibleCount += 30 }, Modifier.fillMaxWidth()) { Text("Показать ещё") }
    }
}

@Composable private fun storeChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
    containerColor = MaterialTheme.colorScheme.surface)

@Composable private fun StoreProductCard(api: ApiClient, representative: JSONObject, products: List<JSONObject>, busy: Boolean,
                                        modifier: Modifier, grid: Boolean, onOpen: (Long) -> Unit, onAdd: (Long) -> Unit) {
    val variants = remember(products, representative) { products.filter { variantGroup(representative).isNotBlank() && variantGroup(it) == variantGroup(representative) } }
    var selectedVariant by rememberSaveable { mutableLongStateOf(representative.optLong("id")) }
    val product = variants.firstOrNull { it.optLong("id") == selectedVariant } ?: representative
    val id = product.optLong("id")
    Card(modifier.testTag("store-product-$id"), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (grid) NetworkImage(photoUrl(api, product), product.optString("title"), Modifier.fillMaxWidth().height(124.dp)
                .clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Подробнее") { onOpen(id) })
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Подробнее") { onOpen(id) },
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!grid) NetworkImage(photoUrl(api, product), product.optString("title"), Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.optString("title"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (product.optString("variant_label").isNotBlank()) Text(product.optString("variant_label"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (product.optInt("review_count") > 0) Text("★ ${product.optDouble("rating_average")} · ${product.optInt("review_count")} отзывов",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (variants.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                variants.forEach { variant -> FilterChip(variant.optLong("id") == id, { selectedVariant = variant.optLong("id") },
                    { Text(variant.optString("variant_label").ifBlank { variant.optString("title") } + " · " + money(variant.optLong("price_kopecks"))) }, colors = storeChipColors()) }
            }
            Text(money(product.optLong("price_kopecks")), color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button({ onAdd(id) }, Modifier.fillMaxWidth(), enabled = !busy, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("В корзину") }
        }
    }
}

internal fun storeBanners(catalog: JSONObject): List<JSONObject> {
    if (catalog.has("banners")) return catalog.items("banners").filter {
        when (val active = it.opt("is_active")) { is Boolean -> active; is Number -> active.toInt() != 0; is String -> active == "true" || active == "1"; else -> true }
    }
        .sortedBy { it.optInt("sort_order") }
    val visibility = catalog.optJSONObject("promo_banner") ?: JSONObject()
    val sausages = catalog.optJSONObject("promo_settings")?.optJSONObject("sausages") ?: JSONObject()
    val referral = catalog.optJSONObject("referral_settings") ?: JSONObject()
    val reviews = catalog.optJSONObject("review_bonus_settings") ?: JSONObject()
    val result = mutableListOf<JSONObject>()
    if (visibility.optBoolean("visible")) result += JSONObject().put("id", "sausages").put("title", "Колбаски на покупки")
        .put("text", "Оплачивайте до ${sausages.optInt("max_redemption_percent", 50)}% заказа. Возвращаем ${sausages.optInt("standard_percent", 5)}%, а с оплаченной PREMIUM — ${sausages.optInt("premium_percent", 10)}%. Первая оплата PREMIUM вернётся колбасками полностью. Доставка оплачивается отдельно.")
    if (visibility.optBoolean("referral_visible", referral.optBoolean("is_active"))) result += JSONObject().put("id", "referral").put("title", "Приведи друга")
        .put("text", "Поделитесь персональной ссылкой из личного кабинета и получите ${referral.optInt("percent", 5)}% колбасками с первой покупки друга без учёта доставки. Максимум — 2 000 колбасок за одного друга.")
    if (visibility.optBoolean("review_bonus_visible", reviews.optBoolean("is_active"))) result += JSONObject().put("id", "review-bonus").put("title", "Колбаски за отзывы")
        .put("text", "Оставьте содержательный отзыв о купленном товаре длиной от ${reviews.optInt("minimum_characters", 100)} символов — начислим ${reviews.optInt("sausages", 20)} колбасок. Пустые и однообразные тексты не участвуют. За каждый товар бонус выдаётся один раз.")
    return result
}

@Composable
private fun StoreBannerCarousel(api: ApiClient, catalog: JSONObject, onNavigate: (String) -> Unit) {
    val banners = storeBanners(catalog)
    if (banners.isEmpty()) return
    val pager = rememberPagerState { banners.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(banners.toString(), pager.currentPage) {
        if (banners.size > 1) { delay(10_000); if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % banners.size) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp) { index ->
            val banner = banners[index]
            Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFE1F1EA), Color(0xFFFFF5E6)))).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val image = banner.optString("image_url")
                    if (image.isNotBlank()) NetworkImage(if (image.startsWith("/")) api.baseUrl + image else image,
                        banner.optString("title"), Modifier.fillMaxWidth().height(160.dp))
                    val accent = when (banner.optString("kind")) { "referral" -> Color(0xFF8A4B16); "reviews", "review-bonus" -> Color(0xFF8A3B65); else -> MaterialTheme.colorScheme.onTertiaryContainer }
                    Text(banner.optString("title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
                    Text(plainText(banner.optString("text", banner.optString("body"))), style = MaterialTheme.typography.bodyMedium)
                    val link = banner.optString("link_url")
                    if (link.isNotBlank()) TextButton({
                        val target = runCatching { java.net.URI(api.baseUrl).resolve(link) }.getOrNull()
                        if (target?.host == java.net.URI(api.baseUrl).host && target.path in listOf("", "/") && target.fragment in setOf("home", "account", "recipes", "custom", "handbook", "articles", "equipment", "faq", "support")) onNavigate(target.fragment)
                        else openLink(context, target?.toString() ?: link)
                    }) {
                        Text(banner.optString("link_text", banner.optString("button_text", "Подробнее")).ifBlank { "Подробнее" })
                    }
                }
            }
        }
        if (banners.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ scope.launch { pager.animateScrollToPage((pager.currentPage - 1 + banners.size) % banners.size) } }) { Text("‹ Назад") }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(banners.size) { index -> Box(Modifier.width(if (pager.currentPage == index) 20.dp else 6.dp).height(6.dp)
                    .background(if (pager.currentPage == index) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))) }
                Text("${pager.currentPage + 1} / ${banners.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton({ scope.launch { pager.animateScrollToPage((pager.currentPage + 1) % banners.size) } }) { Text("Далее ›") }
        }
    }
}

@Composable
private fun ProductScreen(api: ApiClient, id: Long, busy: Boolean, message: String, error: String,
                          onBack: () -> Unit, choose: (Long) -> Unit, add: (Long) -> Unit, cart: () -> Unit, reviewRules: JSONObject? = null) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var rating by rememberSaveable(id) { mutableIntStateOf(5) }
    var review by rememberSaveable(id) { mutableStateOf("") }
    var reviewBusy by remember { mutableStateOf(false) }
    var reviewError by remember { mutableStateOf("") }
    var reviewNotice by remember(id) { mutableStateOf("") }
    key(id) {
        RemotePage("Карточка товара", api, "/api/store/products/$id", onBack) { p, reload ->
            NetworkImage(photoUrl(api, p), p.optString("title"), Modifier.fillMaxWidth().height(240.dp))
            Text(p.optString("title"), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { shareLink(context, p.optString("title"), "${api.baseUrl}/?product=$id#store") }) { Text("Поделиться") }
            Text(money(p.optLong("price_kopecks")), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            val oldPrice = p.optLong("base_price_kopecks")
            if (oldPrice > p.optLong("price_kopecks")) Text("Без скидки: ${money(oldPrice)}")
            if (p.optString("variant_label").isNotBlank()) Text("Вариант: ${p.optString("variant_label")}")
            val variants = p.items("variants")
            if (variants.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                variants.forEach { v -> FilterChip(v.optLong("id") == id, { choose(v.optLong("id")) },
                    { Text(v.optString("variant_label").ifBlank { v.optString("title") } + " · " + money(v.optLong("price_kopecks"))) }) }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            if (error.isNotBlank()) ErrorMessage(error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ add(id) }, enabled = !busy) { Text("В корзину") }
                OutlinedButton(cart) { Text("Открыть корзину") }
            }
            if (p.optString("product_type") == "digital_book") Text("Электронная книга. После оплаты доставляется на указанный e-mail.")
            if (p.optLong("weight_grams") > 0) Text("Вес для доставки: ${p.optLong("weight_grams")} г")
            if (p.optString("length").isNotBlank()) Text("Длина: ${p.optString("length")} м")
            if (p.optString("dimensions").isNotBlank()) Text("Размеры: ${p.optString("dimensions")}")
            if (p.optString("description").isNotBlank()) SectionCard("Описание") { Text(plainText(p.optString("description"))) }
            SectionCard("Отзывы · ${p.optInt("review_count")}") {
                if (reviewNotice.isNotBlank()) Text(reviewNotice)
                p.items("reviews").forEach { r ->
                    Text("${r.optString("user_nickname", "Покупатель")} · ${"★".repeat(r.optInt("rating").coerceIn(0, 5))}", fontWeight = FontWeight.SemiBold)
                    Text(r.optString("review_text")); HorizontalDivider()
                    if (r.optLong("review_bonus_kopecks") > 0) Text("За отзыв начислено ${money(r.optLong("review_bonus_kopecks"))} колбасками")
                }
                if (p.items("reviews").isEmpty()) Text("Отзывов пока нет")
            }
            if (p.optBoolean("can_review") || p.items("reviews").any { it.optBoolean("is_mine") }) SectionCard("Ваш отзыв") {
                LaunchedEffect(id) {
                    p.items("reviews").firstOrNull { it.optBoolean("is_mine") }?.let {
                        rating = it.optInt("rating", 5); review = it.optString("review_text")
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { value -> FilterChip(rating == value, { rating = value }, { Text("$value ★") }) }
                }
                OutlinedTextField(review, { review = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("Текст отзыва") }, minLines = 3)
                val characters = java.text.Normalizer.normalize(review, java.text.Normalizer.Form.NFKC).codePoints().filter { Character.isLetterOrDigit(it) }.count()
                val minimum = maxOf(100, reviewRules?.optInt("minimum_characters", 100) ?: 100)
                Text("Содержательных символов: $characters / $minimum", style = MaterialTheme.typography.bodySmall)
                if (reviewRules?.optBoolean("is_active") == true) Text("За содержательный отзыв от $minimum символов — ${reviewRules.optInt("sausages")} колбасок. Бонус за товар начисляется один раз.")
                if (reviewError.isNotBlank()) ErrorMessage(reviewError)
                Button({
                    reviewBusy = true; reviewError = ""
                    scope.launch {
                        try {
                            val result = api.put("/api/store/products/$id/review", JSONObject().put("rating", rating).put("review_text", review))
                            val eligibility = result.optJSONObject("review_bonus_eligibility")
                            reviewNotice = when {
                                result.optBoolean("review_bonus_granted") -> "Отзыв сохранён. Начислено ${result.optInt("review_bonus_sausages")} колбасок!"
                                eligibility?.optString("reason") == "too_repetitive" -> "Отзыв сохранён без бонуса: текст слишком однообразный. Опишите личный опыт разными словами."
                                eligibility != null && !eligibility.optBoolean("eligible") -> "Отзыв сохранён без бонуса. Нужно ${eligibility.optInt("minimum_characters", minimum)} содержательных символов, сейчас ${eligibility.optInt("meaningful_characters")}."
                                reviewRules?.optBoolean("is_active") == false -> "Отзыв сохранён. Акция за отзывы приостановлена."
                                else -> "Отзыв сохранён. Бонус за этот товар уже начислялся."
                            }
                            reload()
                        }
                        catch (e: Exception) { reviewError = storeFailure(e) } finally { reviewBusy = false }
                    }
                }, enabled = !reviewBusy) { Text("Сохранить отзыв") }
            }
            if (p.items("related_products").isNotEmpty()) SectionCard("С этим товаром покупают") {
                p.items("related_products").forEach { related ->
                    TextButton({ choose(related.optLong("id")) }) { Text(related.optString("title") + " · " + money(related.optLong("price_kopecks"))) }
                }
            }
        }
    }
}

@Composable
private fun CartScreen(api: ApiClient, originalCatalog: JSONObject?, onBack: () -> Unit, onLogin: () -> Unit) {
    var cart by remember { mutableStateOf<JSONObject?>(null) }
    var catalog by remember { mutableStateOf(originalCatalog) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var checkout by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storeCart = remember(api) { StoreCart(context, api) }
    LaunchedEffect(api, revision) {
        error = ""
        try { catalog = api.get("/api/store"); cart = storeCart.load(catalog!!) }
        catch (e: Exception) { error = storeFailure(e) }
    }
    BackHandler(checkout) { checkout = false; revision++ }
    if (checkout && cart != null && catalog != null) {
        CheckoutScreen(api, cart!!, catalog!!) { checkout = false; revision++ }
        return
    }
    Page("Корзина", if (api.authenticated) "Синхронизируется с вашим аккаунтом на сайте" else "Сохранена на этом устройстве. После входа добавится к корзине аккаунта.", onBack) {
        if (error.isNotBlank()) ErrorMessage(error)
        if (cart == null && error.isBlank()) CircularProgressIndicator()
        cart?.optString("merge_notice")?.takeIf { it.isNotBlank() }?.let { Text(it) }
        OutlinedButton({ revision++ }, enabled = !busy) { Text("Обновить корзину") }
        val rows = cart?.items("items") ?: emptyList()
        if (cart != null && rows.isEmpty()) Text("Корзина пуста. Выберите товары в магазине.")
        rows.forEach { item ->
            SectionCard(item.optString("title")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetworkImage(photoUrl(api, item), item.optString("title"), Modifier.size(60.dp))
                    Text(money(item.optLong("price_kopecks") * item.optInt("quantity", 1)), fontWeight = FontWeight.Bold)
                }
                fun change(quantity: Int) {
                    if (busy) return
                    busy = true; error = ""
                    val update = rows.mapNotNull { row ->
                        if (row.optLong("product_id") != item.optLong("product_id")) row
                        else if (quantity <= 0) null
                        else JSONObject(row.toString()).put("quantity", quantity.coerceAtMost(999))
                    }
                    scope.launch {
                        try { cart = storeCart.save(update, catalog ?: JSONObject()) }
                        catch (e: Exception) { error = storeFailure(e) } finally { busy = false }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ change(item.optInt("quantity", 1) - 1) }, enabled = !busy) { Text("−") }
                    Text("${item.optInt("quantity", 1)} шт.")
                    OutlinedButton({ change(item.optInt("quantity", 1) + 1) }, enabled = !busy && item.optInt("quantity", 1) < 999) { Text("+") }
                }
                TextButton({ change(0) }, enabled = !busy) { Text("Убрать товар") }
            }
        }
        if (rows.isNotEmpty()) {
            val subtotal = rows.sumOf { it.optLong("price_kopecks") * it.optInt("quantity", 1) }
            val digitalIds = catalog?.items("products")?.filter { it.optString("product_type") == "digital_book" }?.map { it.optLong("id") }?.toSet().orEmpty()
            val minimum = if (rows.all { it.optLong("product_id") in digitalIds }) 0 else catalog?.optJSONObject("store_settings")?.optLong("minimum_order_kopecks") ?: 0
            Text("Товары: ${money(subtotal)}", style = MaterialTheme.typography.titleLarge)
            if (subtotal < minimum) Text("До минимального заказа осталось ${money(minimum - subtotal)}")
            if (!api.authenticated) Button(onLogin, Modifier.fillMaxWidth(), enabled = !busy) { Text("Войти для оформления") }
            else Button({ checkout = true }, Modifier.fillMaxWidth(), enabled = !busy && catalog != null && subtotal >= minimum) { Text("Оформить заказ") }
        }
    }
}

internal fun nativeDeliveryPrice(totalWeight: Long, sizes: List<JSONObject>): Long? {
    val sorted = sizes.filter { it.optLong("max_weight_grams") > 0 }.sortedBy { it.optLong("max_weight_grams") }
    if (sorted.isEmpty()) return null
    val maximum = sorted.last().optLong("max_weight_grams")
    var remaining = totalWeight.coerceAtLeast(1)
    var price = 0L
    while (remaining > 0) {
        val chunk = minOf(remaining, maximum)
        price += (sorted.firstOrNull { it.optLong("max_weight_grams") >= chunk } ?: sorted.last()).optLong("price_kopecks")
        remaining -= chunk
    }
    return price
}

@Composable
private fun CheckoutScreen(api: ApiClient, cart: JSONObject, catalog: JSONObject, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("store_checkout", Context.MODE_PRIVATE) }
    val uid = catalog.optLong("user_id", api.userId)
    val prefix = "user_${uid}_"
    val scope = rememberCoroutineScope()
    val rows = cart.items("items")
    val products = catalog.items("products").associateBy { it.optLong("id") }
    val allDigital = rows.isNotEmpty() && rows.all { products[it.optLong("product_id")]?.optString("product_type") == "digital_book" }
    val hasDigital = rows.any { products[it.optLong("product_id")]?.optString("product_type") == "digital_book" }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(if (allDigital) "digital" else "") }
    var pointQuery by rememberSaveable { mutableStateOf("") }
    var points by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var point by remember { mutableStateOf<JSONObject?>(null) }
    var manualPoint by rememberSaveable { mutableStateOf(false) }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var useRewards by rememberSaveable { mutableStateOf(false) }
    var offer by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var adult by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var pendingPayload by remember { mutableStateOf(prefs.getString(prefix + "payload", "") ?: "") }
    var attemptKey by remember { mutableStateOf(prefs.getString(prefix + "key", "") ?: "") }
    var orderId by remember { mutableStateOf(prefs.getString(prefix + "order", "") ?: "") }
    var paymentUrl by remember { mutableStateOf(prefs.getString(prefix + "payment", "") ?: "") }
    var order by remember { mutableStateOf<JSONObject?>(null) }
    val locked = pendingPayload.isNotBlank()
    val ozonApi = catalog.optJSONObject("ozon_delivery")?.optBoolean("checkout_enabled") == true
    val total = rows.sumOf { it.optLong("price_kopecks") * it.optInt("quantity", 1) }
    val weight = rows.sumOf { it.optLong("shipping_weight_grams", it.optLong("weight_grams")) * it.optInt("quantity", 1) }
    val rewards = cart.optJSONObject("rewards")?.optLong("sausages_kopecks") ?: 0
    val rewardRules = catalog.optJSONObject("promo_settings")?.optJSONObject("sausages")
    val maxRedemption = rewardRules?.optInt("max_redemption_percent", 50)?.coerceIn(0, 50) ?: 50
    val spent = if (useRewards) minOf(rewards, total * maxRedemption / 100) else 0L
    val cashbackPercent = if (catalog.optBoolean("paid_premium_cashback")) rewardRules?.optInt("premium_percent", 10) ?: 10 else rewardRules?.optInt("standard_percent", 5) ?: 5
    val cashback = if (rewardRules?.optInt("is_active") == 1) (total - spent) * cashbackPercent.coerceIn(0, 100) / 100 else 0
    val minimumOrder = if (allDigital) 0 else catalog.optJSONObject("store_settings")?.optLong("minimum_order_kopecks") ?: 0
    val shipping = if (allDigital) 0L else if (method == "ozon" && ozonApi && !manualPoint) quote?.optLong("price_kopecks")
        else nativeDeliveryPrice(weight, catalog.items("delivery_sizes"))
    fun clearAttempt() {
        prefs.edit().remove(prefix + "payload").remove(prefix + "key").remove(prefix + "order").remove(prefix + "payment").commit()
        pendingPayload = ""; attemptKey = ""; orderId = ""; paymentUrl = ""
    }
    fun consumeResult(result: JSONObject) {
        orderId = result.optString("order_id", orderId)
        paymentUrl = result.optString("payment_url", paymentUrl)
        order = result.optJSONObject("order")
        prefs.edit().putString(prefix + "order", orderId).putString(prefix + "payment", paymentUrl).commit()
        info = if (result.optBoolean("pending_reconciliation")) "Банк обрабатывает запрос. Повторная оплата не создаётся. Проверьте статус заказа позже."
            else "Заказ создан. Проверьте сумму и перейдите к оплате."
    }
    fun submit() {
        if (busy) return
        error = ""; busy = true
        scope.launch {
            try {
                if (pendingPayload.isBlank()) {
                    val body = cartRequest(rows).put("customer_name", name.trim()).put("phone", phone.trim())
                        .put("address", address.trim()).put("digital_delivery_email", email.trim()).put("delivery_method", method)
                        .put("use_sausages", useRewards).put("accept_public_offer", offer)
                        .put("accept_privacy_policy", privacy).put("accept_age_18", adult)
                    if (method == "ozon" && !allDigital) {
                        if (manualPoint) body.put("ozon_manual_pvz_address", address.trim())
                        else body.put("ozon_delivery_point_id", point?.optLong("delivery_point_id") ?: 0L)
                        if (ozonApi && !manualPoint) body.put("expected_ozon_quote", quote)
                    }
                    attemptKey = UUID.randomUUID().toString()
                    pendingPayload = body.toString()
                    val saved = prefs.edit().putString(prefix + "key", attemptKey).putString(prefix + "payload", pendingPayload).commit()
                    if (!saved) { pendingPayload = ""; attemptKey = ""; throw IllegalStateException("Не удалось сохранить попытку заказа. Освободите память устройства.") }
                }
                require(attemptKey.isNotBlank()) { "Ключ заказа отсутствует. Обратитесь в поддержку для проверки последней оплаты." }
                consumeResult(api.post("/api/store/checkout", JSONObject(pendingPayload), mapOf("Idempotency-Key" to attemptKey)))
            } catch (e: ApiException) {
                error = e.message
                if (e.details.optString("reason") == "ozon_quote_changed") {
                    quote = e.details.optJSONObject("quote"); clearAttempt()
                    info = "Стоимость доставки обновлена. Проверьте сумму перед повторным подтверждением."
                } else if (e.status in listOf(400, 401, 403) || e.details.optString("reason") == "checkout_attempt_finished") {
                    order = e.details.optJSONObject("order"); clearAttempt()
                }
            } catch (e: Exception) { error = storeFailure(e) }
            finally { busy = false }
        }
    }
    Page("Оформление заказа", "Оплата через Т-Банк", onBack) {
        if (error.isNotBlank()) ErrorMessage(error)
        if (info.isNotBlank()) Text(info)
        if (locked) {
            SectionCard("Сохранённая попытка заказа") {
                val draft = runCatching { JSONObject(pendingPayload) }.getOrDefault(JSONObject())
                Text(draft.optString("customer_name"))
                Text(draft.optString("phone"))
                Text(draft.optString("address"))
                if (orderId.isNotBlank()) Text("Заказ $orderId")
                Text("Эта попытка сохраняется при закрытии приложения. Повторный запрос использует тот же ключ заказа.")
                if (order != null) StoreOrderSummary(order!!)
                if (paymentUrl.isNotBlank()) Button({ openLink(context, paymentUrl) }, enabled = !busy) { Text("Перейти к оплате") }
                if (orderId.isNotBlank()) OutlinedButton({
                    busy = true; error = ""
                    scope.launch {
                        try {
                            val refreshed = api.get("/api/store/orders/${URLEncoder.encode(orderId, "UTF-8")}")
                            order = refreshed
                            paymentUrl = refreshed.optString("payment_url", paymentUrl)
                            prefs.edit().putString(prefix + "payment", paymentUrl).commit()
                            val status = refreshed.optString("status")
                            info = "Статус: ${orderStatus(status)}"
                            if (status in setOf("paid", "ready", "cancelled", "payment_failed", "payment_expired", "refunded")) clearAttempt()
                        } catch (e: Exception) { error = storeFailure(e) } finally { busy = false }
                    }
                }, enabled = !busy) { Text("Проверить оплату") }
                if (paymentUrl.isBlank()) OutlinedButton(::submit, enabled = !busy) { Text("Повторить запрос с тем же ключом") }
            }
            return@Page
        }
        if (order != null) SectionCard("Результат заказа") { StoreOrderSummary(order!!) }
        if (order?.optString("status") in setOf("paid", "ready")) {
            Button(onBack) { Text("Вернуться в корзину") }; return@Page
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("ФИО") }, singleLine = true)
        OutlinedTextField(phone, { phone = it; quote = null }, Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text("Телефон") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
        if (hasDigital) OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text("E-mail для электронной книги") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
        if (allDigital) Text("Электронная доставка: бесплатно")
        else SectionCard("Доставка") {
            catalog.items("delivery_methods").forEach { delivery ->
                val code = delivery.optString("code")
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { method = code; quote = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(method == code, { method = code; quote = null }, enabled = !busy)
                    Text(delivery.optString("title"))
                }
            }
            if (method == "ozon") {
                StoreCheck("Ввести адрес ПВЗ вручную", manualPoint, !busy) { manualPoint = it; point = null; quote = null }
                if (!manualPoint) {
                    OutlinedTextField(pointQuery, { pointQuery = it }, Modifier.fillMaxWidth(), enabled = !busy,
                        label = { Text("Город, улица или номер ПВЗ") }, singleLine = true)
                    OutlinedButton({
                        busy = true; error = ""
                        scope.launch {
                            try { points = api.get("/api/store/ozon-delivery/points?q=${URLEncoder.encode(pointQuery.trim(), "UTF-8")}&limit=30").items("items") }
                            catch (e: Exception) { error = storeFailure(e) } finally { busy = false }
                        }
                    }, enabled = !busy && pointQuery.trim().length >= 2) { Text("Найти пункты выдачи") }
                    points.forEach { candidate ->
                        val selected = point?.optLong("delivery_point_id") == candidate.optLong("delivery_point_id")
                        OutlinedButton({ point = candidate; quote = null; points = emptyList() }, Modifier.fillMaxWidth(), enabled = !busy) {
                            Text((if (selected) "✓ " else "") + candidate.optString("name") + "\n" + candidate.optString("full_address"))
                        }
                    }
                    if (point != null) {
                        Text("Выбран ПВЗ: ${point!!.optString("full_address")}")
                        if (ozonApi) OutlinedButton({
                            busy = true; error = ""
                            scope.launch {
                                try {
                                    quote = api.post("/api/store/ozon-delivery/quote", cartRequest(rows)
                                        .put("delivery_point_id", point!!.optLong("delivery_point_id")).put("phone", phone)).optJSONObject("quote")
                                } catch (e: Exception) { error = storeFailure(e) } finally { busy = false }
                            }
                        }, enabled = !busy && phone.filter(Char::isDigit).length >= 7) { Text("Рассчитать доставку") }
                    }
                    if (quote != null) Text("Доставка: ${money(quote!!.optLong("price_kopecks"))} · примерный срок ${quote!!.optInt("estimated_delivery_days")} дн.")
                }
            }
            if (method.isNotBlank() && (method != "ozon" || manualPoint)) OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text(if (manualPoint && method == "ozon") "Полный адрес ПВЗ Ozon" else "Адрес доставки") }, minLines = 2)
        }
        if (rewards > 0 && rewardRules?.optInt("is_active") == 1) StoreCheck("Использовать колбаски (доступно ${money(rewards)})", useRewards, !busy) { useRewards = it }
        SectionCard("Сумма заказа") {
            rows.forEach { Text("${it.optString("title")} × ${it.optInt("quantity", 1)} · ${money(it.optLong("price_kopecks") * it.optInt("quantity", 1))}") }
            Text("Товары: ${money(total)}")
            if (spent > 0) Text("Колбаски: −${money(spent)}")
            Text("Доставка: ${shipping?.let(::money) ?: "требуется расчёт"}")
            if (shipping != null) Text("Предварительно: ${money(total - spent + shipping)}", style = MaterialTheme.typography.titleLarge)
            if (cashback > 0) Text("Колбаски после оплаты: ${money(cashback)}")
            if (total - spent < minimumOrder) Text("До минимального заказа после списания колбасок осталось ${money(minimumOrder - total + spent)}")
            Text("Окончательную сумму подтверждает сервер перед переходом к оплате.", style = MaterialTheme.typography.bodySmall)
        }
        StoreCheck("Принимаю публичную оферту", offer, !busy) { offer = it }
        TextButton({ openLink(context, api.baseUrl + "/public-offer") }) { Text("Прочитать оферту") }
        StoreCheck("Согласен на обработку персональных данных для доставки", privacy, !busy) { privacy = it }
        TextButton({ openLink(context, api.baseUrl + "/android/privacy") }) { Text("Политика конфиденциальности") }
        StoreCheck("Мне исполнилось 18 лет", adult, !busy) { adult = it }
        val deliveryReady = allDigital || (method.isNotBlank() && if (method == "ozon" && !manualPoint) point != null && (!ozonApi || quote != null) else address.trim().length >= 5)
        val ready = name.trim().length >= 3 && phone.filter(Char::isDigit).length >= 7 && deliveryReady &&
            (!hasDigital || android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) && offer && privacy && adult && total - spent >= minimumOrder
        Button(::submit, Modifier.fillMaxWidth(), enabled = !busy && ready && catalog.optJSONObject("payments")?.optBoolean("enabled") == true) {
            Text(if (busy) "Создаём заказ…" else "Подтвердить заказ")
        }
        if (catalog.optJSONObject("payments")?.optBoolean("enabled") != true) Text("Оплата сейчас недоступна. Попробуйте позже.")
    }
}

@Composable
private fun StoreCheck(label: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { change(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, change, enabled = enabled); Text(label, Modifier.weight(1f))
    }
}

private fun orderStatus(status: String) = siteOrderStatus(status).label

@Composable
private fun StoreOrderSummary(order: JSONObject) {
    AdminCopyField("Номер заказа", order.optString("order_id"))
    OrderStatusPill(order.optString("status"), customer = true)
    siteOrderStatus(order.optString("status"), customer = true).hint.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (order.optString("created_at").isNotBlank()) AdminCopyField("Создан", order.optString("created_at"))
    if (order.has("products_original_total_kopecks")) AdminCopyField("Товары до скидок", money(order.optLong("products_original_total_kopecks")))
    if (order.optLong("subscription_discount_kopecks") > 0) AdminCopyField("Скидка PREMIUM", money(order.optLong("subscription_discount_kopecks")))
    if (order.optLong("sausages_spent_kopecks") > 0) AdminCopyField("Списано колбасок", money(order.optLong("sausages_spent_kopecks")))
    if (order.has("delivery_price_kopecks")) AdminCopyField("Стоимость доставки", money(order.optLong("delivery_price_kopecks")))
    AdminCopyField("Итого", money(order.optLong("total_kopecks")))
    if (order.optLong("sausages_bonus_kopecks") > 0) AdminCopyField("Колбаски за заказ", money(order.optLong("sausages_bonus_kopecks")))
    order.items("items").forEach { item -> SectionCard {
        AdminCopyField("Товар", item.optString("title"))
        AdminCopyField("Количество", item.optInt("quantity", 1).toString())
        AdminCopyField("Сумма", money(item.optLong("amount_kopecks")))
    } }
    if (order.optString("customer_name").isNotBlank()) AdminCopyField("ФИО", order.optString("customer_name"))
    if (order.optString("phone").isNotBlank()) AdminCopyField("Телефон", order.optString("phone"))
    if (order.optString("address").isNotBlank()) AdminCopyField("Адрес", order.optString("address"))
    if (order.optString("digital_delivery_email").isNotBlank()) AdminCopyField("Электронная доставка", order.optString("digital_delivery_email"))
}

@Composable
fun OrdersScreen(api: ApiClient, initialOrderId: String = "", onOrderOpened: () -> Unit = {}) {
    var selected by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(initialOrderId) { if (initialOrderId.isNotBlank()) { selected = initialOrderId; onOrderOpened() } }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("store_checkout", Context.MODE_PRIVATE) }
    val prefix = "user_${api.userId}_"
    val scope = rememberCoroutineScope()
    var pendingOrder by remember { mutableStateOf(prefs.getString(prefix + "order", "") ?: "") }
    var recovering by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf("") }
    BackHandler(selected.isNotBlank()) { selected = "" }
    if (selected.isNotBlank()) {
        RemotePage("Заказ", api, "/api/store/orders/${URLEncoder.encode(selected, "UTF-8")}", { selected = "" }) { order, reload ->
            LaunchedEffect(order.optString("status")) {
                if (order.optString("status") in setOf("paid", "ready", "cancelled", "payment_failed", "payment_expired", "refunded") &&
                    prefs.getString(prefix + "order", "") == selected) {
                    prefs.edit().remove(prefix + "payload").remove(prefix + "key").remove(prefix + "order").remove(prefix + "payment").commit()
                    pendingOrder = ""
                }
            }
            StoreOrderSummary(order)
            OutlinedButton(reload) { Text("Проверить статус") }
            val url = order.optString("payment_url")
            if (url.isNotBlank() && order.optString("status") in setOf("new", "payment_pending")) Button({ openLink(context, url) }) { Text("Перейти к оплате") }
        }
    } else RemotePage("История заказов", api, "/api/store/orders") { data, reload ->
        val pendingBody = prefs.getString(prefix + "payload", "") ?: ""
        if (pendingBody.isNotBlank()) SectionCard("Незавершённое оформление") {
            if (recoveryError.isNotBlank()) ErrorMessage(recoveryError)
            if (pendingOrder.isNotBlank()) OutlinedButton({ selected = pendingOrder }) { Text("Проверить заказ $pendingOrder") }
            else {
                Text("Последняя попытка оформления сохранена. Можно безопасно запросить её результат с тем же ключом заказа.")
                OutlinedButton({
                    recovering = true; recoveryError = ""
                    scope.launch {
                        try {
                            val key = prefs.getString(prefix + "key", "") ?: ""
                            require(key.isNotBlank()) { "Ключ заказа отсутствует. Обратитесь в поддержку для проверки последней оплаты." }
                            val result = api.post("/api/store/checkout", JSONObject(pendingBody), mapOf("Idempotency-Key" to key))
                            pendingOrder = result.optString("order_id")
                            prefs.edit().putString(prefix + "order", pendingOrder).putString(prefix + "payment", result.optString("payment_url")).commit()
                            if (pendingOrder.isNotBlank()) selected = pendingOrder
                        } catch (e: ApiException) {
                            recoveryError = e.message
                            if (e.details.optString("reason") == "checkout_attempt_finished" || e.status in listOf(400, 401, 403)) {
                                prefs.edit().remove(prefix + "payload").remove(prefix + "key").remove(prefix + "order").remove(prefix + "payment").commit()
                                reload()
                            }
                        } catch (e: Exception) { recoveryError = storeFailure(e) } finally { recovering = false }
                    }
                }, enabled = !recovering) { Text("Получить результат оформления") }
            }
        }
        Text("История оплаченных заказов вашего аккаунта")
        OutlinedButton(reload) { Text("Обновить") }
        if (data.items("items").isEmpty()) Text("Оплаченных заказов пока нет")
        data.items("items").forEach { order ->
            OrderCompactRow(order, customer = true) { selected = order.optString("order_id") }
        }
    }
}
