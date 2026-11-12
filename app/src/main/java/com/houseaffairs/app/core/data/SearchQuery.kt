package com.houseaffairs.app.core.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * What a search asks for, read the way a person says it: "uber last month", "dining over 1000",
 * "riya's medicines", "expiring this week", "overdue tasks", "#goa". The rest of the words are
 * searched as before. Pure, so it's tested.
 */
data class SearchQuery(
    /** Words still to match against names and text. */
    val words: List<String>,
    /** Only these collections ("transactions", "tasks"…), when a kind was named. */
    val kinds: Set<String> = emptySet(),
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    /** In minor units. */
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
    val tags: List<String> = emptyList(),
    val state: ItemState? = null,
    /** What was understood, in plain words, for the chips above the results ("Spending", "Last month"). */
    val understood: List<String> = emptyList(),
) {
    val hasFilters: Boolean get() = kinds.isNotEmpty() || from != null || to != null || minMinor != null || maxMinor != null || tags.isNotEmpty() || state != null
}

enum class ItemState { EXPIRING, EXPIRED, OVERDUE, DUE }

/** Plural kind words name a kind; "trip" alone is still a word, so "goa trip" finds Goa's spending too. */
private val KIND_WORDS: Map<String, Pair<Set<String>, String>> = mapOf(
    "expenses" to (setOf("transactions") to "Spending"), "expense" to (setOf("transactions") to "Spending"),
    "spending" to (setOf("transactions") to "Spending"), "spent" to (setOf("transactions") to "Spending"),
    "spend" to (setOf("transactions") to "Spending"), "paid" to (setOf("transactions") to "Spending"),
    "tasks" to (setOf("tasks") to "Tasks"), "todo" to (setOf("tasks") to "Tasks"), "todos" to (setOf("tasks") to "Tasks"), "chores" to (setOf("tasks") to "Tasks"),
    "medicines" to (setOf("medicines") to "Medicines"), "meds" to (setOf("medicines") to "Medicines"), "tablets" to (setOf("medicines") to "Medicines"),
    "recipes" to (setOf("recipes") to "Recipes"), "dishes" to (setOf("recipes") to "Recipes"),
    "places" to (setOf("places") to "Places"), "restaurants" to (setOf("places") to "Places"),
    "trips" to (setOf("trips") to "Trips"), "outings" to (setOf("trips") to "Trips"),
    "bills" to (setOf("recurring") to "Bills & subs"), "subscriptions" to (setOf("recurring") to "Bills & subs"),
    "events" to (setOf("events") to "Events"), "birthdays" to (setOf("events") to "Birthdays"),
    "documents" to (setOf("documents", "keptAt") to "Documents"), "papers" to (setOf("documents", "keptAt") to "Documents"),
    "goals" to (setOf("weeklyGoals") to "Goals"), "dreams" to (setOf("dreams") to "Dreams"),
    "lists" to (setOf("listItems") to "Lists"), "wishlist" to (setOf("listItems") to "Lists"),
    "pantry" to (setOf("stock") to "Pantry"),
)

private val FILLER = setOf(
    "show", "me", "all", "my", "our", "the", "a", "an", "find", "search", "what", "which", "did", "do", "we", "i", "were", "was", "is",
    "are", "have", "has", "any", "list", "of", "with", "how", "much", "many", "things", "stuff", "everything", "please", "in", "on", "at", "from", "for",
)

private val MONTHS = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

private fun amountOf(word: String): Long? {
    val w = word.removePrefix("₹").removePrefix("rs").replace(",", "")
    val k = w.endsWith("k")
    val n = w.removeSuffix("k").toDoubleOrNull() ?: return null
    return ((if (k) n * 1000 else n) * 100).toLong()
}

