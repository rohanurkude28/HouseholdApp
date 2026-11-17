package com.houseaffairs.app.core.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupEntryPoint {
    fun session(): SessionManager
    fun settings(): SettingsRepository
    fun driveAuth(): DriveAuth
    fun drive(): DriveRepository
}

/**
 * The daily automatic backup to Drive › HouseAffairs › Backups. Runs once a day when the
 * network allows (Wi-Fi only unless the person chose any network) and the battery isn't low.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, BackupEntryPoint::class.java)
        val today = LocalDate.now().toString()
        if (deps.settings().backupPrefs.first().lastDay == today) return Result.success()
        val ready = withTimeoutOrNull(20_000) { deps.session().state.first { it is SessionState.Ready || it == SessionState.SignedOut } }
            as? SessionState.Ready ?: return Result.success()
        return when (val auth = deps.driveAuth().authorize()) {
            is DriveAuth.Result.Token -> runCatching { deps.drive().backup(auth.value, ready.household.id, ready.user.uid) }
                .fold({ deps.settings().markBackedUp(today); Result.success() }, { Result.retry() })
            // Drive access was never allowed (or was taken back): ask once, in a notification.
            is DriveAuth.Result.NeedsConsent -> {
                com.houseaffairs.app.notify.Reminders.show(applicationContext, "backup-consent", "Backups need Google Drive", "Open HouseAffairs and tap Back up now once to allow it.", null, null)
                Result.success()
            }
            is DriveAuth.Result.Failed -> Result.retry()
        }
    }

    companion object {
        private const val WORK = "drive_backup"

        fun schedule(context: Context, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build()
            // UPDATE, so switching Wi-Fi only on or off takes effect without losing the daily rhythm.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}
