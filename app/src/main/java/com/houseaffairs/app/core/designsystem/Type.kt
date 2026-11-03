package com.houseaffairs.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.houseaffairs.app.R

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Plus Jakarta Sans (SIL Open Font License), bundled as one variable font. */
val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private val base = Typography()

private fun TextStyle.jakarta(weight: FontWeight, tracking: Float = 0f) =
    copy(fontFamily = Jakarta, fontWeight = weight, letterSpacing = tracking.em)

internal val AppTypography = Typography(
    displayLarge = base.displayLarge.jakarta(FontWeight.ExtraBold, -0.02f),
    displayMedium = base.displayMedium.jakarta(FontWeight.ExtraBold, -0.02f),
    displaySmall = base.displaySmall.jakarta(FontWeight.ExtraBold, -0.02f),
    headlineLarge = base.headlineLarge.jakarta(FontWeight.Bold, -0.015f),
    headlineMedium = base.headlineMedium.jakarta(FontWeight.Bold, -0.015f),
    headlineSmall = base.headlineSmall.jakarta(FontWeight.Bold, -0.01f),
    titleLarge = base.titleLarge.jakarta(FontWeight.Bold, -0.01f),
    titleMedium = base.titleMedium.jakarta(FontWeight.SemiBold),
    titleSmall = base.titleSmall.jakarta(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.jakarta(FontWeight.Normal),
    bodyMedium = base.bodyMedium.jakarta(FontWeight.Normal),
    bodySmall = base.bodySmall.jakarta(FontWeight.Medium),
    labelLarge = base.labelLarge.jakarta(FontWeight.SemiBold),
    labelMedium = base.labelMedium.jakarta(FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)
