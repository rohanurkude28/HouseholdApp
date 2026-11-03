package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One shared way to show long lists (pantry, shopping, medicines, supplies, kept things) so a
 * home with hundreds of items stays usable: search and filter chips on top, a short "Needs a look"
 * section, then categories that collapse. Pure, so the rules are tested.
 */

/** Why an item wants attention, most urgent first. */
enum class Flag(val label: String) { EXPIRED("Expired"), EXPIRING("Expiring"), LOW("Running low") }

data class ListRow<T>(
    val item: T,
    val key: String,
    val title: String,
    val category: String,
    val flag: Flag? = null,
    /** Extra words search should match ("bank locker" for a kept thing). */
    val searchText: String = "",
)

enum class ListFilter(val label: String) { ALL("All"), EXPIRING("Expiring"), LOW("Running low") }

data class ListSection<T>(val category: String, val rows: List<ListRow<T>>, val flagged: Int, val open: Boolean)

data class OrganizedList<T>(
    /** Expired, expiring and low items, shown first. Empty while searching or filtering. */
    val needsLook: List<ListRow<T>>,
    val sections: List<ListSection<T>>,
    /** Every category with its item count, for the chips. */
    val categories: List<Pair<String, Int>>,
    val total: Int,
    /** True when a search, filter or category narrows the list. */
    val narrowed: Boolean,
)

/** A stock item's flag: expired, expiring within [soonDays], or at a quarter or less left. */
fun stockFlag(stock: PantryStock, today: LocalDate, soonDays: Long = 7): Flag? {
    val days = stock.expiry?.let { ChronoUnit.DAYS.between(today, it) }
    val level = stock.initialAmount?.takeIf { it > 0 }?.let { (stock.amount ?: it) / it }
    return when {
        days != null && days < 0 -> Flag.EXPIRED
        days != null && days <= soonDays -> Flag.EXPIRING
        level != null && level <= 0.25 -> Flag.LOW
        else -> null
    }
}

/**
 * Arranges [rows]. Categories follow [order] (others alphabetically, "Other" last). A category is
 * open when it has [openUpTo] items or fewer, unless [toggled] says the person flipped it.
 * Searching, filtering or picking a category opens everything that's left. A list with its own
 * search (kept things understand "where are my keys?") passes the keys it found as [matching].
 */
fun <T> organize(
    rows: List<ListRow<T>>,
    query: String = "",
    filter: ListFilter = ListFilter.ALL,
    category: String? = null,
    order: List<String> = emptyList(),
    toggled: Set<String> = emptySet(),
    openUpTo: Int = 5,
    matching: Set<String>? = null,
): OrganizedList<T> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val narrowed = words.isNotEmpty() || filter != ListFilter.ALL || category != null
    val shown = rows.filter { r ->
        val text = "${r.title} ${r.category} ${r.searchText}".lowercase()
        (if (matching != null && words.isNotEmpty()) r.key in matching else words.all { it in text }) &&
            (category == null || r.category == category) &&
            when (filter) {
                ListFilter.ALL -> true
                ListFilter.EXPIRING -> r.flag == Flag.EXPIRED || r.flag == Flag.EXPIRING
                ListFilter.LOW -> r.flag == Flag.LOW
            }
    }
    val sections = grouped(shown, order) { it.category }.map { (name, list) ->
        val byDefault = list.size <= openUpTo
        ListSection(
            category = name,
            rows = list,
            flagged = list.count { it.flag != null },
            open = narrowed || (if (name in toggled) !byDefault else byDefault),
        )
    }
    return OrganizedList(
        needsLook = if (narrowed) emptyList() else rows.filter { it.flag != null }.sortedBy { it.flag!!.ordinal },
        sections = sections,
        categories = grouped(rows, order) { it.category }.map { it.first to it.second.size },
        total = rows.size,
        narrowed = narrowed,
    )
}
