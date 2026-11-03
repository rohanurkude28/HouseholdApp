package com.houseaffairs.app.core.data

enum class Fit { FITS, EATS_SAVINGS, OVER }

/** One planned or due payment, and whether the month can carry it after everything before it. */
data class UpcomingFit(
    val spend: UpcomingSpend,
    val fit: Fit,
    /** Budget left once this and everything earlier is paid. */
    val leftAfterMinor: Long,
    /** Its category envelope can't cover it: by how much. Null when fine or not a category spend. */
    val envelopeShortMinor: Long? = null,
)

/**
 * Walks this month's planned spends and bills in date order against what's left of the budget.
 * Each one fits, fits only by dipping into the savings goal, or goes over. Pure, so it's tested.
 */
fun upcomingFit(summary: BudgetSummary, envelopes: List<Envelope> = emptyList()): List<UpcomingFit> {
    var left = summary.limitMinor - summary.spentMinor
    return summary.upcoming.map { u ->
        left -= u.amountMinor
        val envelope = u.category?.let { c -> envelopes.firstOrNull { it.category == c } }
        UpcomingFit(
            spend = u,
            fit = when {
                left < 0 -> Fit.OVER
                left < summary.savingsGoalMinor -> Fit.EATS_SAVINGS
                else -> Fit.FITS
            },
            leftAfterMinor = left,
            // The envelope already counts its planned spends, so negative means this doesn't fit in it.
            envelopeShortMinor = envelope?.availableMinor?.takeIf { it < 0 }?.let { -it },
        )
    }
}

/** Where saved money sits right now. */
data class SavingsBreakdown(
    /** Free to use: unused budget from past months, minus what's been moved out. */
    val gullakMinor: Long,
    val dreams: List<Pair<Dream, Long>>,
    val creditsMinor: Long,
    /** This month's projected leftover, joining the gullak when the month ends. */
    val comingMinor: Long,
) {
    val totalMinor: Long get() = gullakMinor + dreams.sumOf { it.second }
}

fun savingsBreakdown(m: MoneyState): SavingsBreakdown = SavingsBreakdown(
    gullakMinor = m.envelopes.potMinor,
    // Every goal gullak still being saved for, even empty ones, so they can be filled from here.
    dreams = m.dreams.filter { !it.dream.achieved }.map { p -> p.dream to (m.envelopes.dreamFunding[p.dream.id] ?: 0) }.sortedByDescending { it.second },
    creditsMinor = m.credits.sumOf { it.valueMinor },
    comingMinor = m.summary.leftMinor.coerceAtLeast(0),
)

/** "Gullak", "Groceries", "Bali trip": a friendly name for a ledger key. */
fun pocketLabel(key: String, dreams: List<Dream>): String = when (val p = Pocket.parse(key)) {
    Pocket.Pot -> "Gullak"
    is Pocket.Category -> p.name
    is Pocket.Dream -> dreams.firstOrNull { it.id == p.id }?.let { "${it.emoji} ${it.title}" } ?: "A dream"
    is Pocket.Investment -> "Investment"
    Pocket.Growth -> "Interest"
    null -> key
}

/** Paid expenses matching a search and a category, newest first. */
fun findExpenses(txns: List<Txn>, query: String, category: String?, cycle: Cycle?): List<Txn> {
    val words = query.lowercase().split(" ").filter { it.isNotBlank() }
    return txns.filter { t ->
        !t.planned &&
            (category == null || t.category == category) &&
            (cycle == null || t.date in cycle) &&
            words.all { w -> listOfNotNull(t.title, t.category, t.subcategory, t.trip).any { it.lowercase().contains(w) } }
    }.sortedByDescending { it.date }
}
