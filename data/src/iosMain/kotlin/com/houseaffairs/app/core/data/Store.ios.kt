package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.localDate
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.cinterop.toKString
import platform.Foundation.NSDate
import platform.Foundation.valueForKey
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun platformValue(v: Any): Any? = when (v) {
    is NSNumber -> when (v.objCType?.toKString()) {
        "c", "B" -> v.boolValue
        "d", "f" -> v.doubleValue
        else -> v.longLongValue
    }
    is NSDate -> day(v.timeIntervalSince1970)
    // FIRTimestamp: read through Objective-C's key-value access, since its class isn't visible here.
    is platform.darwin.NSObject -> runCatching {
        val seconds = (v.valueForKey("seconds") as? NSNumber)?.longLongValue ?: return@runCatching v
        day(seconds.toDouble())
    }.getOrDefault(v)
    else -> v
}

private fun day(epochSeconds: Double) = Instant.fromEpochMilliseconds((epochSeconds * 1000).toLong())
    .toLocalDateTime(TimeZone.currentSystemDefault()).date.let { localDate(it.year, it.monthNumber, it.dayOfMonth) }

/** For backups: exact times and map points, read through key-value access (Firebase's classes aren't visible here). */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun platformBackupValue(v: Any): Any? = when (v) {
    is NSNumber -> platformValue(v)
    is NSDate -> mapOf("__time" to (v.timeIntervalSince1970 * 1000).toLong())
    is platform.darwin.NSObject -> runCatching {
        val seconds = (v.valueForKey("seconds") as? NSNumber)?.longLongValue
        if (seconds != null) {
            val nanos = (v.valueForKey("nanoseconds") as? NSNumber)?.longLongValue ?: 0L
            return@runCatching mapOf("__time" to seconds * 1000 + nanos / 1_000_000)
        }
        val lat = (v.valueForKey("latitude") as? NSNumber)?.doubleValue ?: return@runCatching v.toString()
        val lng = (v.valueForKey("longitude") as? NSNumber)?.doubleValue ?: 0.0
        mapOf("__geo" to listOf(lat, lng))
    }.getOrDefault(v.toString())
    else -> v.toString()
}
