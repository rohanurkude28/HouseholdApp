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

/** Something that came home: ticked off the shopping list or added as a grocery. [type] is its aisle, if said. */
data class KitchenBuy(val title: String, val date: LocalDate, val type: String? = null)

/**
 * Something that went to waste: [thrown] away on [date], or still there after its best-before
 * [date]. [costMinor] and [level] (% left) value what was lost.
 */
data class Expired(val title: String, val date: LocalDate, val costMinor: Long? = null, val level: Int? = null, val thrown: Boolean = false) {
    /** What was left of it when it went: the cost times the share still left (all of it, if unknown). */
    val lostMinor: Long? get() = costMinor?.let { it * (level ?: 100).coerceIn(0, 100) / 100 }
}

enum class StockKind { FOOD, SUPPLIES, MEDICINES }

/** Everything the month and year views need for one kind of stock. */
data class StockHistory(val bought: List<KitchenBuy>, val lives: List<StockLife>, val wasted: List<Expired>)

/** One month in the kitchen: what came home and of which kind, what ran out, what went off, what it cost. */
data class KitchenMonth(
    val month: YearMonth,
    val label: String,
    val bought: List<Pair<String, Int>>,
    val usedUp: List<Pair<String, Int>>,
    /** How many things of each kind came home, most first. */
    val byType: List<Pair<Aisle, Int>>,
    val wasted: List<Expired>,
    /** Spent under Groceries; null when money isn't shown (no budget, or the role can't see it). */
    val spentMinor: Long?,
    val lastMonthSpentMinor: Long? = null,
    /** The Groceries limit in the budget, when there is one. */
    val budgetMinor: Long? = null,
) {
    val wastedMinor: Long get() = wasted.sumOf { it.lostMinor ?: 0 }

    /** Spend against last month: +12 is 12% more. Null without spending in both months. */
    val changePercent: Int?
        get() {
            val now = spentMinor?.takeIf { it > 0 } ?: return null
            val before = lastMonthSpentMinor?.takeIf { it > 0 } ?: return null
            return Math.round((now - before) * 100.0 / before).toInt()
        }
}

/**
 * Month by month, newest first, for the last [months] months: items bought ([bought]), packs
 * finished ([lives]), what went to waste ([expired]: thrown away, or once its best-before has passed)
 * and grocery spending ([groceries], paid ones only). Months with nothing are left out, except
 * this one, so the Kitchen card always has a month to talk about.
 */
fun kitchenMonths(
    bought: List<KitchenBuy>,
    lives: List<StockLife>,
    expired: List<Expired>,
    groceries: List<Txn>?,
    today: LocalDate,
    groceryBudgetMinor: Long? = null,
    months: Int = 6,
): List<KitchenMonth> {
    val now = YearMonth.from(today)
    fun count(titles: List<String>) = titles.groupBy { key(it) }.map { (_, l) -> l.first() to l.size }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
    fun spent(ym: YearMonth) = groceries?.filter { !it.planned && YearMonth.from(it.date) == ym }?.sumOf { it.amountMinor }
    return (0 until months).map { now.minusMonths(it.toLong()) }.map { ym ->
        val came = bought.filter { YearMonth.from(it.date) == ym }
        KitchenMonth(
            month = ym,
            label = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.US)),
            bought = count(came.map { it.title }),
            usedUp = count(lives.filter { it.end != null && YearMonth.from(it.end) == ym }.map { it.title }),
            byType = came.groupingBy { aisleOf(it.title, it.type) }.eachCount().toList()
                .sortedWith(compareByDescending<Pair<Aisle, Int>> { it.second }.thenBy { it.first.ordinal }),
            wasted = expired.filter { YearMonth.from(it.date) == ym && (it.thrown || it.date.isBefore(today)) }.sortedBy { it.date },
            spentMinor = spent(ym),
            lastMonthSpentMinor = spent(ym.minusMonths(1)),
            budgetMinor = groceryBudgetMinor?.takeIf { it > 0 && groceries != null },
        )
    }.filter { it.month == now || it.bought.isNotEmpty() || it.usedUp.isNotEmpty() || it.wasted.isNotEmpty() || (it.spentMinor ?: 0) > 0 }
}

/** One year of [KitchenMonth]s, added up: the summary shown before any month is opened. */
data class StockYear(val year: Int, val months: List<KitchenMonth>) {
    /** Null when money isn't shown, as for each month. */
    val spentMinor: Long? get() = months.mapNotNull { it.spentMinor }.takeIf { it.isNotEmpty() }?.sum()
    val bought: Int get() = months.sumOf { m -> m.bought.sumOf { it.second } }
    val usedUp: Int get() = months.sumOf { m -> m.usedUp.sumOf { it.second } }
    val wasted: Int get() = months.sumOf { it.wasted.size }
    val wastedMinor: Long get() = months.sumOf { it.wastedMinor }
    val byType: List<Pair<Aisle, Int>>
        get() = months.flatMap { it.byType }.groupBy({ it.first }, { it.second }).map { (a, n) -> a to n.sum() }
            .sortedWith(compareByDescending<Pair<Aisle, Int>> { it.second }.thenBy { it.first.ordinal })

    /** What came home most often over the year: "Milk ×14". */
    fun mostBought(n: Int = 5): List<Pair<String, Int>> =
        months.flatMap { it.bought }.groupBy { key(it.first) }.map { (_, l) -> l.first().first to l.sumOf { it.second } }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() }).take(n)
}

/** Months grouped by year, newest first. */
fun stockYears(months: List<KitchenMonth>): List<StockYear> =
    months.groupBy { it.month.year }.map { (y, ms) -> StockYear(y, ms.sortedByDescending { it.month }) }.sortedByDescending { it.year }
