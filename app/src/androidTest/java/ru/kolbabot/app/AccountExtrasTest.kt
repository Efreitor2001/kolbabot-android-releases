package ru.kolbabot.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

@RunWith(AndroidJUnit4::class)
class AccountExtrasTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun promoPreviewSeparatesFirstPaymentAndRenewalAndInvalidatesOnEdit() {
        val api = ExtrasFakeApi(context)
        val validated = CopyOnWriteArrayList<String>()
        compose.setContent { KolbaTheme {
            var code by remember { mutableStateOf(" save25 ") }
            Column {
                OutlinedTextField(code, { code = it }, label = { Text("Промокод") })
                SubscriptionPromoPreview(api, code, onValidated = { validated.add(it?.optString("tier") ?: "empty") })
            }
        } }
        compose.onNodeWithText("Проверить промокод").performClick()
        compose.onNodeWithText("Промокод SAVE25").assertExists()
        compose.onNodeWithText("Первый период:", substring = true).assertTextContains("590 ₽", substring = true)
        compose.onNodeWithText("Далее:", substring = true).assertTextContains("790 ₽", substring = true)
        assertEquals(1, api.posts.size)
        assertEquals("/api/payments/tbank/preview-promo", api.posts[0].first)
        assertEquals(setOf("promo_code"), api.posts[0].second.keys().asSequence().toSet())
        assertEquals("SAVE25", api.posts[0].second.getString("promo_code"))
        assertEquals("premium", validated.last())
        compose.onNode(hasSetTextAction() and hasText("Промокод")).performTextReplacement("NEWCODE")
        compose.onNodeWithText("Промокод SAVE25").assertDoesNotExist()
        compose.onNodeWithText("Первый период:", substring = true).assertDoesNotExist()
        assertEquals("empty", validated.last())
        assertEquals(1, api.posts.size)
    }

    @Test fun linkedAccountsRequireConsentAndRejectCrossOriginApprovalUrl() {
        val api = ExtrasFakeApi(context)
        compose.setContent { KolbaTheme {
            LinkedAccountsScreen(api, JSONObject().put("id", 7).put("username", "native-reader").put("email", "reader@example.invalid"), {})
        } }
        compose.onNodeWithText("google@example.invalid").assertExists()
        compose.onNodeWithText("Привязать Google").assertDoesNotExist()
        compose.onNodeWithText("Привязать VK ID").assertDoesNotExist()
        compose.onNodeWithText("Привязать Mail.ru").performScrollTo().assertIsNotEnabled()
        assertTrue(api.posts.isEmpty())
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Привязать Mail.ru").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Сервер вернул некорректную ссылку подтверждения.").assertExists()
        assertEquals(1, api.posts.size)
        assertEquals("/api/mobile/account-link/start", api.posts[0].first)
        assertEquals("vk", api.posts[0].second.getString("provider"))
        assertTrue(api.posts[0].second.getBoolean("profile_consent"))
    }

    @Test fun profileShowsVerifiedEmailPhoneProviderAndActualRewardBalance() {
        val api = ExtrasFakeApi(context)
        compose.setContent { KolbaTheme { AccountProfileDetails(api, JSONObject().put("id", 7)
            .put("first_name", "Иван").put("last_name", "Петров").put("email", "reader@example.invalid")
            .put("email_verified", true).put("phone", "+79991234567").put("oauth_provider", "mail_ru")) } }
        compose.onNodeWithText("Иван Петров").assertExists()
        compose.onNodeWithText("Email подтверждён").assertExists()
        compose.onNodeWithText("Телефон").assertExists()
        compose.onNodeWithText("+79991234567").assertExists()
        compose.onNodeWithText("Основной вход").assertExists()
        compose.onNodeWithText("Mail.ru").assertExists()
        compose.onNodeWithText("123,45 колбасок").assertExists()
        assertTrue(api.posts.isEmpty())
    }
}

private class ExtrasFakeApi(context: Context) : ApiClient(context, "https://native-account.invalid") {
    val posts = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    init { userId = 7 }
    override suspend fun get(path: String): JSONObject = when (path) {
        "/api/account/oauth/connections" -> JSONObject().put("connections", JSONArray("""[
          {"code":"google","label":"Google","connected":true,"enabled":true,"email":"google@example.invalid"},
          {"code":"vkid","label":"VK ID","connected":false,"enabled":false},
          {"code":"mail_ru","label":"Mail.ru","connected":false,"enabled":true}
        ]"""))
        "/api/store/cart" -> JSONObject().put("items", JSONArray()).put("rewards", JSONObject().put("sausages_kopecks", 12345))
        else -> throw AssertionError("Unexpected GET $path")
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        posts.add(path to JSONObject(body.toString()))
        return when (path) {
            "/api/payments/tbank/preview-promo" -> JSONObject().put("tier", "premium").put("first_month_amount_rub", 590).put("regular_amount_rub", 790).put("duration_days", 30)
            "/api/mobile/account-link/start" -> JSONObject().put("authorization_url", "https://another-account.invalid/mobile/account-link/approve?token=test").put("expires_in", 300)
            else -> throw AssertionError("Unexpected POST $path")
        }
    }
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PATCH $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")
}
