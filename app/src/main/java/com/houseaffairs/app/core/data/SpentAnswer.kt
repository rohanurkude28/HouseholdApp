package com.houseaffairs.app.core.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** "How much did we spend on groceries this month?": the total, how many, and where most went. */
data class Spent(val totalMinor: Long, val count: Int, val top: List<Pair<String, Long>>, val matched: String?)

/**
 * The days [period] covers, up to today: "this month" is the budget month (salary day or the
 * 1st), so it matches Money; weeks start on Monday.
 */
fun periodDays(period: String?, today: LocalDate, cycleStartDay: Int = 1): ClosedRange<LocalDate> = when (period?.lowercase()) {
    "today" -> today..today
    "this week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))..today
    "last week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1).let { it..it.plusDays(6) }
    "this year" -> today.withDayOfYear(1)..today
    "last year" -> today.minusYears(1).withDayOfYear(1).let { it..it.withDayOfYear(it.lengthOfYear()) }
    "last month" -> previousCycle(cycleFor(today, cycleStartDay), cycleStartDay).let { it.start..it.endExclusive.minusDays(1) }
    else -> cycleFor(today, cycleStartDay).start..today
}

/**
 * Paid spending in [days] on [subject]: a category ("groceries"), a kind within one ("hotel"),
 * or a shop ("DMart"); everything when null. Pure, so it's tested.
 */
fun spentOn(txns: List<Txn>, subject: String?, days: ClosedRange<LocalDate>): Spent {
    val paid = txns.filter { !it.planned && it.date in days }
    val s = subject?.trim()?.lowercase()
    fun same(a: String?) = a != null && s != null && (a.lowercase() == s || a.lowercase().removeSuffix("s") == s.removeSuffix("s"))
    val (hits, matched) = when {
        s == null -> paid to null
        paid.any { same(it.category) } -> paid.filter { same(it.category) } to paid.first { same(it.category) }.category
        paid.any { same(it.subcategory) } -> paid.filter { same(it.subcategory) } to paid.first { same(it.subcategory) }.subcategory
        else -> paid.filter { it.title.lowercase().contains(s) } to paid.firstOrNull { it.title.lowercase().contains(s) }?.title
    }
    // Where most of it went: shops within a category, categories overall.
    val top = hits.groupBy { if (s == null) it.category else it.title }.map { (k, l) -> k to l.sumOf { it.amountMinor } }.sortedByDescending { it.second }.take(3)
    return Spent(hits.sumOf { it.amountMinor }, hits.size, top, matched)
}
