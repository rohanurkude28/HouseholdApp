package com.houseaffairs.ios

import com.houseaffairs.app.core.data.HealthWeek
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.dayOfMonth
import com.houseaffairs.app.core.time.monthValue
import com.houseaffairs.app.core.time.plusDays
import com.houseaffairs.app.core.time.year
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeIntervalSinceDate
import platform.HealthKit.HKCategorySample
import platform.HealthKit.HKCategoryType
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepCore
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepDeep
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepREM
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepUnspecified
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKObjectQueryNoLimit
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuery
import platform.HealthKit.HKSample
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKSampleType
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.countUnit
import platform.HealthKit.predicateForSamplesWithStartDate
import kotlin.coroutines.resume

// Apple Health, read only: steps, sleep and workouts, the same three Android reads from Health
// Connect. Apple never tells an app whether reading was allowed; a refused type just reads as none.

private val store by lazy { HKHealthStore() }

actual val healthAvailable: Boolean get() = HKHealthStore.isHealthDataAvailable()

private val steps get() = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount)!!
private val sleep get() = HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis)!!
private val workouts get() = HKObjectType.workoutType()

actual suspend fun connectHealth(): Boolean {
    if (!healthAvailable) return false
    return suspendCancellableCoroutine { cont ->
        store.requestAuthorizationToShareTypes(null, setOf(steps, sleep, workouts)) { asked, _ ->
            if (cont.isActive) cont.resume(asked)
        }
    }
}

/** The local start of [day], as Apple Health's dates. */
private fun startOf(day: LocalDate): NSDate {
    val instant = kotlinx.datetime.LocalDate(day.year, day.monthValue, day.dayOfMonth).atStartOfDayIn(TimeZone.currentSystemDefault())
    return NSDate.dateWithTimeIntervalSince1970(instant.epochSeconds.toDouble())
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun stepTotal(from: NSDate, to: NSDate): Double = suspendCancellableCoroutine { cont ->
    val query = HKStatisticsQuery(steps, HKQuery.predicateForSamplesWithStartDate(from, to, 0u), HKStatisticsOptionCumulativeSum) { _, stats, _ ->
        if (cont.isActive) cont.resume(stats?.sumQuantity()?.doubleValueForUnit(HKUnit.countUnit()) ?: 0.0)
    }
    store.executeQuery(query)
}

private suspend fun samples(type: HKSampleType, from: NSDate, to: NSDate): List<HKSample> = suspendCancellableCoroutine { cont ->
    val query = HKSampleQuery(type, HKQuery.predicateForSamplesWithStartDate(from, to, 0u), HKObjectQueryNoLimit, null) { _, results, _ ->
        if (cont.isActive) cont.resume(results.orEmpty().filterIsInstance<HKSample>())
    }
    store.executeQuery(query)
}

private val ASLEEP = setOf(
    HKCategoryValueSleepAnalysisAsleepUnspecified, HKCategoryValueSleepAnalysisAsleepCore,
    HKCategoryValueSleepAnalysisAsleepDeep, HKCategoryValueSleepAnalysisAsleepREM,
).map { it.toLong() }.toSet()

actual suspend fun readHealth(from: LocalDate, to: LocalDate): HealthWeek? {
    if (!healthAvailable || loadPref("healthConnected") != "yes") return null
    return runCatching {
        val start = startOf(from)
        val end = startOf(to.plusDays(1))
        val stepCount = stepTotal(start, end).toLong()
        // Time actually asleep (not just in bed), averaged over the nights that have any.
        val asleep = samples(sleep, start, end).filterIsInstance<HKCategorySample>().filter { it.value in ASLEEP }
        val nights = asleep.map { s ->
            kotlinx.datetime.Instant.fromEpochSeconds(s.endDate.timeIntervalSince1970.toLong()).toLocalDateTime(TimeZone.currentSystemDefault()).date
        }.distinct().size
        val sleepHours = if (nights == 0) null else asleep.sumOf { it.endDate.timeIntervalSinceDate(it.startDate) } / 3600.0 / nights
        val exercise = (samples(workouts, start, end).sumOf { it.endDate.timeIntervalSinceDate(it.startDate) } / 60).toInt()
        HealthWeek(stepCount, sleepHours, exercise)
    }.getOrNull()
}
