package com.houseaffairs.app.core.data

/**
 * The month's budget in one sentence of what to do: how much more can be spent (and still save
 * the goal), or how much over it is and that the gullak can cover it. [gullakMinor] is what's in
 * the gullak, or null when this screen doesn't know. Whole currency units, no paise.
 */
fun spendLine(summary: BudgetSummary, money: (Long) -> String, gullakMinor: Long?): String {
    fun whole(minor: Long) = money(minor - minor % 100)
    val goal = summary.savingsGoalMinor
    val faster = if (summary.pace == Pace.AHEAD) " Spending is faster than usual." else ""
    return when {
        summary.leftMinor < 0 -> "Over the budget by ${whole(-summary.leftMinor)}. " + when {
            gullakMinor == null -> "Cover it from your gullak if there's money in it."
            gullakMinor > 0 -> "Cover it from your gullak (${whole(gullakMinor)} in it)."
            else -> "It will come out of next month's savings."
        }
        summary.safeToSpendMinor < 0 -> "You can spend ${whole(summary.leftMinor)} more, but then you'd save ${whole(-summary.safeToSpendMinor)} less than your ${whole(goal)} goal."
        goal > 0 -> "You can spend ${whole(summary.safeToSpendMinor)} more this month and still save ${whole(goal)}.$faster"
        else -> "You can spend ${whole(summary.leftMinor)} more this month.$faster"
    }
}
