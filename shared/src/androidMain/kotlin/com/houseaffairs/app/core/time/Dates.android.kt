package com.houseaffairs.app.core.time

import kotlinx.datetime.DayOfWeek

// java.time.LocalDate compares against ChronoLocalDate, its parent: the same ordering, a wider type.
@Suppress("NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS")
actual typealias LocalDate = java.time.LocalDate

actual fun localDate(year: Int, month: Int, day: Int): LocalDate = java.time.LocalDate.of(year, month, day)
actual fun parseDate(iso: String): LocalDate = java.time.LocalDate.parse(iso)

actual fun LocalDate.toEpochDay(): Long = toEpochDay()
actual fun dateOfEpochDay(epochDay: Long): LocalDate = java.time.LocalDate.ofEpochDay(epochDay)

actual val LocalDate.year: Int get() = getYear()
actual val LocalDate.monthValue: Int get() = getMonthValue()
actual val LocalDate.dayOfMonth: Int get() = getDayOfMonth()
actual val LocalDate.dayOfWeek: DayOfWeek get() = getDayOfWeek()

actual fun LocalDate.plusMonths(months: Long): LocalDate = plusMonths(months)
actual fun LocalDate.plusYears(years: Long): LocalDate = plusYears(years)

actual fun systemToday(): LocalDate = java.time.LocalDate.now()

actual fun dateOfMillis(millis: Long): LocalDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

actual fun nowMillis(): Long = System.currentTimeMillis()
