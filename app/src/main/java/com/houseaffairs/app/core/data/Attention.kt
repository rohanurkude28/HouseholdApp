package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class Priority(val label: String) { URGENT("Urgent"), TODAY("Today"), SOON("Soon") }

/** Which Home filter an item belongs to. */
enum class Area(val label: String) { MONEY("Money"), KITCHEN("Kitchen"), HEALTH("Health"), PLANS("Plans") }

data class Attention(
    val id: String,
    val area: Area,
    val priority: Priority,
    val title: String,
    val detail: String,
    val date: LocalDate,
    /** Collection of the entry behind it (tasks, stock…), so a tap can open it; null for budget alerts. */
    val collection: String? = null,
)

/** The day [left] would be used up if spending carries on at the rate so far ([spent] over [day] days). */
internal fun runOutDate(spent: Long, left: Long, day: Int, today: LocalDate): LocalDate? {
    if (spent <= 0 || day <= 0) return null
    val perDay = spent.toDouble() / day
    return today.plusDays((left / perDay).toLong())
}

/** Attention ids for card bills; the rest of the id is the bill's [billKey]. */
const val BILL_PREFIX = "bill-"

private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

private fun relative(date: LocalDate, today: LocalDate): String = when (val days = ChronoUnit.DAYS.between(today, date)) {
    0L -> "today"
    1L -> "tomorrow"
    in 2L..6L -> "in $days days"
    else -> "on ${date.format(SHORT)}"
}

/** Next date a repeating event happens on or after [today]. */
fun nextOccurrence(date: LocalDate, repeat: String?, today: LocalDate): LocalDate {
    var next = date
    when (repeat) {
        "Yearly" -> while (next.isBefore(today)) next = next.plusYears(1)
        "Monthly" -> while (next.isBefore(today)) next = next.plusMonths(1)
    }
    return next
}

/**
 * Ranks everything that needs someone's attention: overdue and expired first, then today,
 * then the coming week. Pure, so the ordering rules are unit-tested.
 */
