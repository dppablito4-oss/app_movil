package com.example.posapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.unit.dp
import com.example.posapp.ui.theme.SpaceSaleColors
import com.example.posapp.ui.theme.SpaceSaleRadii
import com.example.posapp.ui.theme.SpaceSaleSizes
import com.example.posapp.ui.theme.SpaceSaleSpacing

@Composable
fun SpaceSaleCard(
    modifier: Modifier = Modifier,
    containerColor: Color = SpaceSaleColors.Surface,
    borderColor: Color = SpaceSaleColors.Border,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = SpaceSaleColors.TextPrimary,
        border = BorderStroke(1.dp, borderColor),
        elevation = 0.dp,
        shape = RoundedCornerShape(SpaceSaleRadii.Large),
        content = content
    )
}

@Composable
fun SpaceSalePrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = SpaceSaleColors.Violet,
    contentColor: Color = Color.White,
    disabledContainerColor: Color = SpaceSaleColors.VioletContainer,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = SpaceSaleSizes.ButtonHeight),
        shape = RoundedCornerShape(SpaceSaleRadii.Medium),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = containerColor,
            contentColor = contentColor,
            disabledBackgroundColor = disabledContainerColor,
            disabledContentColor = SpaceSaleColors.TextDisabled
        ),
        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        content = content
    )
}

@Composable
fun SpaceSaleSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = SpaceSaleSizes.ButtonHeight),
        shape = RoundedCornerShape(SpaceSaleRadii.Medium),
        border = BorderStroke(1.dp, SpaceSaleColors.ControlOutline),
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = SpaceSaleColors.Surface,
            contentColor = SpaceSaleColors.TextPrimary,
            disabledContentColor = SpaceSaleColors.TextDisabled
        ),
        content = content
    )
}

@Composable
fun SpaceSaleStatusPill(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    foreground: Color = SpaceSaleColors.Cyan,
    background: Color = SpaceSaleColors.CyanContainer
) {
    Surface(
        modifier = modifier,
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(SpaceSaleRadii.Small)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SpaceSaleSpacing.Sm, vertical = SpaceSaleSpacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = foreground)
            Text(text, style = MaterialTheme.typography.caption, color = foreground)
        }
    }
}

@Composable
fun SpaceSaleScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SpaceSaleSizes.TouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val navigationAction = onBack ?: onMenu
        if (navigationAction != null) {
            IconButton(onClick = navigationAction, modifier = Modifier.size(SpaceSaleSizes.TouchTarget)) {
                Icon(
                    if (onBack != null) Icons.Default.ArrowBack else Icons.Default.Menu,
                    contentDescription = if (onBack != null) "Volver" else "Abrir menu",
                    tint = SpaceSaleColors.Cyan
                )
            }
            Spacer(Modifier.width(SpaceSaleSpacing.Sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.h5,
                color = SpaceSaleColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.body2, color = SpaceSaleColors.TextSecondary)
            }
        }
        action?.invoke(this)
    }
}

@Composable
fun SpaceSaleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SpaceSaleSizes.TouchTarget),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(SpaceSaleRadii.Medium),
        colors = spaceSaleTextFieldColors()
    )
}

@Composable
fun SpaceSaleEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(SpaceSaleSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)
    ) {
        Surface(
            color = SpaceSaleColors.CyanContainer,
            shape = RoundedCornerShape(SpaceSaleRadii.Large)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = SpaceSaleColors.Cyan,
                modifier = Modifier.padding(SpaceSaleSpacing.Lg).size(SpaceSaleSizes.IconLarge)
            )
        }
        Text(title, style = MaterialTheme.typography.h6, color = SpaceSaleColors.TextPrimary)
        Text(description, style = MaterialTheme.typography.body2, color = SpaceSaleColors.TextSecondary)
        if (action != null) {
            Spacer(Modifier.size(SpaceSaleSpacing.Sm))
            action()
        }
    }
}

@Composable
fun SpaceSaleInlineMessage(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = true
) {
    val foreground = if (isError) SpaceSaleColors.Error else SpaceSaleColors.Success
    val background = if (isError) SpaceSaleColors.ErrorContainer else SpaceSaleColors.SuccessContainer
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(SpaceSaleRadii.Small)
    ) {
        Text(text, style = MaterialTheme.typography.body2, modifier = Modifier.padding(SpaceSaleSpacing.Md))
    }
}

@Composable
fun SpaceSaleSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SpaceSaleSpacing.Sm)) {
        Text(title, style = MaterialTheme.typography.subtitle1, color = SpaceSaleColors.TextPrimary)
        content()
    }
}

@Composable
fun spaceSaleTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = SpaceSaleColors.TextPrimary,
    cursorColor = SpaceSaleColors.Cyan,
    focusedBorderColor = SpaceSaleColors.Cyan,
    unfocusedBorderColor = SpaceSaleColors.ControlOutline,
    errorBorderColor = SpaceSaleColors.Error,
    focusedLabelColor = SpaceSaleColors.Cyan,
    unfocusedLabelColor = SpaceSaleColors.TextSecondary,
    placeholderColor = SpaceSaleColors.TextMuted,
    leadingIconColor = SpaceSaleColors.TextSecondary,
    trailingIconColor = SpaceSaleColors.TextSecondary
)
