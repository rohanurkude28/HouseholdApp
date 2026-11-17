package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*


/**
 * Repeating tasks and chores. Ticking one off creates the next, due one step after its due date
 * (or after today, if it had none or was very late), and passes it to whoever's turn is next.
 * Pure, so the rules are tested.
 */

/** How often a task or chore can repeat, as the form offers it. */
val REPEAT_CHOICES = listOf("Never", "Daily", "Every 2 days", "Every 3 days", "Weekly", "Every 2 weeks", "Monthly")

private val EVERY = Regex("^every (\\d+) (day|days|week|weeks)$", RegexOption.IGNORE_CASE)

/** Days between repeats ("Every 3 days" → 3, "Weekly" → 7); null for monthly or none. */
fun repeatDays(repeat: String?): Int? = when (repeat) {
    "Daily" -> 1
    "Weekly" -> 7
    else -> EVERY.matchEntire(repeat.orEmpty().trim())?.let { m -> m.groupValues[1].toInt() * if (m.groupValues[2].startsWith("week")) 7 else 1 }
}

/** The next due date for a task repeating [repeat] ("Daily", "Every 3 days", "Weekly", "Monthly"), or null if it doesn't repeat. */
fun nextDue(due: LocalDate?, repeat: String?, today: LocalDate): LocalDate? {
    val days = repeatDays(repeat)
    val step: (LocalDate) -> LocalDate = when {
        days != null -> { d -> d.plusDays(days.toLong()) }
        repeat == "Monthly" -> { d -> d.plusMonths(1) }
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
