package com.houseaffairs.app.core.calendar

import java.time.LocalDate

/**
 * Two-way sync between the app and the person's own "HouseAffairs" Google calendar. The app's
 * dated tasks and events go to the calendar as all-day events; edits made in Google Calendar come
 * back. Pure, so the rules (who wins, what gets deleted) are tested:
 * - Changed in the app since the last sync → the app's version goes to Google.
 * - Changed only in Google → it comes back to the app (title and date).
 * - Done or deleted in the app → the calendar event is removed.
 * - Deleted in Google → that item just stops syncing; app data is never deleted from the calendar.
 * - A new event in the HouseAffairs calendar → a new task in the app.
 */

/** A task or event to show in the calendar. [haId] is "tasks/<id>" or "events/<id>". */
data class CalItem(val haId: String, val title: String, val date: LocalDate, val yearly: Boolean = false)

/** An event as Google has it. [haId] is set on events the app created. */
data class GEvent(val id: String, val haId: String?, val title: String, val date: LocalDate?, val cancelled: Boolean = false)

/** What both sides last agreed on, so a change can be told apart from the other side's. */
data class Synced(val eventId: String, val title: String, val date: LocalDate)

data class SyncPlan(
    val create: List<CalItem> = emptyList(),
    /** Google event id → the app's version to write over it. */
    val update: List<Pair<String, CalItem>> = emptyList(),
    /** Google event ids to remove (the app item is done or gone). */
    val delete: List<String> = emptyList(),
    /** haId → the title and date to write back into the app. */
    val pull: List<Triple<String, String, LocalDate>> = emptyList(),
    /** New events made in the HouseAffairs calendar, to become tasks. */
    val adopt: List<GEvent> = emptyList(),
    /** haIds deleted in Google: stop syncing them. */
    val forget: Set<String> = emptySet(),
    /** The agreed state after applying the plan, except for [create], whose ids come from Google. */
    val state: Map<String, Synced> = emptyMap(),
)

fun planSync(app: List<CalItem>, google: List<GEvent>, synced: Map<String, Synced>, skipped: Set<String>): SyncPlan {
    val byHaId = google.filter { it.haId != null }.associateBy { it.haId!! }
    val byId = google.associateBy { it.id }
    val create = mutableListOf<CalItem>()
    val update = mutableListOf<Pair<String, CalItem>>()
    val delete = mutableListOf<String>()
    val pull = mutableListOf<Triple<String, String, LocalDate>>()
    val forget = mutableSetOf<String>()
    val state = mutableMapOf<String, Synced>()

    val appIds = app.map { it.haId }.toSet()
    app.filter { it.haId !in skipped }.forEach { item ->
        val last = synced[item.haId]
        val g = byHaId[item.haId] ?: last?.let { byId[it.eventId] }
        when {
            g == null && last == null -> create += item
            // Deleted in Google (or gone): stop syncing it, keep the app's data.
            g == null || g.cancelled -> forget += item.haId
            else -> {
                val appChanged = last == null || item.title != last.title || item.date != last.date
                val googleChanged = last != null && (g.title != last.title || (g.date != null && g.date != last.date))
                when {
                    appChanged && (g.title != item.title || g.date != item.date) -> {
                        update += g.id to item
                        state[item.haId] = Synced(g.id, item.title, item.date)
                    }
                    googleChanged -> {
                        val date = g.date ?: item.date
                        pull += Triple(item.haId, g.title, date)
                        state[item.haId] = Synced(g.id, g.title, date)
                    }
                    else -> state[item.haId] = Synced(g.id, item.title, item.date)
                }
            }
        }
    }
    // Done or deleted in the app: take it out of the calendar.
    synced.filterKeys { it !in appIds }.forEach { (_, s) -> if (byId[s.eventId]?.cancelled == false) delete += s.eventId }

    val known = synced.values.map { it.eventId }.toSet()
    val adopt = google.filter { it.haId == null && !it.cancelled && it.date != null && it.id !in known && it.title.isNotBlank() }

    return SyncPlan(create, update, delete, pull, adopt, forget, state)
}
