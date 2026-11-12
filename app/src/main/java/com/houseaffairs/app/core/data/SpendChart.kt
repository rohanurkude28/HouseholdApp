package com.houseaffairs.app.core.data

import java.time.LocalDate

/**
 * A budget month as a line: what's been spent by the end of each day so far, against the budget
 * ([limitMinor]) and an even pace towards it. Planned spends aren't spending yet.
 */
data class SpendPace(
    /** Spent by the end of day 1, 2, … up to today (or the whole month once it's over). */
    val cumulative: List<Long>,
    /** Days in the budget month. */
    val days: Int,
    val limitMinor: Long,
    /** The first day (or month) the target covers: a budget started in November paces from November. */
    val from: Int = 1,
) {
    val spentMinor: Long get() = cumulative.lastOrNull() ?: 0
    private val span: Int get() = (days - from + 1).coerceAtLeast(1)
    /** Where an even pace would be by day [day], in whole currency units. */
    fun paceAt(day: Int): Long = if (day < from) 0 else limitMinor * (day - from + 1) / span / 100 * 100
    /** Positive: spending faster than an even pace by this much. */
    val aheadOfPaceMinor: Long get() = (spentMinor - paceAt(cumulative.size)) / 100 * 100
    /** The first day spending went past the whole budget, or null. */
    val overFromDay: Int? get() = cumulative.indexOfFirst { it > limitMinor }.takeIf { it >= 0 }?.plus(1)
    /** At this pace, what the month ends at. */
    val projectedMinor: Long get() = if (cumulative.size < from) 0 else spentMinor * span / (cumulative.size - from + 1) / 100 * 100
}

fun spendPace(txns: List<Txn>, cycle: Cycle, limitMinor: Long, today: LocalDate): SpendPace {
    val shown = if (today in cycle) cycle.dayOf(today) else if (today.isBefore(cycle.start)) 0 else cycle.days
    val paid = txns.filter { !it.planned && it.date in cycle }
    val byDay = paid.groupBy { cycle.dayOf(it.date) }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
    var total = 0L
    val cumulative = (1..shown).map { day -> total += byDay[day] ?: 0; total }
    return SpendPace(cumulative, cycle.days, limitMinor)
}

/** The month's line in words, for under the chart. */
fun paceLine(p: SpendPace, money: (Long) -> String): String {
    val over = p.overFromDay
    return when {
        over != null -> "Over the budget by ${money(p.spentMinor - p.limitMinor)} since day $over"
        p.cumulative.size < p.days && p.aheadOfPaceMinor > 0 -> "${money(p.aheadOfPaceMinor)} ahead of an even pace · heading for ${money(p.projectedMinor)}"
        p.cumulative.size < p.days -> "${money(-p.aheadOfPaceMinor)} under an even pace · on track"
        else -> "${money(p.limitMinor - p.spentMinor)} under the budget"
    }
}

/** One category against its target for the period; [targetMinor] is null when it has no limit. */
data class CategoryBar(val name: String, val spentMinor: Long, val targetMinor: Long?) {
    val over: Boolean get() = targetMinor != null && spentMinor > targetMinor
}

/**
 * Every category spent on in [period], or with a target, against its target: the monthly limit
 * times [months] (a year so far has as many targets as months). Over-target ones first, then by
 * how much was spent.
 */
fun categoryBars(txns: List<Txn>, period: Cycle, limits: Map<String, Long>, months: Int = 1): List<CategoryBar> {
    val spent = txns.filter { !it.planned && it.date in period }.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
    return (spent.keys + limits.filterValues { it > 0 }.keys).distinct()
        .map { CategoryBar(it, spent[it] ?: 0, limits[it]?.takeIf { l -> l > 0 }?.times(months)) }
        .filter { it.spentMinor > 0 || it.targetMinor != null }
        .sortedWith(compareByDescending<CategoryBar> { it.over }.thenByDescending { it.spentMinor })
}

/** January 1st to the next January 1st, as a period the expense filters understand. */
fun yearPeriod(year: Int): Cycle = Cycle(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))

/**
 * The year as a line, month by month: spent by the end of each month so far against the year's
 * target, which is each month's budget added up (or, for one [category], its monthly limit for
 * each month). Months before the budget started target nothing. Future months aren't drawn.
 */
fun yearPace(txns: List<Txn>, year: Int, today: LocalDate, budget: Budget, startedOn: LocalDate, category: String? = null): SpendPace {
    val shown = when {
        year < today.year -> 12
        year > today.year -> 0
        else -> today.monthValue
    }
    val paid = txns.filter { !it.planned && it.date.year == year && (category == null || it.category == category) }
    val byMonth = paid.groupBy { it.date.monthValue }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
    var total = 0L
    val cumulative = (1..shown).map { month -> total += byMonth[month] ?: 0; total }
    val firstMonth = if (startedOn.year == year) startedOn.monthValue else if (startedOn.year > year) 13 else 1
    val target = (firstMonth..12).sumOf { month ->
        if (category == null) budget.limitFor(LocalDate.of(year, month, 1)) else budget.categories[category] ?: 0L
    }
    return SpendPace(cumulative, 12, target, from = firstMonth.coerceIn(1, 12))
}

/** The year's line in words; [monthName] turns 1..12 into "Jan"…, so tests don't depend on the locale. */
fun yearLine(p: SpendPace, money: (Long) -> String, monthName: (Int) -> String): String {
    val over = p.overFromDay
    return when {
        p.limitMinor <= 0 -> "${money(p.spentMinor)} so far this year · no target set"
        over != null -> "Over the year's target by ${money(p.spentMinor - p.limitMinor)} since ${monthName(over)}"
        p.cumulative.size < p.days && p.aheadOfPaceMinor > 0 -> "${money(p.aheadOfPaceMinor)} ahead of pace · heading for ${money(p.projectedMinor)} by December"
        p.cumulative.size < p.days -> "${money(-p.aheadOfPaceMinor)} under pace · on track for the year"
        else -> "${money(p.limitMinor - p.spentMinor)} under the year's target"
    }
}
