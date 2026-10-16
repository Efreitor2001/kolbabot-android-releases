package ru.kolbabot.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** External website labels must remain attached to the editable accessibility node. */
@RunWith(AndroidJUnit4::class)
class SiteFieldSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun externalLabelKeepsInputSemanticsAndDoesNotMoveOnFocusOrTyping() {
        var input by mutableStateOf("")
        compose.setContent { KolbaTheme {
            OutlinedTextField(input, { input = it }, label = { Text("Email", Modifier.testTag("email-label")) })
        } }
        val labelTop = compose.onNodeWithTag("email-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val editable = compose.onNode(hasText("Email") and hasSetTextAction())
        editable.performClick().performTextInput("user@example.com")
        compose.runOnIdle { assertEquals("user@example.com", input) }
        editable.assertTextContains("user@example.com")
        assertEquals(labelTop, compose.onNodeWithTag("email-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top, 0.5f)
        editable.performTextReplacement("changed@example.com")
        compose.runOnIdle { assertEquals("changed@example.com", input) }
    }

    @Test fun disabledAndPasswordFieldsKeepTheirSemantics() {
        var password by mutableStateOf("")
        compose.setContent { KolbaTheme { Column {
            OutlinedTextField("Отключено", {}, enabled = false, label = { Text("Недоступное поле") })
            OutlinedTextField(password, { password = it }, label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true)
        } } }
        compose.onNode(hasText("Недоступное поле")).assertIsNotEnabled()
        compose.onNode(hasText("Пароль") and hasSetTextAction()).performTextInput("test-password")
        compose.runOnIdle { assertEquals("test-password", password) }
        // Compose exposes the raw InputText to test/autofill APIs even for password fields.
        // The rendered/accessibility EditableText must remain masked and marked Password.
        compose.onNode(hasText("Пароль") and hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("•".repeat(13))))
    }
}
