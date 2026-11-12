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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.houseaffairs.app.MainActivity
import com.houseaffairs.app.R
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.data.buildDigest
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.util.formatMinor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DigestEntryPoint {
    fun session(): SessionManager
    fun home(): HomeRepository
    fun money(): MoneyRepository
    fun settings(): SettingsRepository
    fun pantry(): com.houseaffairs.app.core.data.PantryRepository
}

/** Builds today's digest from the same data Home shows and posts one notification. */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, DigestEntryPoint::class.java)
        val test = inputData.getBoolean(KEY_TEST, false)
        val prefs = deps.settings().notificationPrefs.first()
        if (!prefs.enabled && !test) return Result.success()

        val ready = withTimeoutOrNull(20_000) { deps.session().state.first { it is SessionState.Ready || it == SessionState.SignedOut } }
            as? SessionState.Ready ?: return Result.success()
        val me = ready.me
        val data = withTimeoutOrNull(20_000) { deps.home().observe(ready.household.id, me, ready.household.baseCurrency).first { it.loaded } }
            ?: return Result.retry()
        val money = if (me.canAccess(AppModule.MONEY)) withTimeoutOrNull(15_000) { deps.money().observeMoney(ready.household.id, me.uid).first() } else null
        val currency = money?.budget?.currency ?: ready.household.baseCurrency

        val areas = prefs.areas.copy(
            money = prefs.areas.money && me.canAccess(AppModule.MONEY),
            kitchen = prefs.areas.kitchen && me.canAccess(AppModule.STOCK),
            health = prefs.areas.health && me.canAccess(AppModule.MEDICINE),
            plans = prefs.areas.plans && me.canAccess(AppModule.PLANS),
        )
        val digest = buildDigest(data, money?.summary, money, areas, LocalDate.now()) { formatMinor(it, currency) }
        when {
            digest != null -> post(digest.title, digest.lines + listOfNotNull(digest.more.takeIf { it > 0 }?.let { "+$it more in the app" }))
            test -> post("All clear today", listOf("Nothing due, expiring or over budget. Your daily digest will only show up when something needs you."))
        }
        return Result.success()
    }

    private fun post(title: String, lines: List<String>) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title).also { s -> lines.forEach(s::addLine) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: "Open HouseAffairs")
            .setStyle(if (lines.isEmpty()) null else style)
            .setColor(0xFF5B3FD9.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(DIGEST_ID, notification)
    }

    companion object {
        private const val CHANNEL = "daily_digest"
        private const val DIGEST_ID = 1001
        private const val KEY_TEST = "test"
        private const val WORK = "daily_digest"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, "Daily digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One summary a day of what's due, expiring or over budget"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Runs every day at [hour]:00. Re-scheduling with a new hour replaces the old one. */
        fun schedule(context: Context, hour: Int) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .addTag("hour:$hour")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }

        /** "Send a test now" in Settings: shows today's digest (or "all clear") right away. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<DigestWorker>().setInputData(workDataOf(KEY_TEST to true)).build(),
            )
        }
    }
}
