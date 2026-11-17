package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = TqmSkyLight,
    onPrimary = Color(0xFF041C33),
    primaryContainer = Color(0xFF14355C),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = TqmEmeraldLight,
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF114229),
    onSecondaryContainer = Color(0xFFC6F7D8),
    tertiary = TqmAmberLight,
    onTertiary = Color(0xFF3A2200),
    tertiaryContainer = Color(0xFF4F3200),
    onTertiaryContainer = Color(0xFFFFE0A3),
    error = TqmCrimsonLight,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5F1414),
    onErrorContainer = Color(0xFFFEE2E2),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    outlineVariant = Color(0xFF1E2C45)
)

private val LightColorScheme = lightColorScheme(
    primary = TqmNavyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EBFF),
    onPrimaryContainer = Color(0xFF0A2747),
    secondary = TqmEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F4E1),
    onSecondaryContainer = Color(0xFF0C3820),
    tertiary = TqmAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDD5),
    onTertiaryContainer = Color(0xFF4D2B00),
    error = TqmCrimson,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun IbagueTqmTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
