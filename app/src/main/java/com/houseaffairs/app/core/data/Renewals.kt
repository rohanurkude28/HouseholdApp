package com.houseaffairs.app.core.data

import java.time.LocalDate

/**
 * Subscriptions and other repeating payments. A renewal becomes real spending only once it's
 * logged (by tapping "Log it", or automatically), and `lastLogged` remembers the latest one,
 * so nothing is counted twice. Missed renewals aren't back-filled: at most the latest one waits.
 * Pure, so the date rules are tested.
 */

val RENEWAL_FREQUENCIES = listOf("Weekly", "Monthly", "Every 2 months", "Quarterly", "Every 6 months", "Yearly")

/** One period after [date]; unknown frequencies count as monthly. */
fun nextPeriod(date: LocalDate, frequency: String?): LocalDate = when (frequency) {
    "Weekly" -> date.plusWeeks(1)
    "Every 2 months" -> date.plusMonths(2)
    "Quarterly" -> date.plusMonths(3)
    "Every 6 months" -> date.plusMonths(6)
    "Yearly" -> date.plusYears(1)
    else -> date.plusMonths(1)
}

/**
 * The renewal that's owed next: the first one after [lastLogged] (or from [start]), but never
 * earlier than the latest one on or before [today]. May be today or in the past (waiting to be
 * logged), or in the future.
 */
fun nextRenewal(start: LocalDate, frequency: String?, lastLogged: LocalDate?, today: LocalDate): LocalDate {
    var d = start
    // The latest renewal on or before today, if there's been one.
    var latestPast: LocalDate? = null
    while (!d.isAfter(today)) {
        latestPast = d
        d = nextPeriod(d, frequency)
    }
    var candidate = latestPast ?: start
    while (lastLogged != null && !candidate.isAfter(lastLogged)) candidate = nextPeriod(candidate, frequency)
    return candidate
}

/** The renewal waiting to be logged (on or before [today]), or null if none is. */
fun pendingRenewal(start: LocalDate, frequency: String?, lastLogged: LocalDate?, today: LocalDate): LocalDate? =
    nextRenewal(start, frequency, lastLogged, today).takeIf { !it.isAfter(today) }
