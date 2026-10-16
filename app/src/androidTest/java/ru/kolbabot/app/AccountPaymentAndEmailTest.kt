package ru.kolbabot.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** The real payment entry contract, always intercepted before reaching a bank. */
@RunWith(AndroidJUnit4::class)
class AccountPaymentAndEmailTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun clearPendingPayment() {
        context.getSharedPreferences("subscription_payment", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun removeTestPayment() = clearPendingPayment()

    @Test fun activePremiumShowsSavedSbpAndCannotCreateAnotherPayment() {
        val api = AccountPaymentFakeApi(context, active = true)
        compose.setContent { KolbaTheme { SubscriptionScreen(api) {} } }
        compose.onNodeWithText("Способ оплаты: СБП").assertExists()
        compose.onNodeWithText("PREMIUM уже активна.", substring = true).assertExists()
        compose.onNodeWithText("Перейти к оплате в Т-Банке").assertDoesNotExist()
        compose.onNodeWithText("Оформить подписку").assertDoesNotExist()
        assertTrue(api.posts.isEmpty())
    }

    @Test fun paymentRequiresEveryConsentAndKeepsPendingInvoiceInsteadOfCreatingAnother() {
        val api = AccountPaymentFakeApi(context)
        compose.setContent { KolbaTheme { SubscriptionScreen(api) {} } }
        compose.onNodeWithText("Перейти к оплате в Т-Банке").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("Промокод")).performScrollTo().performTextReplacement(" sale25 ")
        (0..2).forEach { compose.onAllNodes(isToggleable())[it].performScrollTo().performClick() }
        compose.onNodeWithText("Перейти к оплате в Т-Банке").performScrollTo().assertIsNotEnabled()
        assertTrue(api.posts.isEmpty())
        compose.onAllNodes(isToggleable())[3].performScrollTo().performClick()
        compose.onNodeWithText("Перейти к оплате в Т-Банке").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Банк проверяет платёж. Повторно оплачивать не нужно.").assertExists()
        compose.onNodeWithText("Перейти к оплате в Т-Банке").assertDoesNotExist()
        val payment = api.posts.single()
        assertEquals("/api/payments/tbank/init", payment.first)
        assertEquals("premium", payment.second.getString("tier"))
        assertEquals("SALE25", payment.second.getString("promo_code"))
        assertEquals(setOf("tier", "promo_code", "accept_public_offer", "accept_subscription_agreement", "accept_payment_credentials", "accept_privacy_policy"), payment.second.keys().asSequence().toSet())
        listOf("accept_public_offer", "accept_subscription_agreement", "accept_payment_credentials", "accept_privacy_policy").forEach { assertTrue(payment.second.getBoolean(it)) }
        compose.onNodeWithText("Проверить статус").performScrollTo().performClick()
        compose.onNodeWithText("Статус оплаты: NEW").assertExists()
        assertEquals(1, api.posts.size)
        assertEquals("native-fixture-order", context.getSharedPreferences("subscription_payment", Context.MODE_PRIVATE).getString("order_7", ""))
    }

    @Test fun resendingEmailUsesPendingAddressAndConfirmationRequiresSixDigits() {
        val api = AccountPaymentFakeApi(context)
        var confirmed = 0
        compose.setContent { KolbaTheme { EmailScreen(api, JSONObject(), onConfirmed = { confirmed++ }) {} } }
        compose.onNode(hasSetTextAction() and hasText("Email")).performTextReplacement(" Reader@Example.Invalid ")
        compose.onNodeWithText("Получить код").performClick()
        compose.onNodeWithText("Отправить снова").performScrollTo().performClick()
        assertEquals(2, api.posts.size)
        api.posts.forEach { (path, body) ->
            assertEquals("/api/account/email/start", path)
            assertEquals(setOf("email"), body.keys().asSequence().toSet())
            assertEquals("reader@example.invalid", body.getString("email"))
        }
        compose.onNodeWithText("Подтвердить email").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("Код из письма")).performTextReplacement("12345")
        compose.onNodeWithText("Подтвердить email").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("Код из письма")).performTextReplacement("123456")
        compose.onNodeWithText("Подтвердить email").performClick()
        compose.onNodeWithText("Адрес подтверждён.").assertExists()
        val request = api.posts.last()
        assertEquals("/api/account/email/confirm", request.first)
        assertEquals("reader@example.invalid", request.second.getString("email"))
        assertEquals("123456", request.second.getString("code"))
        assertEquals(1, confirmed)
    }
}

private class AccountPaymentFakeApi(context: Context, private val active: Boolean = false) : ApiClient(context, "https://native-payment.invalid") {
    val posts = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    init { userId = 7 }
    override suspend fun get(path: String): JSONObject = when {
        path == "/api/account/billing" -> JSONObject().put("user_id", 7).put("tier", if (active) "premium" else "none")
            .put("status", if (active) "active" else "inactive").put("active_until", if (active) "2099-01-01 00:00:00" else JSONObject.NULL)
            .put("autopay_enabled", active).put("autopay_method", if (active) "sbp" else "card").put("has_sbp", active).put("has_rebill", false)
        path == "/api/bootstrap" -> JSONObject().put("payments", JSONObject().put("enabled", true).put("plans", JSONArray("""[{"tier":"premium","title":"PREMIUM","amount_kopecks":79000,"duration_days":30,"description":"Подписка на 30 дней"}]""")))
        path == "/api/payments/tbank/status?order_id=native-fixture-order" -> JSONObject().put("status", "NEW").put("is_success", false).put("is_final", false)
        else -> throw AssertionError("Unexpected GET $path")
    }
    override suspend fun post(path: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        posts.add(path to JSONObject(body.toString()))
        return when (path) {
            "/api/payments/tbank/init" -> {
                check(!active) { "An active subscriber must never submit Init" }
                JSONObject().put("order_id", "native-fixture-order").put("pending_reconciliation", true)
            }
            "/api/account/email/start" -> JSONObject().put("email", body.getString("email")).put("pending_verification", true).put("expires_in_minutes", 30)
            "/api/account/email/confirm" -> JSONObject().put("user", JSONObject().put("id", 7).put("email", body.getString("email")).put("email_verified", true))
            else -> throw AssertionError("Unexpected POST $path")
        }
    }
    override suspend fun put(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PUT $path")
    override suspend fun patch(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected PATCH $path")
    override suspend fun delete(path: String, body: JSONObject, headers: Map<String, String>): JSONObject = throw AssertionError("Unexpected DELETE $path")
}
