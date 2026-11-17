package com.houseaffairs.ios

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/** Handles the buttons; iOS holds its delegate weakly, so it's kept here. */
private class ReminderDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(center: UNUserNotificationCenter, didReceiveNotificationResponse: UNNotificationResponse, withCompletionHandler: () -> Unit) {
        val request = didReceiveNotificationResponse.notification.request
        val button = ReminderButton.of(didReceiveNotificationResponse.actionIdentifier)
        if (button == null) return withCompletionHandler()
        connectEmulatorsOnce()
        // iOS gives a pressed button a little time in the background; finish within it.
        MainScope().launch {
            withTimeoutOrNull(25_000) { runCatching { onReminderButton(request.identifier, request.content.title, request.content.body, button) } }
            withCompletionHandler()
        }
    }

    // Shown as a banner even while the app is open, as Android's are.
    override fun userNotificationCenter(center: UNUserNotificationCenter, willPresentNotification: UNNotification, withCompletionHandler: (UNNotificationPresentationOptions) -> Unit) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
    }
}

private var delegate: ReminderDelegate? = null

/** Called from the Swift app's start, before any reminder can be answered. */
fun installReminderButtons() {
    if (delegate != null) return
    val center = UNUserNotificationCenter.currentNotificationCenter()
    fun action(b: ReminderButton) = UNNotificationAction.actionWithIdentifier(b.id, b.label, UNNotificationActionOptionNone)
    fun category(id: String, buttons: List<ReminderButton>) =
        UNNotificationCategory.categoryWithIdentifier(id, buttons.map(::action), intentIdentifiers = emptyList<Any>(), options = UNNotificationCategoryOptionNone)
    center.setNotificationCategories(setOf(
        category("dose", listOf(ReminderButton.TAKEN, ReminderButton.SKIP)),
        category("habit", listOf(ReminderButton.DONE, ReminderButton.LATER, ReminderButton.TOMORROW)),
    ))
    delegate = ReminderDelegate().also { center.delegate = it }
    // Test runs: "-pressReminder habit:water the plants:2026-11-17|Water the plants?|done" presses one, as a tap would.
    val args = platform.Foundation.NSProcessInfo.processInfo.arguments
    val i = args.indexOf("-pressReminder")
    if (useEmulators && i >= 0) (args.getOrNull(i + 1) as? String)?.split("|")?.takeIf { it.size == 3 }?.let { (key, title, b) ->
        val button = ReminderButton.of(b) ?: return@let
        connectEmulatorsOnce()
        MainScope().launch {
            kotlinx.coroutines.delay(4_000)
            val r = runCatching { onReminderButton(key, title, "", button) }
            println("PRESSED $b on $key: ${r.exceptionOrNull()?.message ?: "ok"} · putOff=${loadPref("putOff").orEmpty().replace("\n", ", ")}")
        }
    }
}

actual fun snoozeReminder(key: String, title: String, body: String) {
    val id = "snooze:" + key.removePrefix("snooze:")
    val content = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(UNNotificationSound.defaultSound())
        buttonsFor(id)?.let { setCategoryIdentifier(it) }
    }
    val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(com.houseaffairs.app.core.data.SNOOZE_MINUTES * 60.0, repeats = false)
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, trigger), withCompletionHandler = null)
}
