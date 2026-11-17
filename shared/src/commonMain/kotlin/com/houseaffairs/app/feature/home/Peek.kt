package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.Area
import com.houseaffairs.app.core.data.Attention
import com.houseaffairs.app.core.data.COMING_UP_COLLECTIONS
import com.houseaffairs.app.core.data.Priority
import com.houseaffairs.app.core.data.TaskItem
import com.houseaffairs.app.core.data.handoffLine
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.daysBetween
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.core.time.isAfter
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.core.time.plusDays

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
        val days = daysBetween(today, e.next)
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

/** "Good evening": the greeting for the hour of the day. */
fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/** "Overdue", "Today", "Tomorrow" or "Fri 20 Nov", and whether it's late. */
fun dueLabel(day: LocalDate, today: LocalDate): Pair<String, Boolean> = when {
    day.isBefore(today) -> "Overdue" to true
    day == today -> "Today" to false
    day == today.plusDays(1) -> "Tomorrow" to false
    else -> day.format("EEE d MMM") to false
}

/** A task's second line on Home: who it's waiting on, its priority, when, and who it's for. */
fun taskDetail(task: TaskItem, myUid: String, today: LocalDate, nameOf: (String) -> String): String =
    handoffLine(task, myUid, nameOf).let { asked ->
        listOfNotNull(
            asked,
            task.priority.takeIf { it != "Low" }?.let { "$it priority" },
            task.dueDate?.let { dueLabel(it, today).first },
            // "Waiting for Ravi" already says who it's for.
            task.assignee?.takeIf { it != myUid && asked == null }?.let { "for ${nameOf(it)}" },
        ).joinToString(" · ").ifEmpty { "No date" }
    }

/** One date card in Home's "Coming up" strip. [gifts] counts gift ideas saved for it. */
data class ComingUpRow(val id: String, val area: Area, val title: String, val detail: String, val date: LocalDate, val gifts: Int = 0)

/** Birthdays, renewals and payments in the next 30 days, soonest first, minus what Today shows. */
fun comingUp(
    data: HomeData,
    attention: List<Attention>,
    today: LocalDate,
    showEvents: Boolean,
    showRenewals: Boolean,
    giftsFor: Map<String, Int> = emptyMap(),
): List<ComingUpRow> {
    val horizon = today.plusDays(30)
    val inToday = attention.filter { it.collection in COMING_UP_COLLECTIONS && it.priority != Priority.SOON }.map { it.id }.toSet()
    val events = if (showEvents) data.events.filter { !it.next.isAfter(horizon) }.map { e -> ComingUpRow(e.id, Area.PLANS, e.title, e.kind, e.next, giftsFor[e.title.lowercase()] ?: 0) } else emptyList()
    // Renewals waiting to be logged live in Today, not here.
    val renewals = if (showRenewals) data.renewals.filter { !it.next.isAfter(horizon) && !it.pending(today) }.map { ComingUpRow(it.id, Area.MONEY, it.title, it.amountText ?: "Payment", it.next) } else emptyList()
    return (events + renewals).filter { it.id !in inToday }.sortedBy { it.date }.take(6)
}
