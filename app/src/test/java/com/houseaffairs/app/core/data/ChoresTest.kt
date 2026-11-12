package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChoresTest {
    private val today = LocalDate.of(2026, 11, 12)
    private fun chore(id: String, title: String, due: LocalDate, repeat: String = "Every 3 days", assignee: String? = null) =
        TaskItem(id, title, due, assignee, repeat = repeat, chore = true)

    @Test
    fun `a chore due today is nudged once, at the usual time`() {
        val done = listOf(DoneTask("Do laundry", today.minusDays(3), 19 * 60), DoneTask("Do laundry", today.minusDays(6), 19 * 60))
        val n = choreNudges(listOf(chore("c1", "Do laundry", today)), done, "me", today, nowMinute = 9 * 60).filter { it.date == today }
        assertEquals(1, n.size)
        assertEquals(19 * 60, n.single().minute)
        assertEquals("Do laundry?", n.single().title)
        assertEquals("A family chore, every 3 days. Tap Done when it's done, or Later.", n.single().body)
    }

    @Test
    fun `a late chore is nudged sooner and more often`() {
        val n = choreNudges(listOf(chore("c1", "Change bedsheets", today.minusDays(2), "Weekly")), emptyList(), "me", today, nowMinute = 8 * 60).filter { it.date == today }
        assertEquals(listOf(10 * 60, 18 * 60), n.map { it.minute })
        assertEquals("Change bedsheets? (2 days late)", n.first().title)
    }

    @Test
    fun `only a few at a time, late ones first, and someone else's are left alone`() {
        val tasks = listOf(
            chore("a", "Sweep", today), chore("b", "Dust", today), chore("c", "Dishes", today),
            chore("d", "Fridge", today.minusDays(4)), chore("e", "Car", today, assignee = "ravi"),
        )
        val ids = choreNudges(tasks, emptyList(), "me", today, nowMinute = 0, most = 3).filter { it.date == today }.map { it.key.split(":")[1] }.distinct()
        assertEquals(3, ids.size)
        assertEquals("d", ids.first())
        assertTrue("e" !in ids)
    }

    @Test
    fun `a time already gone today moves to the evening, then to tomorrow`() {
        val n = choreNudges(listOf(chore("c1", "Water the plants", today, "Daily")), emptyList(), "me", today, nowMinute = 11 * 60)
        val evening = n.single { it.date == today }
        assertEquals(18 * 60, evening.minute)
        assertEquals("Water the plants? (still to do today)", evening.title)
        val late = choreNudges(listOf(chore("c1", "Water the plants", today, "Daily")), emptyList(), "me", today, nowMinute = 21 * 60)
        assertTrue(late.none { it.date == today })
        // Not done today, it's a day late tomorrow: nudged morning and evening.
        assertEquals(2, n.count { it.date == today.plusDays(1) })
    }
}
