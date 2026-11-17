package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek


/**
 * Finished things (books read, tasks done, goals reached) stay for the story: cleared a year after
 * they were done, but never before February after their year, so last year's story is still whole.
 */
fun clearsOn(done: LocalDate): LocalDate = maxOf(done.plusYears(1), localDate(done.year + 1, 2, 1))

fun shouldClear(done: LocalDate, today: LocalDate): Boolean = !today.isBefore(clearsOn(done))
