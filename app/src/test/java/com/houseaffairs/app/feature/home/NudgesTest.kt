package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.ShoppingItem
import com.houseaffairs.app.core.data.StockItem
import com.houseaffairs.app.core.data.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NudgesTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun nudges(data: HomeData, hasBudget: Boolean = true) = homeNudges(data, today, true, true, true, true, hasBudget)

    @Test
    fun `tidying comes before adding`() {
        val data = HomeData(
            loaded = true,
            pantry = listOf(StockItem("p", "Milk", today.minusDays(2), null)),
            tasks = listOf(TaskItem("t", "Fix tap", today.minusDays(10), null)),
            shopping = List(16) { ShoppingItem("s$it", "Item $it", null) },
        )
        val n = nudges(data, hasBudget = false)
        assertEquals(listOf("🗑️", "🧹", "🛒"), n.map { it.emoji })
        assertTrue(n.all { it.tidy })
        assertEquals("Milk has expired", n.first().text)
    }

    @Test
    fun `an empty home is nudged to add the basics`() {
        val n = nudges(HomeData(loaded = true), hasBudget = false)
        assertEquals(listOf("money", "stock/PANTRY", "plans/EVENTS"), n.map { it.route })
        assertTrue(n.none { it.tidy })
    }

    @Test
    fun `nothing for parts a role can't open`() {
        val kid = homeNudges(HomeData(loaded = true), today, canMoney = false, canStock = false, canMedicine = false, canPlans = true, hasBudget = false)
        assertEquals(listOf("plans/EVENTS"), kid.map { it.route })
    }
}
