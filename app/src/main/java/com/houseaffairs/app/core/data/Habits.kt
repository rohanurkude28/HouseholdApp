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
