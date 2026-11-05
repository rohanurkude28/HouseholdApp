package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.HomeData
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PeekKind { BUDGET, SPENT, TASKS, EVENT, EXPIRING, SHOPPING, CALM }

/** One tile in the Home hero: "3 · due today", "₹12k · budget left". [route] opens where it's from. */
data class Peek(val value: String, val label: String, val kind: PeekKind, val route: String?)

/**
 * The day at a glance under the greeting: what's due, the next occasion, what's expiring, what
 * to buy and the budget. Without a budget (a new or reset home) the rest still shows, and
 * "Set a budget" goes last instead of being all there is. Pure, so it's tested.
 */
fun heroPeek(
    data: HomeData,
    today: LocalDate,
    canMoney: Boolean,
    canStock: Boolean,
    canMedicine: Boolean,
    /** Budget left this cycle, or null when no budget is set. */
    budgetLeftMinor: Long?,
    hideAmounts: Boolean,
    money: (Long) -> String,
): List<Peek> = buildList {
    if (canMoney && budgetLeftMinor != null) {
        add(
            when {
                hideAmounts -> Peek("••••", "budget left", PeekKind.BUDGET, "money")
                budgetLeftMinor < 0 -> Peek(money(-budgetLeftMinor), "over budget", PeekKind.BUDGET, "money")
                else -> Peek(money(budgetLeftMinor), "budget left", PeekKind.BUDGET, "money")
            },
        )
    }
    val due = data.tasks.count { t -> t.dueDate?.let { !it.isAfter(today) } == true }
    when {
        due > 0 -> add(Peek(due.toString(), "due today", PeekKind.TASKS, "plans/TASKS"))
        data.tasks.isNotEmpty() -> add(Peek(data.tasks.size.toString(), if (data.tasks.size == 1) "open task" else "open tasks", PeekKind.TASKS, "plans/TASKS"))
    }
    data.events.filter { !it.next.isAfter(today.plusDays(30)) }.minByOrNull { it.next }?.let { e ->
        val days = ChronoUnit.DAYS.between(today, e.next)
        add(Peek(if (days == 0L) "Today" else if (days == 1L) "Tomorrow" else "${days}d", e.title, PeekKind.EVENT, "plans/EVENTS"))
    }
    val stock = (if (canStock) data.pantry else emptyList()) + (if (canMedicine) data.medicines else emptyList())
    val expiring = stock.count { s -> s.expiry?.let { !it.isAfter(today.plusDays(3)) } == true }
    if (expiring > 0) add(Peek(expiring.toString(), "expiring soon", PeekKind.EXPIRING, "stock/PANTRY"))
    if (canStock && data.shopping.isNotEmpty()) add(Peek(data.shopping.size.toString(), "to buy", PeekKind.SHOPPING, "stock/SHOPPING"))
    if (canMoney && budgetLeftMinor == null) {
        if (data.monthSpentMinor > 0 && !hideAmounts) add(Peek(money(data.monthSpentMinor), "spent this month", PeekKind.SPENT, "money"))
        add(Peek("Set", "a monthly budget", PeekKind.BUDGET, "money"))
    }
    // A fresh home: say so kindly rather than show one lonely tile.
    // A set budget keeps the lead; the calm tile goes before "Set a budget".
    if (none { it.kind != PeekKind.BUDGET }) add(if (budgetLeftMinor != null && canMoney) 1 else 0, Peek("All clear", "nothing due today", PeekKind.CALM, null))
}
