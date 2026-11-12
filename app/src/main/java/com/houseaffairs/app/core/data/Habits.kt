package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Things a family does again and again without ever setting a repeat ("do laundry"), noticed
 * from the tasks they've ticked off, so Home can ask before it's forgotten. Pure, so it's tested.
 */

/** [title] is usually done every [every] days and was last done [daysAgo] days ago. */
data class UsualTask(val title: String, val every: Int, val daysAgo: Int) {
    val why: String get() = "You usually do this every $every days · last done ${if (daysAgo == 1) "yesterday" else "$daysAgo days ago"}"
}

/** Same chore however it was typed: "Do laundry" and "do the laundry ". */
fun choreKey(title: String): String = title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() && it !in setOf("the", "a", "an", "please") }.joinToString(" ")

/**
 * Chores that are due by their own rhythm: done three or more times, the usual gap has passed,
 * and nobody has it as an open task. One that's been left for three gaps was dropped, not forgotten.
 */
fun usualTasks(done: List<Purchase>, open: List<String>, today: LocalDate, most: Int = 2): List<UsualTask> {
    val waiting = open.map(::choreKey).toSet()
    return done.filter { choreKey(it.title).isNotEmpty() }.groupBy { choreKey(it.title) }.mapNotNull { (key, times) ->
        if (key in waiting) return@mapNotNull null
        val every = boughtEvery(times.map { it.date }) ?: return@mapNotNull null
        val last = times.maxBy { it.date }
        val daysAgo = ChronoUnit.DAYS.between(last.date, today).toInt()
        UsualTask(last.title, every, daysAgo).takeIf { daysAgo >= every && daysAgo <= every * 3 }
    }
        // The most overdue for its own rhythm first.
        .sortedByDescending { it.daysAgo.toDouble() / it.every }.take(most)
}

/**
 * "Last time you loved it": a restaurant or café the family rated ❤️, for when a meal is coming
 * up. One that's good for this [meal] first; otherwise a different one each day.
 */
fun likedBefore(places: List<PlaceItem>, meal: String?, today: LocalDate): Idea? {
    val loved = places.filter { (it.kind == "Restaurant" || it.kind == "Café") && it.rating?.contains("Love") == true }.sortedBy { it.title }
    if (loved.isEmpty()) return null
    val pick = loved.firstOrNull { meal != null && it.goodFor?.contains(meal, ignoreCase = true) == true }
        ?: loved[(today.toEpochDay() % loved.size).toInt()]
    return Idea(pick.title, listOfNotNull("You loved it last time", pick.goodFor?.let { "for $it" }, pick.area).joinToString(" · "))
}

/** Without a history of times, chore reminders go off mid-morning. */
const val HABIT_DEFAULT_MINUTE = 10 * 60

/** "Later today" on a chore reminder rings again this many minutes on. */
const val SNOOZE_MINUTES = 120

/**
 * The time of day someone usually does a chore: the middle of the times it was ticked off,
 * rounded to the quarter hour, so the reminder comes when they'd do it anyway.
 */
fun usualMinute(minutes: List<Int>): Int {
    if (minutes.isEmpty()) return HABIT_DEFAULT_MINUTE
    val sorted = minutes.sorted()
    val middle = sorted[sorted.size / 2]
    return (middle + 7) / 15 * 15 % (24 * 60)
}

/**
 * Notifications for chores due by the family's own rhythm (see [usualTasks]): on the day it's
 * due, at the time it's usually done. [putOff] holds chores someone moved to a later day
 * ("Tomorrow" on the notification). Today's ones whose time has passed are left to Home.
 */
fun habitReminders(
    done: List<DoneTask>,
    open: List<String>,
    today: LocalDate,
    nowMinute: Int,
    putOff: Map<String, LocalDate> = emptyMap(),
    days: Int = 7,
): List<PlannedReminder> {
    val waiting = open.map(::choreKey).toSet()
    return done.filter { choreKey(it.title).isNotEmpty() }.groupBy { choreKey(it.title) }.mapNotNull { (key, times) ->
        if (key in waiting) return@mapNotNull null
        val every = boughtEvery(times.map { it.date }) ?: return@mapNotNull null
        val last = times.maxBy { it.date }
        // Left for three gaps: dropped, not forgotten.
        if (ChronoUnit.DAYS.between(last.date, today) > every * 3) return@mapNotNull null
        val due = listOfNotNull(last.date.plusDays(every.toLong()), today, putOff[key]).max()
        val minute = usualMinute(times.map { it.minute })
        val ahead = ChronoUnit.DAYS.between(today, due)
        if (ahead > days || (ahead == 0L && minute <= nowMinute)) return@mapNotNull null
        PlannedReminder(
            "habit:$key:$due", "${last.title}?",
            "You usually do this around ${formatClock(minute)}, every $every days.", due, minute,
        )
    }
}
