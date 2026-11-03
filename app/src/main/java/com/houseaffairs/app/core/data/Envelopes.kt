package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Where moved money comes from or goes to: a category envelope, the gullak, or a dream. */
sealed interface Pocket {
    val key: String

    data class Category(val name: String) : Pocket { override val key get() = "cat:$name" }
    data object Pot : Pocket { override val key get() = "pot" }
    data class Dream(val id: String) : Pocket { override val key get() = "dream:$id" }
    data class Investment(val id: String) : Pocket { override val key get() = "inv:$id" }

    /**
     * Money that appears without leaving another pocket: interest, gains, or the value an
     * investment already had when it was added. A negative amount from here is a loss.
     */
    data object Growth : Pocket { override val key get() = "growth" }

    companion object {
        fun parse(key: String): Pocket? = when {
            key == "pot" -> Pot
            key == "growth" -> Growth
            key.startsWith("cat:") -> Category(key.removePrefix("cat:"))
            key.startsWith("dream:") -> Dream(key.removePrefix("dream:"))
            key.startsWith("inv:") -> Investment(key.removePrefix("inv:"))
            else -> null
        }
    }
}

data class Transfer(val id: String, val from: String, val to: String, val amountMinor: Long, val date: LocalDate, val note: String? = null)

data class Envelope(
    val category: String,
    val limitMinor: Long,
    val spentMinor: Long,
    val plannedMinor: Long,
    /** Moved in or out this month (from the gullak or another envelope). */
    val movedInMinor: Long,
    val movedOutMinor: Long,
    /** Credits (gift cards, points) earmarked for this category. */
    val creditsMinor: Long = 0,
) {
    val availableMinor: Long get() = limitMinor - spentMinor - plannedMinor + movedInMinor - movedOutMinor
}

data class EnvelopeState(
    val envelopes: List<Envelope>,
    /** The main gullak: every past month's unspent budget, plus and minus what's been moved. */
    val potMinor: Long,
    /** Unspent budget this year: "money you didn't spend". */
    val savedThisYearMinor: Long,
    /** Goal gullaks (dreams) and what's in each. */
    val dreamFunding: Map<String, Long>,
)

/**
 * Envelopes are this month's spending limit per category and start fresh each month. When a
 * month ends, everything not spent (in envelopes or never assigned) drops into the main gullak.
 * Money moved from the gullak into an envelope and not spent goes back with it. Goal gullaks
 * (dreams) fill only by moves. Pure, so it's tested.
 */
fun envelopes(
    budget: Budget,
    startedOn: LocalDate,
    txns: List<Txn>,
    transfers: List<Transfer>,
    credits: List<Credit>,
    today: LocalDate,
): EnvelopeState {
    val current = cycleFor(today, budget.cycleStartDay)
    val moved = transfers.filter { !it.date.isAfter(today) }
    fun isCategory(key: String) = key.startsWith("cat:")
    var pot = 0L
    var savedThisYear = 0L

    var cycle = cycleFor(startedOn, budget.cycleStartDay)
    while (cycle.start.isBefore(current.start)) {
        val spent = txns.filter { !it.planned && it.date in cycle }.sumOf { it.amountMinor }
        // Moves into envelopes that month (from the gullak or a goal) were budget too; moves out weren't.
        val inMonth = moved.filter { it.date in cycle }
        val topUps = inMonth.filter { isCategory(it.to) && !isCategory(it.from) }.sumOf { it.amountMinor }
        val takenOut = inMonth.filter { isCategory(it.from) && !isCategory(it.to) }.sumOf { it.amountMinor }
        val left = budget.limitMinor - spent + topUps - takenOut
        pot += left
        if (cycle.start.year == today.year) savedThisYear += left
        cycle = cycleFor(cycle.endExclusive, budget.cycleStartDay)
    }

    fun into(key: String, within: Cycle? = null) = moved.filter { it.to == key && (within == null || it.date in within) }.sumOf { it.amountMinor }
    fun outOf(key: String, within: Cycle? = null) = moved.filter { it.from == key && (within == null || it.date in within) }.sumOf { it.amountMinor }

    val thisCycle = txns.filter { it.date in current || (it.planned && !it.date.isBefore(today)) }
    val active = credits.filter { !it.used && (it.expiry == null || !it.expiry.isBefore(today)) }
    val list = budget.categories.map { (name, limit) ->
        val key = Pocket.Category(name).key
        Envelope(
            category = name,
            limitMinor = limit,
            spentMinor = thisCycle.filter { !it.planned && it.category == name }.sumOf { it.amountMinor },
            plannedMinor = thisCycle.filter { it.planned && it.category == name }.sumOf { it.amountMinor },
            movedInMinor = into(key, current),
            movedOutMinor = outOf(key, current),
            creditsMinor = active.filter { it.earmark.equals(name, ignoreCase = true) }.sumOf { it.valueMinor },
        )
    }.sortedByDescending { it.availableMinor }

    val dreamFunding = moved.mapNotNull { (Pocket.parse(it.to) as? Pocket.Dream)?.id }.distinct()
        .associateWith { id -> into(Pocket.Dream(id).key) - outOf(Pocket.Dream(id).key) }

    return EnvelopeState(list, pot + into("pot") - outOf("pot"), savedThisYear, dreamFunding)
}

// ---------------------------------------------------------------------------------------------
// Credits and dreams

data class Credit(
    val id: String,
    val title: String,
    val kind: String,
    val valueMinor: Long,
    val expiry: LocalDate?,
    /** A category name or a dream title this credit is saved for; null = use for anything. */
    val earmark: String?,
    val used: Boolean = false,
)

