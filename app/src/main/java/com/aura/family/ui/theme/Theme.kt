package com.aura.family.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = DeepNavy,
    secondary = DeepTeal,
    onSecondary = Color.White,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = DeepNavy,
    tertiary = SoftTeal,
    onTertiary = DeepNavy,
    background = SurfaceLight,
    onBackground = DeepNavy,
    surface = CardLight,
    onSurface = DeepNavy,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = MutedSlate,
    outline = OutlineLight,
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = SoftTeal,
    onPrimary = DeepNavy,
    primaryContainer = DeepTeal,
    onPrimaryContainer = Color.White,
    secondary = SoftTeal,
    onSecondary = DeepNavy,
    secondaryContainer = SlateInk,
    onSecondaryContainer = Color.White,
    background = SurfaceDark,
    onBackground = Color.White,
    surface = CardDark,
    onSurface = Color.White,
    surfaceVariant = SlateInk,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = DeepNavy,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuraTypography,
        content = content
    )
}
