package com.houseaffairs.ios

import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.localDate
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.ServerTimestampBehavior
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

// Field readers that never throw: documents written by older app versions may lack fields
// or hold them in another type, and one odd document mustn't blank the whole screen.

internal fun DocumentSnapshot.str(field: String): String? = runCatching { get<String?>(field) }.getOrNull()
internal fun DocumentSnapshot.long(field: String): Long? = runCatching { get<Long?>(field) }.getOrNull()
internal fun DocumentSnapshot.bool(field: String): Boolean? = runCatching { get<Boolean?>(field) }.getOrNull()
internal fun DocumentSnapshot.strings(field: String): List<String> = runCatching { get<List<String>?>(field) }.getOrNull().orEmpty()
internal fun DocumentSnapshot.flags(field: String): Map<String, Boolean> = runCatching { get<Map<String, Boolean>?>(field) }.getOrNull().orEmpty()

/** Dates are stored as the local start of that day, as on Android. */
internal fun DocumentSnapshot.date(field: String): LocalDate? = runCatching { get<Timestamp?>(field) }.getOrNull()?.let { ts ->
    val d = Instant.fromEpochSeconds(ts.seconds, ts.nanoseconds).toLocalDateTime(TimeZone.currentSystemDefault()).date
    localDate(d.year, d.monthNumber, d.dayOfMonth)
}

/** Just-saved items have no server time yet; the estimate keeps them at the top. */
internal fun DocumentSnapshot.createdMillis(): Long? =
    runCatching { get<Timestamp?>("createdAt", ServerTimestampBehavior.ESTIMATE) }.getOrNull()?.let { it.seconds * 1000 + it.nanoseconds / 1_000_000 }

internal fun DocumentSnapshot.titleOr(legacy: String): String = str("title") ?: str(legacy).orEmpty()

internal fun startOfDay(date: kotlinx.datetime.LocalDate): Timestamp =
    date.atStartOfDayIn(TimeZone.currentSystemDefault()).let { Timestamp(it.epochSeconds, it.nanosecondsOfSecond) }
