package com.houseaffairs.ios

import com.houseaffairs.app.core.data.*
import com.houseaffairs.app.core.data.PlannedReminder

/** Sets the phone's reminders to exactly these (asks permission the first time). */
expect fun scheduleReminders(reminders: List<PlannedReminder>)

/** True when started with "-useEmulators": the local Firebase emulators, test accounts on. */
expect val useEmulators: Boolean

/** True when also started with "-smokeTest": runs [SmokeTest] against the emulators and logs it. */
expect val smokeTest: Boolean

/** Local testing: "-joinCode ABC123" signs in as a test person and joins that home (Android's, say). */
expect val joinCode: String?

/** Local testing: "-openTab THINGS" (with -useEmulators) opens on that tab, for screenshots without tapping. */
expect val startTab: String?

/** A small text kept on this phone across launches (iOS: UserDefaults), like Android's DataStore. */
expect fun loadPref(key: String): String?
expect fun savePref(key: String, value: String)

/** What was dealt with on Home on [day] (ISO date), as Android keeps it: the day, then one id a line. */
fun handledOn(day: String): Set<String> =
    loadPref("handledDay").orEmpty().split("\n").let { lines -> if (lines.firstOrNull() == day) lines.drop(1).filter { it.isNotBlank() }.toSet() else emptySet() }

fun saveHandled(day: String, ids: Set<String>) = savePref("handledDay", (listOf(day) + ids).joinToString("\n"))

/** Whether this phone has a health app to read (Apple Health on iPhone). */
expect val healthAvailable: Boolean

/** Asks to read steps, sleep and workouts; true once asked (Apple never says whether reading was allowed). */
expect suspend fun connectHealth(): Boolean

/** Steps, sleep a night and workout minutes for [from]..[to]; null when it can't be read. */
expect suspend fun readHealth(from: com.houseaffairs.app.core.time.LocalDate, to: com.houseaffairs.app.core.time.LocalDate): com.houseaffairs.app.core.data.HealthWeek?

/** Hands files (name → text) to the phone's share sheet: Files, Drive, mail. */
expect fun shareFiles(files: List<Pair<String, String>>)
