package ru.kolbabot.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TsehNativeSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unactivatedGuestCanOpenTheNativeProductAndSignIn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val api = object : ApiClient(context) {
            override suspend fun downloadFile(path: String, body: JSONObject?): Pair<ByteArray, String> {
                assertEquals("/tseh", path)
                return """
                    <title>Цех на бумаге — Колбабот</title>
                    <main><a id="tsehBuyLink" href="/?product=82#home">Купить за 1500 ₽</a></main>
                """.trimIndent().toByteArray() to "text/html"
            }
        }
        var product = 0L
        var signIn = false
        compose.setContent { KolbaTheme {
            TsehScreen(api, 0, onStore = { product = it }, onSignIn = { signIn = true }, onSupport = {})
        } }
        waitForText("Купить за 1500 ₽")
        compose.onNodeWithText("Купить за 1500 ₽").performScrollTo().performClick()
        compose.onNodeWithText("Войти в аккаунт КолбаБота").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(82L, product); assertEquals(true, signIn) }
    }

    @Test fun paidCalculatorBindsNativeInputToTheResult() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val api = object : ApiClient(context) {
            override suspend fun downloadFile(path: String, body: JSONObject?): Pair<ByteArray, String> = when (path) {
                "/tseh" -> """
                    <title>Цех на бумаге — Колбабот</title>
                    <main class="panel">
                      <section id="step1"><div class="field"><label>Как вас зовут?</label><input data-path="profile.name"></div></section>
                      <section id="step4"><div id="report"></div></section>
                    </main>
                """.trimIndent().toByteArray() to "text/html"
                "/tseh/calculator.js" -> """
                    let step = 0, data = {profile: {name: ""}};
                    document.addEventListener("input", ({target}) => { data.profile.name = target.value; });
                    function go(index) {
                        step = index;
                        nativeNode("report").textContent = "Ваш проект: " + data.profile.name;
                    }
                """.trimIndent().toByteArray() to "text/javascript"
                else -> error("Unexpected route: " + path)
            }
        }
        compose.setContent { KolbaTheme { TsehScreen(api, 1, {}, {}, {}) } }
        waitForText("Как вас зовут?")
        compose.onNode(hasSetTextAction() and hasText("Как вас зовут?")).performScrollTo().performTextInput("Василий")
        compose.onNodeWithText("Результат").performScrollTo().performClick()
        waitForText("Ваш проект: Василий")
        compose.onNodeWithText("Ваш проект: Василий").assertExists()
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
