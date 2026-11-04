package com.houseaffairs.app.core.data

import java.time.LocalDate

/**
 * Repeating tasks and chores. Ticking one off creates the next, due one step after its due date
 * (or after today, if it had none or was very late), and passes it to whoever's turn is next.
 * Pure, so the rules are tested.
 */

/** The next due date for a task repeating [repeat] ("Daily", "Weekly", "Monthly"), or null if it doesn't repeat. */
fun nextDue(due: LocalDate?, repeat: String?, today: LocalDate): LocalDate? {
    val step: (LocalDate) -> LocalDate = when (repeat) {
        "Daily" -> { d -> d.plusDays(1) }
        "Weekly" -> { d -> d.plusWeeks(1) }
        "Monthly" -> { d -> d.plusMonths(1) }
        else -> return null
    }
    // Done late? The next one still lands in the future, on the same rhythm.
    var next = step(due ?: today)
    while (!next.isAfter(today)) next = step(next)
    return next
}

/** Whose turn it is after [current] in [rotation]; the current person again when nobody else takes turns. */
fun nextInRotation(rotation: List<String>, current: String?): String? {
    if (rotation.isEmpty()) return current
    val i = rotation.indexOf(current)
    return rotation[(i + 1) % rotation.size]
}
