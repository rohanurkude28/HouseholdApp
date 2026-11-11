package com.houseaffairs.app.core.data

import java.time.LocalDate

/**
 * Days in a row this person opened the app, ending today. A gap of one day breaks it, so the
 * count stays honest; it's kept per phone as ISO dates ([days]).
 */
fun streakOf(days: Set<String>, today: LocalDate): Int {
    var n = 0
    var d = today
    while (d.toString() in days) {
        n++
        d = d.minusDays(1)
    }
    return n
}

/** The days worth keeping: the last [keep], so the saved set stays small. */
fun keptDays(days: Set<String>, today: LocalDate, keep: Int = 120): Set<String> =
    (days + today.toString()).filter { runCatching { LocalDate.parse(it) }.getOrNull()?.let { d -> !d.isBefore(today.minusDays(keep.toLong())) } == true }.toSet()

/** The streak's line in the hero: a cheer on round numbers, quiet otherwise, nothing before day two. */
fun streakLine(n: Int): String? = when {
    n < 2 -> null
    n in STREAK_MILESTONES -> "🎉 $n days in a row!"
    else -> "🔥 $n days in a row"
}

val STREAK_MILESTONES = setOf(3, 7, 14, 21, 30, 50, 100, 200, 365)

/** One line of "What's new at home", e.g. "Ravi added Milk to the shopping list". */
data class FeedLine(val id: String, val emoji: String, val text: String, val whenMillis: Long?, val kind: ActivityKind)

/**
 * What the family added lately, newest first: the last [days] days, at most [max]. Other people's
 * things come first, since your own you already know about.
 */
fun familyFeed(recent: List<ActivityItem>, me: String, nowMillis: Long, nameOf: (String) -> String, days: Int = 3, max: Int = 5): List<FeedLine> {
    val since = nowMillis - days * 86_400_000L
    val fresh = recent.filter { (it.createdAtMillis ?: nowMillis) >= since }.sortedByDescending { it.createdAtMillis ?: nowMillis }
    val others = fresh.filter { it.createdBy != me }
    val picked = (others + fresh.filter { it.createdBy == me }).take(max).sortedByDescending { it.createdAtMillis ?: nowMillis }
    return picked.map { a ->
        val who = if (a.createdBy == me) "You" else nameOf(a.createdBy)
        val (emoji, what) = when (a.kind) {
            ActivityKind.EXPENSE -> "💸" to "spent on ${a.title.ifBlank { "something" }}"
            ActivityKind.SHOPPING -> "🛒" to "added ${a.title} to the shopping list"
            ActivityKind.TASK -> "✅" to "added a task: ${a.title}"
            ActivityKind.LIST_ITEM -> "📝" to "saved ${a.title} to a list"
            ActivityKind.GROCERY -> "🥫" to "put ${a.title} in the kitchen"
            ActivityKind.MEDICINE -> "💊" to "added ${a.title} to medicines"
            ActivityKind.SUBSCRIPTION -> "🔁" to "added ${a.title} to subscriptions"
            ActivityKind.EVENT -> "🎂" to "added ${a.title} to the calendar"
        }
        FeedLine(a.id, emoji, "$who $what", a.createdAtMillis, a.kind)
    }
}

/** "just now", "2h ago", "yesterday": short enough for a row. */
fun agoText(thenMillis: Long?, nowMillis: Long): String {
    val mins = ((nowMillis - (thenMillis ?: nowMillis)) / 60_000).coerceAtLeast(0)
    return when {
        mins < 2 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 24 * 60 -> "${mins / 60}h ago"
        mins < 48 * 60 -> "yesterday"
        else -> "${mins / (24 * 60)} days ago"
    }
}

/** The evening reminder: only when there's a streak of two or more to lose and today isn't in it yet. */
data class StreakNudge(val title: String, val text: String)

fun streakNudge(days: Set<String>, today: LocalDate): StreakNudge? {
    if (today.toString() in days) return null
    val kept = streakOf(days, today.minusDays(1))
    if (kept < 2) return null
    return StreakNudge("Keep your $kept-day streak 🔥", "Open HouseAffairs for a minute to see what's new at home.")
}
