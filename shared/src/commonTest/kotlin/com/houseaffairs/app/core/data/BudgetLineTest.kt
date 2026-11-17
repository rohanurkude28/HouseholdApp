package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.localDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetLineTest {
    private val today = localDate(2026, 11, 12)
    private val money = { m: Long -> "₹${m / 100}" }
    private fun spent(rupees: Long) = listOf(Txn("a", "Rent", "Rent", rupees * 100, today, planned = false))

    @Test
    fun saysHowMuchMoreCanBeSpent() {
        val s = summarize(Budget(50_000_00, "INR", savingsGoalMinor = 10_000_00), spent(500), emptyList(), today)
        assertEquals("You can spend ₹39500 more this month and still save ₹10000.", spendLine(s, money, null))
    }

    @Test
    fun overTheBudgetPointsAtTheGullak() {
        val s = summarize(Budget(50_000_00, "INR"), spent(52_000), emptyList(), today)
        assertEquals("Over the budget by ₹2000. Cover it from your gullak (₹5000 in it).", spendLine(s, money, 5_000_00))
        assertEquals("Over the budget by ₹2000. It will come out of next month's savings.", spendLine(s, money, 0))
    }
}
