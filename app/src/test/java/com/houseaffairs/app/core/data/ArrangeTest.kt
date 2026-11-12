package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ArrangeTest {
    // Wednesday 11 Nov 2026
    private val today = LocalDate.of(2026, 11, 11)
    private fun task(id: String, due: LocalDate?, priority: String = "Low") = TaskItem(id, id, due, null, priority = priority)

    @Test
    fun `tasks fall into buckets by when they're due`() {
        val buckets = taskBuckets(
            listOf(
                task("later", today.plusDays(9)), task("late", today.minusDays(2)), task("now", today, "Low"), task("urgent", today, "High"),
                task("tmrw", today.plusDays(1)), task("sun", today.plusDays(4)), task("someday", null), task("asked", today.plusDays(9)),
            ),
            today, waiting = setOf("asked"),
        )
        assertEquals(
            listOf(TaskBucket.WAITING, TaskBucket.OVERDUE, TaskBucket.TODAY, TaskBucket.TOMORROW, TaskBucket.THIS_WEEK, TaskBucket.LATER, TaskBucket.NO_DATE),
            buckets.map { it.first },
        )
        assertEquals(listOf("urgent", "now"), buckets.first { it.first == TaskBucket.TODAY }.second.map { it.id })
    }

    @Test
    fun `recipes group by meal in day order`() {
        val r = listOf("Kheer" to "Dessert", "Poha" to "Breakfast", "Dal" to "Lunch or dinner", "Rajma" to "Dinner", "Chaat" to null, "Masala chai" to null, "Lassi" to "Drink")
        val groups = byMeal(r, { it.second }, { it.first })
        assertEquals(listOf("Breakfast", "Lunch", "Dinner", "Desserts", "Drinks", "Other"), groups.map { it.first })
        // Lunch or dinner is on both menus.
        assertEquals(listOf("Dal"), groups[1].second.map { it.first })
        assertEquals(listOf("Dal", "Rajma"), groups[2].second.map { it.first })
        assertEquals(listOf("Lassi", "Masala chai"), groups[4].second.map { it.first })
    }

    @Test
    fun `the meal that's coming up leads the menu`() {
        val r = listOf("Poha" to "Breakfast", "Rajma" to "Dinner", "Samosa" to "Snack")
        assertEquals(listOf("Dinner", "Breakfast", "Snacks"), byMeal(r, { it.second }, { it.first }, hour = 19).map { it.first })
        assertEquals(listOf("Snacks", "Breakfast", "Dinner"), byMeal(r, { it.second }, { it.first }, hour = 16).map { it.first })
        assertEquals(listOf("Breakfast", "Dinner", "Snacks"), byMeal(r, { it.second }, { it.first }, hour = 23).map { it.first })
    }
}
