package com.example.posapp.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberpunkColors = darkColors(
    primary = Color(0xFF00E5FF), // neon cyan
    primaryVariant = Color(0xFF00B8CC),
    secondary = Color(0xFFFF00D6), // neon magenta
    background = Color(0xFF05060A), // near black
    surface = Color(0xFF0F0F16),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE6F7FF),
    onSurface = Color(0xFFE6F7FF)
)

@Composable
fun PablitoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = CyberpunkColors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
