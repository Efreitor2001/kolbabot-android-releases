package ru.kolbabot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Package-level controls deliberately take precedence over Material3 wildcard imports.
 * Every customer/admin screen gets the website's controls, including dialogs/editors.
 * Explicit per-screen colors (Ozon, variants, destructive actions) stay intact. */

@Composable fun Button(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp), colors: ButtonColors? = null,
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit,
) {
    val gradient = colors == null
    val decoration = if (gradient) Modifier.background(
        Brush.linearGradient(listOf(SiteColors.Brand, SiteColors.Brand2)), shape) else Modifier
    androidx.compose.material3.Button(onClick, modifier.heightIn(min = 44.dp)
        .alpha(if (enabled) 1f else 0.78f).then(decoration), enabled, shape,
        colors ?: ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White,
            disabledContainerColor = Color.Transparent, disabledContentColor = Color.White),
        elevation, border, contentPadding, interactionSource, content)
}

@Composable fun OutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp), colors: ButtonColors? = null,
    elevation: ButtonElevation? = null, border: BorderStroke? = BorderStroke(1.dp, SiteColors.Border),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.OutlinedButton(onClick, modifier.heightIn(min = 44.dp).alpha(if (enabled) 1f else 0.78f),
        enabled, shape, colors ?: ButtonDefaults.outlinedButtonColors(containerColor = Color.White,
            contentColor = SiteColors.Ink, disabledContainerColor = Color.White, disabledContentColor = SiteColors.Muted),
        elevation, border, contentPadding, interactionSource, content)
}

@Composable fun TextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp), colors: ButtonColors? = null,
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.TextButton(onClick, modifier, enabled, shape,
        colors ?: ButtonDefaults.textButtonColors(contentColor = SiteColors.Brand),
        elevation, border, contentPadding, interactionSource, content)
}

@Composable fun FilledTonalButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp), colors: ButtonColors? = null,
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.FilledTonalButton(onClick, modifier.heightIn(min = 44.dp), enabled, shape,
        colors ?: ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFEEF2FF), contentColor = SiteColors.Brand),
        elevation, border, contentPadding, interactionSource, content)
}

@Composable fun OutlinedTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null, suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(if (LocalSiteAdmin.current) 10.dp else 12.dp),
    colors: TextFieldColors? = null,
) {
    val admin = LocalSiteAdmin.current
    val auth = LocalSiteAuth.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val palette = colors ?: OutlinedTextFieldDefaults.colors(
            focusedTextColor = SiteColors.Ink, unfocusedTextColor = SiteColors.Ink,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = if (admin) Color.White else SiteColors.InputBackground,
            disabledContainerColor = if (admin) Color.White else SiteColors.InputBackground,
            focusedBorderColor = SiteColors.Brand,
            unfocusedBorderColor = if (admin) SiteColors.InputBorder else Color(0xFF495674).copy(alpha = 0.14f),
            focusedLabelColor = SiteColors.Brand, unfocusedLabelColor = Color(0xFF596579),
            focusedPlaceholderColor = Color(0xFF9CA3AF), unfocusedPlaceholderColor = Color(0xFF9CA3AF),
            errorBorderColor = SiteColors.Error, errorLabelColor = SiteColors.Error,
            cursorColor = SiteColors.Brand)
    fun stateColor(normal: Color, focusedColor: Color, disabled: Color, errorColor: Color) = when {
        !enabled -> disabled
        isError -> errorColor
        focused -> focusedColor
        else -> normal
    }
    val ink = stateColor(palette.unfocusedTextColor, palette.focusedTextColor, palette.disabledTextColor, palette.errorTextColor)
    val fill = stateColor(palette.unfocusedContainerColor, palette.focusedContainerColor, palette.disabledContainerColor, palette.errorContainerColor)
    val edge = stateColor(palette.unfocusedIndicatorColor, palette.focusedIndicatorColor, palette.disabledIndicatorColor, palette.errorIndicatorColor)
    val caption = if (isError) palette.errorLabelColor else if (!enabled) palette.disabledLabelColor else palette.unfocusedLabelColor
    CompositionLocalProvider(LocalTextSelectionColors provides palette.textSelectionColors) {
        BasicTextField(value = value, onValueChange = onValueChange,
            modifier = modifier.semantics(mergeDescendants = true) {
                if (isError) error("Проверьте значение поля")
            }, enabled = enabled, readOnly = readOnly, textStyle = textStyle.copy(color = ink,
                fontSize = if (auth) 15.sp else textStyle.fontSize),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            visualTransformation = visualTransformation, interactionSource = interactions,
            cursorBrush = SolidColor(if (isError) palette.errorCursorColor else palette.cursorColor),
            decorationBox = { innerTextField ->
                // Keep the external caption inside the editable's decoration. It is merged with
                // SetText/EditableText for TalkBack and hasText(label) input automation.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (label != null) CompositionLocalProvider(LocalContentColor provides caption) {
                        ProvideTextStyle(if (auth) MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp) else
                            MaterialTheme.typography.labelMedium) { label() }
                    }
                    Row(Modifier.fillMaxWidth().drawBehind {
                        if (focused && enabled) drawOutline(shape.createOutline(size, layoutDirection, this),
                            SiteColors.Brand.copy(alpha = 0.1f), style = Stroke(6.dp.toPx()))
                    }.background(fill, shape).border(1.dp, edge, shape).heightIn(min = if (auth) 48.dp else 44.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalContentColor provides ink) {
                            leadingIcon?.invoke()
                            prefix?.invoke()
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (value.isEmpty() && placeholder != null) CompositionLocalProvider(LocalContentColor provides
                                    stateColor(palette.unfocusedPlaceholderColor, palette.focusedPlaceholderColor,
                                        palette.disabledPlaceholderColor, palette.errorPlaceholderColor)) {
                                    ProvideTextStyle(textStyle) { placeholder() }
                                }
                                innerTextField()
                            }
                            suffix?.invoke()
                            trailingIcon?.invoke()
                        }
                    }
                    if (supportingText != null) CompositionLocalProvider(LocalContentColor provides
                        stateColor(palette.unfocusedSupportingTextColor, palette.focusedSupportingTextColor,
                            palette.disabledSupportingTextColor, palette.errorSupportingTextColor)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) { supportingText() }
                    }
                }
            })
    }
}

@Composable fun FilterChip(
    selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50), colors: SelectableChipColors? = null,
    elevation: SelectableChipElevation? = null, border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val decoration = if (selected && colors == null) Modifier.background(
        Brush.linearGradient(listOf(SiteColors.Brand, SiteColors.Brand2)), shape) else Modifier
    androidx.compose.material3.FilterChip(selected, onClick, label, modifier.then(decoration), enabled,
        leadingIcon, trailingIcon, shape,
        colors ?: FilterChipDefaults.filterChipColors(containerColor = Color.White, labelColor = SiteColors.Ink,
            selectedContainerColor = Color.Transparent, selectedLabelColor = Color.White),
        elevation, border ?: BorderStroke(1.dp, if (selected) Color.Transparent else SiteColors.Border), interactionSource)
}
