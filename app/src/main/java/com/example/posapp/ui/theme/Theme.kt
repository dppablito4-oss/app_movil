package com.example.posapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PablitoColors {
    val Background = Color(0xFF050608)
    val Surface = Color(0xFF0D1117)
    val SurfaceElevated = Color(0xFF141A22)
    val Border = Color(0xFF26313D)

    val Cyan = Color(0xFF22D3EE)
    val CyanPressed = Color(0xFF06B6D4)
    val CyanContainer = Color(0xFF0B2C33)
    val Magenta = Color(0xFFF43F8C)
    val MagentaPressed = Color(0xFFDB2777)
    val MagentaContainer = Color(0xFF3A1226)

    val TextPrimary = Color(0xFFF4F7FA)
    val TextSecondary = Color(0xFFAAB6C3)
    val TextDisabled = Color(0xFF6F7B87)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFFB7185)
}

object PablitoSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
}

object PablitoRadii {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
}

object PablitoSizes {
    val TouchTarget = 48.dp
    val IconSmall = 20.dp
    val IconMedium = 24.dp
    val IconLarge = 32.dp
    val Logo = 40.dp
}

private val PablitoDarkColors = darkColors(
    primary = PablitoColors.Cyan,
    primaryVariant = PablitoColors.CyanPressed,
    secondary = PablitoColors.Magenta,
    secondaryVariant = PablitoColors.MagentaPressed,
    background = PablitoColors.Background,
    surface = PablitoColors.Surface,
    error = PablitoColors.Error,
    onPrimary = Color(0xFF001014),
    onSecondary = Color.White,
    onBackground = PablitoColors.TextPrimary,
    onSurface = PablitoColors.TextPrimary,
    onError = Color(0xFF240008)
)

private val PablitoTypography = Typography(
    h4 = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
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
        letterSpacing = 0.6.sp
    )
)

private val PablitoShapes = Shapes(
    small = RoundedCornerShape(PablitoRadii.Small),
    medium = RoundedCornerShape(PablitoRadii.Medium),
    large = RoundedCornerShape(PablitoRadii.Large)
)

@Composable
fun PablitoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = PablitoDarkColors,
        typography = PablitoTypography,
        shapes = PablitoShapes,
        content = content
    )
}
