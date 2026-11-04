package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.Pillar
import com.houseaffairs.app.core.data.WellPlan
import com.houseaffairs.app.core.data.Wheel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * What Home sends Gemini for smart suggestions: real titles plus a short situation line.
 * Only changes when the meal time, the lists or what's expiring change, so Home asks rarely.
 */
internal data class PickInputs(val dishes: List<String>, val cookAbout: String, val watch: List<String>, val watchAbout: String)

private fun mealOf(hour: Int) = when (hour) {
    in 5..10 -> "breakfast"
    in 11..15 -> "lunch"
    in 16..21 -> "dinner"
    else -> "a late snack"
}

/** Builds [PickInputs]. Pure: no amounts, no names, nothing about who an entry is for. */
internal fun pickInputs(
    now: LocalDateTime,
    dishes: List<String>,
    pantry: List<PantryStock>,
    lastCooked: Map<String, LocalDate>,
    watch: List<String>,
): PickInputs {
    val today = now.toLocalDate()
    val day = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val weekend = today.dayOfWeek.value >= 6
    val expiring = pantry.filter { s -> s.expiry?.let { ChronoUnit.DAYS.between(today, it) in 0..3 } == true }.map { it.title }
    val recent = lastCooked.filterValues { ChronoUnit.DAYS.between(it, today) in 0..6 }.keys
    val cookAbout = listOfNotNull(
        "It's $day, time for ${mealOf(now.hour)}.",
        expiring.takeIf { it.isNotEmpty() }?.let { "About to expire: ${it.take(5).joinToString()}." },
        recent.takeIf { it.isNotEmpty() }?.let { "Already cooked this week: ${it.take(5).joinToString()}." },
    ).joinToString(" ")
    val watchAbout = "It's $day ${if (now.hour >= 17) "evening" else "daytime"}${if (weekend) ", a weekend" else ", a weeknight"}. A family at home."
    return PickInputs(dishes, cookAbout, watch, watchAbout)
}

/** The week's habit scores (and the person's own plan) for the coach. Pure: no names or amounts. */
internal fun coachSummary(wheel: Wheel, plan: WellPlan): String {
    val habits = wheel.habits.filter { it.score != null }.joinToString("\n") { h ->
        "- ${h.pillar.label} / ${h.name}: ${(h.score!! * 100).roundToInt()} (${h.detail})"
    }
    val mine = Pillar.entries.mapNotNull { p -> plan.commitments[p]?.takeIf { it.isNotEmpty() }?.let { "${p.label}: ${it.joinToString()}" } }
    return habits + if (mine.isEmpty()) "" else "\nTheir plan: ${mine.joinToString("; ")}"
}

/** "Water plants · done. Next: Wed 11 Nov, Prachi's turn" for a repeating task; "… · done" otherwise. */
internal fun doneMessage(task: com.houseaffairs.app.core.data.TaskItem, members: List<com.houseaffairs.app.core.model.Member>, today: LocalDate = LocalDate.now()): String {
    val next = com.houseaffairs.app.core.data.nextDue(task.dueDate, task.repeat, today) ?: return "${task.title} · done"
    val who = com.houseaffairs.app.core.data.nextInRotation(task.rotation, task.assignee)
        ?.takeIf { task.rotation.size > 1 }
        ?.let { uid -> members.firstOrNull { it.uid == uid }?.displayName?.substringBefore(' ') }
    return "${task.title} · done. Next: ${com.houseaffairs.app.feature.add.formatDate(next)}" + (who?.let { ", $it's turn" } ?: "")
}
