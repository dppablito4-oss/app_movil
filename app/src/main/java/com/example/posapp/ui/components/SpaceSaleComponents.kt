package com.example.posapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
