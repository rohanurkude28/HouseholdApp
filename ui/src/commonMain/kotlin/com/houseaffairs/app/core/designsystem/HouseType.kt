package com.houseaffairs.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.houseaffairs.ui.res.Res
import com.houseaffairs.ui.res.jakarta_400
import com.houseaffairs.ui.res.jakarta_500
import com.houseaffairs.ui.res.jakarta_600
import com.houseaffairs.ui.res.jakarta_700
import com.houseaffairs.ui.res.jakarta_800
import org.jetbrains.compose.resources.Font

/**
 * Plus Jakarta Sans (SIL Open Font License) in the five weights the app uses. Fixed-weight copies
 * of Android's variable font, since shared resources can't pick a weight from one file.
 */
@Composable
fun jakartaFamily(): FontFamily {
    val fonts = listOf(
        Font(Res.font.jakarta_400, FontWeight.Normal),
        Font(Res.font.jakarta_500, FontWeight.Medium),
        Font(Res.font.jakarta_600, FontWeight.SemiBold),
        Font(Res.font.jakarta_700, FontWeight.Bold),
        Font(Res.font.jakarta_800, FontWeight.ExtraBold),
    )
    return remember(fonts) { FontFamily(fonts) }
}

/** The app's type: tight, bold headings and easy body text. The same sizes on both phones. */
fun houseTypography(family: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.j(weight: FontWeight, tracking: Float = 0f) = copy(fontFamily = family, fontWeight = weight, letterSpacing = tracking.em)
    return Typography(
        displayLarge = base.displayLarge.j(FontWeight.ExtraBold, -0.02f),
        displayMedium = base.displayMedium.j(FontWeight.ExtraBold, -0.02f),
        displaySmall = base.displaySmall.j(FontWeight.ExtraBold, -0.02f),
        headlineLarge = base.headlineLarge.j(FontWeight.Bold, -0.015f),
        headlineMedium = base.headlineMedium.j(FontWeight.Bold, -0.015f),
        headlineSmall = base.headlineSmall.j(FontWeight.Bold, -0.01f),
        titleLarge = base.titleLarge.j(FontWeight.Bold, -0.01f),
        titleMedium = base.titleMedium.j(FontWeight.SemiBold),
        titleSmall = base.titleSmall.j(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.j(FontWeight.Normal),
        bodyMedium = base.bodyMedium.j(FontWeight.Normal),
        bodySmall = base.bodySmall.j(FontWeight.Medium),
        labelLarge = base.labelLarge.j(FontWeight.SemiBold),
        labelMedium = base.labelMedium.j(FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
    )
}
