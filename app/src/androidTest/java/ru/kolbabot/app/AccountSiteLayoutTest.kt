package ru.kolbabot.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** The site keeps profile, billing, OAuth and orders on one account page. */
@RunWith(AndroidJUnit4::class)
class AccountSiteLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun accountKeepsWebsiteBlockOrderAndExpandsHistoryAndConnectionsInline() {
        val api = AccountLayoutApi(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent { KolbaTheme { AccountScreen(api, api.profile()) { error("Unexpected logout") } } }
        compose.onNodeWithText("Рады вас видеть, Иван").assertExists()
        val headings = listOf("Ваши данные", "Ваш доступ", "Способы входа", "История заказов", "Оформить PREMIUM")
        val positions = headings.map { compose.onNodeWithText(it).getUnclippedBoundsInRoot().top }
        assertEquals("Account blocks must follow the website order.", positions.sorted(), positions)
        compose.onNodeWithText("Покупки и доступ").assertDoesNotExist()
        compose.onNodeWithText("Аккаунт и помощь").assertDoesNotExist()
        compose.onNodeWithText("Способы входа").performScrollTo().performClick()
        compose.waitUntil { api.reads.contains("/api/account/oauth/connections") }
        compose.onNodeWithText("Подключённые аккаунты").assertExists()
        compose.onNodeWithText("Ваши данные").assertExists()
        compose.onNodeWithText("История заказов").performScrollTo().performClick()
        compose.waitUntil { api.reads.contains("/api/store/orders") }
        compose.onNodeWithText("Оплаченных заказов пока нет").assertExists()
        compose.onNodeWithText("Ваш доступ").assertExists()
        compose.onNodeWithText("Оформить PREMIUM").assertExists()
    }
}

private class AccountLayoutApi(context: Context) : ApiClient(context, "https://account-layout.invalid") {
    val reads = CopyOnWriteArrayList<String>()
    init { userId = 900000818 }
    fun profile() = JSONObject().put("id", userId).put("username", "layout-reader")
        .put("first_name", "Иван").put("email", "reader@example.invalid").put("email_verified", true)

    override suspend fun get(path: String): JSONObject {
        reads += path
        return when (path) {
            "/api/auth/me" -> JSONObject().put("user", profile())
            "/api/store/cart" -> JSONObject().put("rewards", JSONObject().put("sausages_kopecks", 10000))
            "/api/account/billing" -> JSONObject().put("tier", "none").put("status", "inactive")
            "/api/bootstrap" -> JSONObject().put("payments", JSONObject().put("enabled", true).put("plans", JSONArray("""[{"title":"PREMIUM","amount_kopecks":79000,"duration_days":30}]""")))
            "/api/account/oauth/connections", "/api/store/orders" -> JSONObject().put("items", JSONArray()).put("connections", JSONArray())
            "/api/android/push/config" -> JSONObject().put("enabled", false)
            else -> throw AssertionError("Unexpected GET $path")
        }
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected POST $path")
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PATCH $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")
}
