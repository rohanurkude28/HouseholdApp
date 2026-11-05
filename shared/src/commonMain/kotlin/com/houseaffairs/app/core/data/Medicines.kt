package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.daysBetween
import com.houseaffairs.app.core.time.isAfter
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.core.time.plusDays
import com.houseaffairs.app.core.time.toEpochDay
import com.houseaffairs.app.core.time.format

/** The form's "How often" choices; "As needed" means no schedule and no dose reminders. */
val DOSE_FREQUENCIES = listOf("As needed", "Once a day", "Twice a day", "Three times a day")

/** "Each dose" quick choices; any number with a unit works ("2 tablets", "7.5 ml"). */
val DOSE_AMOUNTS = listOf("½", "1", "2", "5 ml", "10 ml")

/**
 * A medicine being taken on a schedule: who it's for, when, how much each time, and until when.
 * [left] is how much there was at [since] (bought, or last counted), in the medicine's own unit.
 */
data class MedicineCourse(
    val id: String,
    val title: String,
    val forWhom: List<String>,
    val createdBy: String?,
    /** Minutes after midnight, earliest first. */
    val times: List<Int>,
    val perDose: Double,
    val unit: String,
    val left: Double?,
    val since: LocalDate?,
    val until: LocalDate?,
    val remindDoses: Boolean,
    /** "Remind to restock" isn't Off: worth warning before it runs out. */
    val restock: Boolean,
    /** Days ("won't take it today") with no doses, as epoch days: no reminders, not counted down. */
    val skipped: Set<Long> = emptySet(),
)

/** Dose times for [frequency]: what was picked, or the usual ones (morning, then night, then lunch). */
fun doseTimes(frequency: String?, picked: List<String>): List<Int> {
    picked.mapNotNull(::minuteOfDay).takeIf { it.isNotEmpty() }?.let { return it.distinct().sorted() }
    return when (frequency) {
        "Once a day" -> listOf(9 * 60)
        "Twice a day" -> listOf(9 * 60, 21 * 60)
        "Three times a day" -> listOf(9 * 60, 13 * 60, 21 * 60)
        else -> emptyList()
    }
}

/** "½" → 0.5, "2 tablets" → 2, "7.5 ml" → 7.5; 1 when nothing sensible was said. */
fun doseAmount(text: String?): Double {
    val t = text?.trim().orEmpty()
    if (t.startsWith("½")) return 0.5
    return Regex("^(\\d+(?:\\.\\d+)?)").find(t)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
}

/** What's left today: [MedicineCourse.perDose] at every dose since it was counted, skipped days aside. */
fun MedicineCourse.leftOn(today: LocalDate): Double? {
    val start = left ?: return null
    val from = since ?: return start
    val days = daysBetween(from, today).coerceAtLeast(0)
    val skippedDays = skipped.count { it >= from.toEpochDay() && it < today.toEpochDay() }
    return (start - (days - skippedDays) * times.size * perDose).coerceAtLeast(0.0)
}

/** How it reads in the list: "2 a day · ~8 left · until 10 Nov" (or "runs out Sat" when that's sooner). */
fun scheduleLine(c: MedicineCourse, today: LocalDate): String {
    fun n(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    val perDay = c.times.size * c.perDose
    val left = c.leftOn(today)
    val out = c.runsOutOn(today)
    return listOfNotNull(
        "${n(perDay)} ${c.unit} a day",
        left?.let { "~${n(kotlin.math.floor(it))} left" },
        if (out != null && (c.until == null || out.isBefore(c.until))) "runs out ${out.format("EEE d MMM")}" else c.until?.let { "until ${it.format("d MMM")}" },
        if (today.toEpochDay() in c.skipped) "skipping today" else null,
    ).joinToString(" · ")
}

/** The day it runs out at this pace; null without a schedule or a count. */
fun MedicineCourse.runsOutOn(today: LocalDate): LocalDate? {
    if (times.isEmpty() || perDose <= 0) return null
    val now = leftOn(today) ?: return null
    val days = (now / (times.size * perDose)).toLong()
    return today.plusDays(days)
}

/**
 * "Crocin runs out around Saturday": worth restocking when it runs out within [leadDays] and
 * the course doesn't end first. Only when restock reminders are on.
 */
fun MedicineCourse.needsRefill(today: LocalDate, leadDays: Int = 3): LocalDate? {
    if (!restock) return null
    val out = runsOutOn(today) ?: return null
    if (daysBetween(today, out) > leadDays) return null
    if (until != null && !until.isAfter(out)) return null
    return out
}

/** Whose phone rings for a dose: the people it's for, or whoever added it when it's for nobody in particular. */
fun MedicineCourse.remindsMe(me: String): Boolean = remindDoses && (me in forWhom || (forWhom.isEmpty() && createdBy == me))

/** Dose reminders for the next [days] days (today's past ones left out), up to the course's end. */
fun doseReminders(courses: List<MedicineCourse>, me: String, today: LocalDate, nowMinute: Int, days: Int = 2, nameOf: (String) -> String = { "" }): List<PlannedReminder> =
    courses.filter { it.remindsMe(me) }.flatMap { c ->
        (0..days).map { today.plusDays(it.toLong()) }
            .filter { d -> c.until == null || !d.isAfter(c.until) }
            .filter { d -> c.since == null || !d.isBefore(c.since) }
            .filter { d -> d.toEpochDay() !in c.skipped }
            .flatMap { d ->
                c.times.filter { m -> d != today || m > nowMinute }.map { m ->
                    val amount = if (c.perDose % 1.0 == 0.0) c.perDose.toLong().toString() else c.perDose.toString()
                    val who = c.forWhom.filter { it != me }.map(nameOf).filter { it.isNotEmpty() }
                    PlannedReminder(
                        "dose:${c.id}:${d.toEpochDay()}:$m", "💊 ${c.title}",
                        "$amount ${c.unit}" + (if (who.isNotEmpty()) " · for ${who.joinToString()}" else "") + " · ${formatClock(m)}",
                        d, m,
                    )
                }
            }
    }

/**
 * "Crocin runs out Saturday": a morning heads-up two days before a scheduled medicine runs out
 * (or this morning, if that's already passed), for whoever it reminds and whoever added it.
 */
fun refillReminders(courses: List<MedicineCourse>, me: String, today: LocalDate, nowMinute: Int): List<PlannedReminder> =
    courses.filter { it.remindsMe(me) || it.createdBy == me }.mapNotNull { c ->
        val out = c.needsRefill(today) ?: return@mapNotNull null
        val day = maxOf(today.toEpochDay(), out.toEpochDay() - 2)
        val date = com.houseaffairs.app.core.time.dateOfEpochDay(day)
        if (day == today.toEpochDay() && nowMinute >= MORNING_MINUTE) {
            // Past this morning's slot: say it at the next round hour instead of skipping it.
            val at = ((nowMinute / 60) + 1) * 60
            if (at >= 21 * 60) return@mapNotNull null
            PlannedReminder("refill:${c.id}:${out.toEpochDay()}", "💊 ${c.title} runs out soon", "Around ${out.format("EEE d MMM")} · add it to the buy list", date, at)
        } else {
            PlannedReminder("refill:${c.id}:${out.toEpochDay()}", "💊 ${c.title} runs out soon", "Around ${out.format("EEE d MMM")} · add it to the buy list", date, MORNING_MINUTE)
        }
    }
