package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** One pack of something: when it came home and, once finished, when it ran out. */
data class StockLife(val title: String, val start: LocalDate, val end: LocalDate?)

/**
 * How long [title] usually lasts: "detergent lasts about 34 days". [current] is the pack in use
 * now (when it started), so "about 10 days left" can be said.
 */
data class ItemUsage(
    val title: String,
    val finished: Int,
    val averageDays: Int?,
    val lastDays: Int?,
    val currentSince: LocalDate?,
) {
    fun daysLeft(today: LocalDate): Int? {
        val avg = averageDays ?: return null
        val since = currentSince ?: return null
        return (avg - ChronoUnit.DAYS.between(since, today).toInt()).coerceAtLeast(0)
    }
}

private fun key(title: String) = title.lowercase().trim().removeSuffix("es").removeSuffix("s")

/** Per item, from every pack bought and finished. Pure, so it's tested. */
fun usageOf(lives: List<StockLife>): List<ItemUsage> = lives.groupBy { key(it.title) }.map { (_, packs) ->
    val done = packs.filter { it.end != null }.sortedBy { it.end }
    val days = done.map { ChronoUnit.DAYS.between(it.start, it.end).toInt().coerceAtLeast(1) }
    ItemUsage(
        title = packs.maxByOrNull { it.start }!!.title,
        finished = done.size,
        averageDays = days.takeIf { it.isNotEmpty() }?.average()?.let { ceil(it).toInt() },
        lastDays = days.lastOrNull(),
        currentSince = packs.filter { it.end == null }.maxByOrNull { it.start }?.start,
    )
}.sortedWith(compareByDescending<ItemUsage> { it.finished }.thenBy { it.title.lowercase() })

/** Month by month, how many of each thing ran out: "Oct: detergent 2, rice 1". Newest month first. */
fun monthlyUsage(lives: List<StockLife>, today: LocalDate, months: Int = 6): List<Pair<String, List<Pair<String, Int>>>> {
    val now = YearMonth.from(today)
    return (0 until months).map { now.minusMonths(it.toLong()) }.map { ym ->
        val finished = lives.filter { it.end != null && YearMonth.from(it.end) == ym }
        ym.format(DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.US)) to finished.groupBy { key(it.title) }
            .map { (_, l) -> l.first().title to l.size }.sortedByDescending { it.second }
    }.filter { it.second.isNotEmpty() }
}

/** "Detergent lasted 34 days" sentences for one item, for the answer to "how long did it last?". */
fun usageLines(u: ItemUsage, today: LocalDate): List<String> = buildList {
    if (u.averageDays == null) {
        add("Nothing finished yet, so there's no pattern. Tap Finished when it runs out and I'll start counting.")
        return@buildList
    }
    add(if (u.finished == 1) "The last one lasted ${u.lastDays} days" else "It usually lasts about ${u.averageDays} days (${u.finished} used up)")
    if (u.finished > 1 && u.lastDays != null) add("The last one: ${u.lastDays} days")
    u.daysLeft(today)?.let { add(if (it == 0) "The one open now is due to run out about now" else "The one open now should last about $it more days") }
}

/** One month in the kitchen: what came home, what ran out, and what groceries cost. */
data class KitchenMonth(
    val label: String,
    val bought: List<Pair<String, Int>>,
    val usedUp: List<Pair<String, Int>>,
    /** Spent under Groceries; null when money isn't shown (no budget, or the role can't see it). */
    val spentMinor: Long?,
)

/**
 * Month by month, newest first: items bought ([bought] title and date), packs finished ([lives])
 * and grocery spending ([groceries], paid ones only). Months with nothing are left out.
 */
fun kitchenMonths(bought: List<Pair<String, LocalDate>>, lives: List<StockLife>, groceries: List<Txn>?, today: LocalDate, months: Int = 6): List<KitchenMonth> {
    val now = YearMonth.from(today)
    fun count(titles: List<String>) = titles.groupBy { key(it) }.map { (_, l) -> l.first() to l.size }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
    return (0 until months).map { now.minusMonths(it.toLong()) }.map { ym ->
        KitchenMonth(
            label = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.US)),
            bought = count(bought.filter { YearMonth.from(it.second) == ym }.map { it.first }),
            usedUp = count(lives.filter { it.end != null && YearMonth.from(it.end) == ym }.map { it.title }),
            spentMinor = groceries?.filter { !it.planned && YearMonth.from(it.date) == ym }?.sumOf { it.amountMinor },
        )
    }.filter { it.bought.isNotEmpty() || it.usedUp.isNotEmpty() || (it.spentMinor ?: 0) > 0 }
}
