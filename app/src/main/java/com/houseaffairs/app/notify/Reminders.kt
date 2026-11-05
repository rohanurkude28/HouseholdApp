package com.houseaffairs.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.houseaffairs.app.MainActivity
import com.houseaffairs.app.R
import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.PlannedReminder
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.plannedReminders
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Task and event reminders at their time. The digest is one summary a day; these are the
 * "call mom at 5:30" pop-ups. Alarms live on this phone only, so whatever the family adds is
 * re-planned whenever Home data changes, every few hours, and after a restart.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val PREFS = "reminders"
    private const val KEY_SCHEDULED = "scheduled"
    private const val WORK = "reminder_sync"

    fun createChannel(context: Context) {
        // High importance so it pops up over whatever is open, like a phone reminder does.
        val channel = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tasks and events at the time you set"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Plans this person's reminders from [data] and replaces whatever was set before. */
    fun sync(context: Context, data: HomeData, me: String) {
        val now = LocalTime.now()
        val minute = now.hour * 60 + now.minute
        // Doses two days ahead: re-planned every few hours and on every change, so that's plenty.
        val planned = (plannedReminders(data.tasks, data.events, me, LocalDate.now(), minute) +
            com.houseaffairs.app.core.data.doseReminders(data.courses, me, LocalDate.now(), minute) +
            com.houseaffairs.app.core.data.refillReminders(data.courses, me, LocalDate.now(), minute))
            .sortedWith(compareBy({ it.date.toEpochDay() }, { it.minute }))
        schedule(context, planned)
    }

    fun schedule(context: Context, planned: List<PlannedReminder>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val keep = planned.take(60)
        // Done, deleted or moved items lose their old alarm.
        (before - keep.map { it.key }.toSet()).forEach { key -> alarms.cancel(pending(context, key, null)) }
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        keep.forEach { r ->
            val at = r.date.atStartOfDay(ZoneId.systemDefault()).plusMinutes(r.minute.toLong()).toInstant().toEpochMilli()
            val intent = pending(context, r.key, r)
            // Exact when allowed; otherwise Android may shift it by a few minutes, still while dozing.
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, keep.map { it.key }.toSet()).apply()
    }

    private fun pending(context: Context, key: String, r: PlannedReminder?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction("reminder:$key")
        if (r != null) {
            intent.putExtra("key", r.key).putExtra("title", r.title).putExtra("body", r.body)
                .putExtra("reach", r.reach).putExtra("phone", r.phone)
        }
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun show(context: Context, key: String, title: String, body: String, reach: String?, phone: String?) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, key.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setColor(0xFF5B3FD9.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        // A dose: "Taken" just clears it; "Skip today" stops today's reminders and the count down.
        if (key.startsWith("dose:")) {
            val parts = key.split(":")
            fun action(what: String) = PendingIntent.getBroadcast(
                context, (key + what).hashCode(),
                Intent(context, DoseActionReceiver::class.java).setAction("dose:$what:$key")
                    .putExtra("what", what).putExtra("medicine", parts.getOrNull(1)).putExtra("day", parts.getOrNull(2)?.toLongOrNull() ?: 0L).putExtra("notification", key.hashCode()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Taken ✓", action("taken")).addAction(0, "Skip today", action("skip"))
        }
        // "Call mom": one tap from the notification to the dialer or WhatsApp.
        reachIntent(reach, phone)?.let { reachOut ->
            builder.addAction(0, reach, PendingIntent.getActivity(context, key.hashCode() + 1, reachOut.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
        }
        NotificationManagerCompat.from(context).notify(key.hashCode(), builder.build())
    }

    private fun reachIntent(reach: String?, phone: String?): Intent? {
        if (reach == null || phone.isNullOrBlank()) return null
        return when (reach) {
            "Call" -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            "SMS" -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
            else -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${com.houseaffairs.app.core.util.whatsAppNumber(phone)}"))
        }
    }

    /** Re-plans every few hours, so tasks others add while the app is closed still ring here. */
    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(4, TimeUnit.HOURS).build(),
        )
    }

    fun syncSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("${WORK}_now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }
}

/** Shows one reminder when its alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        Reminders.show(context, key, intent.getStringExtra("title").orEmpty(), intent.getStringExtra("body").orEmpty(), intent.getStringExtra("reach"), intent.getStringExtra("phone"))
    }
}

/** "Taken ✓" or "Skip today" on a dose reminder. A skip is saved, then today's reminders are planned again without it. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("notification", 0))
        if (intent.getStringExtra("what") != "skip") return
        val medicine = intent.getStringExtra("medicine") ?: return
        val day = java.time.LocalDate.ofEpochDay(intent.getLongExtra("day", LocalDate.now().toEpochDay()))
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val deps = EntryPointAccessors.fromApplication(context.applicationContext, DigestEntryPoint::class.java)
                val ready = withTimeoutOrNull(10_000) { deps.session().state.first { it is SessionState.Ready || it == SessionState.SignedOut } } as? SessionState.Ready
                if (ready != null) {
                    deps.pantry().skipDose(ready.household.id, medicine, day)
                    Reminders.syncSoon(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms are wiped by a restart, an app update or a time-zone change; plan them again. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.syncSoon(context)
    }
}

/** Loads Home data the same way the digest does and re-plans this phone's reminders. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, DigestEntryPoint::class.java)
        val ready = withTimeoutOrNull(20_000) { deps.session().state.first { it is SessionState.Ready || it == SessionState.SignedOut } }
            as? SessionState.Ready ?: return Result.success()
        val data = withTimeoutOrNull(20_000) { deps.home().observe(ready.household.id, ready.me, ready.household.baseCurrency).first { it.loaded } }
            ?: return Result.retry()
        Reminders.sync(applicationContext, data, ready.me.uid)
        return Result.success()
    }
}
