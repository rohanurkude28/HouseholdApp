package com.houseaffairs.app.core.data

import java.time.LocalDate

/**
 * Days worth a quick diary line on opening the app: the last [back] days with nothing written
 * and not skipped, yesterday first. Today joins only in the evening, once there's a day to tell.
 * Pure, so it's tested.
 */
fun missedDays(today: LocalDate, nowHour: Int, written: Set<LocalDate>, skipped: Set<LocalDate>, back: Int = 3): List<LocalDate> {
    val days = (if (nowHour >= 20) listOf(today) else emptyList()) + (1..back).map { today.minusDays(it.toLong()) }
    return days.filter { it !in written && it !in skipped }
}

/** When the evening check-in reminder rings: after dinner, before bed. */
const val CHECK_IN_MINUTE = 20 * 60 + 30

/**
 * Tonight's "How was today?" reminder, and tomorrow's, unless today's check-in is already in
 * ([checkedIn] is the last day answered). Tapping it opens the app, where the check-in waits.
 */
fun checkInReminders(today: LocalDate, nowMinute: Int, checkedIn: LocalDate?): List<PlannedReminder> =
    listOf(today, today.plusDays(1)).filter { day ->
        day != checkedIn && !(day == today && nowMinute >= CHECK_IN_MINUTE)
    }.map { day ->
        PlannedReminder("checkin:$day", "How was today?", "Four taps keep your live-to-100 wheel honest.", day, CHECK_IN_MINUTE)
    }
