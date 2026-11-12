package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A chore to offer when setting up the family's list, with how often it's usually done. */
data class ChoreIdea(val emoji: String, val title: String, val repeat: String)

val CHORE_IDEAS = listOf(
    ChoreIdea("🗑️", "Take out the trash", "Daily"),
    ChoreIdea("🍽️", "Wash the dishes", "Daily"),
    ChoreIdea("🧽", "Wipe the kitchen counters", "Daily"),
    ChoreIdea("💧", "Refill water bottles", "Daily"),
    ChoreIdea("🪴", "Water the plants", "Every 2 days"),
    ChoreIdea("🧹", "Sweep and mop", "Every 2 days"),
    ChoreIdea("👕", "Do laundry", "Every 3 days"),
    ChoreIdea("👔", "Iron clothes", "Weekly"),
    ChoreIdea("🛏️", "Change bedsheets", "Weekly"),
    ChoreIdea("🚿", "Clean the bathroom", "Weekly"),
    ChoreIdea("🪶", "Dust the shelves", "Weekly"),
    ChoreIdea("🛒", "Check what to restock", "Weekly"),
    ChoreIdea("🧊", "Clean the fridge", "Every 2 weeks"),
    ChoreIdea("🌀", "Clean the fans", "Monthly"),
    ChoreIdea("🚗", "Wash the car", "Every 2 weeks"),
)

/** Without a history of when it's done, a chore is nudged mid-morning. */
private const val CHORE_DEFAULT_MINUTE = 10 * 60
/** A late chore is nudged again in the evening, but never after this. */
private const val LATEST_NUDGE = 20 * 60

/**
 * Notifications for the family's chores that are due, today and tomorrow. A few at a time, not
 * the whole list: late ones first, the rest in an order that changes every day so it doesn't feel
 * like the same nag. Due today: one nudge at the time it's usually done. Late: a second one in the
 * evening, so it's nudged sooner and more often until it's done. Pure, so it's tested.
 */
fun choreNudges(
    tasks: List<TaskItem>,
    done: List<DoneTask>,
    me: String,
    today: LocalDate,
    nowMinute: Int,
    most: Int = 3,
): List<PlannedReminder> = listOf(today, today.plusDays(1)).flatMap { day ->
    val dueThen = tasks.filter { t -> t.chore && (t.assignee == null || t.assignee == me) && t.dueDate?.let { !it.isAfter(day) } == true }
    val picked = dueThen.sortedWith(
        compareByDescending<TaskItem> { ChronoUnit.DAYS.between(it.dueDate, day) }
            // A different few each day, the same all day long.
            .thenBy { (it.id.hashCode() xor day.toEpochDay().toInt()) and 0x7fffffff },
    ).take(most)
    picked.flatMap { t ->
        val late = ChronoUnit.DAYS.between(t.dueDate, day)
        val usual = usualMinute(done.filter { choreKey(it.title) == choreKey(t.title) }.map { it.minute }).takeIf { done.any { d -> choreKey(d.title) == choreKey(t.title) } } ?: CHORE_DEFAULT_MINUTE
        val every = repeatDays(t.repeat)?.let { if (it == 1) "every day" else if (it % 7 == 0) "every ${if (it == 7) "week" else "${it / 7} weeks"}" else "every $it days" } ?: t.repeat?.lowercase()
        // Late, or its usual time today has gone by undone: one more nudge in the evening.
        val missed = day == today && usual <= nowMinute
        val times = if (late >= 1 || missed) listOf(usual, minOf(maxOf(usual + 5 * 60, 18 * 60), LATEST_NUDGE)).distinct() else listOf(usual)
        times.mapIndexedNotNull { slot, minute ->
            if (day == today && minute <= nowMinute) return@mapIndexedNotNull null
            PlannedReminder(
                "chore:${t.id}:$day:$slot",
                when {
                    late >= 1 -> "${t.title}? (${if (late == 1L) "a day" else "$late days"} late)"
                    slot > 0 -> "${t.title}? (still to do today)"
                    else -> "${t.title}?"
                },
                listOfNotNull(every?.let { "A family chore, $it" }, "Tap Done when it's done, or Later.").joinToString(". "),
                day, minute,
            )
        }
    }
}
