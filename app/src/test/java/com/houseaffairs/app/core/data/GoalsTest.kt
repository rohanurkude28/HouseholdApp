package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalsTest {

    private val sunday = LocalDate.of(2026, 11, 8)

    @Test
    fun `the week's goals and the Sunday recap`() {
        val goals = listOf(
            Goal("1", "Finish a book", "Learning", emptyList(), 80, null, LocalDate.of(2026, 11, 3)),
            Goal("2", "3 workouts", "Health", emptyList(), 100, null, LocalDate.of(2026, 11, 2)),
            Goal("3", "Old goal", null, emptyList(), 10, null, LocalDate.of(2026, 10, 20)),
            Goal("4", "File taxes", "To get done", emptyList(), 0, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 10, 25)),
        )
        assertEquals(listOf("1", "2", "4"), thisWeeksGoals(goals, sunday).map { it.id })
        assertEquals("1 of 3 goals done this week · Finish a book at 80%", goalsRecap(goals, sunday))
        assertNull(goalsRecap(emptyList(), sunday))
    }
}
