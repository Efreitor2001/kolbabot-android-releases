package ru.kolbabot.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    var cartRevision by remember { mutableIntStateOf(0) }
    var cartScrollRequest by remember { mutableIntStateOf(0) }
    var handledCartScroll by remember { mutableIntStateOf(0) }
    var handledChromeRequest by remember { mutableIntStateOf(0) }
    var cart by remember { mutableStateOf<JSONObject?>(null) }
    var cartOwner by remember { mutableLongStateOf(-1L) }
    var cartLoading by remember { mutableStateOf(true) }
    var cartError by remember { mutableStateOf("") }
    val cartTarget = remember { BringIntoViewRequester() }
    val chrome = LocalSiteChrome.current
    var productId by rememberSaveable { mutableLongStateOf(0L) }
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var visibleCount by rememberSaveable { mutableIntStateOf(30) }
    var selectedVariants by rememberSaveable { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var revision by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storeCart = remember(api) { StoreCart(context, api) }
    val cartReady = cart != null && cartOwner == api.userId && !cartLoading
    val cartRows = if (cartOwner == api.userId) cart?.items("items").orEmpty() else emptyList()
    fun acceptCart(current: JSONObject, owner: Long) {
        if (api.userId != owner) return
        cart = current; cartOwner = owner
        chrome?.cartCount = current.items("items").sumOf { it.optInt("quantity", 1) }
    }
    LaunchedEffect(initialProductId) {
        if (initialProductId > 0) { productId = initialProductId; route = "product"; onProductOpened() }
    }
    LaunchedEffect(api, revision) {
        error = ""
        try { catalog = api.get("/api/store") } catch (e: Exception) { error = storeFailure(e) }
    }
    LaunchedEffect(api, catalog, api.userId, cartRevision) {
        val products = catalog ?: return@LaunchedEffect
        val owner = api.userId
        cartLoading = true; cartError = ""
        if (cartOwner != owner) cart = null
        try { acceptCart(storeCart.load(products), owner) }
        catch (e: Exception) { cartError = storeFailure(e) }
        finally { cartLoading = false }
    }
    OnResume { cartRevision++ }
    LaunchedEffect(route, cartScrollRequest, catalog) {
        if (route == "catalog" && catalog != null && cartScrollRequest > handledCartScroll) {
            withFrameNanos { }
            cartTarget.bringIntoView()
            handledCartScroll = cartScrollRequest
        }
    }
    LaunchedEffect(chrome?.openCartRequest) {
        val request = chrome?.openCartRequest ?: 0
        if (request > handledChromeRequest) { route = "catalog"; cartScrollRequest++; handledChromeRequest = request }
    }
    fun back() {
        route = "catalog"
        if (route == "catalog") productId = 0
        revision++
    }
    fun openCart() { route = "catalog"; cartScrollRequest++ }
    BackHandler(route != "catalog") { back() }
    fun updateQuantity(id: Long, quantity: Int, relative: Boolean, added: Boolean = false, closeDetail: Boolean = false) {
        if (busy || !cartReady) return
        busy = true; message = ""; error = ""
        val owner = api.userId
        scope.launch {
            try {
                val products = checkNotNull(catalog)
                val updated = if (relative) storeCart.changeQuantity(id, quantity, products)
                    else storeCart.setQuantity(id, quantity, products)
                if (api.userId == owner) {
                    acceptCart(updated, owner)
                    if (added) message = "Товар добавлен в корзину"
                    if (closeDetail) {
                        products.items("products").firstOrNull { it.optLong("id") == id }?.let {
                            selectedVariants = selectedVariants + (variantGroup(it).ifBlank { id.toString() } to id)
                        }
                        route = "catalog"; productId = 0
                    }
                }
            } catch (e: Exception) { error = storeFailure(e) } finally { busy = false }
        }
    }
    when (route) {
        "product" -> {
            ProductScreen(api, productId, busy || !cartReady, message, error.ifBlank { cartError }, ::back,
                { productId = it; message = ""; error = "" }, { updateQuantity(it, 1, relative = true, added = true, closeDetail = true) },
                ::openCart, catalog?.optJSONObject("review_bonus_settings"))
            return
        }
    }
    Page("Всё для домашнего производства", "Ингредиенты, оболочки и полезные инструменты — в одном каталоге.", eyebrow = "Магазин") {
        if (chrome == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        SectionCard {
        Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("КАТАЛОГ", color = SiteColors.Brand, fontSize = 10.sp, lineHeight = 13.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
            Text("Выберите товары", fontSize = 17.28.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(search, { search = it; visibleCount = 30; selectedVariants = emptyMap() }, Modifier.fillMaxWidth(),
            label = { Text("Поиск") }, singleLine = true, shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Оболочка, специи, нож…") },
            trailingIcon = { Text("⌕", color = Color(0xFF778195), fontSize = 22.sp) })
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(category.isEmpty(), { category = ""; visibleCount = 30; selectedVariants = emptyMap() },
                { Text("Все (${visibleStoreProducts(data.items("products"), "", "").size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                Modifier.heightIn(min = 34.dp), colors = storeChipColors(), border = BorderStroke(0.dp, Color.Transparent))
            data.items("categories").forEach { cat ->
                val id = cat.optString("id", cat.optString("category_id"))
                FilterChip(category == id, { category = id; visibleCount = 30; selectedVariants = emptyMap() },
                    { Text(cat.optString("title", cat.optString("name")) + " (${visibleStoreProducts(data.items("products"), id, "").size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    Modifier.heightIn(min = 34.dp), colors = storeChipColors(), border = BorderStroke(0.dp, Color.Transparent))
            }
        }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 6.dp), color = Color(0x121D2A4E))
        val filtered = remember(data, category, search) { visibleStoreProducts(data.items("products"), category, search) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (LocalConfiguration.current.screenWidthDp <= 420 || LocalDensity.current.fontScale > 1.3f) 1
                else ((maxWidth.value + 14) / 219).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                filtered.take(visibleCount).chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { representative ->
                            key(variantGroup(representative).ifBlank { representative.optString("id") }, category, search) {
                                val selectionKey = variantGroup(representative).ifBlank { representative.optString("id") }
                                StoreProductCard(api, representative, data.items("products"), cartRows, selectedVariants[selectionKey],
                                    busy || !cartReady, Modifier.weight(1f),
                                    onOpen = { productId = it; route = "product"; message = ""; error = "" },
                                    onVariant = { selectedVariants = selectedVariants + (selectionKey to it) },
                                    onAdd = { updateQuantity(it, 1, relative = true, added = true) },
                                    onChange = { id, delta -> updateQuantity(id, delta, relative = true) },
                                    onSet = { id, quantity -> updateQuantity(id, quantity, relative = false) })
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (filtered.isEmpty()) SectionCard { Text("По вашему запросу ничего не найдено.") }
        if (filtered.size > visibleCount) OutlinedButton({ visibleCount += 30 }, Modifier.fillMaxWidth()) { Text("Показать ещё") }
        }
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(cartTarget).testTag("storeInlineCart")) {
            CartContent(api, data, if (cartOwner == api.userId) cart else null, busy || cartLoading, cartError,
                onLogin = { onNavigate("account") }, onRefresh = { cartRevision++ },
                onChange = { id, delta -> updateQuantity(id, delta, relative = true) },
                onRemove = { updateQuantity(it, 0, relative = false) })
        }
    }
}

@Composable private fun storeChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
    containerColor = Color(0xFFF2F4F8), labelColor = Color(0xFF536074))

@Composable private fun storeVariantColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Color(0xFFEAF6F1), selectedLabelColor = Color(0xFF0B6050),
    containerColor = MaterialTheme.colorScheme.surface, labelColor = MaterialTheme.colorScheme.onSurface)

@Composable private fun storeVariantBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true, selected = selected, borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    selectedBorderColor = Color(0xFF147A66), borderWidth = 1.dp, selectedBorderWidth = 1.dp)

@Composable private fun StoreProductCard(api: ApiClient, representative: JSONObject, products: List<JSONObject>,
                                        cartRows: List<JSONObject>, selectedVariant: Long?, busy: Boolean, modifier: Modifier,
                                        onOpen: (Long) -> Unit, onVariant: (Long) -> Unit, onAdd: (Long) -> Unit,
                                        onChange: (Long, Int) -> Unit, onSet: (Long, Int) -> Unit) {
    val variants = remember(products, representative) { products.filter { variantGroup(representative).isNotBlank() && variantGroup(it) == variantGroup(representative) } }
    val product = variants.firstOrNull { it.optLong("id") == selectedVariant } ?: representative
    val id = product.optLong("id")
    Card(modifier.testTag("store-product-$id"), shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (photoUrl(api, product).isNotBlank()) NetworkImage(photoUrl(api, product), product.optString("title"), Modifier.fillMaxWidth().aspectRatio(1.6f)
                .clip(RoundedCornerShape(13.dp)).clickable(onClickLabel = "Подробнее") { onOpen(id) })
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Подробнее") { onOpen(id) },
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.optString("title"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (product.optString("variant_label").isNotBlank()) Text(product.optString("variant_label"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (product.optInt("review_count") > 0) Text("★ ${product.optDouble("rating_average")} · ${product.optInt("review_count")} отзывов",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (variants.size > 1) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                variants.forEach { variant -> FilterChip(variant.optLong("id") == id, { onVariant(variant.optLong("id")) },
                    { Text(variant.optString("variant_label").ifBlank { variant.optString("title") } + " · " + money(variant.optLong("price_kopecks"))) },
                    Modifier.testTag("store-variant-${variant.optLong("id")}"),
                    colors = storeVariantColors(), border = storeVariantBorder(variant.optLong("id") == id), shape = RoundedCornerShape(10.dp)) }
            }
            Text(money(product.optLong("price_kopecks")), color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            val quantity = cartRows.firstOrNull { it.optLong("product_id", it.optLong("id")) == id }?.optInt("quantity") ?: 0
            if (quantity > 0) StoreQuantityControl(id, quantity, busy, onChange, onSet)
            else OutlinedButton({ onAdd(id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("store-add-$id"),
                enabled = !busy, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, Color(0xFF04C160)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF2C2D30),
                    disabledContainerColor = Color.White, disabledContentColor = Color(0xFF2C2D30)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("В корзину", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Добавить", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StoreQuantityControl(id: Long, quantity: Int, busy: Boolean,
                                 onChange: (Long, Int) -> Unit, onSet: (Long, Int) -> Unit) {
    var input by remember(id, quantity) { mutableStateOf(quantity.toString()) }
    var focused by remember(id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(id, quantity, busy, focused) {
        if (!busy && !focused) input = quantity.toString()
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Color(0xFFF4F7FB), RoundedCornerShape(12.dp))
        .testTag("store-quantity-$id"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        TextButton({ onChange(id, -1) }, Modifier.size(48.dp).semantics { contentDescription = "Уменьшить количество" },
            enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1769FF))) { Text("−", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
        BasicTextField(input, { value -> input = value.filter(Char::isDigit).take(3) },
            Modifier.width(48.dp).heightIn(min = 48.dp).testTag("store-quantity-input-$id")
                .semantics { contentDescription = "Количество товара" }
                .onFocusChanged { state ->
                    if (focused && !state.isFocused && !busy) {
                        val next = (input.toIntOrNull() ?: 0).coerceIn(0, 999)
                        if (next != quantity) onSet(id, next)
                    }
                    focused = state.isFocused
                },
            enabled = !busy, singleLine = true,
            textStyle = TextStyle(color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { field -> Box(contentAlignment = Alignment.Center) { field() } })
        TextButton({ onChange(id, 1) }, Modifier.size(48.dp).semantics { contentDescription = "Увеличить количество" },
            enabled = !busy && quantity < 999, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1769FF))) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
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
            Text(money(p.optLong("price_kopecks")), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold)
            val oldPrice = p.optLong("base_price_kopecks")
            if (oldPrice > p.optLong("price_kopecks")) Text("Без скидки: ${money(oldPrice)}")
            if (p.optString("variant_label").isNotBlank()) Text("Вариант: ${p.optString("variant_label")}")
            val variants = p.items("variants")
            if (variants.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                variants.forEach { v -> FilterChip(v.optLong("id") == id, { choose(v.optLong("id")) },
                    { Text(v.optString("variant_label").ifBlank { v.optString("title") } + " · " + money(v.optLong("price_kopecks"))) },
                    colors = storeVariantColors(), border = storeVariantBorder(v.optLong("id") == id), shape = RoundedCornerShape(10.dp)) }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            if (error.isNotBlank()) ErrorMessage(error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ add(id) }, Modifier.weight(1f).testTag("store-detail-add-$id")
                    .background(Brush.linearGradient(listOf(Color(0xFF1B937C), Color(0xFF147A66))), RoundedCornerShape(9.dp)),
                    enabled = !busy, shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White,
                        disabledContainerColor = Color.Transparent, disabledContentColor = Color.White)) {
                    Text("Добавить в корзину", fontWeight = FontWeight.ExtraBold)
                }
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
private fun CartContent(api: ApiClient, catalog: JSONObject, cart: JSONObject?, busy: Boolean, error: String,
                        onLogin: () -> Unit, onRefresh: () -> Unit, onChange: (Long, Int) -> Unit, onRemove: (Long) -> Unit) {
    SectionCard("Корзина") {
        Text("Ваш заказ · Товары и доставка", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error.isNotBlank()) ErrorMessage(error)
        if (cart == null && error.isBlank()) CircularProgressIndicator()
        cart?.optString("merge_notice")?.takeIf { it.isNotBlank() }?.let { Text(it) }
        OutlinedButton(onRefresh, enabled = !busy) { Text("Обновить корзину") }
        val rows = cart?.items("items") ?: emptyList()
        if (cart != null && rows.isEmpty()) Text("Корзина пуста. Выберите товары в магазине.")
        rows.forEach { item ->
            SectionCard(item.optString("title")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetworkImage(photoUrl(api, item), item.optString("title"), Modifier.size(60.dp))
                    Text(money(item.optLong("price_kopecks") * item.optInt("quantity", 1)), fontWeight = FontWeight.Bold)
                }
                val id = item.optLong("product_id", item.optLong("id"))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onChange(id, -1) }, Modifier.testTag("cart-minus-$id"), enabled = !busy) { Text("−") }
                    Text("${item.optInt("quantity", 1)} шт.")
                    OutlinedButton({ onChange(id, 1) }, Modifier.testTag("cart-plus-$id"), enabled = !busy && item.optInt("quantity", 1) < 999) { Text("+") }
                }
                TextButton({ onRemove(id) }, enabled = !busy) { Text("Убрать товар") }
            }
        }
        if (rows.isNotEmpty()) {
            val subtotal = rows.sumOf { it.optLong("price_kopecks") * it.optInt("quantity", 1) }
            val digitalIds = catalog.items("products").filter { it.optString("product_type") == "digital_book" }.map { it.optLong("id") }.toSet()
            val minimum = if (rows.all { it.optLong("product_id") in digitalIds }) 0 else catalog.optJSONObject("store_settings")?.optLong("minimum_order_kopecks") ?: 0
            Text("Товары: ${money(subtotal)}", style = MaterialTheme.typography.titleLarge)
            if (subtotal < minimum) Text("До минимального заказа осталось ${money(minimum - subtotal)}")
            if (!api.authenticated) Button(onLogin, Modifier.fillMaxWidth(), enabled = !busy) { Text("Войти для оформления") }
            else if (cart != null) CheckoutContent(api, cart, catalog, busy, onRefresh)
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
internal fun CheckoutScreen(api: ApiClient, cart: JSONObject, catalog: JSONObject, onBack: () -> Unit) {
    Page("Оформление заказа", "Оплата через Т-Банк", onBack) { CheckoutContent(api, cart, catalog, onBack = onBack) }
}

@Composable
private fun CheckoutContent(api: ApiClient, cart: JSONObject, catalog: JSONObject, cartUpdating: Boolean = false, onBack: () -> Unit) {
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
    var method by rememberSaveable { mutableStateOf(if (allDigital) "digital" else catalog.items("delivery_methods").firstOrNull()?.optString("code") ?: "") }
    var showPointPicker by rememberSaveable { mutableStateOf(false) }
    var point by remember { mutableStateOf<JSONObject?>(null) }
    var manualPoint by rememberSaveable { mutableStateOf(false) }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var quotedFor by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf<JSONObject?>(null) }
    var deliveryLoading by remember { mutableStateOf(false) }
    var estimateLoading by remember { mutableStateOf(false) }
    var deliveryError by remember { mutableStateOf("") }
    var estimateError by remember { mutableStateOf("") }
    var deliveryAttempt by remember { mutableIntStateOf(0) }
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
    val pointId = if (!allDigital && method == "ozon" && !manualPoint) point?.optLong("delivery_point_id") ?: 0L else 0L
    val quoteKey = if (ozonApi && pointId > 0) OzonDelivery.quoteKey(pointId, phone, rows, catalog.optBoolean("premium_active")) else ""
    val currentQuote = quote?.takeIf { quoteKey.isNotBlank() && quotedFor == quoteKey }
    val estimateDays = OzonDelivery.estimateDays(estimate, pointId)
    val deliveryDays = currentQuote?.optInt("estimated_delivery_days")?.takeIf { it > 0 } ?: estimateDays
    val shipping = if (allDigital) 0L else if (method == "ozon" && ozonApi && !manualPoint) currentQuote?.optLong("price_kopecks")
        else nativeDeliveryPrice(weight, catalog.items("delivery_sizes"))
    LaunchedEffect(allDigital) {
        if (allDigital) method = "digital"
        else if (method == "digital") method = catalog.items("delivery_methods").firstOrNull()?.optString("code") ?: ""
    }
    LaunchedEffect(pointId, deliveryAttempt) {
        estimate = null; estimateError = ""; estimateLoading = false
        if (pointId <= 0) return@LaunchedEffect
        estimateLoading = true
        try {
            val response = api.post("${OzonDelivery.API}estimate", JSONObject().put("delivery_point_id", pointId)).optJSONObject("estimate")
            ensureActive()
            estimate = response
            if (OzonDelivery.estimateDays(response, pointId) <= 0) estimateError = "Ozon пока не сообщил срок доставки для этого ПВЗ."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { estimateError = storeFailure(e) }
        finally { estimateLoading = false }
    }
    LaunchedEffect(quoteKey, deliveryAttempt) {
        quote = null; quotedFor = ""; deliveryError = ""; deliveryLoading = false
        if (quoteKey.isBlank()) return@LaunchedEffect
        delay(280)
        deliveryLoading = true
        try {
            val response = api.post("${OzonDelivery.API}quote", cartRequest(rows)
                .put("delivery_point_id", pointId).put("phone", phone.trim())).optJSONObject("quote")
            ensureActive()
            if (response == null || response.optJSONObject("delivery_point")?.optLong("delivery_point_id") != pointId)
                throw IllegalStateException("Ozon не подтвердил доставку в выбранный ПВЗ. Повторите расчёт.")
            quote = response; quotedFor = quoteKey
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { deliveryError = storeFailure(e) }
        finally { deliveryLoading = false }
    }
    if (showPointPicker) OzonPickupPicker(api, point, { showPointPicker = false }) { selected ->
        point = selected
        manualPoint = OzonDelivery.manual(selected)
        address = selected.optString("full_address")
        quote = null; quotedFor = ""; estimate = null
        showPointPicker = false
    }
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
        if (busy || cartUpdating) return
        error = ""; busy = true
        scope.launch {
            try {
                if (pendingPayload.isBlank()) {
                    val body = cartRequest(rows).put("customer_name", name.trim()).put("phone", phone.trim())
                        .put("address", address.trim()).put("digital_delivery_email", email.trim()).put("delivery_method", method)
                        .put("use_sausages", useRewards).put("accept_public_offer", offer)
                        .put("accept_privacy_policy", privacy).put("accept_age_18", adult)
                    if (method == "ozon" && !allDigital) {
                        if (manualPoint) {
                            body.put("ozon_manual_pvz_address", address.trim())
                            point?.let { body.put("ozon_manual_pvz_name", OzonDelivery.pointName(it)) }
                        }
                        else body.put("ozon_delivery_point_id", point?.optLong("delivery_point_id") ?: 0L)
                        if (ozonApi && !manualPoint) body.put("expected_ozon_quote", currentQuote)
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
                    quote = e.details.optJSONObject("quote"); quotedFor = quoteKey; clearAttempt()
                    info = "Стоимость доставки обновлена. Проверьте сумму перед повторным подтверждением."
                } else if (e.status in listOf(400, 401, 403) || e.details.optString("reason") == "checkout_attempt_finished") {
                    order = e.details.optJSONObject("order"); clearAttempt()
                }
            } catch (e: Exception) { error = storeFailure(e) }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            return@Column
        }
        if (order != null) SectionCard("Результат заказа") { StoreOrderSummary(order!!) }
        if (order?.optString("status") in setOf("paid", "ready")) {
            Button(onBack) { Text("Обновить корзину") }; return@Column
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("ФИО") }, singleLine = true)
        OutlinedTextField(phone, { phone = it; quote = null }, Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text("Телефон") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
        if (allDigital) Text("Электронная доставка: бесплатно")
        else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SiteSelect("Способ доставки", method, catalog.items("delivery_methods").map { it.optString("code") to it.optString("title") },
                { method = it; quote = null }, enabled = !busy)
            if (method == "ozon") {
                Text("Пункт выдачи Ozon", style = MaterialTheme.typography.labelMedium)
                Button({ showPointPicker = true }, Modifier.fillMaxWidth().testTag("ozonChoosePoint"), enabled = !busy) {
                    Text(if (point != null) "Выбрать другой ПВЗ" else "Выбрать ПВЗ")
                }
                if (point != null) {
                    Text(OzonDelivery.pointName(point!!), style = MaterialTheme.typography.titleSmall)
                    Text("Выбран ПВЗ: ${point!!.optString("full_address")}")
                }
                if (pointId > 0) {
                    if (deliveryDays > 0) Text("Примерный срок доставки: ${OzonDelivery.daysText(deliveryDays)}", Modifier.testTag("ozonDeliveryEstimate"))
                    else if (estimateLoading) Text("Ozon рассчитывает срок доставки…")
                    if (deliveryLoading) Text("Ozon рассчитывает стоимость доставки…")
                    if (currentQuote != null) {
                        Text("Доставка: ${money(currentQuote.optLong("price_kopecks"))}")
                        if (currentQuote.optLong("insurance_cost_kopecks") > 0) Text("Включая страховку ${money(currentQuote.optLong("insurance_cost_kopecks"))}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (ozonApi && quoteKey.isBlank()) Text("Укажите телефон перед расчётом стоимости доставки Ozon.", style = MaterialTheme.typography.bodySmall)
                    if (deliveryError.isNotBlank()) ErrorMessage(deliveryError)
                    if (estimateError.isNotBlank() && deliveryDays <= 0) ErrorMessage(estimateError)
                    if (deliveryError.isNotBlank() || (estimateError.isNotBlank() && deliveryDays <= 0))
                        OutlinedButton({ deliveryAttempt++ }, enabled = !busy && !deliveryLoading && !estimateLoading) { Text("Повторить расчёт доставки") }
                } else if (manualPoint) {
                    Text("Стоимость доставки — по тарифу магазина. Ozon не предоставляет срок для ПВЗ, введённого вручную.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (method.isNotBlank() && method != "ozon") OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text("Адрес пункта выдачи заказов") }, minLines = 3)
        }
        if (hasDigital) OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text("E-mail для доставки цифровых товаров") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
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
        if (rewards > 0 && rewardRules?.optInt("is_active") == 1) StoreCheck("Списать колбаски (доступно ${money(rewards)})", useRewards, !busy) { useRewards = it }
        val deliveryReady = allDigital || (method.isNotBlank() && if (method == "ozon" && !manualPoint) pointId > 0 && (!ozonApi || currentQuote != null) else address.trim().length >= 5)
        val ready = name.trim().length >= 3 && phone.filter(Char::isDigit).length >= 7 && deliveryReady &&
            (!hasDigital || android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) && offer && privacy && adult && total - spent >= minimumOrder
        Button(::submit, Modifier.fillMaxWidth(), enabled = !busy && !cartUpdating && ready && catalog.optJSONObject("payments")?.optBoolean("enabled") == true) {
            Text(if (busy) "Создаём заказ…" else "Оформить заказ")
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
