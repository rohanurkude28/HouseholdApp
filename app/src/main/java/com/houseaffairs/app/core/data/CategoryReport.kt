package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.YearMonth

/** Where one category's money went: totals, month by month, by shop, and every expense. */
data class CategoryReport(
    val category: String,
    val thisMonthMinor: Long,
    val thisYearMinor: Long,
    val allMinor: Long,
    /** Last 12 calendar months, oldest first, including empty ones. */
    val months: List<Pair<YearMonth, Long>>,
    /** Shop or payee, how many times, how much; biggest first. */
    val places: List<Triple<String, Int, Long>>,
    /** Newest first. */
    val items: List<Txn>,
)

/** Everything paid under [category] in [txns] (planned spends aren't spending yet). Pure, so it's tested. */
fun categoryReport(txns: List<Txn>, category: String, today: LocalDate): CategoryReport {
    val paid = txns.filter { !it.planned && it.category == category }
    val now = YearMonth.from(today)
    val months = (11 downTo 0).map { now.minusMonths(it.toLong()) }.map { ym -> ym to paid.filter { YearMonth.from(it.date) == ym }.sumOf { it.amountMinor } }
    val places = paid.groupBy { it.title.trim().lowercase().ifEmpty { "(no name)" } }
        .map { (_, l) -> Triple(l.first().title.trim().ifEmpty { "No name" }, l.size, l.sumOf { it.amountMinor }) }
        .sortedByDescending { it.third }
    return CategoryReport(
        category,
        thisMonthMinor = paid.filter { YearMonth.from(it.date) == now }.sumOf { it.amountMinor },
        thisYearMinor = paid.filter { it.date.year == today.year }.sumOf { it.amountMinor },
        allMinor = paid.sumOf { it.amountMinor },
        months = months,
        places = places,
        items = paid.sortedByDescending { it.date },
    )
}
