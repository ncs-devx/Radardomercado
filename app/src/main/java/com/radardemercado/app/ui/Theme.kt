package com.radardemercado.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF08111F)
val Surface = Color(0xFF101C2D)
val SurfaceRaised = Color(0xFF172538)
val Green = Color(0xFF16D9A0)
val Red = Color(0xFFFF5C6C)
val Blue = Color(0xFF4DA3FF)
val Muted = Color(0xFF93A4B8)

private val RadarColors = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF002117),
    secondary = Blue,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceRaised,
    onBackground = Color(0xFFF2F6FA),
    onSurface = Color(0xFFF2F6FA),
    onSurfaceVariant = Muted,
    error = Red
)

@Composable
fun RadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RadarColors, typography = Typography(), content = content)
}
