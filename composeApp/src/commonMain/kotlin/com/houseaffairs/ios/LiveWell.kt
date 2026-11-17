package com.houseaffairs.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.houseaffairs.app.core.data.WeeklyRepository
import com.houseaffairs.app.core.data.missedDays
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.parseDate
import com.houseaffairs.app.core.time.plusDays
import com.houseaffairs.app.core.time.toEpochDay
import com.houseaffairs.app.feature.review.DayCheckInDialog

/** Live to 100 on the iPhone: the same reader as Android, with Apple Health once connected in Settings. */
internal val weekly = WeeklyRepository(health = ::readHealth)

internal fun SessionState.Ready.member() = Member(uid, me.displayName, role = me.role, moduleAccess = me.moduleAccess)

/** "2026-11-12" by day, "2026-11-12 evening" once today's check-in is due (as Android marks it). */
private fun askedMark(today: LocalDate, hour: Int) = if (hour >= 20) "$today evening" else today.toString()

/**
 * The daily check-in on opening, as Android asks it: the days missed lately (and today from 8 pm),
 * once in the day and once in the evening. The latest must be answered; older ones can be
 * skipped (kept skipped on this phone) or left for tomorrow.
 */
@Composable
internal fun CheckInOnOpen(s: SessionState.Ready) {
    // "-openTab HEALTH" (test runs only) asks for Apple Health at launch, to see the permission sheet.
    if (startTab == "HEALTH") LaunchedEffect(Unit) { if (connectHealth()) savePref("healthConnected", "yes") }
    var days by remember { mutableStateOf(emptyList<LocalDate>()) }
    var required by remember { mutableStateOf<LocalDate?>(null) }
    val today = today()
    val hour = nowMinute() / 60
    fun skipped() = loadPref("diarySkipped").orEmpty().split(",").mapNotNull { runCatching { parseDate(it) }.getOrNull() }.toSet()
    fun done() { if (days.isEmpty()) savePref("diaryAsked", askedMark(today, hour)) }
    LaunchedEffect(s.uid) {
        if (loadPref("diaryAsked") == askedMark(today, hour)) return@LaunchedEffect
        val candidates = missedDays(today, hour, emptySet(), skipped())
        val written = runCatching { weekly.writtenDays(s.household.id, s.uid, candidates) }.getOrDefault(candidates.toSet())
        days = candidates.filter { it !in written }
        required = days.firstOrNull()
        done()
    }
    val day = days.firstOrNull() ?: return
    DayCheckInDialog(
        day, required = day == required, stepsOn = weekly::stepsOn, stepsSource = "Apple Health",
        onSave = { mood, note, check ->
            weekly.saveDiary(s.household.id, s.uid, day, note, mood, check)
            // Answered for today: tonight's check-in reminder isn't needed.
            if (day == today) savePref("checkedIn", day.toString())
            days = days - day; done()
        },
        onSkip = { savePref("diarySkipped", (skipped() + day).joinToString(",")); days = days - day; done() },
        onLater = { days = emptyList(); savePref("diaryAsked", askedMark(today, hour)) },
    )
}

/**
 * Everything this phone should ring for, as Android plans it: timed tasks and dates, medicine
 * doses and refills, chores and the family's habits at their usual time, the evening check-in
 * until it's answered, and tomorrow's 8 pm streak nudge (re-planned away if the app is opened).
 */
internal fun allReminders(data: com.houseaffairs.app.core.data.HomeData, me: String): List<com.houseaffairs.app.core.data.PlannedReminder> {
    val today = today()
    val minute = nowMinute()
    val checkedIn = loadPref("checkedIn")?.let { runCatching { parseDate(it) }.getOrNull() }
    val opened = loadPref("openedDays").orEmpty().split(",").filter { it.isNotBlank() }.toSet() + today.toString()
    val tomorrow = today.plusDays(1L)
    val streak = com.houseaffairs.app.core.data.streakNudge(opened, tomorrow)?.let { n ->
        listOf(com.houseaffairs.app.core.data.PlannedReminder("streak:$tomorrow", n.title, n.text, tomorrow, 20 * 60))
    }.orEmpty()
    return (
        com.houseaffairs.app.core.data.plannedReminders(data.tasks, data.events, me, today, minute) +
            com.houseaffairs.app.core.data.doseReminders(data.courses, me, today, minute) +
            com.houseaffairs.app.core.data.refillReminders(data.courses, me, today, minute) +
            com.houseaffairs.app.core.data.habitReminders(data.done, data.tasks.map { it.title }, today, minute, putOff(today)) +
            com.houseaffairs.app.core.data.choreNudges(data.tasks, data.done, me, today, minute) +
            com.houseaffairs.app.core.data.checkInReminders(today, minute, checkedIn) +
            streak
        ).sortedWith(compareBy({ it.date.toEpochDay() }, { it.minute }))
        // Test runs print the plan, since the simulator can't list scheduled notifications.
        .also { plan -> if (useEmulators) println("REMINDERS ${plan.size}: " + plan.joinToString { "${it.key.substringBefore(':')}@${it.date} ${it.minute / 60}:${(it.minute % 60).toString().padStart(2, '0')} ${it.title}" }) }
}
