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