data class Dream(
    val id: String,
    val title: String,
    val kind: String,
    val emoji: String,
    val targetMinor: Long,
    val targetDate: LocalDate?,
    val achieved: Boolean = false,
    /** What the family picked, if anything; otherwise it follows from [targetDate]. */
    val horizonChoice: String? = null,
) {
    fun horizon(today: LocalDate = LocalDate.now()): Horizon = horizonOf(horizonChoice, targetDate, today)
}

/** How far off a dream is. The vision board groups by it. */
enum class Horizon(val label: String, val detail: String) {
    SHORT("Short term", "within a year"),
    MEDIUM("Medium term", "1–3 years"),
    LONG("Long term", "3+ years"),
    SOMEDAY("Someday", "no date yet"),
}

val HORIZON_CHOICES = listOf(Horizon.SHORT, Horizon.MEDIUM, Horizon.LONG).map { it.label }

fun horizonOf(choice: String?, targetDate: LocalDate?, today: LocalDate): Horizon {
    Horizon.entries.firstOrNull { it.label == choice }?.let { return it }
    if (targetDate == null) return Horizon.SOMEDAY
    return when {
        !targetDate.isAfter(today.plusYears(1)) -> Horizon.SHORT
        !targetDate.isAfter(today.plusYears(3)) -> Horizon.MEDIUM
        else -> Horizon.LONG
    }
}

data class DreamProgress(
    val dream: Dream,
    val savedMinor: Long,
    val creditsMinor: Long,
    /** Current value of investments earmarked for this dream. */
    val investedMinor: Long = 0,
    /** Per month still needed to reach the target by its date; null without a date. */
    val monthlyNeededMinor: Long?,
    /** True when saved + credits + the gullak already covers the target. */
    val affordableNow: Boolean,
) {
    val totalMinor: Long get() = savedMinor + creditsMinor + investedMinor
    val progress: Float get() = if (dream.targetMinor <= 0) 0f else (totalMinor.toFloat() / dream.targetMinor).coerceIn(0f, 1f)
    val remainingMinor: Long get() = (dream.targetMinor - totalMinor).coerceAtLeast(0)
}

fun dreamProgress(
    dreams: List<Dream>,
    state: EnvelopeState?,
    credits: List<Credit>,
    today: LocalDate,
    investments: List<InvestmentValue> = emptyList(),
): List<DreamProgress> =
    dreams.map { dream ->
        val saved = state?.dreamFunding?.get(dream.id) ?: 0
        val invested = investments.filter { it.investment.forDream.equals(dream.title, ignoreCase = true) }.sumOf { it.currentMinor }
        val creditValue = credits
            .filter { !it.used && it.earmark.equals(dream.title, ignoreCase = true) && (it.expiry == null || !it.expiry.isBefore(today)) }
            .sumOf { it.valueMinor }
        val remaining = (dream.targetMinor - saved - creditValue - invested).coerceAtLeast(0)
        val months = dream.targetDate?.let { ceil(ChronoUnit.DAYS.between(today, it) / 30.0).toLong().coerceAtLeast(1) }
        DreamProgress(
            dream = dream,
            savedMinor = saved,
            creditsMinor = creditValue,
            investedMinor = invested,
            monthlyNeededMinor = months?.let { (remaining + it - 1) / it },
            affordableNow = remaining == 0L || (state != null && state.potMinor >= remaining),
        )
    }.sortedWith(compareBy<DreamProgress> { it.dream.achieved }.thenBy { it.dream.targetDate ?: LocalDate.MAX })

// ---------------------------------------------------------------------------------------------
// Investments: FDs, mutual funds, gold… fed from the pot or envelopes, valued from a ledger.

data class Investment(
    val id: String,
    val title: String,
    val kind: String,
    /** Annual rate in percent, for FDs / RDs; shows an estimate, never posts interest by itself. */
    val ratePercent: Double?,
    val maturity: LocalDate?,
    /** Dream this money is meant for, by title. */
    val forDream: String?,
)

data class InvestmentValue(
    val investment: Investment,
    /** Money put in from the pot, envelopes or other investments, minus money taken out. */
    val investedMinor: Long,
    /** Interest, gains and value edits (negative for losses). */
    val growthMinor: Long,
    val history: List<Transfer>,
) {
    val currentMinor: Long get() = investedMinor + growthMinor
    val returnPercent: Double? get() = if (investedMinor > 0) growthMinor * 100.0 / investedMinor else null

    /** Simple-interest estimate of the value at maturity, from today's value. */
    fun estimateAtMaturity(today: LocalDate): Long? {
        val rate = investment.ratePercent ?: return null
        val end = investment.maturity ?: return null
        val days = ChronoUnit.DAYS.between(today, end).coerceAtLeast(0)
        return currentMinor + (currentMinor * rate / 100.0 * days / 365.0).toLong()
    }
}

fun investmentValues(investments: List<Investment>, transfers: List<Transfer>, today: LocalDate): List<InvestmentValue> =
    investments.map { inv ->
        val key = Pocket.Investment(inv.id).key
        val mine = transfers.filter { (it.to == key || it.from == key) && !it.date.isAfter(today) }
        val growth = mine.filter { it.from == Pocket.Growth.key }.sumOf { it.amountMinor }
        val moneyIn = mine.filter { it.to == key && it.from != Pocket.Growth.key }.sumOf { it.amountMinor }
        val moneyOut = mine.filter { it.from == key }.sumOf { it.amountMinor }
        InvestmentValue(inv, moneyIn - moneyOut, growth, mine.sortedByDescending { it.date })
    }.sortedByDescending { it.currentMinor }

/** The ledger entry that sets an investment's value to [newValueMinor] (a gain or loss). */
fun revaluation(current: InvestmentValue, newValueMinor: Long): Long = newValueMinor - current.currentMinor
