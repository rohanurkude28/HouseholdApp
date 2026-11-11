package com.houseaffairs.app.core.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Groups for a long task list, most pressing first. */
enum class TaskBucket(val label: String) {
    WAITING("Waiting on you"), OVERDUE("Overdue"), TODAY("Today"), TOMORROW("Tomorrow"),
    THIS_WEEK("This week"), LATER("Later"), NO_DATE("No date"),
}

/**
 * Tasks into [TaskBucket]s: ones waiting on an answer first, then by when they're due. Inside a
 * bucket, the earliest and highest priority first. Empty buckets are left out. Pure, so it's tested.
 */
fun taskBuckets(tasks: List<TaskItem>, today: LocalDate, waiting: Set<String> = emptySet()): List<Pair<TaskBucket, List<TaskItem>>> {
    val weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    fun bucket(t: TaskItem): TaskBucket {
        if (t.id in waiting) return TaskBucket.WAITING
        val due = t.dueDate ?: return TaskBucket.NO_DATE
        return when {
            due.isBefore(today) -> TaskBucket.OVERDUE
            due == today -> TaskBucket.TODAY
            due == today.plusDays(1) -> TaskBucket.TOMORROW
            !due.isAfter(weekEnd) -> TaskBucket.THIS_WEEK
            else -> TaskBucket.LATER
        }
    }
    val rank = mapOf("High" to 0, "Medium" to 1, "Low" to 2)
    return tasks.groupBy(::bucket).toList().sortedBy { it.first.ordinal }
        .map { (b, list) -> b to list.sortedWith(compareBy<TaskItem>({ it.dueDate?.toEpochDay() ?: Long.MAX_VALUE }, { rank[it.priority] ?: 2 }, { it.title.lowercase() })) }
}

/** Meal groups for the recipe menu, in the order a day goes. */
val RECIPE_MEALS = listOf("Breakfast", "Lunch", "Dinner", "Snacks", "Desserts", "Drinks", "Other")

/** How many dishes each meal shows before "See all", while the whole menu is on screen. */
const val MEAL_PEEK = 4

private val DRINK_WORDS = setOf("chai", "tea", "coffee", "juice", "lassi", "smoothie", "shake", "milkshake", "lemonade", "nimbu", "sharbat", "mojito", "soda", "cooler", "kadha", "buttermilk", "chaas")

/**
 * The menu sections a recipe belongs in. "Lunch or dinner" sits in both; with no meal set, a
 * drink is told by its name ("Masala chai"), and anything else goes under Other.
 */
fun mealGroupsOf(meal: String?, title: String = ""): List<String> = when (meal) {
    "Breakfast" -> listOf("Breakfast")
    "Lunch" -> listOf("Lunch")
    "Dinner" -> listOf("Dinner")
    "Lunch or dinner" -> listOf("Lunch", "Dinner")
    "Snack" -> listOf("Snacks")
    "Dessert" -> listOf("Desserts")
    "Drink" -> listOf("Drinks")
    else -> if (title.lowercase().split(Regex("[^\\p{L}]+")).any { it in DRINK_WORDS }) listOf("Drinks") else listOf("Other")
}

/** The meal that's next at this [hour], so the menu opens on what's about to be cooked. */
fun mealAt(hour: Int): String? = when (hour) {
    in 5..10 -> "Breakfast"
    in 11..14 -> "Lunch"
    in 15..17 -> "Snacks"
    in 18..21 -> "Dinner"
    else -> null
}

fun mealEmoji(group: String): String = when (group) {
    "Breakfast" -> "🍳"
    "Lunch" -> "🍛"
    "Dinner" -> "🌙"
    "Snacks" -> "🥨"
    "Desserts" -> "🍰"
    "Drinks" -> "🥤"
    else -> "🍽️"
}

/**
 * Recipes as a menu: by meal, each A–Z, empty meals left out. With [hour], the meal that's
 * coming up leads. Pure, so it's tested.
 */
fun <T> byMeal(items: List<T>, meal: (T) -> String?, title: (T) -> String, hour: Int? = null): List<Pair<String, List<T>>> {
    val now = hour?.let(::mealAt)
    return items.flatMap { item -> mealGroupsOf(meal(item), title(item)).map { it to item } }
        .groupBy({ it.first }, { it.second }).toList()
        .sortedBy { if (it.first == now) -1 else RECIPE_MEALS.indexOf(it.first) }
        .map { (g, l) -> g to l.sortedBy { title(it).lowercase() } }
}
