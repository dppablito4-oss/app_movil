package com.example.posapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Semantic colors for the SpaceSale OLED theme. */
object SpaceSaleColors {
    val Background = Color(0xFF060912)
    val Surface = Color(0xFF0D1422)
    val SurfaceRaised = Color(0xFF141D2E)
    val Border = Color(0xFF26344A)
    val ControlOutline = Color(0xFF56667F)

    val Violet = Color(0xFF7C3AED)
    val VioletPressed = Color(0xFF6D28D9)
    val VioletContent = Color(0xFFA78BFA)
    val VioletContainer = Color(0xFF251A48)

    val Cyan = Color(0xFF22D3EE)
    val CyanPressed = Color(0xFF06B6D4)
    val CyanContainer = Color(0xFF0B2C3A)

    val TextPrimary = Color(0xFFF7F9FC)
    val TextSecondary = Color(0xFFA9B4C7)
    val TextMuted = Color(0xFF768399)
    val TextDisabled = Color(0xFF667085)

    val Success = Color(0xFF34D399)
    val SuccessContainer = Color(0xFF0D3128)
    val OnSuccess = Color(0xFF05251B)
    val Warning = Color(0xFFFBBF24)
    val WarningContainer = Color(0xFF33270A)
    val Error = Color(0xFFF87171)
    val ErrorContainer = Color(0xFF3A171D)
}

object SpaceSaleSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val Xxxl = 40.dp
}

object SpaceSaleRadii {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
}

object SpaceSaleSizes {
    val TouchTarget = 48.dp
    val ButtonHeight = 56.dp
    val IconSmall = 20.dp
    val IconMedium = 24.dp
    val IconLarge = 32.dp
    val Logo = 40.dp
}

private val SpaceSaleDarkColors = darkColors(
    primary = SpaceSaleColors.VioletContent,
    primaryVariant = SpaceSaleColors.Violet,
    secondary = SpaceSaleColors.Cyan,
    secondaryVariant = SpaceSaleColors.CyanPressed,
    background = SpaceSaleColors.Background,
    surface = SpaceSaleColors.Surface,
    error = SpaceSaleColors.Error,
    onPrimary = Color(0xFF160A2C),
    onSecondary = Color(0xFF001014),
    onBackground = SpaceSaleColors.TextPrimary,
    onSurface = SpaceSaleColors.TextPrimary,
    onError = Color(0xFF260007)
)

private val SpaceSaleTypography = Typography(
    defaultFontFamily = FontFamily.SansSerif,
    h4 = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum"
    ),
    h5 = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    h6 = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    subtitle1 = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    subtitle2 = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    body1 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    body2 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    button = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    caption = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    overline = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    )
)

private val SpaceSaleShapes = Shapes(
    small = RoundedCornerShape(SpaceSaleRadii.Small),
    medium = RoundedCornerShape(SpaceSaleRadii.Medium),
    large = RoundedCornerShape(SpaceSaleRadii.Large)
)

@Composable
fun SpaceSaleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = SpaceSaleDarkColors,
        typography = SpaceSaleTypography,
        shapes = SpaceSaleShapes,
        content = content
    )
}

/* Compatibility aliases keep the rest of the app stable while screens migrate gradually. */
object PablitoColors {
    val Background = SpaceSaleColors.Background
    val Surface = SpaceSaleColors.Surface
    val SurfaceElevated = SpaceSaleColors.SurfaceRaised
    val Border = SpaceSaleColors.Border
    val Cyan = SpaceSaleColors.Cyan
    val CyanPressed = SpaceSaleColors.CyanPressed
    val CyanContainer = SpaceSaleColors.CyanContainer
    val Magenta = SpaceSaleColors.Violet
    val MagentaPressed = SpaceSaleColors.VioletPressed
    val MagentaContainer = SpaceSaleColors.VioletContainer
    val TextPrimary = SpaceSaleColors.TextPrimary
    val TextSecondary = SpaceSaleColors.TextSecondary
    val TextDisabled = SpaceSaleColors.TextDisabled
    val Success = SpaceSaleColors.Success
    val Warning = SpaceSaleColors.Warning
    val Error = SpaceSaleColors.Error
}

object PablitoSpacing {
    val Xs = SpaceSaleSpacing.Xs
    val Sm = SpaceSaleSpacing.Sm
    val Md = SpaceSaleSpacing.Md
    val Lg = SpaceSaleSpacing.Lg
    val Xl = SpaceSaleSpacing.Xl
    val Xxl = SpaceSaleSpacing.Xxl
    val Xxxl = SpaceSaleSpacing.Xxxl
}

object PablitoRadii {
    val Small = SpaceSaleRadii.Small
    val Medium = SpaceSaleRadii.Medium
    val Large = SpaceSaleRadii.Large
}

object PablitoSizes {
    val TouchTarget = SpaceSaleSizes.TouchTarget
    val IconSmall = SpaceSaleSizes.IconSmall
    val IconMedium = SpaceSaleSizes.IconMedium
    val IconLarge = SpaceSaleSizes.IconLarge
    val Logo = SpaceSaleSizes.Logo
}

@Composable
fun PablitoTheme(content: @Composable () -> Unit) = SpaceSaleTheme(content)
