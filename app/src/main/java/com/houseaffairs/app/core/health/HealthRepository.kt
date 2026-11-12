package com.houseaffairs.app.core.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Steps, sleep and workouts over some days, as Health Connect has them. */
data class HealthWeek(val steps: Long, val sleepHoursPerNight: Double?, val exerciseMinutes: Int)

/**
 * Reads steps, sleep and workouts from Health Connect, where Google Fit, Samsung Health, Fitbit
 * and watches write them. Read only, on this phone, and only after the person allows it.
 */
@Singleton
class HealthRepository @Inject constructor(@ApplicationContext private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    /** Installed and usable (built in from Android 14; an app from the Play Store before that). */
    fun available(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun connected(): Boolean = available() && runCatching { client.permissionController.getGrantedPermissions().containsAll(permissions) }.getOrDefault(false)

    /** [from]..[to] inclusive; null when not connected or nothing could be read. */
    suspend fun read(from: LocalDate, to: LocalDate): HealthWeek? {
        if (!connected()) return null
        val zone = ZoneId.systemDefault()
        val range = TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
        return runCatching {
            val steps = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL] ?: 0L
            val sleep = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records
            val nights = sleep.map { it.endTime.atZone(zone).toLocalDate() }.distinct().size
            val sleepHours = if (nights == 0) null else sleep.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } / 60.0 / nights
            val exercise = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
            HealthWeek(steps, sleepHours, exercise)
        }.getOrNull()
    }
}
