package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class CardsTest {
    private val hdfc = Card("HDFC", statementDay = 21, dueDay = 10)
    private fun spend(amount: Long, date: LocalDate, paidWith: String? = "Card", card: String? = null) =
        Txn("$amount$date", "x", "Shopping", amount, date, planned = false, paidWith = paidWith, card = card)

    @Test
    fun `statement runs 21st to 20th and the bill is due on the 10th after`() {
        val today = localDate(2026, 11, 3)
        val txns = listOf(
            spend(1_000, localDate(2026, 10, 20)), // previous statement
            spend(2_000, localDate(2026, 10, 21)), // first day of the closed statement
            spend(3_000, localDate(2026, 11, 2)), // the open statement
            spend(9_000, localDate(2026, 11, 2), paidWith = "UPI"), // not on the card
        )
        val (closed, open) = cardBills(listOf(hdfc), txns, emptySet(), today)
        assertEquals(localDate(2026, 9, 21), closed.cycle.start)
        assertEquals(localDate(2026, 10, 20), closed.closesOn)
        assertEquals(localDate(2026, 11, 10), closed.dueDate)
        assertEquals(1_000L, closed.amountMinor)
        assertEquals(localDate(2026, 10, 21), open.cycle.start)
        assertEquals(localDate(2026, 11, 20), open.closesOn)
        assertEquals(localDate(2026, 12, 10), open.dueDate)
        assertEquals(5_000L, open.amountMinor)
    }

    @Test
    fun `a named card wins and plain Card only counts when there is one card`() {
        val amex = Card("Amex", 1, 20)
        assertEquals(hdfc, cardOf(spend(1, localDate(2026, 1, 1), card = "hdfc"), listOf(hdfc, amex)))
        assertNull(cardOf(spend(1, localDate(2026, 1, 1)), listOf(hdfc, amex)))
        assertEquals(hdfc, cardOf(spend(1, localDate(2026, 1, 1)), listOf(hdfc)))
    }

    @Test
    fun `due day earlier in the month than the statement day rolls to next month`() {
        val cycle = cycleFor(localDate(2026, 1, 25), 21) // 21 Jan – 20 Feb
        assertEquals(localDate(2026, 3, 10), dueDateFor(cycle, 10))
        assertEquals(localDate(2026, 2, 28), dueDateFor(cycle, 31)) // short month
    }

    @Test
    fun `paid bills are remembered by card and statement`() {
        val today = localDate(2026, 11, 3)
        val closed = cycleFor(localDate(2026, 10, 1), 21)
        val bills = cardBills(listOf(hdfc), emptyList(), setOf(billKey(hdfc, closed)), today)
        assertEquals(listOf(true, false), bills.map { it.paid })
    }
}

class PaidWithCardTest {
    @Test
    fun `a card picked by name in paid with counts on that card`() {
        val hdfc = Card("HDFC", 21, 10)
        val amex = Card("Amex", 1, 20)
        val t = Txn("1", "x", "Dining", 500, localDate(2026, 11, 2), planned = false, paidWith = "Amex")
        assertEquals(amex, cardOf(t, listOf(hdfc, amex)))
    }

    @Test
    fun `run-out date follows the pace so far`() {
        // 3,000 spent in 3 days, 2,000 left: about 2 days more.
        assertEquals(localDate(2026, 11, 5), runOutDate(3_000, 2_000, 3, localDate(2026, 11, 3)))
    }
}
