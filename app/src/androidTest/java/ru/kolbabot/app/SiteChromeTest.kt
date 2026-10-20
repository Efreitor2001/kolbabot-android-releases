package ru.kolbabot.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cartRemainsAtTheTopAfterTheSiteHeaderScrollsAway() {
        var opened = 0
        val header = SiteChromeState().apply { onCart = { opened++ } }
        compose.setContent { KolbaTheme { CompositionLocalProvider(LocalSiteChrome provides header) {
            Page("Магазин") {
                repeat(16) { Text("Раздел $it"); Spacer(Modifier.height(70.dp)) }
            }
        } } }
        compose.onNodeWithText("Раздел 15").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Корзина: 0").assertIsDisplayed().performClick()
        val top = compose.onNodeWithText("Корзина: 0").getUnclippedBoundsInRoot().top.value
        assertTrue("Cart should remain near the top while scrolling; got $top dp", top in 8f..54f)
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun guestCanOpenExistingCartAndSignInWithoutAccountOnlyActions() {
        var cartOpens = 0
        var loginOpens = 0
        val header = SiteChromeState().apply {
            cartCount = 3
            onCart = { cartOpens++ }
            onAccount = { loginOpens++ }
            onLogout = { error("Guest must not call logout") }
            onNotifications = { error("Guest must not open account notifications") }
        }
        compose.setContent { KolbaTheme { SiteChrome(header) } }
        compose.onNodeWithText("Гость", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Демо").assertIsDisplayed()
        compose.onNodeWithText("Корзина: 3").performClick()
        compose.onNodeWithText("Войти", substring = false).performClick()
        compose.onNodeWithText("Выйти", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Уведомления").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, cartOpens); assertEquals(1, loginOpens) }
    }

    @Test fun logoutAndAccountChangeReplaceIdentityTierAndPrivateActionsWithoutRecreatingHeader() {
        var logoutCalls = 0
        val header = SiteChromeState().apply {
            user = JSONObject().put("id", 7).put("username", "Первый аккаунт")
            access = JSONObject().put("is_admin", true)
        }
        header.onLogout = { logoutCalls++; header.user = null; header.access = null; header.cartCount = 0 }
        compose.setContent { KolbaTheme { SiteChrome(header) } }
        compose.onNodeWithText("Первый аккаунт", substring = false).assertIsDisplayed()
        compose.onNodeWithText("ADMIN").assertIsDisplayed()
        compose.onNodeWithContentDescription("Уведомления").assertIsDisplayed()
        compose.onNodeWithText("Выйти", substring = false).performClick()
        compose.onNodeWithText("Гость", substring = false).assertIsDisplayed()
        compose.onNodeWithText("ADMIN").assertDoesNotExist()
        compose.onNodeWithText("Первый аккаунт", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Уведомления").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, logoutCalls)
            header.user = JSONObject().put("id", 42).put("username", "Второй аккаунт")
            header.access = JSONObject().put("access_tier", "premium")
            header.busy = true
        }
        compose.onNodeWithText("Второй аккаунт", substring = false).assertIsDisplayed()
        compose.onNodeWithText("PREMIUM").assertIsDisplayed()
        compose.onNodeWithText("Выйти", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("ADMIN").assertDoesNotExist()
        compose.onNodeWithText("Гость", substring = false).assertDoesNotExist()
    }
}
