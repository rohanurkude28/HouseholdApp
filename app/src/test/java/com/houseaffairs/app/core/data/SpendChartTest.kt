package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SpendChartTest {
    private val cycle = Cycle(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))
    private fun t(amount: Long, day: Int, cat: String = "Groceries", planned: Boolean = false) =
        Txn("$day-$amount-$cat", "x", cat, amount, LocalDate.of(2026, 11, day), planned)
    private val money: (Long) -> String = { "₹$it" }

    @Test
    fun `the line adds up day by day to today`() {
        val p = spendPace(listOf(t(10_000, 1), t(5_000, 3), t(99_900, 20, planned = true)), cycle, 300_000, LocalDate.of(2026, 11, 4))
        assertEquals(listOf(10_000L, 10_000L, 15_000L, 15_000L), p.cumulative)
        assertEquals(40_000L, p.paceAt(4))
        assertEquals(-25_000L, p.aheadOfPaceMinor)
        assertNull(p.overFromDay)
        assertEquals("₹25000 under an even pace · on track", paceLine(p, money))
    }

    @Test
    fun `going past the budget is marked from the day it happened`() {
        val p = spendPace(listOf(t(2_000, 2), t(1_500, 5)), cycle, 3_000, LocalDate.of(2026, 11, 6))
        assertEquals(5, p.overFromDay)
        assertEquals("Over the budget by ₹500 since day 5", paceLine(p, money))
    }

    @Test
    fun `ahead of pace says where the month is heading`() {
        val p = spendPace(listOf(t(1_000, 1)), cycle, 3_000, LocalDate.of(2026, 11, 5))
        assertEquals(500L, p.aheadOfPaceMinor)
        assertEquals("₹500 ahead of an even pace · heading for ₹6000", paceLine(p, money))
    }

    @Test
    fun `a finished month shows every day`() {
        assertEquals(30, spendPace(emptyList(), cycle, 3_000, LocalDate.of(2027, 1, 5)).cumulative.size)
    }

    @Test
    fun `categories against their targets, over ones first`() {
        val bars = categoryBars(listOf(t(500, 2), t(900, 3, "Dining"), t(100, 4, "Fuel")), cycle, mapOf("Groceries" to 2_000, "Dining" to 600, "Rent" to 30_000))
        assertEquals(listOf("Dining", "Groceries", "Fuel", "Rent"), bars.map { it.name })
        assertEquals(true, bars.first().over)
        assertNull(bars.first { it.name == "Fuel" }.targetMinor)
        assertEquals(6_000L, categoryBars(emptyList(), cycle, mapOf("Groceries" to 2_000), months = 3).single().targetMinor)
    }

    private val budget = Budget(limitMinor = 1_200, currency = "INR", categories = mapOf("Dining" to 100))
    private fun on(date: LocalDate, amount: Long, cat: String = "Groceries") = Txn("$date-$cat", "x", cat, amount, date, false)
    private val months = { m: Int -> listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[m - 1] }

    @Test
    fun `the year adds up month by month against twelve budgets`() {
        val txns = listOf(on(LocalDate.of(2026, 1, 5), 1_000), on(LocalDate.of(2026, 3, 9), 500), on(LocalDate.of(2025, 12, 1), 9_999))
        val p = yearPace(txns, 2026, LocalDate.of(2026, 4, 2), budget, startedOn = LocalDate.of(2025, 6, 1))
        assertEquals(listOf(1_000L, 1_000L, 1_500L, 1_500L), p.cumulative)
        assertEquals(14_400L, p.limitMinor)
        assertEquals("₹3300 under pace · on track for the year", yearLine(p, money, months))
    }

    @Test
    fun `one category against its own limit, and a budget started mid-year targets only its months`() {
        val txns = listOf(on(LocalDate.of(2026, 9, 3), 450, "Dining"), on(LocalDate.of(2026, 9, 4), 1_000))
        val p = yearPace(txns, 2026, LocalDate.of(2026, 10, 1), budget, startedOn = LocalDate.of(2026, 9, 1), category = "Dining")
        assertEquals(400L, p.limitMinor)
        assertEquals(9, p.overFromDay)
        assertEquals("Over the year's target by ₹50 since Sep", yearLine(p, money, months))
        assertEquals("₹1000 so far this year · no target set", yearLine(yearPace(txns, 2026, LocalDate.of(2026, 10, 1), budget, LocalDate.of(2026, 9, 1), "Groceries"), money, months))
    }

    @Test
    fun `a budget started mid-year paces from its first month, in whole units`() {
        val b = Budget(limitMinor = 100_000, currency = "INR")
        val p = yearPace(listOf(on(LocalDate.of(2026, 11, 3), 39_789)), 2026, LocalDate.of(2026, 11, 12), b, startedOn = LocalDate.of(2026, 11, 1))
        assertEquals(11, p.from)
        assertEquals(200_000L, p.limitMinor)
        assertEquals(0L, p.paceAt(10))
        assertEquals(100_000L, p.paceAt(11))
        assertEquals("₹60200 under pace · on track for the year", yearLine(p, money, months))
    }
}
