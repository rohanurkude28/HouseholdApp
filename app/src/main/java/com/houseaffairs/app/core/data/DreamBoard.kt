package com.houseaffairs.app.core.data

/**
 * The vision board's order: where each dream was dragged to (shared by the family), and new
 * dreams after them, soonest first. Pure, so it's tested.
 */
fun boardOrdered(dreams: List<Dream>): List<Dream> =
    dreams.sortedWith(
        compareBy<Dream>({ it.boardOrder == null }, { it.boardOrder ?: 0.0 })
            .thenBy { it.targetDate?.toEpochDay() ?: Long.MAX_VALUE }
            .thenBy { it.title.lowercase() },
    )

/** [ids] with the one at [from] moved to [to]; unchanged when either is out of range. */
fun <T> moved(ids: List<T>, from: Int, to: Int): List<T> {
    if (from !in ids.indices || to !in ids.indices || from == to) return ids
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

/** Positions to save for [ids] in board order: 0, 1, 2…, only for the ones that changed. */
fun boardOrders(ids: List<String>, before: Map<String, Double?>): Map<String, Double> =
    ids.withIndex().associate { (i, id) -> id to i.toDouble() }.filter { (id, pos) -> before[id] != pos }
