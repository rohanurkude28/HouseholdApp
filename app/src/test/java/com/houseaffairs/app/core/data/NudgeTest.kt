package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NudgeTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `suggests something the week is light on`() {
        val wheel = liveWell(WeekData(meals = List(7) { CookedMeal("Dal", 3, true) }, checkIn = CheckIn(2, 2, 2)))
        val nudge = nudgeFor(wheel, null, monday)!!
        // Nothing logged for moving, so that's where the suggestion comes from.
        assertEquals(Pillar.MOVE, nudge.pillar)
    }

    @Test
    fun `uses the person's own plan when they have one`() {
        val wheel = liveWell(WeekData(meals = List(7) { CookedMeal("Dal", 3, true) }, checkIn = CheckIn(2, 2, 2)))
        val plan = WellPlan(commitments = mapOf(Pillar.MOVE to listOf("Walk after dinner")))
        val nudge = nudgeFor(wheel, plan, monday)!!
        assertTrue(nudge.suggestion.contains("walk after dinner"))
    }

    @Test
    fun `quiet when the week went well`() {
        val week = WeekData(
            meals = List(7) { CookedMeal("Dal", 3, true) },
            activities = listOf(ActivityLog("Walk", 160, 2), ActivityLog("Gardening", 60, 1), ActivityLog("Yoga", 90, 2), ActivityLog("Walk", 10, 3)),
            diningSpentMinor = 0, diningAllowanceMinor = 100_000,
            tasksDone = 3, togetherEvents = 2, checkIn = CheckIn(2, 2, 2),
        )
        assertNull(nudgeFor(liveWell(week), null, monday))
    }

    @Test
    fun `plan targets change the score`() {
        val week = WeekData(activities = listOf(ActivityLog("Walk", 60, 1)))
        val easy = liveWell(week, WellPlan(walkMinutes = 60)).habits.first { it.name == "Walk" }.score
        assertEquals(1f, easy)
    }

    @Test
    fun `dream horizons follow the date unless chosen`() {
        val today = LocalDate.of(2026, 11, 3)
        assertEquals(Horizon.SHORT, horizonOf(null, today.plusMonths(6), today))
        assertEquals(Horizon.MEDIUM, horizonOf(null, today.plusYears(2), today))
        assertEquals(Horizon.LONG, horizonOf(null, today.plusYears(5), today))
        assertEquals(Horizon.SOMEDAY, horizonOf(null, null, today))
        assertEquals(Horizon.LONG, horizonOf("Long term", today.plusMonths(2), today))
    }
}
