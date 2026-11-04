package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RestockTest {

    private val today = LocalDate.of(2026, 11, 4)
    private fun bought(title: String, daysAgo: Long) = Purchase(title, today.minusDays(daysAgo))

    @Test
    fun `usual gap needs three buys and uses the median`() {
        assertNull(boughtEvery(listOf(today, today.minusDays(18))))
        assertEquals(18, boughtEvery(listOf(today.minusDays(54), today.minusDays(36), today.minusDays(18), today.minusDays(17))))
    }

    @Test
    fun `rice bought every 18 days is due, unless it's already on the list`() {
        val rice = listOf(bought("Rice", 52), bought("rice", 34), bought("Rice", 17))
        val due = restockDue(rice, emptyList(), onList = emptyList(), today = today).single()
        assertEquals("Rice", due.title)
        assertEquals(today.plusDays(1), due.due)
        assertEquals("You usually buy it every 18 days", due.why)
        assertTrue(restockDue(rice, emptyList(), onList = listOf("rice"), today = today).isEmpty())
    }

    @Test
    fun `a fixed restock choice wins over the history`() {
        val milk = listOf(bought("Milk", 8))
        val memory = listOf(StockMemory("Milk", null, null, "Every week", today.minusDays(8)))
        assertEquals("You restock it every week", restockDue(milk, memory, emptyList(), today).single().why)
    }

    @Test
    fun `rating memory names the brand and the thumb`() {
        val memory = listOf(
            StockMemory("Paneer", "Amul", "👍 Liked it", null, today.minusDays(30)),
            StockMemory("Paneer", "Brand X", "👎 Not great", null, today.minusDays(5)),
            StockMemory("Rice", null, null, null, today),
        )
        assertEquals("Last time: Brand X 👎", lastTimeNote("paneer", memory))
        assertNull(lastTimeNote("Rice", memory))
    }
}
