package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek


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
        compareByDescending<TaskItem> { daysBetween(it.dueDate!!, day) }
            // A different few each day, the same all day long.
            .thenBy { (it.id.hashCode() xor day.toEpochDay().toInt()) and 0x7fffffff },
    ).take(most)
    picked.flatMap { t ->
        val late = daysBetween(t.dueDate!!, day)
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
