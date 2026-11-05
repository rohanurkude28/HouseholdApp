package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UsageTest {
    private val d = { m: Int, day: Int -> LocalDate.of(2026, m, day) }
    private val lives = listOf(
        StockLife("Detergent", d(8, 1), d(9, 2)),
        StockLife("detergent", d(9, 2), d(10, 7)),
        StockLife("Detergent", d(10, 7), null),
        StockLife("Rice", d(10, 1), d(10, 25)),
    )

    @Test
    fun `how long a pack lasts and how much is left of the open one`() {
        val det = usageOf(lives).first { it.title == "Detergent" }
        assertEquals(2, det.finished)
        assertEquals(34, det.averageDays) // 32 and 35 days
        assertEquals(35, det.lastDays)
        assertEquals(d(10, 7), det.currentSince)
        assertEquals(5, det.daysLeft(d(11, 5)))
    }

    @Test
    fun `month by month what ran out`() {
        val months = monthlyUsage(lives, d(11, 5))
        assertEquals("Oct 2026", months.first().first)
        assertEquals(listOf("detergent" to 1, "Rice" to 1).map { it.second }, months.first().second.map { it.second })
        assertEquals(listOf("Oct 2026", "Sep 2026"), months.map { it.first })
    }

    @Test
    fun `nothing finished yet says so`() {
        val u = usageOf(listOf(StockLife("Soap", d(10, 1), null))).single()
        assertEquals(1, usageLines(u, d(11, 5)).size)
    }

    @Test
    fun `a month in the kitchen - bought - used up and spent`() {
        val bought = listOf(KitchenBuy("Milk", d(10, 3)), KitchenBuy("milk", d(10, 20)), KitchenBuy("Rice", d(10, 5)), KitchenBuy("Eggs", d(9, 9)))
        val spend = listOf(Txn("1", "DMart", "Groceries", 1_200_00, d(10, 3), planned = false), Txn("2", "Soon", "Groceries", 500_00, d(10, 30), planned = true))
        val months = kitchenMonths(bought, lives, emptyList(), spend, d(11, 5))
        // This month stays, even with nothing in it yet.
        assertEquals(listOf("November 2026", "October 2026", "September 2026"), months.map { it.label })
        val oct = months[1]
        assertEquals("Milk" to 2, oct.bought.first())
        assertEquals(2, oct.usedUp.size)
        assertEquals(1_200_00L, oct.spentMinor)
        assertEquals(null, kitchenMonths(bought, lives, emptyList(), null, d(11, 5))[1].spentMinor)
    }

    @Test
    fun `things bought are split by kind - a saved type wins over the name`() {
        val bought = listOf(KitchenBuy("Milk", d(11, 1)), KitchenBuy("Curd", d(11, 2)), KitchenBuy("Tomato", d(11, 2)), KitchenBuy("Amul gold", d(11, 3), type = "Dairy & eggs"))
        val nov = kitchenMonths(bought, emptyList(), emptyList(), null, d(11, 5)).first()
        assertEquals(Aisle.DAIRY to 3, nov.byType.first())
        assertEquals(Aisle.VEGETABLES to 1, nov.byType[1])
    }

    @Test
    fun `food that went off counts once its date has passed - valued by what was left`() {
        val expired = listOf(
            Expired("Paneer", d(11, 2), costMinor = 90_00, level = 50),
            Expired("Bread", d(11, 4), costMinor = 45_00),
            Expired("Spinach", d(11, 3)),
            // Still good today: not wasted yet.
            Expired("Curd", d(11, 5), costMinor = 30_00),
        )
        val nov = kitchenMonths(emptyList(), emptyList(), expired, null, d(11, 5)).first()
        assertEquals(listOf("Paneer", "Spinach", "Bread"), nov.wasted.map { it.title })
        assertEquals(45_00L + 45_00L, nov.wastedMinor)
    }

    @Test
    fun `spend is compared with last month and the grocery budget`() {
        val spend = listOf(
            Txn("1", "DMart", "Groceries", 4_000_00, d(10, 3), planned = false),
            Txn("2", "BigBasket", "Groceries", 3_000_00, d(11, 2), planned = false),
        )
        val (nov, oct) = kitchenMonths(emptyList(), emptyList(), emptyList(), spend, d(11, 5), groceryBudgetMinor = 8_000_00)
        assertEquals(-25, nov.changePercent)
        assertEquals(8_000_00L, nov.budgetMinor)
        assertEquals(null, oct.changePercent)
        // Without money access there's no budget to show either.
        assertEquals(null, kitchenMonths(emptyList(), emptyList(), emptyList(), null, d(11, 5), groceryBudgetMinor = 8_000_00).first().budgetMinor)
    }
}

class StockYearTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test
    fun `months add up into years - newest first`() {
        val bought = listOf(KitchenBuy("Milk", d(11, 1)), KitchenBuy("milk", d(10, 2)), KitchenBuy("Rice", d(10, 3)), KitchenBuy("Milk", d(12, 20, 2025)))
        val spend = listOf(Txn("1", "DMart", "Groceries", 1_000_00, d(10, 3), planned = false), Txn("2", "BB", "Groceries", 500_00, d(11, 2), planned = false))
        val wasted = listOf(Expired("Bread", d(10, 9), costMinor = 40_00, thrown = true))
        val years = stockYears(kitchenMonths(bought, emptyList(), wasted, spend, d(11, 5), months = 24))
        assertEquals(listOf(2026, 2025), years.map { it.year })
        val y = years.first()
        assertEquals(3, y.bought)
        assertEquals(1_500_00L, y.spentMinor)
        assertEquals(40_00L, y.wastedMinor)
        assertEquals("Milk" to 2, y.mostBought().first())
        assertEquals(listOf("November 2026", "October 2026"), y.months.map { it.label })
    }

    @Test
    fun `thrown away counts the same day - not only after the date`() {
        val today = d(11, 5)
        val nov = kitchenMonths(emptyList(), emptyList(), listOf(Expired("Milk", today, costMinor = 60_00, level = 50, thrown = true)), null, today).first()
        assertEquals(30_00L, nov.wastedMinor)
    }
}
