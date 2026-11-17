package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.EventItem
import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.ShoppingItem
import com.houseaffairs.app.core.data.TaskItem
import com.houseaffairs.app.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PeekTest {
    private val today = localDate(2026, 11, 5)
    private fun peek(data: HomeData, left: Long?, canMoney: Boolean = true) =
        heroPeek(data, today, canMoney, canStock = true, canMedicine = true, budgetLeftMinor = left, hideAmounts = false) { "₹$it" }

    @Test
    fun `a reset home without a budget still says something useful`() {
        val p = peek(HomeData(loaded = true), null)
        assertEquals(listOf(PeekKind.CALM, PeekKind.BUDGET), p.map { it.kind })
        assertEquals("a monthly budget", p.last().label)
    }

    @Test
    fun `without a budget the day comes first and set a budget goes last`() {
        val data = HomeData(
            loaded = true,
            tasks = listOf(TaskItem("t", "Pay rent", today, null)),
            events = listOf(EventItem("e", "Riya's birthday", "Birthday", today.plusDays(1))),
            shopping = listOf(ShoppingItem("s", "Milk", null)),
            monthSpentMinor = 500,
        )
        val p = peek(data, null)
        assertEquals(listOf(PeekKind.TASKS, PeekKind.EVENT, PeekKind.SHOPPING, PeekKind.SPENT, PeekKind.BUDGET), p.map { it.kind })
        assertEquals("Tomorrow", p[1].value)
    }

    @Test
    fun `with a budget it leads and no money tiles without access`() {
        assertEquals(PeekKind.BUDGET, peek(HomeData(loaded = true), 1200).first().kind)
        assertEquals(listOf(PeekKind.CALM), peek(HomeData(loaded = true), null, canMoney = false).map { it.kind })
    }

    @Test
    fun `greeting and due labels`() {
        assertEquals("Good evening", greetingFor(19))
        assertEquals("Overdue" to true, dueLabel(today.plusDays(-1), today))
        assertEquals("Tomorrow", dueLabel(today.plusDays(1), today).first)
    }

    @Test
    fun `coming up skips what today shows and keeps the soonest`() {
        val data = HomeData(loaded = true, events = listOf(EventItem("e", "Riya's birthday", "Birthday", today.plusDays(3)), EventItem("far", "Far", "Birthday", today.plusDays(40))))
        val rows = comingUp(data, emptyList(), today, showEvents = true, showRenewals = true, giftsFor = mapOf("riya's birthday" to 2))
        assertEquals(listOf("e"), rows.map { it.id })
        assertEquals(2, rows.single().gifts)
    }
}
