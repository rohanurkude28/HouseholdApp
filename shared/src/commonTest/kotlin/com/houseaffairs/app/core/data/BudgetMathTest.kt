package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class BudgetMathTest {

    private val today = localDate(2026, 11, 3)

    @Test
    fun `calendar cycle`() {
        assertEquals(Cycle(localDate(2026, 11, 1), localDate(2026, 12, 1)), cycleFor(today, 1))
    }

    @Test
    fun `salary-day cycle starts last month before payday`() {
        assertEquals(Cycle(localDate(2026, 10, 25), localDate(2026, 11, 25)), cycleFor(today, 25))
    }

    @Test
    fun `day 31 clamps to short months`() {
        assertEquals(localDate(2027, 2, 28), cycleFor(localDate(2027, 2, 28), 31).start)
    }

    private val budget = Budget(
        limitMinor = 100_000_00,
        currency = "INR",
        savingsGoalMinor = 20_000_00,
        categories = mapOf("Rent" to 30_000_00, "Dining" to 5_000_00),
    )

    @Test
    fun `left counts spent - planned and subscriptions still due`() {
        val summary = summarize(
            budget,
            txns = listOf(
                Txn("1", "Rent", "Rent", 30_000_00, localDate(2026, 11, 1), planned = false),
                Txn("2", "Goa trip", "Travel", 15_000_00, localDate(2026, 11, 20), planned = true),
                Txn("3", "Last month", "Dining", 9_999_00, localDate(2026, 10, 30), planned = false),
            ),
            due = listOf(
                DuePayment("n", "Netflix", localDate(2026, 11, 10), 649_00),
                DuePayment("old", "Paid already", localDate(2026, 11, 2), 500_00),
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
            listOf(Txn("1", "Rent", "Rent", 30_000_00, localDate(2026, 11, 1), planned = false)),
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

    @Test
    fun `the family's own categories sit before Other`() {
        val budget = Budget(1000, "INR", categories = mapOf("Maid" to 3000, "Groceries" to 500), customCategories = listOf("Cook", "cook", "Insurance", "other"))
        val all = expenseCategories(budget)
        assertEquals("Other", all.last())
        assertEquals(listOf("Cook", "Insurance", "Maid"), all.dropLast(1).takeLast(3))
        assertEquals(1, all.count { it == "Groceries" })
        assertEquals(com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES.size, expenseCategories(null).size)
    }

    @Test
    fun `a new category is one the family hasn't got yet`() {
        val budget = Budget(1000, "INR", customCategories = listOf("Cook"))
        assertTrue(isNewCategory("Driver", budget))
        assertTrue(!isNewCategory("cook", budget))
        assertTrue(!isNewCategory("Bills", budget))
        assertTrue(!isNewCategory(" ", budget))
    }

    @Test
    fun `a line naming the family's own category uses it`() {
        val budget = Budget(1000, "INR", customCategories = listOf("Insurance", "Car insurance", "Cook"))
        assertEquals("Cook", customCategoryIn("paid cook 6000", budget))
        assertEquals("Car insurance", customCategoryIn("car insurance 12000", budget))
        assertEquals(null, customCategoryIn("cooked dal", budget))
        assertEquals(null, customCategoryIn("cook 5000", null))
    }

    @Test
    fun `renaming a category keeps its limit - merging adds limits`() {
        val budget = Budget(1000, "INR", categories = mapOf("Cook" to 5000, "Home" to 2000), customCategories = listOf("Cook"))
        val renamed = budgetWithCategory(budget, "Cook", "Cook & maid")
        assertEquals(mapOf("Home" to 2000L, "Cook & maid" to 5000L), renamed.categories)
        assertEquals(listOf("Cook & maid"), renamed.customCategories)
        val merged = budgetWithCategory(budget, "Cook", "Home")
        assertEquals(mapOf("Home" to 7000L), merged.categories)
        assertEquals(emptyList<String>(), merged.customCategories)
        val gone = budgetWithCategory(budget, "Cook", null)
        assertEquals(mapOf("Home" to 2000L), gone.categories)
        assertEquals(emptyList<String>(), gone.customCategories)
    }

    @Test
    fun `each month keeps the amount it had`() {
        val budget = Budget(60_000, "INR", limitHistory = listOf(LimitChange(localDate(2026, 9, 1), 50_000), LimitChange(localDate(2026, 11, 1), 60_000)))
        assertEquals(50_000L, budget.limitFor(localDate(2026, 8, 1)))
        assertEquals(50_000L, budget.limitFor(localDate(2026, 10, 1)))
        assertEquals(60_000L, budget.limitFor(localDate(2026, 11, 1)))
        assertEquals(70_000L, Budget(70_000, "INR").limitFor(localDate(2026, 1, 1)))
    }

    @Test
    fun `raising the budget applies from this month - or next - or always`() {
        val today = localDate(2026, 11, 5)
        val old = Budget(50_000, "INR")
        val started = localDate(2026, 9, 1)
        assertEquals(
            listOf(LimitChange(started, 50_000), LimitChange(localDate(2026, 11, 1), 60_000)),
            limitHistoryAfter(old, 60_000, LimitFrom.THIS_MONTH, started, today, 1),
        )
        assertEquals(localDate(2026, 12, 1), limitHistoryAfter(old, 60_000, LimitFrom.NEXT_MONTH, started, today, 1).last().from)
        assertEquals(emptyList<LimitChange>(), limitHistoryAfter(old, 60_000, LimitFrom.ALWAYS, started, today, 1))
        // Changing it again in the same month replaces that month's amount.
        val once = old.copy(limitMinor = 60_000, limitHistory = limitHistoryAfter(old, 60_000, LimitFrom.THIS_MONTH, started, today, 1))
        assertEquals(
            listOf(LimitChange(started, 50_000), LimitChange(localDate(2026, 11, 1), 55_000)),
            limitHistoryAfter(once, 55_000, LimitFrom.THIS_MONTH, started, today, 1),
        )
        // Same amount: nothing to record.
        assertEquals(once.limitHistory, limitHistoryAfter(once, 60_000, LimitFrom.THIS_MONTH, started, today, 1))
    }

    @Test
    fun `this month's summary uses this month's amount`() {
        val budget = Budget(60_000, "INR", limitHistory = listOf(LimitChange(localDate(2026, 9, 1), 50_000), LimitChange(localDate(2026, 12, 1), 60_000)))
        assertEquals(50_000L, summarize(budget, emptyList(), emptyList(), localDate(2026, 11, 5)).limitMinor)
    }

    @Test
    fun `monthly dream amounts start - change and stop from a month on`() {
        val oct = localDate(2026, 10, 1)
        val nov = localDate(2026, 11, 1)
        val start = allocationsAfter(emptyList(), mapOf("bali" to 10_000), oct)
        assertEquals(mapOf("bali" to 10_000L), Budget(0, "INR", dreamAllocations = start).allocatedFor(nov))
        val stopped = allocationsAfter(start, emptyMap(), nov)
        val b = Budget(0, "INR", dreamAllocations = stopped)
        assertEquals(mapOf("bali" to 10_000L), b.allocatedFor(oct))
        assertEquals(emptyMap<String, Long>(), b.allocatedFor(nov))
        // Same amount again: nothing changes.
        assertEquals(start, allocationsAfter(start, mapOf("bali" to 10_000), nov))
        assertEquals(2_000L, summarize(Budget(10_000, "INR", dreamAllocations = listOf(DreamAllocation("bali", 2_000, nov))), emptyList(), emptyList(), localDate(2026, 11, 5)).allocatedMinor)
    }
}
