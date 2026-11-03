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
}
