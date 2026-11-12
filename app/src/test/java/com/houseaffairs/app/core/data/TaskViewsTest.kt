package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TaskViewsTest {
    private val today = LocalDate.of(2026, 11, 12)
    private fun t(id: String, due: LocalDate?) = TaskItem(id, id, due, null)
    private val tasks = listOf(t("late", today.minusDays(2)), t("now", today), t("tmrw", today.plusDays(1)), t("later", today.plusDays(9)), t("whenever", null))

    @Test
    fun `a day's tasks, with overdue only on today`() {
        assertEquals(listOf("late", "now"), tasksOn(tasks, today, today).map { it.id })
        assertEquals(listOf("tmrw"), tasksOn(tasks, today.plusDays(1), today).map { it.id })
    }

    @Test
    fun `today's progress reads plainly`() {
        assertEquals("2 things for today", dayProgress(0, tasks, today).line)
        assertEquals("3 of 5 done today", dayProgress(3, tasks, today).line)
        assertEquals("All done for today 🎉", dayProgress(2, emptyList(), today).line)
        assertEquals("Nothing due today", dayProgress(0, emptyList(), today).line)
        assertEquals(0.6f, dayProgress(3, tasks, today).share, 0.001f)
    }

    @Test
    fun `reschedule offers the usual days, minus the one it's on`() {
        // Thursday 12 Nov 2026.
        val choices = rescheduleChoices(today, current = today)
        assertEquals(listOf("Tomorrow", "This weekend", "Next week", "No date"), choices.map { it.first })
        assertEquals(LocalDate.of(2026, 11, 14), choices[1].second)
        assertEquals(LocalDate.of(2026, 11, 16), choices[2].second)
        // On a Sunday, "next week" (Monday) is also tomorrow: shown once. No date already: not offered.
        assertEquals(listOf("Today", "Tomorrow", "This weekend"), rescheduleChoices(LocalDate.of(2026, 11, 15), null).map { it.first })
    }
}
