package com.camarodiag.gen5.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0B0E14)
val SurfaceDark = Color(0xFF131924)
val SurfaceElevated = Color(0xFF1B2332)
val CamaroAmber = Color(0xFFF59E0B)
val CamaroBlue = Color(0xFF38BDF8)
val OkGreen = Color(0xFF10B981)
val WarnRed = Color(0xFFEF4444)
val TextPrimary = Color(0xFFF1F5F9)
val TextMuted = Color(0xFF94A3B8)

private val DarkColors = darkColorScheme(
    primary = CamaroAmber,
    secondary = CamaroBlue,
    background = BgDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceElevated,
    onPrimary = Color(0xFF0B0E14),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    error = WarnRed
)

@Composable
fun CamaroDiagTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
