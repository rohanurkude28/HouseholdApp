package com.houseaffairs.ios

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same palette as Android's core/designsystem/Color.kt: violet "dusk" primary, coral accent.
private val Light = lightColorScheme(
    primary = Color(0xFF5B3FD9), onPrimary = Color.White, primaryContainer = Color(0xFFE7E0FF), onPrimaryContainer = Color(0xFF1E0B66),
    secondary = Color(0xFFFF7A59), onSecondary = Color.White, secondaryContainer = Color(0xFFFFE3D9), onSecondaryContainer = Color(0xFF3A0E00),
    tertiary = Color(0xFF14B88A), onTertiary = Color.White,
    background = Color(0xFFF7F5FB), onBackground = Color(0xFF1B1726), surface = Color(0xFFF7F5FB), onSurface = Color(0xFF1B1726),
    surfaceVariant = Color(0xFFECE8F5), onSurfaceVariant = Color(0xFF625C72),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFF1EEF8),
    surfaceContainerHigh = Color(0xFFEBE7F4), surfaceContainerHighest = Color(0xFFE3DEEE),
    outline = Color(0xFFCBC4D9), outlineVariant = Color(0xFFE6E1EE), error = Color(0xFFD93A4A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC7B8FF), onPrimary = Color(0xFF2A1580), primaryContainer = Color(0xFF4329B8), onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFFFF9C80), onSecondary = Color(0xFF4A1600), secondaryContainer = Color(0xFF6B2A14), onSecondaryContainer = Color(0xFFFFE3D9),
    tertiary = Color(0xFF4FD9AE), onTertiary = Color(0xFF00382A),
    background = Color(0xFF0F0D17), onBackground = Color(0xFFEAE5F5), surface = Color(0xFF0F0D17), onSurface = Color(0xFFEAE5F5),
    surfaceVariant = Color(0xFF2A2539), onSurfaceVariant = Color(0xFFB4ACC6),
    surfaceContainerLowest = Color(0xFF0B0912), surfaceContainerLow = Color(0xFF181421), surfaceContainer = Color(0xFF1D1928),
    surfaceContainerHigh = Color(0xFF252032), surfaceContainerHighest = Color(0xFF2E283D),
)

@Composable
fun HouseAffairsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
