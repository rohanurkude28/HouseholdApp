package com.houseaffairs.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.houseaffairs.app.MainActivity
import com.houseaffairs.app.R
import com.houseaffairs.app.core.data.streakNudge
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * At [HOUR] each evening: if this person has a streak going and hasn't opened the app today,
 * one gentle nudge. Its own channel, so it can be silenced without losing the digest.
 */
class StreakWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = EntryPointAccessors.fromApplication(applicationContext, DigestEntryPoint::class.java).settings()
        if (!settings.notificationPrefs.first().enabled) return Result.success()
        val nudge = streakNudge(settings.openedDays.first(), LocalDate.now()) ?: return Result.success()
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(nudge.title)
            .setContentText(nudge.text)
            .setColor(0xFF5B3FD9.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(STREAK_ID, notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "streak"
        private const val STREAK_ID = 1003
        private const val WORK = "streak_evening"
        /** Evening, after dinner: late enough to have missed the day, early enough to still open it. */
        const val HOUR = 20

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, "Streak reminder", NotificationManager.IMPORTANCE_LOW).apply {
                description = "One evening nudge when you haven't opened the app that day"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(HOUR, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<StreakWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            // KEEP, so reopening the app doesn't keep pushing the first run back.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}
