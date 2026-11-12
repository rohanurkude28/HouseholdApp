package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitRemindersTest {
    private val today = LocalDate.of(2026, 11, 12)
    // Laundry every 4 days, around 7 pm; the last time was 3 days ago, so it's due tomorrow.
    private val laundry = listOf(15, 11, 7, 3).map { DoneTask("Do laundry", today.minusDays(it.toLong()), 19 * 60 + it % 3) }

    @Test
    fun `the usual time is the middle of when it was done, to the quarter hour`() {
        assertEquals(19 * 60, usualMinute(listOf(19 * 60 + 3, 19 * 60 + 7, 18 * 60 + 50)))
        assertEquals(HABIT_DEFAULT_MINUTE, usualMinute(emptyList()))
    }

    @Test
    fun `a chore rings on the day it's due at the time it's usually done`() {
        val r = habitReminders(laundry, open = emptyList(), today, nowMinute = 9 * 60).single()
        assertEquals(today.plusDays(1), r.date)
        assertEquals(19 * 60, r.minute)
        assertEquals("Do laundry?", r.title)
        assertEquals("You usually do this around 7:00 pm, every 4 days.", r.body)
    }

    @Test
    fun `an open task for it or a dropped chore stays quiet`() {
        assertTrue(habitReminders(laundry, open = listOf("do the laundry"), today, 9 * 60).isEmpty())
        assertTrue(habitReminders(laundry, emptyList(), today.plusDays(20), 9 * 60).isEmpty())
    }

    @Test
    fun `put off till tomorrow moves it`() {
        val overdue = habitReminders(laundry, emptyList(), today.plusDays(2), nowMinute = 9 * 60).single()
        assertEquals(today.plusDays(2), overdue.date)
        val moved = habitReminders(laundry, emptyList(), today.plusDays(2), 9 * 60, putOff = mapOf("do laundry" to today.plusDays(3))).single()
        assertEquals(today.plusDays(3), moved.date)
    }

    @Test
    fun `today's time already gone is left to Home`() {
        assertTrue(habitReminders(laundry, emptyList(), today.plusDays(1), nowMinute = 20 * 60).isEmpty())
    }
}
