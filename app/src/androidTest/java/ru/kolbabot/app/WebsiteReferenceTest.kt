package ru.kolbabot.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The production application stays native. Only this test hosts the exact website HTML/CSS.
 * All requests are intercepted locally; page scripts are disabled at the resource boundary.
 * Fixtures only set content and existing visibility/selection classes, never CSS or geometry.
 */
@RunWith(AndroidJUnit4::class)
class WebsiteReferenceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val assets get() = instrumentation.context.assets
    private var web: WebView? = null
    private val loaded = AtomicBoolean(false)

    @After fun destroy() { instrumentation.runOnMainSync { web?.destroy(); web = null } }

    @Test fun authUsesTheExactMobileWebsiteStyles() {
        render("auth.html", """
            ['loginOAuthWrap','loginGoogleWrap','loginOAuthDivider'].forEach(id => document.getElementById(id).classList.remove('hidden'));
            document.getElementById('authResult').textContent='Войдите через Google, VK ID, Mail.ru, Одноклассники или по логину и паролю.';
        """)
        screenshot("auth-top")
        evaluate("document.getElementById('loginOAuthWrap').scrollIntoView(); true")
        screenshot("auth-form")
    }

    @Test fun storeUsesTheExactHeaderAndCatalogOrder() {
        render("index.html", guestFixture + """
            document.getElementById('storeCategories').innerHTML='<button class="chip is-active" type="button">Все товары</button><button class="chip" type="button">Оболочки</button>';
            document.getElementById('storeGrid').innerHTML='<p class="muted">Товаров пока нет.</p>';
        """)
        screenshot("store-top")
        evaluate("document.getElementById('tab-home').scrollIntoView(); true")
        screenshot("store-catalog")
    }

    @Test fun recipesKeepBothSelectsAboveTheRecipeAndCalculator() {
        render("index.html", guestFixture + """
            document.querySelectorAll('.panel').forEach(e=>e.classList.remove('is-active'));
            document.getElementById('tab-recipes').classList.add('is-active');
            document.querySelectorAll('[data-tab]').forEach(e=>e.classList.toggle('is-active',e.dataset.tab==='recipes'));
            document.getElementById('categorySelect').innerHTML='<option>Варёные колбасы</option>';
            document.getElementById('sausageSelect').innerHTML='<option>Выберите сорт</option>';
        """)
        screenshot("recipes-top")
        evaluate("document.getElementById('tab-recipes').scrollIntoView(); true")
        screenshot("recipes-selects")
    }

    @Test fun administratorUsesTheExactDashboardAndNavigation() {
        render("admin.html", """
            document.querySelectorAll('.panel').forEach(e=>e.classList.remove('is-active'));
            document.body.classList.add('admin-ui-open');
            document.getElementById('tab-admin').classList.remove('hidden');
            document.getElementById('tab-admin').classList.add('is-active');
            document.getElementById('adminTabBtn').classList.remove('hidden');
            document.querySelectorAll('.tab[data-tab]').forEach(tab=>{
              tab.classList.toggle('hidden',tab.dataset.tab!=='admin');
              tab.classList.toggle('is-active',tab.dataset.tab==='admin');
            });
            document.getElementById('notificationsBtn').classList.remove('hidden');
            document.getElementById('logoutBtn').classList.remove('hidden');
            document.getElementById('welcomeText').textContent='Привет, Администратор. Сайт готов к работе.';
            document.getElementById('userIdBadge').textContent='Администратор';
            document.getElementById('userRoleBadge').textContent='Администратор';
            document.getElementById('userTierBadge').textContent='ADMIN';
            document.getElementById('adminDashboardAdminName').textContent='Администратор';
            document.getElementById('adminDashboardUpdatedAt').textContent='Обновлено только что';
            Object.entries({Revenue:'12 500 ₽',Orders:'3',Subscriptions:'8',Online:'2',Users:'24',Tickets:'1'}).forEach(([key,value])=>document.getElementById('adminDashboard'+key).textContent=value);
        """)
        screenshot("admin-top")
        evaluate("document.getElementById('admin-panel-dashboard').scrollIntoView(); true")
        screenshot("admin-dashboard")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun render(file: String, fixture: String) {
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { host ->
                WebView(host).apply {
                    web = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.textZoom = 100
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                            val uri = request.url
                            if (uri.host == "fonts.googleapis.com") return response("text/css", fontCss.toByteArray())
                            val name = uri.lastPathSegment.orEmpty()
                            // Returning a response for EVERY request prevents accidental live calls.
                            if (name.endsWith(".js")) return response("application/javascript", ByteArray(0))
                            val allowed = name in setOf("index.html", "auth.html", "admin.html", "styles.css", "max-logo.svg", "site_robot.jpg") ||
                                name.matches(Regex("manrope_(regular|medium|semibold|bold|extrabold)\\.ttf"))
                            val bytes = if (allowed) assets.open("site-reference/$name").use { it.readBytes() } else ByteArray(0)
                            val mime = when {
                                name.endsWith(".css") -> "text/css"
                                name.endsWith(".ttf") -> "font/ttf"
                                name.endsWith(".svg") -> "image/svg+xml"
                                name.endsWith(".jpg") -> "image/jpeg"
                                else -> "text/html"
                            }
                            return response(mime, bytes)
                        }
                        override fun onPageFinished(view: WebView, url: String) { loaded.set(true) }
                    }
                    loadUrl("https://site-reference.invalid/$file")
                }
            })
        }
        compose.waitUntil(30_000) { loaded.get() }
        assertEquals("true", evaluate("""
            (()=>{ $fixture
            void document.body.offsetHeight;
            Promise.all([
              ...[400,500,600,700,800].map(weight=>document.fonts.load(weight+' 14px Manrope')),
              ...Array.from(document.images).map(img=>img.decode().catch(()=>{})),
              ...document.getAnimations().map(animation=>animation.finished.catch(()=>{}))
            ]).then(()=>document.fonts.ready).then(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>window.__referenceReady=true)));
            return true; })()
        """))
        compose.waitUntil(15_000) { evaluate("window.__referenceReady===true") == "true" }
        val size = evaluate("JSON.stringify({width:innerWidth,height:innerHeight,dpr:devicePixelRatio,font:getComputedStyle(document.body).fontFamily})")
        File(outputDirectory(), file.substringBefore('.') + "-viewport.json").writeText(JSONTokener(size).nextValue().toString())
        assertTrue("Website stylesheet was not loaded", evaluate("getComputedStyle(document.body).fontFamily").contains("Manrope"))
    }

    private fun evaluate(script: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync { web!!.evaluateJavascript(script) { result = it; latch.countDown() } }
        check(latch.await(5, TimeUnit.SECONDS)) { "Website reference script timed out" }
        return result
    }

    private fun screenshot(name: String) {
        // A visual-state callback confirms that the requested scroll has reached Chromium's frame.
        val ready = CountDownLatch(1)
        instrumentation.runOnMainSync { web!!.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { ready.countDown() }
        }) }
        assertTrue(ready.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        awaitPresentedUiFrame(instrumentation)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(outputDirectory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val metrics = evaluate("""
            JSON.stringify(['.auth-brand-panel','.auth-card','.auth-card-heading','.auth-switch','.auth-oauth-stack',
              '.auth-oauth-consents','.gsi-material-button','.auth-form','.product-header','.hero__meta','.product-nav',
              '.panel.is-active .user-page-heading','.user-toolbar-card','.admin-tabs','.admin-dashboard__kpis']
              .map(selector=>{const e=document.querySelector(selector);if(!e)return null;const r=e.getBoundingClientRect(),s=getComputedStyle(e);
              return {selector,x:r.x,y:r.y,width:r.width,height:r.height,font:s.fontFamily,fontSize:s.fontSize,color:s.color,background:s.background,borderRadius:s.borderRadius};}).filter(Boolean))
        """)
        File(outputDirectory(), "$name-metrics.json").writeText(JSONTokener(metrics).nextValue().toString())
    }

    private fun outputDirectory() = File(context.getExternalFilesDir(null), "website-reference").apply { mkdirs() }

    private fun response(mime: String, bytes: ByteArray) = WebResourceResponse(mime, "UTF-8", 200, "OK",
        mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(bytes))

    private val fontCss = listOf(400 to "regular", 500 to "medium", 600 to "semibold", 700 to "bold", 800 to "extrabold")
        .joinToString("\n") { (weight, name) -> "@font-face{font-family:Manrope;font-style:normal;font-weight:$weight;src:url('https://site-reference.invalid/manrope_$name.ttf') format('truetype');font-display:block;}" }

    private val guestFixture = """
        document.getElementById('welcomeText').textContent='Изучайте разделы без входа. Для оплаты, сохранения и кабинета войдите в аккаунт.';
        document.getElementById('userIdBadge').textContent='Гость';
        document.getElementById('userRoleBadge').textContent='Гость';
        document.getElementById('userTierBadge').textContent='Демо';
        document.getElementById('authBtn').classList.remove('hidden');
        document.getElementById('heroImage').src='/site_robot.jpg';
        document.getElementById('heroImage').classList.remove('hidden');
        document.getElementById('storeSearchInput').removeAttribute('readonly');
    """
}
