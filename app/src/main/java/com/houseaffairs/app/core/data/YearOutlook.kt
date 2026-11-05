package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * The year at a glance: spending per budget month, what's been saved, where the year is heading,
 * and the big things coming up. Pure, so the arithmetic is tested.
 */
data class MonthSpend(val label: String, val spentMinor: Long, val budgetMinor: Long, val current: Boolean)

data class BigItem(val title: String, val date: LocalDate, val amountMinor: Long?, val kind: String)

data class YearOutlook(
    val months: List<MonthSpend>,
    /** Unspent budget from this year's finished months. */
    val savedSoFarMinor: Long,
    /** Saved so far plus the usual leftover for each month still to come this year. */
    val projectedMinor: Long,
    val upcoming: List<BigItem>,
    /** One plain-language line about the year so far, if there's something worth saying. */
    val insight: String?,
)

private val MONTH = DateTimeFormatter.ofPattern("MMM")

fun yearOutlook(
    budget: Budget,
    startedOn: LocalDate,
    txns: List<Txn>,
    dreams: List<Dream>,
    trips: List<Trip>,
    today: LocalDate,
    money: (Long) -> String = { it.toString() },
): YearOutlook {
    val current = cycleFor(today, budget.cycleStartDay)
    val firstOfYear = cycleFor(LocalDate.of(today.year, 1, 1), budget.cycleStartDay).let { if (it.start.year < today.year) cycleFor(it.endExclusive, budget.cycleStartDay) else it }
    var cycle = maxOf(firstOfYear.start, cycleFor(startedOn, budget.cycleStartDay).start).let { cycleFor(it, budget.cycleStartDay) }
    val paid = txns.filter { !it.planned }
    val months = mutableListOf<MonthSpend>()
    while (!cycle.start.isAfter(current.start)) {
        months += MonthSpend(cycle.start.format(MONTH), paid.filter { it.date in cycle }.sumOf { it.amountMinor }, budget.limitFor(cycle.start), cycle == current)
        cycle = cycleFor(cycle.endExclusive, budget.cycleStartDay)
    }
    val finished = months.filter { !it.current }
    val savedSoFar = finished.sumOf { it.budgetMinor - it.spentMinor }
    // Months still to come this year, the current one included.
    var left = 0
    var c = current
    while (c.start.year == today.year) {
        left++
        c = cycleFor(c.endExclusive, budget.cycleStartDay)
    }
    val usualLeftover = if (finished.isNotEmpty()) {
        savedSoFar / finished.size
    } else {
        // No finished month yet: this month's leftover at today's pace.
        val spent = months.lastOrNull()?.spentMinor ?: 0
        val day = current.dayOf(today).coerceAtLeast(1)
        budget.limitFor(current.start) - spent * current.days / day
    }
    val horizon = today.plusYears(1)
    val upcoming = (
        txns.filter { it.planned && !it.date.isBefore(today) && it.date.isBefore(horizon) }.map { BigItem(it.title, it.date, it.amountMinor, "Planned") } +
            dreams.filter { !it.achieved && it.targetDate != null && !it.targetDate.isBefore(today) && it.targetDate.isBefore(horizon) }
                .map { BigItem(it.title, it.targetDate!!, it.targetMinor, "Dream") } +
            trips.filter { !it.start.isBefore(today) && it.start.isBefore(horizon) }.map { BigItem(it.title, it.start, it.budgetMinor, "Trip") }
        ).sortedBy { it.date }.take(6)
    val insight = when {
        finished.size >= 2 && months.last().current -> {
            val usual = finished.map { it.spentMinor }.average()
            val day = current.dayOf(today).coerceAtLeast(1)
            val pace = months.last().spentMinor.toDouble() * current.days / day
            val change = ((pace - usual) / usual * 100).toInt()
            when {
                usual <= 0 -> null
                abs(change) < 10 -> "This month is on track with your usual ${money(usual.toLong())}."
                change > 0 -> "This month is heading $change% above your usual ${money(usual.toLong())}."
                else -> "This month is heading ${-change}% below your usual ${money(usual.toLong())}. Nice."
            }
        }
        savedSoFar > 0 -> "You've kept ${money(savedSoFar)} of your budget unspent this year."
        else -> null
    }
    return YearOutlook(months, savedSoFar, savedSoFar + usualLeftover * left, upcoming, insight)
}
