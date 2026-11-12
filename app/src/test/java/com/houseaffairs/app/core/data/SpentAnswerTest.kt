package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SpentAnswerTest {
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private val txns = listOf(
        Txn("1", "DMart", "Groceries", 2_340_00, d(11, 3), planned = false),
        Txn("2", "Vegetable vendor", "Groceries", 380_00, d(11, 5), planned = false),
        Txn("3", "Toit", "Dining", 1_850_00, d(11, 2), planned = false),
        Txn("4", "BigBasket", "Groceries", 3_400_00, d(10, 15), planned = false),
        Txn("5", "Goa", "Travel", 15_000_00, d(11, 17), planned = true),
    )

    @Test
    fun `groceries this month - with where it went`() {
        val s = spentOn(txns, "groceries", periodDays("this month", d(11, 5)))
        assertEquals(2_720_00L, s.totalMinor)
        assertEquals(2, s.count)
        assertEquals("DMart" to 2_340_00L, s.top.first())
        assertEquals("Groceries", s.matched)
    }

    @Test
    fun `last month - a shop - and everything`() {
        assertEquals(3_400_00L, spentOn(txns, "Groceries", periodDays("last month", d(11, 5))).totalMinor)
        assertEquals(2_340_00L, spentOn(txns, "dmart", periodDays("this month", d(11, 5))).totalMinor)
        // Planned spends aren't spent yet.
        val all = spentOn(txns, null, periodDays("this month", d(11, 5)))
        assertEquals(4_570_00L, all.totalMinor)
        assertEquals("Groceries" to 2_720_00L, all.top.first())
    }

    @Test
    fun `a salary-day month starts on its day`() {
        assertEquals(d(10, 25)..d(11, 5), periodDays("this month", d(11, 5), cycleStartDay = 25))
    }
}
