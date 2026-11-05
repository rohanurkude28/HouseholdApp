package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.localDate
import com.houseaffairs.app.core.time.toEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MedicinesTest {
    private val nov5 = localDate(2026, 11, 5)
    private fun course(
        left: Double? = 10.0, times: List<Int> = listOf(9 * 60, 21 * 60), until: com.houseaffairs.app.core.time.LocalDate? = null,
        restock: Boolean = true, forWhom: List<String> = listOf("riya"),
    ) = MedicineCourse("m", "Crocin", forWhom, "asha", times, 1.0, "tablets", left, nov5, until, remindDoses = true, restock = restock)

    @Test
    fun usualTimesForEachFrequency() {
        assertEquals(listOf(540, 1260), doseTimes("Twice a day", emptyList()))
        assertEquals(listOf(480, 1200), doseTimes("Twice a day", listOf("8:00 pm", "8:00 am")))
        assertTrue(doseTimes("As needed", emptyList()).isEmpty())
        assertEquals(0.5, doseAmount("½"))
        assertEquals(7.5, doseAmount("7.5 ml"))
    }

    @Test
    fun runsOutFromThePace() {
        // 10 tablets, 2 a day: 5 days.
        assertEquals(localDate(2026, 11, 10), course().runsOutOn(nov5))
        // Three days on, 4 are left and it runs out on the same day.
        assertEquals(4.0, course().leftOn(localDate(2026, 11, 8)))
        assertNull(course(left = null).runsOutOn(nov5))
    }

    @Test
    fun refillOnlyWhenItRunsOutBeforeTheCourseEnds() {
        val day = localDate(2026, 11, 8)
        assertEquals(localDate(2026, 11, 10), course().needsRefill(day))
        assertNull(course().needsRefill(nov5), "five days off is too early")
        assertNull(course(until = localDate(2026, 11, 9)).needsRefill(day), "course ends first")
        assertNull(course(restock = false).needsRefill(day))
    }

    @Test
    fun dosesRingForWhoItsFor() {
        val r = doseReminders(listOf(course()), "riya", nov5, nowMinute = 10 * 60, days = 1) { "Riya" }
        // Today's 9 am has passed: tonight, then tomorrow twice.
        assertEquals(listOf(1260, 540, 1260), r.map { it.minute })
        assertEquals("💊 Crocin", r.first().title)
        assertTrue(doseReminders(listOf(course()), "asha", nov5, 600).isEmpty())
        // For nobody in particular: whoever added it.
        assertEquals(3, doseReminders(listOf(course(forWhom = emptyList())), "asha", nov5, 600, days = 1).size)
    }

    @Test
    fun skippedDaysDontCountOrRing() {
        val nov7 = localDate(2026, 11, 7)
        // Skipped the 5th: only the 6th's two doses are gone by the 7th.
        val c = course().copy(skipped = setOf(nov5.toEpochDay()))
        assertEquals(8.0, c.leftOn(nov7))
        // Skipping today: no reminders left today.
        val today = course().copy(skipped = setOf(nov5.toEpochDay()))
        assertTrue(doseReminders(listOf(today), "riya", nov5, nowMinute = 600, days = 0).isEmpty())
    }

    @Test
    fun theListLineSaysPaceLeftAndEnd() {
        assertEquals("2 tablets a day · ~10 left · runs out Tue 10 Nov", scheduleLine(course(), nov5))
        assertEquals("2 tablets a day · ~10 left · until 7 Nov", scheduleLine(course(until = localDate(2026, 11, 7)), nov5))
    }

    @Test
    fun aHeadsUpTwoDaysBeforeItRunsOut() {
        val day = localDate(2026, 11, 8)
        val r = refillReminders(listOf(course()), "asha", day, nowMinute = 7 * 60).single()
        assertEquals(day, r.date)
        assertEquals(MORNING_MINUTE, r.minute)
        assertTrue(refillReminders(listOf(course(restock = false)), "asha", day, 420).isEmpty())
    }
}

