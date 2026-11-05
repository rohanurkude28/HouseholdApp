package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MonthOverviewTest {
    private val nov = cycleFor(LocalDate.of(2026, 11, 10), 1)
    private fun txn(cat: String, amount: Long, day: LocalDate, sub: String? = null, planned: Boolean = false, trip: String? = null) =
        Txn("$cat$amount$day", cat, cat, amount, day, planned, sub, trip)

    @Test
    fun `groups paid spending by category with last month alongside`() {
        val txns = listOf(
            txn("Rent", 30_000, LocalDate.of(2026, 11, 1)),
            txn("Dining", 1_200, LocalDate.of(2026, 11, 3), "Lunch", trip = "Sunday outing"),
            txn("Dining", 800, LocalDate.of(2026, 11, 5), "Dinner"),
            txn("Dining", 5_000, LocalDate.of(2026, 10, 20)),
            txn("Travel", 9_000, LocalDate.of(2026, 11, 20), planned = true),
        )
        val o = monthOverview(txns, nov, 1)
        assertEquals(32_000L, o.totalMinor)
        assertEquals(5_000L, o.lastMonthMinor)
        assertEquals(listOf("Rent", "Dining"), o.categories.map { it.name })
        val dining = o.categories[1]
        assertEquals(2_000L, dining.totalMinor)
        assertEquals(5_000L, dining.lastMonthMinor)
        assertEquals(listOf("Lunch" to 1_200L, "Dinner" to 800L), dining.subcategories)
        assertEquals(listOf("Sunday outing" to 1_200L), o.trips)
    }

    @Test
    fun `lists months back to when the budget started`() {
        val months = cyclesSince(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 11, 3), 1)
        assertEquals(listOf(11, 10, 9), months.map { it.start.monthValue })
    }

    @Test
    fun `a trip marked done stops collecting expenses`() {
        val trip = Trip("t", "Sunday outing", LocalDate.of(2026, 11, 1), null, null, done = true)
        assertEquals(false, trip.isOngoing(LocalDate.of(2026, 11, 1)))
        assertEquals(false, trip.isCurrent(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `cash and card add up to the month's total`() {
        val day = LocalDate.of(2026, 11, 3)
        val txns = listOf(
            Txn("1", "Toit", "Dining", 2_000, day, planned = false, paidWith = "HDFC"),
            Txn("2", "Auto", "Transport", 300, day, planned = false, paidWith = "Cash"),
            Txn("3", "Swiggy", "Dining", 700, day, planned = false, paidWith = "UPI"),
            Txn("4", "Chai", "Dining", 50, day, planned = false),
            Txn("5", "Groceries", "Groceries", 1_000, day, planned = false, paidWith = "UPI"),
        )
        val o = monthOverview(txns, nov, 1)
        assertEquals(listOf("HDFC" to 2_000L, "UPI" to 1_700L, "Cash" to 300L, "Not set" to 50L), o.paidWith)
        assertEquals(o.totalMinor, o.paidWith.sumOf { it.second })
    }
}

class MoneyYearTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun `budget months group into years with their own limits`() {
        val budget = Budget(limitMinor = 50_000_00, currency = "INR")
        val txns = listOf(
            Txn("1", "Rent", "Rent", 30_000_00, d(2026, 1, 2), planned = false),
            Txn("2", "Toit", "Dining", 2_000_00, d(2026, 2, 3), planned = false),
            Txn("3", "Gifts", "Gifts", 5_000_00, d(2025, 12, 20), planned = false),
            Txn("4", "Soon", "Travel", 9_000_00, d(2026, 2, 9), planned = true),
        )
        val years = moneyYears(txns, budget, startedOn = d(2025, 12, 1), today = d(2026, 2, 10))
        assertEquals(listOf(2026, 2025), years.map { it.year })
        val y = years.first()
        assertEquals(2, y.months.size)
        assertEquals(32_000_00L, y.spentMinor)
        assertEquals(100_000_00L, y.budgetMinor)
        assertEquals("Rent" to 30_000_00L, y.topCategories().first())
        // Newest month first, as everywhere else.
        assertEquals(2, y.months.first().overview.cycle.start.monthValue)
    }
}
