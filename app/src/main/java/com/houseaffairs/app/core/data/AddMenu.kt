package com.houseaffairs.app.core.data

/** How many big "usual" tiles the add sheet shows. */
const val USUAL_COUNT = 4

/** Adding something once doesn't make it a habit; twice does. */
private const val USUAL_AFTER = 2

/**
 * The kinds this person adds most, for the big tiles at the top of the add sheet. Kinds added
 * [USUAL_AFTER] times or more lead, most first; [defaults] fill the rest, so a new phone still
 * shows the everyday ones. Only [allowed] kinds, in names (`EntryType.name`), so it's testable.
 */
fun usualKinds(counts: Map<String, Int>, allowed: List<String>, defaults: List<String>, most: Int = USUAL_COUNT): List<String> {
    val habits = counts.filter { (kind, n) -> n >= USUAL_AFTER && kind in allowed }
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { allowed.indexOf(it.key) })
        .map { it.key }
    return (habits + defaults.filter { it in allowed } + allowed).distinct().take(most)
}

/**
 * The rest of the add menu as a few plain groups, each kind once: nothing already shown as a
 * usual tile, nothing this person can't add, and groups left empty are dropped.
 */
fun addGroups(groups: List<Pair<String, List<String>>>, allowed: List<String>, shown: List<String>): List<Pair<String, List<String>>> =
    groups.map { (name, kinds) -> name to kinds.filter { it in allowed && it !in shown } }.filter { it.second.isNotEmpty() }

/** Per-phone counts as saved ("EXPENSE:5,TASK:2"); anything unreadable is skipped. */
fun parseCounts(saved: String?): Map<String, Int> =
    saved.orEmpty().split(",").mapNotNull { part ->
        val (kind, n) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        n.toIntOrNull()?.let { kind.trim() to it }
    }.toMap()

fun encodeCounts(counts: Map<String, Int>): String = counts.entries.joinToString(",") { "${it.key}:${it.value}" }
