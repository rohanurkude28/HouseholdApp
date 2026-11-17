package com.houseaffairs.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * How this phone draws pictures in shared screens: web images (a place's photo, a cover) and the
 * family's own photos (kept on each phone). Android provides one with Coil; a phone without one
 * shows the plain tile instead.
 */
interface HouseImages {
    fun hasPhoto(id: String): Boolean = false
    @Composable fun Web(url: String, description: String?, modifier: Modifier) {}
    @Composable fun Photo(id: String, modifier: Modifier) {}
    /** Whether web images can be drawn at all, so a tile can fall back to its number or emoji. */
    val web: Boolean get() = false
}

object NoImages : HouseImages

val LocalHouseImages = staticCompositionLocalOf<HouseImages> { NoImages }

/**
 * Whether the owner hid the family's costs from this person (Settings → Privacy). Set from the
 * session; snapshot state, so every screen showing an amount redraws when it changes. Trips,
 * dreams and lists stay visible, only the amounts become "•••".
 */
object CostPrivacy {
    var hidden by mutableStateOf(false)
}

/** An amount as shown outside Money: "•••" when costs are hidden from this person. */
fun shownMinor(amountMinor: Long, currencyCode: String): String =
    if (CostPrivacy.hidden) "•••" else com.houseaffairs.app.core.util.moneyText(amountMinor, currencyCode)

/** [shownMinor], short ("₹12k"). */
fun shownCompact(amountMinor: Long, currencyCode: String): String =
    if (CostPrivacy.hidden) "•••" else com.houseaffairs.app.core.util.compactMoneyText(amountMinor, currencyCode)
