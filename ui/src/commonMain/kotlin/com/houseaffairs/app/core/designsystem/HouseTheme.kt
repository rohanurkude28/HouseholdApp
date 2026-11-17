package com.houseaffairs.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * The app's colours, type, shapes and module accents, the same on both phones. Android passes its
 * own copy of the font (from its resources); each phone handles its status bar.
 */
@Composable
fun HouseTheme(dark: Boolean, typography: Typography = houseTypography(jakartaFamily()), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHouseColors provides if (dark) DarkHouseColors else LightHouseColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
