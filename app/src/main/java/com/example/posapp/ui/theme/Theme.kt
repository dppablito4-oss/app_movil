package com.example.posapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.posapp.data.AppThemeMode

@Immutable
private data class SpaceSalePalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val controlOutline: Color,
    val violet: Color,
    val violetPressed: Color,
    val violetContent: Color,
    val violetContainer: Color,
    val cyan: Color,
    val cyanPressed: Color,
    val cyanContainer: Color,
    val onCyan: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccess: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color
)

private val SpaceSaleDarkPalette = SpaceSalePalette(
    background = Color(0xFF060912),
    surface = Color(0xFF0D1422),
    surfaceRaised = Color(0xFF141D2E),
    border = Color(0xFF26344A),
    controlOutline = Color(0xFF56667F),
    violet = Color(0xFF7C3AED),
    violetPressed = Color(0xFF6D28D9),
    violetContent = Color(0xFFA78BFA),
    violetContainer = Color(0xFF251A48),
    cyan = Color(0xFF22D3EE),
    cyanPressed = Color(0xFF06B6D4),
    cyanContainer = Color(0xFF0B2C3A),
    onCyan = Color(0xFF001014),
    textPrimary = Color(0xFFF7F9FC),
    textSecondary = Color(0xFFA9B4C7),
    textMuted = Color(0xFF768399),
    textDisabled = Color(0xFF667085),
    success = Color(0xFF34D399),
    successContainer = Color(0xFF0D3128),
    onSuccess = Color(0xFF05251B),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF33270A),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF3A171D)
)

private val SpaceSaleLightPalette = SpaceSalePalette(
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEDF2F7),
    border = Color(0xFFD5DCE7),
    controlOutline = Color(0xFF64748B),
    violet = Color(0xFF6D28D9),
    violetPressed = Color(0xFF5B21B6),
    violetContent = Color(0xFF6D28D9),
    violetContainer = Color(0xFFEDE9FE),
    cyan = Color(0xFF087F8C),
    cyanPressed = Color(0xFF0E6672),
    cyanContainer = Color(0xFFCFFAFE),
    onCyan = Color.White,
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    textDisabled = Color(0xFF94A3B8),
    success = Color(0xFF047857),
    successContainer = Color(0xFFD1FAE5),
    onSuccess = Color.White,
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7),
    error = Color(0xFFB91C1C),
    errorContainer = Color(0xFFFEE2E2)
)

private val LocalSpaceSalePalette = staticCompositionLocalOf { SpaceSaleDarkPalette }

/** Semantic colors. Their values follow the active System/Light/Dark preference. */
object SpaceSaleColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.surface
    val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.surfaceRaised
    val Border: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.border
    val ControlOutline: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.controlOutline
    val Violet: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.violet
    val VioletPressed: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.violetPressed
    val VioletContent: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.violetContent
    val VioletContainer: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.violetContainer
    val Cyan: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.cyan
    val CyanPressed: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.cyanPressed
    val CyanContainer: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.cyanContainer
    val OnCyan: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.onCyan
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.textMuted
    val TextDisabled: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.textDisabled
    val Success: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.success
    val SuccessContainer: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.successContainer
    val OnSuccess: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.onSuccess
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.warning
    val WarningContainer: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.warningContainer
    val Error: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.error
    val ErrorContainer: Color @Composable @ReadOnlyComposable get() = LocalSpaceSalePalette.current.errorContainer
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

private fun materialColors(palette: SpaceSalePalette, darkTheme: Boolean) = if (darkTheme) {
    darkColors(
        primary = palette.violetContent,
        primaryVariant = palette.violet,
        secondary = palette.cyan,
        secondaryVariant = palette.cyanPressed,
        background = palette.background,
        surface = palette.surface,
        error = palette.error,
        onPrimary = Color(0xFF160A2C),
        onSecondary = palette.onCyan,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary,
        onError = Color(0xFF260007)
    )
} else {
    lightColors(
        primary = palette.violet,
        primaryVariant = palette.violetPressed,
        secondary = palette.cyan,
        secondaryVariant = palette.cyanPressed,
        background = palette.background,
        surface = palette.surface,
        error = palette.error,
        onPrimary = Color.White,
        onSecondary = palette.onCyan,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary,
        onError = Color.White
    )
}

private val SpaceSaleTypography = Typography(
    defaultFontFamily = FontFamily.SansSerif,
    h4 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum"),
    h5 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    h6 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    subtitle1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    subtitle2 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    body1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    body2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    overline = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp)
)

private val SpaceSaleShapes = Shapes(
    small = RoundedCornerShape(SpaceSaleRadii.Small),
    medium = RoundedCornerShape(SpaceSaleRadii.Medium),
    large = RoundedCornerShape(SpaceSaleRadii.Large)
)

@Composable
fun SpaceSaleTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val palette = if (darkTheme) SpaceSaleDarkPalette else SpaceSaleLightPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalSpaceSalePalette provides palette) {
        MaterialTheme(
            colors = materialColors(palette, darkTheme),
            typography = SpaceSaleTypography,
            shapes = SpaceSaleShapes,
            content = content
        )
    }
}

/* Compatibility aliases keep the rest of the app stable while screens migrate gradually. */
object PablitoColors {
    val Background: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.Background
    val Surface: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.Surface
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.SurfaceRaised
    val Border: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.Border
    val Cyan: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.Cyan
    val CyanPressed: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.CyanPressed
    val CyanContainer: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.CyanContainer
    val OnCyan: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.OnCyan
    val Magenta: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.Violet
    val MagentaPressed: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.VioletPressed
    val MagentaContainer: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.VioletContainer
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.TextPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.TextSecondary
    val TextDisabled: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.TextDisabled
    val Success: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.Success
    val Warning: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.Warning
    val Error: Color @Composable @ReadOnlyComposable get() = SpaceSaleColors.Error
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
fun PablitoTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) = SpaceSaleTheme(themeMode, content)
