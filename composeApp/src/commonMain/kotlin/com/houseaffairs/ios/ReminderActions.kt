package com.houseaffairs.ios

import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.dateOfEpochDay
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.core.time.parseDate
import com.houseaffairs.app.core.time.plusDays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The buttons on a reminder, as Android's: a dose's "Taken ✓ / Skip today", a chore's
 * "Done ✓ / Later today / Tomorrow". They run without opening the app, so who's signed in is
 * kept on the phone ([rememberWho]) rather than read from the screen.
 */
enum class ReminderButton(val id: String, val label: String) {
    TAKEN("taken", "Taken ✓"), SKIP("skip", "Skip today"),
    DONE("done", "Done ✓"), LATER("later", "Later today"), TOMORROW("tomorrow", "Tomorrow");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Which buttons a reminder gets, by its key ("dose:…", "chore:…", "habit:…", maybe behind "snooze:"). */
fun buttonsFor(key: String): String? = key.removePrefix("snooze:").let {
    when {
        it.startsWith("dose:") -> "dose"
        it.startsWith("habit:") || it.startsWith("chore:") -> "habit"
        else -> null
    }
}

/** Kept when the app is open, so a button pressed later knows the home and the person. */
internal fun rememberWho(s: SessionState.Ready) {
    savePref("who", listOf(s.household.id, s.uid, s.me.role.name, s.household.baseCurrency, s.me.moduleAccess.filterValues { it }.keys.joinToString(",")).joinToString("|"))
}

/** Chores moved to a later day from their reminder ("do laundry" → that day), as Android's `putOff`. */
internal fun putOff(today: LocalDate): Map<String, LocalDate> =
    loadPref("putOff").orEmpty().split("\n").mapNotNull { line ->
        val (chore, day) = line.split("|").takeIf { it.size == 2 } ?: return@mapNotNull null
        runCatching { chore to parseDate(day) }.getOrNull()
    }.filter { !it.second.isBefore(today) }.toMap()

private fun putOffTill(chore: String, day: LocalDate) =
    savePref("putOff", (putOff(today()) + (chore to day)).entries.joinToString("\n") { "${it.key}|${it.value}" })

private val actionHome = HomeRepository()
private val actionPantry = PantryRepository()

/** Rings the same reminder again in a couple of hours (each phone's own way). */
expect fun snoozeReminder(key: String, title: String, body: String)

/** Does what [button] on reminder [key] asks, then plans the reminders again. */
suspend fun onReminderButton(key: String, title: String, body: String, button: ReminderButton) {
    val (hid, uid, roleName, currency, access) = loadPref("who")?.split("|")?.takeIf { it.size == 5 } ?: return
    val role = runCatching { Role.valueOf(roleName) }.getOrDefault(Role.ADULT)
    val modules = access.split(",").toSet()
    val canAccess: (AppModule) -> Boolean = { it.key in modules }
    suspend fun home(): HomeData? = withTimeoutOrNull(15_000) { actionHome.observe(hid, uid, role, canAccess, currency).first { it.loaded } }
    val bare = key.removePrefix("snooze:")
    val parts = bare.split(":")
    when {
        bare.startsWith("dose:") -> if (button == ReminderButton.SKIP) {
            // "Taken" just clears it; a skip stops today's doses and the count down.
            val day = parts.getOrNull(2)?.toLongOrNull()?.let(::dateOfEpochDay) ?: today()
            parts.getOrNull(1)?.let { actionPantry.skipDose(hid, it, day) }
        } else return
        button == ReminderButton.LATER -> return snoozeReminder(key, title, body)
        bare.startsWith("chore:") -> {
            // A family chore: done ticks the task off (and plans the next), tomorrow moves it.
            val task = home()?.tasks?.firstOrNull { it.id == parts.getOrNull(1) } ?: return
            if (button == ReminderButton.DONE) actionHome.completeTask(hid, uid, task)
            else actionHome.moveTask(hid, task.id, today().plusDays(1L), task.dueDate)
        }
        bare.startsWith("habit:") -> {
            val chore = parts.getOrNull(1) ?: return
            if (button == ReminderButton.DONE) actionHome.logDone(hid, uid, title.removeSuffix("?"))
            else putOffTill(chore, today().plusDays(1L))
        }
        else -> return
    }
    home()?.let { scheduleReminders(allReminders(it, uid)) }
}
