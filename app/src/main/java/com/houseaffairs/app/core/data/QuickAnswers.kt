package com.houseaffairs.app.core.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A question answered on the spot in the add sheet: a title and a few lines. */
data class QuickAnswer(val title: String, val lines: List<String>)

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

private fun whenLine(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        days < 0 -> "${date.format(DAY)} (${-days} days ago)"
        else -> "${date.format(DAY)} · in $days days"
    }
}

/**
 * "Do we have eggs?": what's at home under that name (how much, use by, where it's kept), and
 * whether it's already on the buy list. Pure, so it's tested.
 */
fun haveAnswer(item: String, stock: List<PantryStock>, shopping: List<String>, today: LocalDate): QuickAnswer {
    val found = stock.filter { sameIngredient(it.title, item) || it.title.contains(item, ignoreCase = true) }
    val onList = shopping.any { sameIngredient(it, item) || it.contains(item, ignoreCase = true) }
    if (found.isEmpty()) {
        return QuickAnswer(
            "No ${item.lowercase()} at home",
            listOf(if (onList) "It's already on the buy list." else "Say “need ${item.lowercase()}” to put it on the buy list."),
        )
    }
    val lines = found.map { s ->
        listOfNotNull(
            s.title,
            s.amount?.let { a -> "${if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()} ${s.unit}".trim() + " left" },
            s.expiry?.let { e -> if (e.isBefore(today)) "expired ${e.format(DAY)}" else "use by ${whenLine(e, today)}" },
            s.keptAt?.takeIf { it.isNotBlank() }?.let { "📍 $it" },
        ).joinToString(" · ")
    } + listOfNotNull("Also on the buy list.".takeIf { onList })
    return QuickAnswer("Yes, you have ${item.lowercase()}", lines)
}

private fun words(text: String) = text.lowercase().replace("'s", "").split(Regex("[^a-z0-9]+")).filter { it.length > 1 && it !in setOf("the", "my", "our", "next", "is") }

/** "When is Riya's birthday?": the closest match among events, renewals and tasks, by the words said. */
fun whenAnswer(subject: String, events: List<EventItem>, renewals: List<Renewal>, tasks: List<TaskItem>, today: LocalDate): QuickAnswer {
    val asked = words(subject)
    data class Hit(val title: String, val date: LocalDate, val score: Int)
    fun score(title: String) = asked.count { w -> words(title).any { it.startsWith(w) || w.startsWith(it) } }
    val hits = events.map { Hit(it.title, it.next, score(it.title + " " + it.kind)) } +
        renewals.map { Hit(it.title + " renews", it.next, score(it.title + " renewal renew")) } +
        tasks.mapNotNull { t -> t.dueDate?.let { Hit(t.title, it, score(t.title)) } }
    val best = hits.filter { it.score > 0 }.maxWithOrNull(compareBy<Hit> { it.score }.thenBy { -it.date.toEpochDay() })
        ?: return QuickAnswer("I couldn't find “$subject”", listOf("Add it with a date, like “Riya birthday 12 Dec”, and I'll remember."))
    return QuickAnswer("${best.title}: ${whenLine(best.date, today)}", emptyList())
}

/** "What's due this week?": tasks, events and renewals in that stretch, soonest first. Overdue tasks lead. */
fun agendaAnswer(period: String, tasks: List<TaskItem>, events: List<EventItem>, renewals: List<Renewal>, today: LocalDate): QuickAnswer {
    val (from, to) = when (period) {
        "tomorrow" -> today.plusDays(1) to today.plusDays(1)
        "this week" -> today to today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        "this weekend" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).let { it to it.plusDays(1) }
        else -> today to today
    }
    fun inRange(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
    val overdue = if (period == "tomorrow") emptyList() else tasks.filter { it.dueDate?.isBefore(today) == true }
    val items = (tasks.mapNotNull { t -> t.dueDate?.takeIf(::inRange)?.let { it to "✅ ${t.title}" + (t.time?.let { tm -> " · $tm" } ?: "") } } +
        events.filter { inRange(it.next) }.map { it.next to "🎂 ${it.title}" } +
        renewals.filter { inRange(it.next) }.map { it.next to "🔁 ${it.title}" + (it.amountText?.let { a -> " · $a" } ?: "") })
        .sortedBy { it.first }
    val lines = overdue.map { "⚠️ ${it.title} · was due ${it.dueDate!!.format(DAY)}" } +
        items.map { (d, line) -> if (from == to) line else "${d.format(DAY)} · $line" }
    val label = if (period == "today") "today" else period
    return if (lines.isEmpty()) QuickAnswer("Nothing due $label 🎉", emptyList())
    else QuickAnswer(if (lines.size == 1) "1 thing $label" else "${lines.size} things $label", lines)
}

/** "Anything to buy?": the buy list, or that it's empty. */
fun toBuyAnswer(shopping: List<String>): QuickAnswer =
    if (shopping.isEmpty()) QuickAnswer("Nothing on the buy list", listOf("Say “need milk” to add something."))
    else QuickAnswer(if (shopping.size == 1) "1 thing to buy" else "${shopping.size} things to buy", shopping.map { "🛒 $it" })

/** "Got salary 85000": money coming in isn't spending, so it isn't logged as an expense. */
fun incomeAnswer(): QuickAnswer = QuickAnswer(
    "Money coming in isn't spending",
    listOf(
        "HouseAffairs tracks spending against your monthly budget, so this isn't logged as an expense.",
        "To keep some aside, say “put 10000 in the gullak”.",
    ),
)
