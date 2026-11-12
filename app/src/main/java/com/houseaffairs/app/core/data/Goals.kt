package com.houseaffairs.app.core.data

import java.time.LocalDate

/** A weekly goal: something to learn or get done, with how far along it is. */
data class Goal(
    val id: String,
    val title: String,
    val kind: String?,
    val who: List<String>,
    val progress: Int,
    val by: LocalDate?,
    val created: LocalDate?,
    /** When it reached 100%, for the month and year views. */
    val doneOn: LocalDate? = null,
) {
    val done: Boolean get() = progress >= 100
}

/** This week's goals: set since Monday, or due this week. Pure, so it's tested. */
fun thisWeeksGoals(goals: List<Goal>, today: LocalDate): List<Goal> {
    val monday = mondayOf(today)
    val sunday = monday.plusDays(6)
    return goals.filter { g -> (g.created != null && !g.created.isBefore(monday)) || (g.by != null && !g.by.isBefore(monday) && !g.by.isAfter(sunday)) }
}

/** The Sunday recap line: "2 of 3 goals done this week", or null when there were none. */
fun goalsRecap(goals: List<Goal>, today: LocalDate): String? {
    val week = thisWeeksGoals(goals, today).takeIf { it.isNotEmpty() } ?: return null
    val done = week.count { it.done }
    val closest = week.filter { !it.done }.maxByOrNull { it.progress }
    return "$done of ${week.size} goal${if (week.size > 1) "s" else ""} done this week" + (closest?.let { " · ${it.title} at ${it.progress}%" } ?: "")
}

/** A goal's progress in words people use, instead of a percentage to drag or add to. */
val GOAL_STEPS: List<Pair<Int, String>> = listOf(0 to "Not started", 25 to "Started", 50 to "Halfway", 75 to "Almost there", 100 to "Done")

/** The step a saved progress is closest to ("Halfway" for 50, and for an old 60 too). */
fun goalStep(progress: Int): String = GOAL_STEPS.minBy { kotlin.math.abs(it.first - progress) }.second
