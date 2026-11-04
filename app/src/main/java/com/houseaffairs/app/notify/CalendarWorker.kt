package com.houseaffairs.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.houseaffairs.app.core.calendar.CalendarSyncer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CalendarEntryPoint {
    fun syncer(): CalendarSyncer
}

/** Two-way Google Calendar sync every few hours while it's switched on, when there's a network. */
class CalendarWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val syncer = EntryPointAccessors.fromApplication(applicationContext, CalendarEntryPoint::class.java).syncer()
        // A failure is saved for Settings to show; retrying wouldn't fix a missing permission.
        syncer.sync()
        return Result.success()
    }

    companion object {
        private const val WORK = "calendar-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CalendarWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}
