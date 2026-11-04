package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * When to buy things again, and what the family thought last time. Purchases come from ticked-off
 * shopping items and groceries added as bought. Pure, so the rules are tested.
 */

/** Something bought on [date]. */
data class Purchase(val title: String, val date: LocalDate)

/** What a pantry item remembers: its brand and rating, and a fixed restock choice if one was set. */
data class StockMemory(val title: String, val brand: String?, val rating: String?, val restock: String?, val date: LocalDate?)

/** Time to buy [title] again, due [due]. [why] says how we know. */
data class RestockDue(val title: String, val due: LocalDate, val why: String)

/** Same thing whatever the case or a plural "s": "Onions" and "onion". */
fun itemKey(title: String): String = title.trim().lowercase().removeSuffix("es").removeSuffix("s")

/** The usual days between buys: the median gap, once there are three or more buys on different days. */
fun boughtEvery(dates: List<LocalDate>): Int? {
    val days = dates.distinct().sorted()
    if (days.size < 3) return null
    val gaps = days.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.sorted()
    return gaps[gaps.size / 2].takeIf { it >= 2 }
}

/**
 * Items due for a restock within [aheadDays], not already on the shopping list. A fixed choice
 * ("Every week", "Every month") wins; otherwise the usual gap between buys is used.
 */
fun restockDue(
    purchases: List<Purchase>,
    memory: List<StockMemory>,
    onList: List<String>,
    today: LocalDate,
    aheadDays: Long = 2,
): List<RestockDue> {
    val listed = onList.map(::itemKey).toSet()
    val fixed = memory.filter { it.restock in setOf("Every week", "Every month") }.associateBy { itemKey(it.title) }
    return purchases.groupBy { itemKey(it.title) }.mapNotNull { (key, buys) ->
        if (key in listed) return@mapNotNull null
        val last = buys.maxOf { it.date }
        val title = buys.maxBy { it.date }.title
        val (days, why) = when (fixed[key]?.restock) {
            "Every week" -> 7 to "You restock it every week"
            "Every month" -> 30 to "You restock it every month"
            else -> boughtEvery(buys.map { it.date })?.let { it to "You usually buy it every $it days" } ?: return@mapNotNull null
        }
        val due = last.plusDays(days.toLong())
        RestockDue(title, due, why).takeIf { !due.isAfter(today.plusDays(aheadDays)) }
    }.sortedBy { it.due }
}

/** "Last time: Amul 👍" from the most recent pantry entry with a brand or rating, or null. */
fun lastTimeNote(title: String, memory: List<StockMemory>): String? {
    val key = itemKey(title)
    val last = memory.filter { itemKey(it.title) == key && (it.brand != null || it.rating != null) }.maxByOrNull { it.date ?: LocalDate.MIN }
        ?: return null
    val thumb = when {
        last.rating == null -> null
        last.rating.startsWith("👍") -> "👍"
        else -> "👎"
    }
    return "Last time: " + listOfNotNull(last.brand, thumb).joinToString(" ")
}
