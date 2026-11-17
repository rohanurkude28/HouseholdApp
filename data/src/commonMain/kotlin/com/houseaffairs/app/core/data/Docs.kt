package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.dayOfMonth
import com.houseaffairs.app.core.time.monthValue
import com.houseaffairs.app.core.time.year
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

fun DocumentSnapshot.str(field: String): String? = runCatching { get<String?>(field) }.getOrNull()
fun DocumentSnapshot.long(field: String): Long? = runCatching { get<Long?>(field) }.getOrNull()
fun DocumentSnapshot.bool(field: String): Boolean? = runCatching { get<Boolean?>(field) }.getOrNull()
fun DocumentSnapshot.strings(field: String): List<String> = runCatching { get<List<String>?>(field) }.getOrNull().orEmpty()
fun DocumentSnapshot.flags(field: String): Map<String, Boolean> = runCatching { get<Map<String, Boolean>?>(field) }.getOrNull().orEmpty()

/** Dates are stored as the local start of that day, as on Android. */
fun DocumentSnapshot.date(field: String): LocalDate? = runCatching { get<Timestamp?>(field) }.getOrNull()?.let { ts ->
    val d = Instant.fromEpochSeconds(ts.seconds, ts.nanoseconds).toLocalDateTime(TimeZone.currentSystemDefault()).date
    localDate(d.year, d.monthNumber, d.dayOfMonth)
}

/** Just-saved items have no server time yet; the estimate keeps them at the top. */
fun DocumentSnapshot.createdMillis(): Long? =
    runCatching { get<Timestamp?>("createdAt", ServerTimestampBehavior.ESTIMATE) }.getOrNull()?.let { it.seconds * 1000 + it.nanoseconds / 1_000_000 }

fun DocumentSnapshot.titleOr(legacy: String): String = str("title") ?: str(legacy).orEmpty()

fun startOfDay(date: kotlinx.datetime.LocalDate): Timestamp =
    date.atStartOfDayIn(TimeZone.currentSystemDefault()).let { Timestamp(it.epochSeconds, it.nanosecondsOfSecond) }

/** A timestamp field as epoch milliseconds (pending server times estimated). */
fun DocumentSnapshot.millis(field: String): Long? =
    runCatching { get<Timestamp?>(field, ServerTimestampBehavior.ESTIMATE) }.getOrNull()?.let { it.seconds * 1000 + it.nanoseconds / 1_000_000 }

/** A day as Firestore stores it: the local start of that day. */
fun com.houseaffairs.app.core.time.LocalDate.stamp(): Timestamp =
    startOfDay(kotlinx.datetime.LocalDate(year, monthValue, dayOfMonth))

/** Plain values ready to write back: days become Firestore times again, nested maps and lists too. */
fun Map<String, Any?>.forFirestore(): Map<String, Any?> = mapValues { (_, v) -> firestoreValue(v) }

private fun firestoreValue(v: Any?): Any? = when (v) {
    is com.houseaffairs.app.core.time.LocalDate -> v.stamp()
    is Map<*, *> -> v.entries.associate { (k, x) -> k.toString() to firestoreValue(x) }
    is List<*> -> v.map(::firestoreValue)
    else -> v
}

/** A timestamp field as the local day and minute of day, for "usually done at 7 pm". */
fun DocumentSnapshot.dayAndMinute(field: String): Pair<com.houseaffairs.app.core.time.LocalDate, Int>? =
    millis(field)?.let { ms ->
        val t = kotlinx.datetime.Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        com.houseaffairs.app.core.time.localDate(t.year, t.monthNumber, t.dayOfMonth) to t.hour * 60 + t.minute
    }
