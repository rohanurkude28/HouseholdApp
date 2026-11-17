package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.core.time.plusDays
import com.houseaffairs.app.core.time.year
import com.houseaffairs.app.core.util.oneDecimal

/** "Today", "Tomorrow", "Yesterday", else "Mon 3 Mar" (with the year when it isn't this one). */
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.plusDays(-1) -> "Yesterday"
    else -> date.format(if (date.year == today.year) "EEE d MMM" else "d MMM yyyy")
}

/** "2 packs", "1.5 kg"; empty when no amount is known. */
fun qty(amount: Double?, unit: String): String = when {
    amount == null -> ""
    amount % 1.0 == 0.0 -> "${amount.toLong()} $unit"
    else -> "${oneDecimal(amount)} $unit"
}

/** A stock row's short detail: "200 g · use by Thu 5 Nov", "50 g · 20% left · 📍 spice box", "1 L · expired". */
fun stockDetail(item: PantryStock, flag: Flag?, today: LocalDate): String = listOfNotNull(
    qty(item.amount, item.unit).takeIf { it.isNotEmpty() },
    when (flag) {
        Flag.EXPIRED -> "expired"
        Flag.EXPIRING -> item.expiry?.let { "use by ${dayLabel(it, today)}" }
        Flag.LOW -> item.initialAmount?.takeIf { it > 0 }?.let { "${(((item.amount ?: it) / it) * 100).toInt()}% left" }
        // Not urgent, but the date still matters (medicines especially).
        null -> item.expiry?.let { "use by ${dayLabel(it, today)}" }
    },
    item.keptAt?.let { "📍 $it" },
).joinToString(" · ").ifEmpty { "In stock" }
