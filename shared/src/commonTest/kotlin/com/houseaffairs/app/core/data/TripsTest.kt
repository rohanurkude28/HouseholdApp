package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class TripsTest {

    private val sunday = localDate(2026, 11, 8)
    private fun t(cat: String, sub: String?, amount: Long, trip: String? = "Sunday outing") =
        Txn("$cat$sub$amount", cat, cat, amount, sunday, planned = false, subcategory = sub, trip = trip)

    @Test
    fun `trip expenses are clubbed by category and sub-category`() {
        val trip = Trip("o", "Sunday outing", sunday, null, budgetMinor = 5_000_00)
        val txns = listOf(
            t("Dining", "Lunch", 1_200_00), t("Dining", "Snacks", 300_00), t("Dining", "Lunch", 400_00),
            t("Shopping", "Clothes", 2_000_00), t("Transport", "Cab", 450_00), t("Transport", null, 150_00),
            t("Dining", "Dinner", 900_00, trip = null), // not part of the outing
        )
        val s = tripSummaries(listOf(trip), txns, sunday).single()
        assertEquals(4_500_00L, s.totalMinor)
        assertEquals(500_00L, s.leftMinor)
        assertEquals(listOf("Shopping", "Dining", "Transport"), s.categories.map { it.name })
        assertEquals(listOf("Lunch" to 1_600_00L, "Snacks" to 300_00L), s.categories.first { it.name == "Dining" }.subcategories)
        assertEquals("Other", s.categories.first { it.name == "Transport" }.subcategories.last().first)
    }

    @Test
    fun `each category keeps its own expenses newest first`() {
        val trip = Trip("o", "Sunday outing", sunday, sunday.plusDays(1), null)
        val early = t("Travel", "Hotel", 17_000_00)
        val late = early.copy(id = "late", amountMinor = 500_00, date = sunday.plusDays(1))
        val travel = tripSummaries(listOf(trip), listOf(early, late, t("Dining", null, 300_00)), sunday).single().categories.first { it.name == "Travel" }
        assertEquals(listOf("late", early.id), travel.txns.map { it.id })
    }

    @Test
    fun `ongoing trips first`() {
        val past = Trip("p", "Goa", sunday.minusDays(30), sunday.minusDays(25), null)
        val now = Trip("n", "Sunday outing", sunday, null, null)
        assertTrue(now.isOngoing(sunday))
        assertEquals("Sunday outing", tripSummaries(listOf(past, now), emptyList(), sunday).first().trip.title)
    }

    @Test
    fun `spending while away goes to the trip until it is marked done`() {
        val goa = Trip("g", "Goa", sunday, sunday.plusDays(3), null)
        assertEquals("Goa", tripOn(listOf(goa), sunday.plusDays(1))?.title)
        assertEquals(null, tripOn(listOf(goa), sunday.plusDays(5)))
        assertEquals(null, tripOn(listOf(goa.copy(done = true)), sunday.plusDays(1)))
    }
}
