package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class YearOutlookTest {

    private val today = LocalDate.of(2026, 11, 16)
    private val budget = Budget(limitMinor = 100_000, currency = "INR")
    private fun spend(amount: Long, date: LocalDate, planned: Boolean = false) = Txn("$amount$date", "x", "Dining", amount, date, planned)

    @Test
    fun `months, savings so far and the projection`() {
        val txns = listOf(spend(80_000, LocalDate.of(2026, 9, 10)), spend(90_000, LocalDate.of(2026, 10, 10)), spend(40_000, LocalDate.of(2026, 11, 5)))
        val o = yearOutlook(budget, LocalDate.of(2026, 9, 1), txns, emptyList(), emptyList(), today)
        assertEquals(listOf("Sept", "Oct", "Nov").map { it.take(3) }, o.months.map { it.label.take(3) })
        assertTrue(o.months.last().current)
        assertEquals(30_000L, o.savedSoFarMinor)
        // Usual leftover 15,000 × Nov and Dec still to come.
        assertEquals(60_000L, o.projectedMinor)
    }

    @Test
    fun `big items in the next year, soonest first`() {
        val trip = Trip("t", "Goa", LocalDate.of(2027, 1, 10), null, 30_000, null)
        val dream = Dream("d", "New car", "Car", "🚗", 500_000, LocalDate.of(2027, 6, 1))
        val o = yearOutlook(budget, LocalDate.of(2026, 9, 1), listOf(spend(20_000, LocalDate.of(2026, 12, 20), planned = true)), listOf(dream), listOf(trip), today)
        assertEquals(listOf("Planned", "Trip", "Dream"), o.upcoming.map { it.kind })
    }

    @Test
    fun `an insight compares this month with the usual`() {
        val txns = listOf(spend(60_000, LocalDate.of(2026, 9, 10)), spend(60_000, LocalDate.of(2026, 10, 10)), spend(40_000, LocalDate.of(2026, 11, 5)))
        val o = yearOutlook(budget, LocalDate.of(2026, 9, 1), txns, emptyList(), emptyList(), today) { "₹${it / 100}" }
        assertTrue(o.insight!!.startsWith("This month is heading"))
    }
}
