package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripsTest {

    private val sunday = LocalDate.of(2026, 11, 8)
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
    fun `ongoing trips first`() {
        val past = Trip("p", "Goa", sunday.minusDays(30), sunday.minusDays(25), null)
        val now = Trip("n", "Sunday outing", sunday, null, null)
        assertTrue(now.isOngoing(sunday))
        assertEquals("Sunday outing", tripSummaries(listOf(past, now), emptyList(), sunday).first().trip.title)
    }
}
