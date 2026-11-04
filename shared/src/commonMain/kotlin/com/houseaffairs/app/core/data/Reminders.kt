package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.daysBetween
import com.houseaffairs.app.core.time.minusDays
import com.houseaffairs.app.core.time.toEpochDay

/** Morning, afternoon, evening and night: the quick choices for a task's time, as the form shows them. */
val TIME_CHOICES = listOf("9:00 am", "1:00 pm", "6:00 pm", "9:00 pm")

/** When reminders without a time go off: the morning of the day. */
const val MORNING_MINUTE = 9 * 60

private val CLOCK = Regex("^(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?$", RegexOption.IGNORE_CASE)

/** "5:30 pm", "17:30", "5pm", "noon" → minutes after midnight; null when it isn't a clock time. */
fun minuteOfDay(text: String?): Int? {
    val t = text?.trim()?.lowercase() ?: return null
    if (t == "noon" || t == "midday") return 12 * 60
    if (t == "midnight") return 0
    val m = CLOCK.matchEntire(t) ?: return null
    var hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
    val half = m.groupValues[3].firstOrNull()
    // A bare "5" is too vague; "17:30" and "5 pm" aren't.
    if (half == null && m.groupValues[2].isEmpty()) return null
    if (minute > 59) return null
    when (half) {
        'a' -> { if (hour !in 1..12) return null; if (hour == 12) hour = 0 }
        'p' -> { if (hour !in 1..12) return null; if (hour != 12) hour += 12 }
        else -> if (hour > 23) return null
    }
    return hour * 60 + minute
}

/** 1050 → "5:30 pm". */
fun formatClock(minute: Int): String {
    val h = minute / 60 % 24
    val m = minute % 60
    val half = if (h < 12) "am" else "pm"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:${m.toString().padStart(2, '0')} $half"
}

/** One notification to show at [minute] on [date]. [key] stays the same while the item does, so it can be replaced or cancelled. */
data class PlannedReminder(
    val key: String,
    val title: String,
    val body: String,
    val date: LocalDate,
    val minute: Int,
    val reach: String? = null,
    val phone: String? = null,
)

/**
 * The reminders this phone should show over the next [days]: tasks for [me] (or nobody in
 * particular) and family events. A task with a time goes off then, plus a heads-up on the
 * chosen days before; anything else with a "Remind" choice goes off in the morning. Ones whose
 * moment has passed are left out, so syncing again never repeats one.
 */
fun plannedReminders(
    tasks: List<TaskItem>,
    events: List<EventItem>,
    me: String,
    today: LocalDate,
    nowMinute: Int,
    days: Int = 14,
): List<PlannedReminder> {
    val out = mutableListOf<PlannedReminder>()
    fun add(key: String, title: String, body: String, date: LocalDate, minute: Int, reach: String? = null, phone: String? = null) {
        val ahead = daysBetween(today, date)
        if (ahead < 0 || ahead > days || (ahead == 0L && minute <= nowMinute)) return
        out += PlannedReminder(key, title, body, date, minute, reach, phone)
    }
    tasks.filter { it.assignee == null || it.assignee == me }.forEach { task ->
        val due = task.dueDate ?: return@forEach
        val remind = task.remindDays
        if (remind == NO_REMINDER) return@forEach
        val at = minuteOfDay(task.time)
        if (at != null) {
            add("task:${task.id}:0", task.title, task.reach?.let { "Tap to ${it.lowercase()}" } ?: "Now · ${formatClock(at)}", due, at, task.reach, task.phone)
            if (remind != null && remind > 0) add("task:${task.id}:$remind", task.title, "${dueIn(remind)} at ${formatClock(at)}", due.minusDays(remind.toLong()), at)
        } else if (remind != null) {
            add("task:${task.id}:$remind", task.title, dueIn(remind), due.minusDays(remind.toLong()), MORNING_MINUTE, task.reach, task.phone)
        }
    }
    events.forEach { event ->
        val remind = event.remindDays ?: return@forEach
        if (remind == NO_REMINDER) return@forEach
        add("event:${event.id}:$remind", event.title, "${event.kind} ${dueIn(remind).lowercase().removePrefix("due ")}", event.next.minusDays(remind.toLong()), MORNING_MINUTE)
    }
    return out.sortedWith(compareBy({ it.date.toEpochDay() }, { it.minute }))
}

private fun dueIn(days: Int) = when (days) {
    0 -> "Due today"
    1 -> "Due tomorrow"
    else -> "Due in $days days"
}
