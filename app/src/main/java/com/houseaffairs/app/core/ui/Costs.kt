package com.houseaffairs.app.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.houseaffairs.app.core.util.HIDDEN_AMOUNT
import com.houseaffairs.app.core.util.formatCompact
import com.houseaffairs.app.core.util.formatMinor

/**
 * Whether the owner hid the family's costs from this person (Settings → Privacy). Set from the
 * session; snapshot state, so every screen showing an amount redraws when it changes. Trips,
 * dreams and lists stay visible, only the amounts become "•••".
 */
object CostPrivacy {
    var hidden by mutableStateOf(false)
}

/** An amount as shown outside Money: "•••" when costs are hidden from this person. */
fun shownMinor(amountMinor: Long, currencyCode: String): String = if (CostPrivacy.hidden) HIDDEN_AMOUNT else formatMinor(amountMinor, currencyCode)

/** [shownMinor], short ("₹12k"). */
fun shownCompact(amountMinor: Long, currencyCode: String): String = if (CostPrivacy.hidden) HIDDEN_AMOUNT else formatCompact(amountMinor, currencyCode)
