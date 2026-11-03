package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MoneyViewsTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val budget = Budget(100_000, "INR", savingsGoalMinor = 20_000, categories = mapOf("Travel" to 10_000))

    @Test
    fun `upcoming spends are checked in date order against what's left`() {
        val txns = listOf(
            Txn("paid", "Rent", "Rent", 40_000, today.minusDays(1), planned = false),
            Txn("goa", "Goa", "Travel", 30_000, today.plusDays(5), planned = true),
            Txn("tv", "TV", "Shopping", 25_000, today.plusDays(10), planned = true),
            Txn("bike", "Bike", "Shopping", 10_000, today.plusDays(20), planned = true),
        )
        val s = summarize(budget, txns, emptyList(), today)
        val e = envelopes(budget, today.withDayOfMonth(1), txns, emptyList(), emptyList(), today).envelopes
        val fits = upcomingFit(s, e)
        assertEquals(listOf(Fit.FITS, Fit.EATS_SAVINGS, Fit.OVER), fits.map { it.fit })
        assertEquals(listOf(30_000L, 5_000L, -5_000L), fits.map { it.leftAfterMinor })
        // Goa is 30k against a 10k Travel envelope.
        assertEquals(20_000L, fits[0].envelopeShortMinor)
    }

    @Test
    fun `finds expenses by words and category`() {
        val txns = listOf(
            Txn("1", "Uber", "Transport", 300, today, false, "Cab"),
            Txn("2", "Swiggy", "Dining", 500, today, false, "Dinner"),
            Txn("3", "Ola", "Transport", 200, today.minusMonths(1), false, "Cab"),
            Txn("4", "Goa", "Travel", 9_000, today.plusDays(3), true),
        )
        assertEquals(listOf("1", "3"), findExpenses(txns, "cab", null, null).map { it.id })
        assertEquals(listOf("1"), findExpenses(txns, "", "Transport", cycleFor(today, 1)).map { it.id })
        assertEquals(listOf("2"), findExpenses(txns, "swig", null, null).map { it.id })
    }
}
