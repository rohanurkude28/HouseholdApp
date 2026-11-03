package com.houseaffairs.app.core.data

import java.time.LocalDate

data class CategorySpend(
    val name: String,
    val totalMinor: Long,
    /** Share of the month's spending, 0..1. */
    val share: Float,
    /** Same category in the month before, for "₹2k more than October". */
    val lastMonthMinor: Long,
    val subcategories: List<Pair<String, Long>>,
    /** Biggest few expenses in it. */
    val top: List<Txn>,
)

/** Where a budget month's money went, biggest category first. */
data class MonthOverview(
    val cycle: Cycle,
    val totalMinor: Long,
    val lastMonthMinor: Long,
    val categories: List<CategorySpend>,
    val count: Int,
    /** Spend clubbed under trips and outings that month. */
    val trips: List<Pair<String, Long>>,
    /**
     * The same total split by how it was paid ("UPI", "Cash", a card's name…), biggest first, so
     * cash and card add up to what was spent. Expenses with no method are "Not set".
     */
    val paidWith: List<Pair<String, Long>> = emptyList(),
)

private const val NOT_SET = "Not set"

/** The budget month before [cycle]. */
fun previousCycle(cycle: Cycle, startDay: Int): Cycle = cycleFor(cycle.start.minusDays(1), startDay)

/** Budget months from the one containing [from] up to the one containing [today], newest first. */
fun cyclesSince(from: LocalDate, today: LocalDate, startDay: Int): List<Cycle> {
    val first = cycleFor(from, startDay)
    val out = mutableListOf(cycleFor(today, startDay))
    while (out.last().start.isAfter(first.start) && out.size < 24) out += previousCycle(out.last(), startDay)
    return out
}

/** Paid spending in [cycle] by category, compared with the month before. Pure, so it's tested. */
fun monthOverview(txns: List<Txn>, cycle: Cycle, startDay: Int): MonthOverview {
    val paid = txns.filter { !it.planned }
    val now = paid.filter { it.date in cycle }
    val before = previousCycle(cycle, startDay).let { prev -> paid.filter { it.date in prev } }
    val total = now.sumOf { it.amountMinor }
    val lastByCategory = before.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
    return MonthOverview(
        cycle = cycle,
        totalMinor = total,
        lastMonthMinor = before.sumOf { it.amountMinor },
        count = now.size,
        categories = now.groupBy { it.category }.map { (cat, list) ->
            val sum = list.sumOf { it.amountMinor }
            CategorySpend(
                name = cat,
                totalMinor = sum,
                share = if (total == 0L) 0f else sum.toFloat() / total,
                lastMonthMinor = lastByCategory[cat] ?: 0,
                subcategories = list.filter { it.subcategory != null }.groupBy { it.subcategory!! }
                    .map { (s, l) -> s to l.sumOf { it.amountMinor } }.sortedByDescending { it.second },
                top = list.sortedByDescending { it.amountMinor }.take(3),
            )
        }.sortedByDescending { it.totalMinor },
        trips = now.filter { it.trip != null }.groupBy { it.trip!! }.map { (t, l) -> t to l.sumOf { it.amountMinor } }.sortedByDescending { it.second },
        paidWith = now.groupBy { it.paidWith?.takeIf(String::isNotBlank) ?: NOT_SET }
            .map { (method, l) -> method to l.sumOf { it.amountMinor } }
            .sortedWith(compareBy<Pair<String, Long>> { it.first == NOT_SET }.thenByDescending { it.second }),
    )
}
