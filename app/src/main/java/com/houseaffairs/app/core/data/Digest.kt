package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Which areas the family wants in their daily notification. */
data class DigestPrefs(
    val money: Boolean = true,
    val kitchen: Boolean = true,
    val health: Boolean = true,
    val plans: Boolean = true,
)

data class Digest(val title: String, val lines: List<String>, val more: Int)

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * True when a dated item belongs in today's digest: on the day (and after, until done), and on
 * its chosen "remind N days before" day. Without a choice, [defaultDays] before applies too.
 */
fun remindToday(date: LocalDate, remindDays: Int?, today: LocalDate, defaultDays: Int): Boolean {
    if (remindDays == NO_REMINDER) return false
    val days = ChronoUnit.DAYS.between(today, date)
    return days <= 0 || days == (remindDays ?: defaultDays).toLong()
}

private fun whenText(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
    in Long.MIN_VALUE..-1 -> "overdue"
    0L -> "today"
    1L -> "tomorrow"
    else -> "on ${date.format(DAY)}"
}

/**
 * One calm notification a day: only what needs someone today, most urgent first, at most six
 * lines. Null when there's nothing worth interrupting for. Pure, so the rules are tested.
 */
fun buildDigest(
    data: HomeData,
    budget: BudgetSummary?,
    money: MoneyState?,
    prefs: DigestPrefs,
    today: LocalDate,
    format: (Long) -> String,
): Digest? {
    val urgent = mutableListOf<String>()
    val normal = mutableListOf<String>()

    if (prefs.plans) {
        data.tasks.forEach { t ->
            val due = t.dueDate ?: return@forEach
            if (!remindToday(due, t.remindDays, today, defaultDays = 1)) return@forEach
            val line = "${t.title} · due ${whenText(due, today)}"
            if (due.isBefore(today)) urgent += line else normal += line
        }
        data.events.forEach { e ->
            if (remindToday(e.next, e.remindDays, today, defaultDays = 1) && !e.next.isBefore(today)) {
                normal += "${e.title} · ${e.kind.lowercase()} ${whenText(e.next, today)}"
            }
        }
    }
    if (prefs.kitchen) {
        data.pantry.forEach { s ->
            val exp = s.expiry ?: return@forEach
            val days = ChronoUnit.DAYS.between(today, exp)
            when {
                days < 0 -> urgent += "${s.title} has expired"
                days <= 1 -> normal += "${s.title} expires ${whenText(exp, today)}"
            }
        }
        if (data.shopping.size >= 5) normal += "${data.shopping.size} things on the shopping list"
    }
    if (prefs.health) {
        data.medicines.forEach { s ->
            val exp = s.expiry ?: return@forEach
            val days = ChronoUnit.DAYS.between(today, exp)
            when {
                days < 0 -> urgent += "Medicine ${s.title} has expired"
                days == 7L || days == 0L -> normal += "Medicine ${s.title} expires ${whenText(exp, today)}"
            }
        }
    }
    if (prefs.money) {
        data.renewals.forEach { r ->
            if (remindToday(r.next, r.remindDays, today, defaultDays = 1) && !r.next.isBefore(today)) {
                normal += "${r.title}${r.amountText?.let { " $it" } ?: ""} due ${whenText(r.next, today)}"
            }
        }
        budget?.let { b ->
            if (b.leftMinor < 0) urgent += "Over this month's budget by ${format(-b.leftMinor)}"
            b.categories.filter { it.over }.take(2).forEach { urgent += "${it.name} is over budget" }
        }
        money?.investments?.forEach { v ->
            val m = v.investment.maturity ?: return@forEach
            val days = ChronoUnit.DAYS.between(today, m)
            if (days == 7L || days == 0L) normal += "${v.investment.title} matures ${whenText(m, today)} · ${format(v.currentMinor)}"
        }
        money?.credits?.forEach { c ->
            val e = c.expiry ?: return@forEach
            val days = ChronoUnit.DAYS.between(today, e)
            if (days == 7L || days == 1L) normal += "${c.title} (${format(c.valueMinor)}) expires ${whenText(e, today)}. Use it!"
        }
    }

    val all = urgent + normal
    if (all.isEmpty()) return null
    val shown = all.take(6)
    val title = if (all.size == 1) all.single() else "${all.size} things for today"
    return Digest(title, if (all.size == 1) emptyList() else shown, all.size - shown.size)
}