fun buildAttention(
    data: HomeData,
    today: LocalDate,
    budget: BudgetSummary? = null,
    bills: List<CardBill> = emptyList(),
    money: (Long) -> String = { it.toString() },
): List<Attention> {
    val items = mutableListOf<Attention>()

    // A closed card statement not yet paid: due soon, today, or overdue.
    bills.filter { !it.open && !it.paid && it.amountMinor > 0 }.forEach { b ->
        val days = ChronoUnit.DAYS.between(today, b.dueDate)
        val priority = when {
            days < 0 -> Priority.URGENT
            days == 0L -> Priority.TODAY
            days <= 5 -> Priority.SOON
            else -> return@forEach
        }
        items += Attention(
            "$BILL_PREFIX${b.key}", Area.MONEY, priority, "${b.card.name} card bill",
            "${money(b.amountMinor)} · " + if (days < 0) "was due ${b.dueDate.format(SHORT)}" else "due ${relative(b.dueDate, today)}",
            b.dueDate,
        )
    }

    budget?.let { b ->
        b.categories.forEach { c ->
            val limit = c.limitMinor ?: return@forEach
            when {
                c.over -> items += Attention("budget-${c.name}", Area.MONEY, Priority.URGENT, "${c.name} is over budget", "${money(c.spentMinor - limit)} over this month", today)
                // Spending faster than the month allows: say when it runs out at this pace, and what a day keeps it on track.
                c.pace == Pace.AHEAD -> {
                    val left = (limit - c.spentMinor).coerceAtLeast(0)
                    val perDay = left / b.daysLeft.coerceAtLeast(1) / 100 * 100
                    val gone = runOutDate(c.spentMinor, left, b.day, today)?.takeIf { it.isBefore(b.cycle.endExclusive) }
                    items += Attention(
                        "budget-${c.name}", Area.MONEY, Priority.SOON,
                        gone?.let { "${c.name} budget will be used up by ${it.format(SHORT)}" } ?: "${c.name}: spending faster than planned",
                        "${money(left)} left for ${b.daysLeft} days · keep to ${money(perDay)} a day",
                        today,
                    )
                }
            }
        }
        if (b.leftMinor < 0) {
            items += Attention("budget", Area.MONEY, Priority.URGENT, "Over this month's budget", "${money(-b.leftMinor)} over", today)
        } else if (b.pace == Pace.AHEAD) {
            items += Attention(
                "budget", Area.MONEY, Priority.TODAY, "Spending faster than this month's budget allows",
                "${money(b.leftMinor)} left for ${b.daysLeft} days · keep to ${money(b.leftMinor / b.daysLeft.coerceAtLeast(1) / 100 * 100)} a day", today,
            )
        }
    }

    data.tasks.forEach { task ->
        val due = task.dueDate ?: return@forEach
        val days = ChronoUnit.DAYS.between(today, due)
        when {
            days < 0 -> items += Attention(task.id, Area.PLANS, Priority.URGENT, task.title, "Overdue since ${due.format(SHORT)}", due, "tasks")
            days == 0L -> items += Attention(task.id, Area.PLANS, Priority.TODAY, task.title, "Due today", due, "tasks")
            days <= 2 -> items += Attention(task.id, Area.PLANS, Priority.SOON, task.title, "Due ${relative(due, today)}", due, "tasks")
        }
    }

    fun stock(list: List<StockItem>, area: Area, soonDays: Long, collection: String) = list.forEach { item ->
        val expiry = item.expiry
        val days = expiry?.let { ChronoUnit.DAYS.between(today, it) }
        when {
            days != null && days < 0 -> items += Attention(item.id, area, Priority.URGENT, item.title, "Expired · replace or throw out", expiry, collection)
            days != null && days <= 2 -> items += Attention(item.id, area, Priority.TODAY, item.title, "Expires ${relative(expiry, today)}", expiry, collection)
            days != null && days <= soonDays -> items += Attention(item.id, area, Priority.SOON, item.title, "Expires ${relative(expiry, today)}", expiry, collection)
            (item.level ?: 100) <= 25 -> items += Attention(item.id, area, Priority.SOON, item.title, "Running low · ${item.level}% left", today.plusDays(7), collection)
        }
    }
    stock(data.pantry, Area.KITCHEN, soonDays = 7, collection = "stock")
    // Medicines get a longer warning so there's time to restock.
    stock(data.medicines, Area.HEALTH, soonDays = 30, collection = "medicines")

    data.renewals.forEach { renewal ->
        val days = ChronoUnit.DAYS.between(today, renewal.next)
        val what = renewal.amountText?.let { "$it due" } ?: "Due"
        when {
            days in 0L..2L -> items += Attention(renewal.id, Area.MONEY, Priority.TODAY, renewal.title, "$what ${relative(renewal.next, today)}", renewal.next, "recurring")
            days in 3L..7L -> items += Attention(renewal.id, Area.MONEY, Priority.SOON, renewal.title, "$what ${relative(renewal.next, today)}", renewal.next, "recurring")
        }
    }

    data.events.forEach { event ->
        val days = ChronoUnit.DAYS.between(today, event.next)
        when {
            days == 0L -> items += Attention(event.id, Area.PLANS, Priority.TODAY, event.title, "${event.kind} today 🎉", event.next, "events")
            days in 1L..7L -> items += Attention(event.id, Area.PLANS, Priority.SOON, event.title, "${event.kind} ${relative(event.next, today)}", event.next, "events")
        }
    }

    return items.sortedWith(compareBy({ it.priority.ordinal }, { it.date }))
}

/** A line in Home's Today list: a task to tick off, or anything else that needs a look. */
sealed interface TodayRow {
    /** Null for a task with no date: it still shows, after everything dated. */
    val priority: Priority?
    val date: LocalDate?

    data class Task(val task: TaskItem, override val priority: Priority?) : TodayRow {
        override val date get() = task.dueDate
    }

    data class Alert(val item: Attention) : TodayRow {
        override val priority get() = item.priority
        override val date get() = item.date
    }
}

/**
 * Home's one "Today" list. Every open task due within a week or with no date sits here with a
 * tick, since tasks are what people open Home for. Alerts come from [attention], minus tasks
 * (drawn as task rows) and events or renewals more than a day or two out, which Coming up lists.
 * Urgent first, then today, then soon, then tasks with no date.
 */
fun buildToday(attention: List<Attention>, tasks: List<TaskItem>, today: LocalDate): List<TodayRow> {
    val taskRows = tasks.mapNotNull { task ->
        val due = task.dueDate ?: return@mapNotNull TodayRow.Task(task, null)
        val days = ChronoUnit.DAYS.between(today, due)
        when {
            days < 0 -> TodayRow.Task(task, Priority.URGENT)
            days == 0L -> TodayRow.Task(task, Priority.TODAY)
            days <= 7 -> TodayRow.Task(task, Priority.SOON)
            else -> null
        }
    }
    val alerts = attention
        .filter { it.collection != "tasks" }
        .filterNot { it.priority == Priority.SOON && it.collection in COMING_UP_COLLECTIONS }
        .map { TodayRow.Alert(it) }
    return (taskRows + alerts).sortedWith(
        compareBy<TodayRow> { it.priority?.ordinal ?: Priority.entries.size }.thenBy(nullsLast()) { it.date },
    )
}

/** Collections Home's Coming up card lists, so Today doesn't repeat them. */
val COMING_UP_COLLECTIONS = setOf("events", "recurring")
