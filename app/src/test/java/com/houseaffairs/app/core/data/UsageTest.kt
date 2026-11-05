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
        val bought = listOf("Milk" to d(10, 3), "milk" to d(10, 20), "Rice" to d(10, 5), "Eggs" to d(9, 9))
        val spend = listOf(Txn("1", "DMart", "Groceries", 1_200_00, d(10, 3), planned = false), Txn("2", "Soon", "Groceries", 500_00, d(10, 30), planned = true))
        val months = kitchenMonths(bought, lives, spend, d(11, 5))
        val oct = months.first()
        assertEquals("October 2026", oct.label)
        assertEquals("Milk" to 2, oct.bought.first())
        assertEquals(2, oct.usedUp.size)
        assertEquals(1_200_00L, oct.spentMinor)
        assertEquals(null, kitchenMonths(bought, lives, null, d(11, 5)).first().spentMinor)
    }
}
