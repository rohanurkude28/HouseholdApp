package com.houseaffairs.app.core.data

import com.houseaffairs.app.feature.quickadd.ListTemplate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One card of the home-screen widget; [route] is where a tap opens ("plans/TASKS"). */
data class WidgetCard(val emoji: String, val title: String, val line: String, val route: String)

/**
 * What the home-screen widget rotates through: today's tasks, the next occasion, the live-well
 * score with one suggestion, something to watch together and a book to read, and what's
 * expiring. Only cards with something to say; never money, since anyone can see a home screen.
 * Pure, so it's tested.
 */
fun widgetCards(
    data: HomeData,
    wheel: Wheel?,
    nudge: Nudge?,
    media: List<WatchItem>,
    today: LocalDate,
): List<WidgetCard> = buildList {
    val due = data.tasks.filter { t -> t.dueDate?.let { !it.isAfter(today) } == true }
    when {
        due.isNotEmpty() -> add(WidgetCard("✅", if (due.size == 1) "1 thing today" else "${due.size} things today", due.take(2).joinToString(" · ") { it.title }, "plans/TASKS"))
        data.tasks.isNotEmpty() -> add(WidgetCard("✅", "Nothing due today", "Next: ${data.tasks.first().title}", "plans/TASKS"))
    }
    data.events.filter { !it.next.isAfter(today.plusDays(14)) }.minByOrNull { it.next }?.let { e ->
        val days = ChronoUnit.DAYS.between(today, e.next)
        add(WidgetCard(if (e.kind == "Birthday") "🎂" else "📅", e.title, when (days) { 0L -> "Today"; 1L -> "Tomorrow"; else -> "In $days days" }, "plans/EVENTS"))
    }
    if (wheel != null && wheel.habits.any { it.score != null }) {
        val tip = nudge?.suggestion ?: wheel.focus?.tip
        add(WidgetCard("🌿", "Live well · ${wheel.overall}/100", tip ?: "A good week so far. Keep it going.", "home"))
    }
    fun pick(template: ListTemplate) = media.filter { it.template == template }
        // Planned first, then the family's favourite, then the best rated.
        .sortedWith(compareBy<WatchItem>({ it.status != "PLANNED" }, { -it.votes.size }, { -(it.imdb?.toDoubleOrNull() ?: it.rating ?: 0.0) }))
        .firstOrNull()
    pick(ListTemplate.WATCH)?.let { w ->
        add(WidgetCard("🎬", "Watch together: ${w.title}", listOfNotNull(w.imdb?.takeIf { it.isNotEmpty() }?.let { "IMDb $it" } ?: w.rating?.let { "★ %.1f".format(it) }, w.whereToWatch).joinToString(" · ").ifEmpty { "From your watch list" }, "plans/LISTS"))
    }
    pick(ListTemplate.BOOKS)?.let { b ->
        add(WidgetCard("📚", "Read next: ${b.title}", listOfNotNull(b.creator, b.rating?.let { "★ %.1f".format(it) }).joinToString(" · ").ifEmpty { "From your reading list" }, "plans/LISTS"))
    }
    val expiring = (data.pantry + data.medicines).filter { s -> s.expiry?.let { !it.isAfter(today.plusDays(3)) } == true }
    if (expiring.isNotEmpty()) add(WidgetCard("⏳", "Use soon", expiring.take(3).joinToString(" · ") { it.title }, "stock/PANTRY"))
    if (data.shopping.isNotEmpty()) add(WidgetCard("🛒", "${data.shopping.size} to buy", data.shopping.take(3).joinToString(" · ") { it.name }, "stock/SHOPPING"))
    // Never an empty widget.
    if (isEmpty()) add(WidgetCard("🏠", "All calm at home", "Tap to add anything in one line", "home"))
}
