package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.localDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemindersTest {
    // Thursday 5 Nov 2026, 10:00 am
    private val today = localDate(2026, 11, 5)
    private val now = 10 * 60

    @Test
    fun `clock times are read in any common form`() {
        assertEquals(17 * 60 + 30, minuteOfDay("5:30 pm"))
        assertEquals(17 * 60 + 30, minuteOfDay("17:30"))
        assertEquals(17 * 60, minuteOfDay("5pm"))
        assertEquals(0, minuteOfDay("12 am"))
        assertEquals(12 * 60, minuteOfDay("noon"))
        assertNull(minuteOfDay("5"))
        assertNull(minuteOfDay("25:00"))
        assertNull(minuteOfDay("Evening"))
        assertEquals("5:30 pm", formatClock(17 * 60 + 30))
        assertEquals("12:00 am", formatClock(0))
    }

    @Test
    fun `a call with a time reminds at that time`() {
        val call = TaskItem("t1", "Call mom", today, "me", reach = "Call", phone = "98765", time = "5:30 pm")
        val r = plannedReminders(listOf(call), emptyList(), "me", today, now).single()
        assertEquals(17 * 60 + 30, r.minute)
        assertEquals(today, r.date)
        assertEquals("Call", r.reach)
        assertEquals("98765", r.phone)
    }

    @Test
    fun `a time already gone today is skipped`() {
        val past = TaskItem("t1", "Call mom", today, null, time = "9:00 am")
        assertTrue(plannedReminders(listOf(past), emptyList(), "me", today, now).isEmpty())
    }

    @Test
    fun `remind days before gives a heads-up too`() {
        val task = TaskItem("t1", "Pay rent", localDate(2026, 11, 8), "me", remindDays = 1, time = "6:00 pm")
        val rs = plannedReminders(listOf(task), emptyList(), "me", today, now)
        assertEquals(listOf(localDate(2026, 11, 7), localDate(2026, 11, 8)), rs.map { it.date })
        assertEquals("Due tomorrow at 6:00 pm", rs.first().body)
    }

    @Test
    fun `without a time only a remind choice reminds - in the morning`() {
        val plain = TaskItem("t1", "Water plants", localDate(2026, 11, 6), "me")
        val asked = TaskItem("t2", "Renew passport", localDate(2026, 11, 6), "me", remindDays = 0)
        val r = plannedReminders(listOf(plain, asked), emptyList(), "me", today, now).single()
        assertEquals("Renew passport", r.title)
        assertEquals(MORNING_MINUTE, r.minute)
    }

    @Test
    fun `other people's tasks and switched off reminders stay quiet`() {
        val theirs = TaskItem("t1", "Call plumber", today, "ravi", time = "6:00 pm")
        val off = TaskItem("t2", "Call bank", today, "me", remindDays = NO_REMINDER, time = "6:00 pm")
        assertTrue(plannedReminders(listOf(theirs, off), emptyList(), "me", today, now).isEmpty())
    }

    @Test
    fun `events remind on the chosen day`() {
        val bday = EventItem("e1", "Riya's birthday", "Birthday", localDate(2026, 11, 7), remindDays = 1)
        val r = plannedReminders(emptyList(), listOf(bday), "me", today, now).single()
        assertEquals(localDate(2026, 11, 6), r.date)
        assertEquals("Birthday tomorrow", r.body)
    }
}
