package com.houseaffairs.app.core.time

import kotlinx.datetime.DayOfWeek

/**
 * A calendar date. On Android it *is* java.time.LocalDate, so the app's own code keeps using
 * java.time unchanged; on iOS it's kotlinx-datetime's. Shared rules use only what's declared here.
 */
expect class LocalDate : Comparable<LocalDate>

expect fun localDate(year: Int, month: Int, day: Int): LocalDate

/** Parses "2025-03-14". */
expect fun parseDate(iso: String): LocalDate

expect fun LocalDate.toEpochDay(): Long
expect fun dateOfEpochDay(epochDay: Long): LocalDate

expect val LocalDate.year: Int
expect val LocalDate.monthValue: Int
expect val LocalDate.dayOfMonth: Int
expect val LocalDate.dayOfWeek: DayOfWeek

expect fun LocalDate.plusMonths(months: Long): LocalDate
expect fun LocalDate.plusYears(years: Long): LocalDate

fun LocalDate.plusDays(days: Long): LocalDate = dateOfEpochDay(toEpochDay() + days)
fun LocalDate.minusDays(days: Long): LocalDate = plusDays(-days)
fun LocalDate.plusWeeks(weeks: Long): LocalDate = plusDays(7 * weeks)
fun LocalDate.minusMonths(months: Long): LocalDate = plusMonths(-months)
fun LocalDate.isBefore(other: LocalDate): Boolean = toEpochDay() < other.toEpochDay()
fun LocalDate.isAfter(other: LocalDate): Boolean = toEpochDay() > other.toEpochDay()

/** Days from [from] to [to]; negative when [to] is earlier (ChronoUnit.DAYS.between). */
fun daysBetween(from: LocalDate, to: LocalDate): Long = to.toEpochDay() - from.toEpochDay()

fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

fun LocalDate.lengthOfMonth(): Int = lengthOfMonth(year, monthValue)

/** [day] in the given month, clamped to its length (the 31st of February is the 28th or 29th). */
fun clampedDate(year: Int, month: Int, day: Int): LocalDate =
    localDate(year, month, day.coerceIn(1, lengthOfMonth(year, month)))

/** The same day [months] later, as a (year, month) pair; month is 1-based. */
fun addMonths(year: Int, month: Int, months: Int): Pair<Int, Int> {
    val index = year * 12 + (month - 1) + months
    return DateMath.floorDiv(index, 12) to DateMath.floorMod(index, 12) + 1
}

private object DateMath {
    fun floorDiv(a: Int, b: Int): Int = (a - floorMod(a, b)) / b
    fun floorMod(a: Int, b: Int): Int = ((a % b) + b) % b
}

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/**
 * English date text from a DateTimeFormatter-style pattern: EEEE/EEE, d/dd, MMMM/MMM/MM/M, yyyy/yy.
 * Other characters are copied as they are. The same on both platforms, so tests pin the wording.
 */
fun LocalDate.format(pattern: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        var n = 1
        while (i + n < pattern.length && pattern[i + n] == c) n++
        when (c) {
            'E' -> DAYS[dayOfWeek.ordinal].let { out.append(if (n >= 4) it else it.take(3)) }
            'd' -> out.append(if (n >= 2) dayOfMonth.toString().padStart(2, '0') else dayOfMonth.toString())
            'M' -> out.append(
                when {
                    n >= 4 -> MONTHS[monthValue - 1]
                    n == 3 -> MONTHS[monthValue - 1].take(3)
                    n == 2 -> monthValue.toString().padStart(2, '0')
                    else -> monthValue.toString()
                },
            )
            'y' -> out.append(if (n == 2) (year % 100).toString().padStart(2, '0') else year.toString())
            else -> repeat(n) { out.append(c) }
        }
        i += n
    }
    return out.toString()
}

/** [date] itself if it's a [day], otherwise the next [day] (TemporalAdjusters.nextOrSame). */
fun nextOrSame(date: LocalDate, day: DayOfWeek): LocalDate =
    date.plusDays(((day.ordinal - date.dayOfWeek.ordinal + 7) % 7).toLong())

fun lastDayOfMonth(year: Int, month: Int): LocalDate = localDate(year, month, lengthOfMonth(year, month))

/** Today on this phone. Only for defaults at the edges; rules take `today` as a parameter. */
expect fun systemToday(): LocalDate

/** The phone's local day for a moment in epoch millis (a saved timestamp). */
expect fun dateOfMillis(millis: Long): LocalDate

/** Now, in epoch milliseconds. */
expect fun nowMillis(): Long

// ---- More of java.time's everyday calls, so rules read the same on both phones.

fun LocalDate.plusDays(days: Int): LocalDate = plusDays(days.toLong())
fun LocalDate.minusDays(days: Int): LocalDate = plusDays(-days.toLong())
fun LocalDate.plusWeeks(weeks: Int): LocalDate = plusDays(7L * weeks)
fun LocalDate.minusWeeks(weeks: Long): LocalDate = plusDays(-7 * weeks)
fun LocalDate.plusMonths(months: Int): LocalDate = plusMonths(months.toLong())
fun LocalDate.minusMonths(months: Int): LocalDate = plusMonths(-months.toLong())
fun LocalDate.plusYears(years: Int): LocalDate = plusYears(years.toLong())
fun LocalDate.minusYears(years: Long): LocalDate = plusYears(-years)
fun LocalDate.minusYears(years: Int): LocalDate = plusYears(-years.toLong())

/** The same month, on [day] (clamped to the month's length). */
fun LocalDate.withDayOfMonth(day: Int): LocalDate = clampedDate(year, monthValue, day)
fun LocalDate.withMonth(month: Int): LocalDate = clampedDate(year, month, dayOfMonth)

/** Day of the year, 1 for 1 January. */
val LocalDate.dayOfYear: Int get() = (daysBetween(localDate(year, 1, 1), this) + 1).toInt()
fun LocalDate.withDayOfYear(day: Int): LocalDate = localDate(year, 1, 1).plusDays((day - 1).toLong())

/** The earlier and later of two dates (minOf/maxOf need Comparable, which the iOS date isn't). */
fun earlier(a: LocalDate, b: LocalDate): LocalDate = if (a.isAfter(b)) b else a
fun later(a: LocalDate, b: LocalDate): LocalDate = if (a.isBefore(b)) b else a

fun LocalDate.isEqualTo(other: LocalDate): Boolean = toEpochDay() == other.toEpochDay()

/** The Monday of [date]'s week (the week starts on Monday). */
fun mondayOf(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.ordinal.toLong())

/** The next [day] strictly after [date] (TemporalAdjusters.next). */
fun nextAfter(date: LocalDate, day: DayOfWeek): LocalDate = nextOrSame(date.plusDays(1), day)

fun LocalDate.lastDayOfMonth(): LocalDate = lastDayOfMonth(year, monthValue)
fun LocalDate.lengthOfYear(): Int = if (isLeapYear(year)) 366 else 365

/** Stand-ins for LocalDate.MIN/MAX when sorting "no date" to one end. */
val FAR_PAST: LocalDate get() = localDate(1, 1, 1)
val FAR_FUTURE: LocalDate get() = localDate(9999, 12, 31)

/** "2026-W45": the ISO week (Monday start; week 1 holds the year's first Thursday). */
fun isoWeek(date: LocalDate): String {
    val thursday = date.plusDays((3 - date.dayOfWeek.ordinal).toLong())
    val week = (thursday.dayOfYear - 1) / 7 + 1
    return "${thursday.year}-W${week.toString().padStart(2, '0')}"
}
