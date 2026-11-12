package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWellTest {

    @Test
    fun `an empty week scores what it can and asks for the rest`() {
        val wheel = liveWell(WeekData())
        assertEquals(0, wheel.overall)
        assertNull(wheel.habits.first { it.name == "Plant-based" }.score)
        assertNull(wheel.habits.first { it.name == "Unwind" }.score)
    }

    @Test
    fun `a good week fills the wheel`() {
        val w = WeekData(
            meals = List(7) { CookedMeal("Dal", people = 3, plantBased = it < 6) },
            activities = listOf(ActivityLog("Walk", 160, 2), ActivityLog("Gardening", 60, 1), ActivityLog("Yoga", 90, 2)),
            diningSpentMinor = 500_00, diningAllowanceMinor = 1_000_00,
            tasksDone = 4, tasksOverdue = 0, togetherEvents = 1,
            checkIn = CheckIn(unwind = 2, social = 2, gratitude = 1),
        )
        val wheel = liveWell(w)
        assertEquals(1f, wheel.pillarScore(Pillar.MOVE))
        assertEquals(6f / 7, wheel.habits.first { it.name == "Plant-based" }.score!!, 0.001f)
        assertEquals("Gratitude", wheel.focus?.name)
        assertTrue(wheel.overall >= 90)
    }

    @Test
    fun `eating out over budget lowers moderation`() {
        val h = liveWell(WeekData(diningSpentMinor = 1_500_00, diningAllowanceMinor = 1_000_00)).habits.first { it.name == "Moderation" }
        assertEquals(0.5f, h.score!!, 0.001f)
    }

    @Test
    fun `meat and eggs aren't plant-based`() {
        assertFalse(isPlantBased(listOf(Ingredient("Eggs", 2.0, "pcs"))))
        assertTrue(isPlantBased(listOf(Ingredient("Paneer", 200.0, "g"), Ingredient("Peas", 100.0, "g"))))
    }

    @Test
    fun `keep learning counts only once someone studies`() {
        assertNull(liveWell(WeekData()).habits.first { it.name == "Keep learning" }.score)
        val studied = liveWell(WeekData(activities = listOf(ActivityLog("Study", 30, 1))))
        assertEquals(1f, studied.habits.first { it.name == "Keep learning" }.score)
        // Studying isn't a workout.
        assertEquals(0f, studied.habits.first { it.name == "Workout" }.score)
        val target = liveWell(WeekData(activities = listOf(ActivityLog("Study", 60, 1))), WellPlan(studyMinutes = 120))
        assertEquals(0.5f, target.habits.first { it.name == "Keep learning" }.score!!, 0.001f)
        assertEquals(1f, liveWell(WeekData(learned = 1), WellPlan(studyMinutes = 120)).habits.first { it.name == "Keep learning" }.score)
    }

    @Test
    fun `steps and sleep from Health Connect join the wheel`() {
        val wheel = liveWell(WeekData(steps = 35_000, sleepHours = 6.0, days = 7))
        val steps = wheel.habits.first { it.name == "Steps" }
        assertEquals(5_000f / 7_000f, steps.score!!, 0.001f)
        assertEquals(0.75f, wheel.habits.first { it.name == "Sleep" }.score!!, 0.001f)
        // 35,000 steps is about 350 minutes of walking.
        assertEquals(1f, wheel.habits.first { it.name == "Walk" }.score!!, 0.001f)
        // Not connected: neither habit shows.
        assertTrue(liveWell(WeekData()).habits.none { it.name == "Steps" || it.name == "Sleep" })
    }

    @Test
    fun `sleep scores full from 7 to 9 hours`() {
        assertEquals(1f, sleepScore(8.0), 0.001f)
        assertEquals(0.5f, sleepScore(5.0), 0.001f)
        assertEquals(0.75f, sleepScore(10.0), 0.001f)
    }

    @Test
    fun `daily check-ins score each pillar and come before the weekly one`() {
        val checks = listOf(DayCheck(2, 1, 2, 0), DayCheck(2, 1, 0, 2))
        val wheel = liveWell(WeekData(days = 2, dayChecks = checks, checkIn = CheckIn(unwind = 0, social = 0, gratitude = 0)))
        fun habit(name: String) = wheel.habits.first { it.name == name }
        assertEquals(1f, habit("Moved each day").score!!, 0.001f)
        assertEquals(0.5f, habit("Ate well").score!!, 0.001f)
        assertEquals(0.5f, habit("Right tribe").score!!, 0.001f)
        assertEquals(0.5f, habit("Unwind").score!!, 0.001f)
        assertEquals("Yes on 2 of 2 days you checked in", habit("Moved each day").detail)
    }

    @Test
    fun `no check-ins means no check-in habits`() {
        val wheel = liveWell(WeekData())
        assertNull(wheel.habits.firstOrNull { it.name == "Moved each day" })
    }
}
