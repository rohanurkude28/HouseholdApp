package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.Habit
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.Pillar
import com.houseaffairs.app.core.data.WellPlan
import com.houseaffairs.app.core.data.Wheel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SmartPicksTest {

    private val evening = LocalDateTime.of(2026, 11, 4, 19, 30)

    @Test
    fun `cook summary names the meal, what's expiring and what was just cooked`() {
        val pantry = listOf(
            PantryStock("p", "Paneer", 200.0, "g", 200.0, expiry = LocalDate.of(2026, 11, 5)),
            PantryStock("r", "Rice", 2.0, "kg", 5.0, expiry = LocalDate.of(2027, 1, 1)),
        )
        val inputs = pickInputs(evening, listOf("Paneer sabzi"), pantry, mapOf("Dal tadka" to LocalDate.of(2026, 11, 3)), listOf("Panchayat"))
        assertEquals("It's Wednesday, time for dinner. About to expire: Paneer. Already cooked this week: Dal tadka.", inputs.cookAbout)
        assertTrue("weeknight" in inputs.watchAbout)
    }

    @Test
    fun `same hour and lists give the same inputs, so Home doesn't ask again`() {
        val a = pickInputs(evening, listOf("Poha"), emptyList(), emptyMap(), emptyList())
        val b = pickInputs(evening.plusMinutes(20), listOf("Poha"), emptyList(), emptyMap(), emptyList())
        assertEquals(a, b)
    }

    @Test
    fun `coach summary has scores and the plan, nothing else`() {
        val wheel = Wheel(
            listOf(
                Habit(Pillar.MOVE, "Walking", 0.3f, "45 of 150 min", "tip"),
                Habit(Pillar.EAT, "Home-cooked", null, "no meals logged", "tip"),
            ),
        )
        val summary = coachSummary(wheel, WellPlan(commitments = mapOf(Pillar.MOVE to listOf("Walk after dinner"))))
        assertEquals("- Move naturally / Walking: 30 (45 of 150 min)\nTheir plan: Move naturally: Walk after dinner", summary)
        assertFalse("Home-cooked" in summary)
    }
}