fun parseSearch(query: String, today: LocalDate): SearchQuery {
    val raw = query.lowercase().replace("'s ", " ").replace("'s", "").replace(Regex("[^\\p{L}\\p{N}#₹,.\\s-]"), " ")
    val tokens = raw.split(Regex("\\s+")).map { it.trim(',', '.') }.filter { it.isNotEmpty() }.toMutableList()
    var kinds = emptySet<String>()
    var from: LocalDate? = null
    var to: LocalDate? = null
    var min: Long? = null
    var max: Long? = null
    var state: ItemState? = null
    val tags = mutableListOf<String>()
    val understood = mutableListOf<String>()
    val words = mutableListOf<String>()
    fun range(f: LocalDate, t: LocalDate, label: String) { from = f; to = t; understood += label }
    var i = 0
    while (i < tokens.size) {
        val w = tokens[i]
        val next = tokens.getOrNull(i + 1)
        when {
            w.startsWith("#") && w.length > 1 -> { tags += w.removePrefix("#"); understood += w }
            w == "today" -> range(today, today, "Today")
            w == "yesterday" -> range(today.minusDays(1), today.minusDays(1), "Yesterday")
            (w == "this" || w == "last" || w == "next") && next in setOf("week", "month", "year") -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                when ("$w $next") {
                    "this week" -> range(monday, monday.plusDays(6), "This week")
                    "last week" -> range(monday.minusWeeks(1), monday.minusDays(1), "Last week")
                    "next week" -> range(monday.plusWeeks(1), monday.plusDays(13), "Next week")
                    "this month" -> range(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()), "This month")
                    "last month" -> today.minusMonths(1).let { m -> range(m.withDayOfMonth(1), m.with(TemporalAdjusters.lastDayOfMonth()), "Last month") }
                    "next month" -> today.plusMonths(1).let { m -> range(m.withDayOfMonth(1), m.with(TemporalAdjusters.lastDayOfMonth()), "Next month") }
                    "this year" -> range(today.withDayOfYear(1), today.withMonth(12).withDayOfMonth(31), "This year")
                    "last year" -> today.minusYears(1).let { y -> range(y.withDayOfYear(1), y.withMonth(12).withDayOfMonth(31), "Last year") }
                    "next year" -> today.plusYears(1).let { y -> range(y.withDayOfYear(1), y.withMonth(12).withDayOfMonth(31), "Next year") }
                }
                i++
            }
            // "october", "in oct", "oct 2025": the latest one that has begun, unless a year is said.
            w.length >= 3 && MONTHS.any { it.startsWith(w) } && (w.length >= 4 || w in setOf("jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "oct", "nov", "dec")) -> {
                val month = MONTHS.indexOfFirst { it.startsWith(w) } + 1
                val year = next?.toIntOrNull()?.takeIf { it in 2000..2100 }?.also { i++ }
                    ?: if (month > today.monthValue) today.year - 1 else today.year
                val start = LocalDate.of(year, month, 1)
                range(start, start.with(TemporalAdjusters.lastDayOfMonth()), MONTHS[month - 1].replaceFirstChar { it.uppercase() } + if (year != today.year) " $year" else "")
            }
            w in setOf("over", "above", "more", ">") -> {
                val at = if (next == "than") i + 2 else i + 1
                amountOf(tokens.getOrNull(at).orEmpty())?.let { min = it; understood += "Over ₹${it / 100}"; i = at } ?: words.add(w)
            }
            w in setOf("under", "below", "less", "<") -> {
                val at = if (next == "than") i + 2 else i + 1
                amountOf(tokens.getOrNull(at).orEmpty())?.let { max = it; understood += "Under ₹${it / 100}"; i = at } ?: words.add(w)
            }
            w == "expiring" || (w == "going" && next == "off") -> { state = ItemState.EXPIRING; understood += "Expiring soon"; if (w == "going") i++ }
            w == "expired" -> { state = ItemState.EXPIRED; understood += "Expired" }
            w == "overdue" || w == "late" || w == "pending" -> { state = ItemState.OVERDUE; understood += "Overdue" }
            w == "due" -> { state = ItemState.DUE; understood += "Due" }
            w in KIND_WORDS -> { val (k, label) = KIND_WORDS.getValue(w); kinds = kinds + k; if (label !in understood) understood += label }
            w in FILLER -> Unit
            else -> words += w
        }
        i++
    }
    return SearchQuery(words.map { if (it.length > 3) it.removeSuffix("s") else it }.distinct(), kinds, from, to, min, max, tags, state, understood)
}

/** True when [item] fits every filter [q] names; words are matched separately. */
fun SearchQuery.fits(item: Findable, today: LocalDate): Boolean {
    if (kinds.isNotEmpty() && item.collection !in kinds) return false
    if ((from != null || to != null)) {
        val d = item.date ?: return false
        if (from != null && d.isBefore(from)) return false
        if (to != null && d.isAfter(to)) return false
    }
    if (minMinor != null || maxMinor != null) {
        val a = item.amountMinor ?: return false
        if (minMinor != null && a <= minMinor) return false
        if (maxMinor != null && a >= maxMinor) return false
    }
    if (tags.isNotEmpty() && tags.any { t -> item.tags.none { it.equals(t, ignoreCase = true) } }) return false
    return when (state) {
        null -> true
        ItemState.EXPIRING -> item.collection in setOf("stock", "medicines") && item.date?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(7)) } == true
        ItemState.EXPIRED -> item.collection in setOf("stock", "medicines") && item.date?.isBefore(today) == true
        ItemState.OVERDUE -> item.collection == "tasks" && item.date?.isBefore(today) == true
        ItemState.DUE -> item.collection in setOf("tasks", "recurring", "events") && item.date?.let { !it.isAfter(to ?: today.plusDays(7)) } == true
    }
}
