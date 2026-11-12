package com.houseaffairs.app.core.data

import java.time.LocalDate

/** What's on [day]: due that day, plus anything overdue when [day] is today. */
fun tasksOn(tasks: List<TaskItem>, day: LocalDate, today: LocalDate): List<TaskItem> = tasks.filter { t ->
    val due = t.dueDate ?: return@filter false
    due == day || (day == today && due.isBefore(today))
}

/** Today's header: how much of today is done, in a ring and a line. */
data class DayProgress(val done: Int, val total: Int) {
    val share: Float get() = if (total == 0) 0f else done.toFloat() / total
    val line: String get() = when {
        total == 0 -> "Nothing due today"
        done == total -> "All done for today 🎉"
        done == 0 -> if (total == 1) "1 thing for today" else "$total things for today"
        else -> "$done of $total done today"
    }
}

/** [doneToday] is what was ticked off today; [open] is everything still open. */
fun dayProgress(doneToday: Int, open: List<TaskItem>, today: LocalDate): DayProgress =
    DayProgress(doneToday, doneToday + tasksOn(open, today, today).size)

/**
 * The quick choices behind a task's date: today, tomorrow, the weekend (Saturday, or next week's
 * on a weekend), next Monday, or no date. Ones equal to the task's own date are left out.
 */
fun rescheduleChoices(today: LocalDate, current: LocalDate?): List<Pair<String, LocalDate?>> {
    val saturday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY))
    val monday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
    return listOf<Pair<String, LocalDate?>>(
        "Today" to today, "Tomorrow" to today.plusDays(1), "This weekend" to saturday, "Next week" to monday, "No date" to null,
    ).distinctBy { it.second }.filter { it.second != current }
}
