package com.houseaffairs.app.core.data

import java.time.LocalDate

/**
 * Quick options shown right after marking something done ("Got paneer · how much? use by?").
 * Each group is one tap; ignoring the sheet keeps the defaults.
 */
data class FollowUpChoice(val label: String, val apply: () -> Unit)

data class FollowUpGroup(val label: String, val choices: List<FollowUpChoice>)

data class FollowUp(val title: String, val note: String? = null, val groups: List<FollowUpGroup>)

/** Quantity chips that fit the unit: grams in grams and kilos, litres in ml and L, pieces in counts. Pure, so it's tested. */
fun quantityChoices(unit: String): List<Pair<Double, String>> = when (unit.lowercase()) {
    "g", "gm" -> listOf(250.0 to "g", 500.0 to "g", 1000.0 to "g", 2000.0 to "g")
    "kg" -> listOf(1.0 to "kg", 2.0 to "kg", 5.0 to "kg")
    "ml" -> listOf(500.0 to "ml", 1000.0 to "ml", 2000.0 to "ml")
    "l" -> listOf(0.5 to "L", 1.0 to "L", 2.0 to "L")
    "pack", "packs" -> listOf(1.0 to "pack", 2.0 to "pack", 3.0 to "pack")
    else -> listOf(1.0 to unit, 6.0 to unit, 12.0 to unit)
}

/** "500 g", "1 kg" (1000 g reads as 1 kg), "1 L". */
fun quantityLabel(amount: Double, unit: String): String = when {
    unit.equals("g", true) && amount >= 1000 -> "${trim(amount / 1000)} kg"
    unit.equals("ml", true) && amount >= 1000 -> "${trim(amount / 1000)} L"
    unit.equals("pcs", true) && amount == 1.0 -> "1 pc"
    else -> "${trim(amount)} $unit"
}

private fun trim(n: Double) = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

/** "Use by" chips, from today. */
fun expiryChoices(today: LocalDate): List<Pair<String, LocalDate>> = listOf(
    "3 days" to today.plusDays(3), "1 week" to today.plusWeeks(1), "2 weeks" to today.plusWeeks(2),
    "1 month" to today.plusMonths(1), "6 months" to today.plusMonths(6),
)

/** After "bought": how much came home and when to use it by. Each tap writes just that part. */
fun boughtFollowUp(title: String, unit: String, today: LocalDate, refine: (amount: Double?, unit: String, expiry: LocalDate?) -> Unit) = FollowUp(
    title = "Got $title",
    note = "Now in the pantry. Add details if you like.",
    groups = listOf(
        // Grams for paneer, litres for milk, pieces for eggs: not "1 pc" of everything.
        FollowUpGroup("How much?", quantityChoices(if (unit == "pcs") naturalUnit(title) else unit).map { (a, u) -> FollowUpChoice(quantityLabel(a, u)) { refine(a, u, null) } }),
        FollowUpGroup("Use by", expiryChoices(today).map { (label, date) -> FollowUpChoice(label) { refine(null, unit, date) } }),
    ),
)

/** After "watched", "read" or "heard": a thumbs up or down, so picks get better. */
fun ratedFollowUp(title: String, rate: (liked: Boolean) -> Unit) = FollowUp(
    title = "How was $title?",
    groups = listOf(FollowUpGroup("Your take", listOf(FollowUpChoice("👍 Liked it") { rate(true) }, FollowUpChoice("👎 Not great") { rate(false) }))),
)

/** After "finished": it went on the shopping list; one tap takes it off. */
fun finishedFollowUp(title: String, dontBuy: () -> Unit) = FollowUp(
    title = "$title · finished",
    note = "Added to the shopping list.",
    groups = listOf(FollowUpGroup("Buy again?", listOf(FollowUpChoice("Yes, keep on the list") {}, FollowUpChoice("Don't buy again", dontBuy)))),
)

/** After "threw it away": counted as waste and put on the shopping list; one tap takes it off. */
fun thrownFollowUp(title: String, dontBuy: () -> Unit) = FollowUp(
    title = "$title · thrown away",
    note = "Counted as waste this month, and added to the shopping list.",
    groups = listOf(FollowUpGroup("Buy again?", listOf(FollowUpChoice("Yes, keep on the list") {}, FollowUpChoice("Don't buy again", dontBuy)))),
)
