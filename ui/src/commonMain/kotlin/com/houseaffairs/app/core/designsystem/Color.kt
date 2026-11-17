package com.houseaffairs.app.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand: a violet "dusk" primary with a warm coral accent. Each module gets its own accent so
// the app reads at a glance: money = mint, stock = amber, plans = sky, vault = lilac.

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3FD9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1E0B66),
    secondary = Color(0xFFFF7A59),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D9),
    onSecondaryContainer = Color(0xFF3A0E00),
    tertiary = Color(0xFF14B88A),
    onTertiary = Color.White,
    background = Color(0xFFF7F5FB),
    onBackground = Color(0xFF1B1726),
    surface = Color(0xFFF7F5FB),
    onSurface = Color(0xFF1B1726),
    surfaceVariant = Color(0xFFECE8F5),
    onSurfaceVariant = Color(0xFF625C72),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EEF8),
    surfaceContainerHigh = Color(0xFFEBE7F4),
    surfaceContainerHighest = Color(0xFFE3DEEE),
    outline = Color(0xFFCBC4D9),
    outlineVariant = Color(0xFFE6E1EE),
    error = Color(0xFFD93A4A),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFFC7B8FF),
    onPrimary = Color(0xFF2A1580),
    primaryContainer = Color(0xFF4329B8),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFFFF9C80),
    onSecondary = Color(0xFF4A1600),
    secondaryContainer = Color(0xFF6B2A14),
    onSecondaryContainer = Color(0xFFFFE3D9),
    tertiary = Color(0xFF4FD9AE),
    onTertiary = Color(0xFF00382A),
    background = Color(0xFF0F0D17),
    onBackground = Color(0xFFEAE5F5),
    surface = Color(0xFF0F0D17),
    onSurface = Color(0xFFEAE5F5),
    surfaceVariant = Color(0xFF2A2539),
    onSurfaceVariant = Color(0xFFB4ACC6),
    surfaceContainerLowest = Color(0xFF0B0912),
    surfaceContainerLow = Color(0xFF181421),
    surfaceContainer = Color(0xFF1D1928),
    surfaceContainerHigh = Color(0xFF252032),
    surfaceContainerHighest = Color(0xFF2E283D),
    outline = Color(0xFF4A4359),
    outlineVariant = Color(0xFF2E283D),
    error = Color(0xFFFF8A94),
)

@Immutable
data class Accent(val color: Color, val container: Color)

@Immutable
data class HouseColors(
    val money: Accent,
    val stock: Accent,
    val plans: Accent,
    val vault: Accent,
    val coral: Color,
)

internal val LightHouseColors = HouseColors(
    money = Accent(Color(0xFF0E9F76), Color(0xFFD9F5EC)),
    stock = Accent(Color(0xFFD98A0B), Color(0xFFFFF0D4)),
    plans = Accent(Color(0xFF2F78F0), Color(0xFFDDEAFF)),
    vault = Accent(Color(0xFF8E45F0), Color(0xFFEFE2FF)),
    coral = Color(0xFFFF7A59),
)

internal val DarkHouseColors = HouseColors(
    money = Accent(Color(0xFF4FD9AE), Color(0xFF123A30)),
    stock = Accent(Color(0xFFFFC15C), Color(0xFF3D2C0E)),
    plans = Accent(Color(0xFF7DB4FF), Color(0xFF16294A)),
    vault = Accent(Color(0xFFC79BFF), Color(0xFF2E1B4A)),
    coral = Color(0xFFFF9C80),
)

val LocalHouseColors = staticCompositionLocalOf { LightHouseColors }
