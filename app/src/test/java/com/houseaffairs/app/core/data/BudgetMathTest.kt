package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetMathTest {

    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun `calendar cycle`() {
        assertEquals(Cycle(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), cycleFor(today, 1))
    }

    @Test
    fun `salary-day cycle starts last month before payday`() {
        assertEquals(Cycle(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 25)), cycleFor(today, 25))
    }

    @Test
    fun `day 31 clamps to short months`() {
        assertEquals(LocalDate.of(2027, 2, 28), cycleFor(LocalDate.of(2027, 2, 28), 31).start)
    }

    private val budget = Budget(
        limitMinor = 100_000_00,
        currency = "INR",
        savingsGoalMinor = 20_000_00,
        categories = mapOf("Rent" to 30_000_00, "Dining" to 5_000_00),
    )

    @Test
    fun `left counts spent, planned and subscriptions still due`() {
        val summary = summarize(
            budget,
            txns = listOf(
                Txn("1", "Rent", "Rent", 30_000_00, LocalDate.of(2026, 11, 1), planned = false),
                Txn("2", "Goa trip", "Travel", 15_000_00, LocalDate.of(2026, 11, 20), planned = true),
                Txn("3", "Last month", "Dining", 9_999_00, LocalDate.of(2026, 10, 30), planned = false),
            ),
            due = listOf(
                DuePayment("n", "Netflix", LocalDate.of(2026, 11, 10), 649_00),
                DuePayment("old", "Paid already", LocalDate.of(2026, 11, 2), 500_00),
            ),
            today = today,
        )
        assertEquals(30_000_00L, summary.spentMinor)
        assertEquals(15_000_00L, summary.plannedMinor)
        assertEquals(649_00L, summary.committedMinor)
        assertEquals(100_000_00L - 30_000_00 - 15_000_00 - 649_00, summary.leftMinor)
        assertEquals(summary.leftMinor - 20_000_00, summary.safeToSpendMinor)
        assertEquals(listOf("Goa trip", "Netflix").sorted(), summary.upcoming.map { it.title }.sorted())
    }

    @Test
    fun `over-limit category sorts first and paces ahead`() {
        val summary = summarize(
            budget,
            txns = listOf(
                Txn("1", "Toit", "Dining", 6_000_00, today, planned = false),
                Txn("2", "Rent", "Rent", 10_000_00, today, planned = false),
            ),
            due = emptyList(),
            today = today,
        )
        val dining = summary.categories.first()
        assertEquals("Dining", dining.name)
        assertTrue(dining.over)
        assertEquals(Pace.AHEAD, dining.pace)
        assertEquals(-1_000_00L, dining.leftMinor)
    }

    @Test
    fun `quiet start of month is on track or under`() {
        val summary = summarize(budget, emptyList(), emptyList(), today)
        assertEquals(Pace.UNDER, summary.pace)
        assertEquals(3, summary.day)
        assertEquals(28, summary.daysLeft)
    }

    @Test
    fun `rent paid on day one is not spending fast`() {
        val summary = summarize(
            budget,
            listOf(Txn("1", "Rent", "Rent", 30_000_00, LocalDate.of(2026, 11, 1), planned = false)),
            emptyList(),
            today,
        )
        assertEquals(Pace.UNDER, summary.pace)
        assertEquals(Pace.ON_TRACK, summary.categories.first { it.name == "Rent" }.pace)
    }

    @Test
    fun `a dinner out early in the month is not spending fast`() {
        // Day 3 with 800 of a 3000 Dining limit used: past a quarter, but too early to call a trend.
        val early = summarize(
            budget.copy(categories = mapOf("Dining" to 3_000_00)),
            listOf(Txn("1", "Toit", "Dining", 800_00, today, planned = false)),
            emptyList(),
            today,
        )
        assertEquals(Pace.ON_TRACK, early.categories.first { it.name == "Dining" }.pace)
    }

    @Test
    fun `half used in the first days is still spending fast`() {
        val early = summarize(
            budget.copy(categories = mapOf("Dining" to 3_000_00)),
            listOf(Txn("1", "Toit", "Dining", 1_600_00, today, planned = false)),
            emptyList(),
            today,
        )
        assertEquals(Pace.AHEAD, early.categories.first { it.name == "Dining" }.pace)
    }

    @Test
    fun `after the first week a quarter used ahead of the line is fast`() {
        val day10 = today.plusDays(7)
        val summary = summarize(
            budget.copy(categories = mapOf("Dining" to 3_000_00)),
            listOf(Txn("1", "Toit", "Dining", 1_400_00, day10, planned = false)),
            emptyList(),
            day10,
        )
        assertEquals(Pace.AHEAD, summary.categories.first { it.name == "Dining" }.pace)
    }
}
