package com.houseaffairs.ios

import com.houseaffairs.app.core.data.*
import com.houseaffairs.app.core.data.PlannedReminder
import com.houseaffairs.app.core.time.dayOfMonth
import com.houseaffairs.app.core.time.monthValue
import com.houseaffairs.app.core.time.toEpochDay
import com.houseaffairs.app.core.time.year
import platform.Foundation.NSDateComponents
import platform.Foundation.NSProcessInfo
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

actual val useEmulators: Boolean = NSProcessInfo.processInfo.arguments.contains("-useEmulators")

// iOS keeps at most 64 waiting; the soonest ones matter.
private const val MOST = 60

actual fun scheduleReminders(reminders: List<PlannedReminder>) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { granted, _ ->
        if (!granted) return@requestAuthorizationWithOptions
        // Replaced as a whole each time, like Android's alarms, so a done task stops ringing;
        // a "Later today" snooze stays, since the plan doesn't know about it.
        center.getPendingNotificationRequestsWithCompletionHandler { pending ->
            // A re-plan from a reminder's button has no digest in it; the one already set stays.
            val keepDigest = reminders.none { it.key.startsWith("digest:") }
            val old = pending.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }
                .filter { !it.startsWith("snooze:") && !(keepDigest && it.startsWith("digest:")) }
            center.removePendingNotificationRequestsWithIdentifiers(old)
            plan(center, reminders)
        }
    }
}

private fun plan(center: UNUserNotificationCenter, reminders: List<PlannedReminder>) {
    reminders.sortedBy { it.date.toEpochDay() * 1440 + it.minute }.take(MOST).forEach { r ->
            val content = UNMutableNotificationContent().apply {
                setTitle(r.title)
                setBody(r.body)
                setSound(UNNotificationSound.defaultSound())
                buttonsFor(r.key)?.let { setCategoryIdentifier(it) }
            }
            val at = NSDateComponents().apply {
                setYear(r.date.year.toLong())
                setMonth(r.date.monthValue.toLong())
                setDay(r.date.dayOfMonth.toLong())
                setHour((r.minute / 60).toLong())
                setMinute((r.minute % 60).toLong())
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(r.key, content, trigger), withCompletionHandler = null)
        }
}

actual val smokeTest: Boolean = useEmulators && NSProcessInfo.processInfo.arguments.contains("-smokeTest")

actual val joinCode: String? = NSProcessInfo.processInfo.arguments.let { args ->
    val i = args.indexOf("-joinCode")
    if (useEmulators && i >= 0) args.getOrNull(i + 1) as? String else null
}

actual val startTab: String? = if (!useEmulators) null else NSProcessInfo.processInfo.arguments.let { args ->
    val i = args.indexOf("-openTab")
    if (i >= 0 && i + 1 < args.size) args[i + 1] as? String else null
}

actual fun loadPref(key: String): String? = platform.Foundation.NSUserDefaults.standardUserDefaults.stringForKey(key)
actual fun savePref(key: String, value: String) = platform.Foundation.NSUserDefaults.standardUserDefaults.setObject(value, key)
