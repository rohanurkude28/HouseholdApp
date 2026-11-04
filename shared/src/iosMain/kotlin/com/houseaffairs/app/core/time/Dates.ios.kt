package com.houseaffairs.app.core.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

actual typealias LocalDate = kotlinx.datetime.LocalDate

actual fun localDate(year: Int, month: Int, day: Int): LocalDate = kotlinx.datetime.LocalDate(year, month, day)
actual fun parseDate(iso: String): LocalDate = kotlinx.datetime.LocalDate.parse(iso)

actual fun LocalDate.toEpochDay(): Long = toEpochDays().toLong()
actual fun dateOfEpochDay(epochDay: Long): LocalDate = kotlinx.datetime.LocalDate.fromEpochDays(epochDay.toInt())

actual val LocalDate.year: Int get() = this.year
actual val LocalDate.monthValue: Int get() = monthNumber
actual val LocalDate.dayOfMonth: Int get() = this.dayOfMonth
actual val LocalDate.dayOfWeek: DayOfWeek get() = this.dayOfWeek

// kotlinx clamps to the month's last day, like java.time.
actual fun LocalDate.plusMonths(months: Long): LocalDate = plus(months.toInt(), DateTimeUnit.MONTH)
actual fun LocalDate.plusYears(years: Long): LocalDate = plus(years.toInt(), DateTimeUnit.YEAR)

actual fun systemToday(): LocalDate = kotlinx.datetime.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault())
