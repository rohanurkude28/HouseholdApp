package com.houseaffairs.app.core.data

/** How many big "usual" tiles the add sheet shows, at least. */
const val USUAL_COUNT = 4

/** Pinned kinds can fill two rows of tiles, no more. */
const val MAX_PINNED = 8

/** Adding something once doesn't make it a habit; twice does. */
private const val USUAL_AFTER = 2

/**
 * The big tiles at the top of the add sheet. Kinds the person pinned come first, in the order
 * pinned, and can take up to [MAX_PINNED] tiles; the rest of the [most] are what they add most
 * (added [USUAL_AFTER] times or more), then [defaults], so a new phone still shows the everyday
 * ones. Only [allowed] kinds, in names (`EntryType.name`), so it's testable.
 */
fun usualKinds(counts: Map<String, Int>, allowed: List<String>, defaults: List<String>, most: Int = USUAL_COUNT, pinned: List<String> = emptyList()): List<String> {
    val pins = pinned.filter { it in allowed }.distinct().take(MAX_PINNED)
    val habits = counts.filter { (kind, n) -> n >= USUAL_AFTER && kind in allowed }
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { allowed.indexOf(it.key) })
        .map { it.key }
    return (pins + habits + defaults.filter { it in allowed } + allowed).distinct().take(maxOf(most, pins.size))
}

/** Per-phone counts as saved ("EXPENSE:5,TASK:2"); anything unreadable is skipped. */
fun parseCounts(saved: String?): Map<String, Int> =
    saved.orEmpty().split(",").mapNotNull { part ->
        val (kind, n) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        n.toIntOrNull()?.let { kind.trim() to it }
    }.toMap()

fun encodeCounts(counts: Map<String, Int>): String = counts.entries.joinToString(",") { "${it.key}:${it.value}" }
