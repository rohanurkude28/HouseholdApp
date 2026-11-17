package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.Test

class TripBudgetTest {
    private val money: (Long) -> String = { "₹$it" }

    @Test
    fun `a trip's budget reads fine nearing then over and over is allowed`() {
        assertEquals(BudgetLevel.FINE, tripBudget(5_000, 20_000, money)!!.level)
        assertEquals("Nearing the budget · ₹3000 left of ₹20000", tripBudget(17_000, 20_000, money)!!.line)
        val over = tripBudget(23_000, 20_000, money)!!
        assertEquals(BudgetLevel.OVER, over.level)
        assertEquals("₹3000 over the ₹20000 budget", over.line)
        assertNull(tripBudget(5_000, null, money))
    }

    @Test
    fun `saving into a trip says where it stands`() {
        assertEquals("₹15000 left of Goa's budget", tripSavedNote("Goa", 5_000, 20_000, money))
        assertEquals("Goa is nearing its budget: ₹1000 left", tripSavedNote("Goa", 19_000, 20_000, money))
        assertEquals("Goa is ₹500 over budget", tripSavedNote("Goa", 20_500, 20_000, money))
        assertEquals("Goa so far: ₹5000", tripSavedNote("Goa", 5_000, null, money))
    }

    @Test
    fun `a place's map search is narrowed to the trip`() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=Baga+Beach%2C+Goa", routeUrl(listOf(Stop(1, "Baga Beach")), area = "Goa"))
        assertEquals("https://www.google.com/maps/search/?api=1&query=Old+Goa+Church", routeUrl(listOf(Stop(1, "Old Goa Church")), area = "Goa"))
    }

    @Test
    fun `a trip's unspent budget is reserved once not on top of a planned spend for it`() {
        val today = localDate(2026, 11, 12)
        val goa = Trip("g", "Goa", today.minusDays(1), today.plusDays(3), budgetMinor = 20_000_00)
        val txns = listOf(
            Txn("p", "Goa trip", "Travel", 15_000_00, today.plusDays(10), planned = true),
            Txn("s", "Stay", "Travel", 17_000_00, today, planned = false, trip = "Goa"),
            Txn("o", "Gift for Riya", "Gifts", 2_000_00, today.plusDays(5), planned = true),
        )
        val real = txns.filterNot { coveredByTrip(it, listOf(goa)) }
        assertEquals(listOf("s", "o"), real.map { it.id })
        val reserve = tripReserves(real, listOf(goa), today).single()
        assertEquals(3_000_00, reserve.amountMinor)
        assertEquals(TRIP_RESERVE + "g", reserve.id)
        // Over its budget, or done: nothing more is held back.
        assertTrue(tripReserves(real + Txn("x", "Food", "Dining", 4_000_00, today, planned = false, trip = "Goa"), listOf(goa), today).isEmpty())
        assertTrue(tripReserves(real, listOf(goa.copy(done = true)), today).isEmpty())
    }
}
