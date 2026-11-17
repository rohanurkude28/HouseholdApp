package com.houseaffairs.ios

import com.houseaffairs.app.core.calendar.CalItem
import com.houseaffairs.app.core.calendar.GEvent
import com.houseaffairs.app.core.calendar.Synced
import com.houseaffairs.app.core.calendar.planSync
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.data.collection
import com.houseaffairs.app.core.data.stamp
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.parseDate
import com.houseaffairs.app.core.time.plusDays
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Two-way sync with the person's own "HouseAffairs" Google calendar, as Android's
 * `CalendarSyncer`: the same rules (`:shared` planSync), the same calendar and event tags, so
 * both phones can sync one account. What was last agreed is kept on this phone.
 */
private const val API = "https://www.googleapis.com/calendar/v3"

private class Calendar(private val token: String) {
    suspend fun create(): String =
        parseJson(googleCall(token, "POST", "$API/calendars", buildJsonObject { put("summary", "HouseAffairs"); put("timeZone", kotlinx.datetime.TimeZone.currentSystemDefault().id) }.toString()))["id"]!!.jsonPrimitive.content

    /** Every event, including ones deleted in Google, so deletes can be noticed. */
    suspend fun events(calendarId: String): List<GEvent> {
        val out = mutableListOf<GEvent>()
        var page: String? = null
        do {
            val json = parseJson(googleCall(token, "GET", "$API/calendars/${enc(calendarId)}/events?showDeleted=true&maxResults=2500" + (page?.let { "&pageToken=" + enc(it) } ?: "")))
            json["items"]?.jsonArray?.forEach { out += eventOf(it.jsonObject) }
            page = json["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (page != null)
        return out
    }

    suspend fun insert(calendarId: String, item: CalItem): String =
        parseJson(googleCall(token, "POST", "$API/calendars/${enc(calendarId)}/events", body(item).toString()))["id"]!!.jsonPrimitive.content

    suspend fun update(calendarId: String, eventId: String, item: CalItem) {
        googleCall(token, "PATCH", "$API/calendars/${enc(calendarId)}/events/${enc(eventId)}", body(item).toString())
    }

    /** Tags an event made in Google with the app item it became, so it's synced from now on. */
    suspend fun link(calendarId: String, eventId: String, haId: String) {
        googleCall(token, "PATCH", "$API/calendars/${enc(calendarId)}/events/${enc(eventId)}", buildJsonObject { put("extendedProperties", props(haId)) }.toString())
    }

    suspend fun delete(calendarId: String, eventId: String) {
        runCatching { googleCall(token, "DELETE", "$API/calendars/${enc(calendarId)}/events/${enc(eventId)}") }
            .onFailure { if (it !is GoogleException || it.code !in setOf(404, 410)) throw it }
    }

    private fun props(haId: String) = buildJsonObject { putJsonObject("private") { put("haId", haId) } }

    private fun body(item: CalItem) = buildJsonObject {
        put("summary", item.title)
        // All-day: Google's end date is the day after.
        putJsonObject("start") { put("date", item.date.toString()) }
        putJsonObject("end") { put("date", item.date.plusDays(1L).toString()) }
        putJsonArray("recurrence") { if (item.yearly) add("RRULE:FREQ=YEARLY") }
        put("extendedProperties", props(item.haId))
    }

}

/** One event from the API, as Android's `parseEvent`. */
private fun eventOf(o: JsonObject): GEvent {
    val start = o["start"] as? JsonObject
    fun str(e: JsonObject?, k: String) = (e?.get(k) as? JsonPrimitive)?.contentOrNull
    val date = str(start, "date")?.let { runCatching { parseDate(it) }.getOrNull() }
        ?: str(start, "dateTime")?.takeIf { it.length >= 10 }?.let { runCatching { parseDate(it.take(10)) }.getOrNull() }
    return GEvent(
        id = str(o, "id").orEmpty(),
        haId = str((o["extendedProperties"] as? JsonObject)?.get("private") as? JsonObject, "haId")?.takeIf { it.isNotEmpty() },
        title = str(o, "summary").orEmpty(),
        date = date,
        cancelled = str(o, "status") == "cancelled",
    )
}

/** On or off, when it last worked, and what went wrong; on this phone. */
internal data class CalendarState(val on: Boolean, val lastSync: Long?, val error: String?)

internal fun calendarState() = CalendarState(loadPref("calOn") == "yes", loadPref("calLast")?.toLongOrNull(), loadPref("calError"))

internal fun setCalendarOn(on: Boolean) = savePref("calOn", if (on) "yes" else "no")

private fun savedSynced(): Map<String, Synced> = runCatching {
    parseJson(loadPref("calSynced").orEmpty()).mapValues { (_, v) ->
        val o = v.jsonObject
        Synced(o["e"]!!.jsonPrimitive.content, o["t"]!!.jsonPrimitive.content, parseDate(o["d"]!!.jsonPrimitive.content))
    }
}.getOrDefault(emptyMap())

private val calendarHome = HomeRepository()

/** One round of sync for [s]; a short line saying what happened. */
internal suspend fun syncCalendar(s: SessionState.Ready): String {
    fun fail(message: String) = message.also { savePref("calError", it) }
    if (!s.me.canAccess(AppModule.PLANS)) return fail("Calendar sync covers tasks and events, which your role doesn't include")
    val ask = googleToken ?: return fail("Google isn't available here")
    val token = runCatching { ask(CALENDAR_SCOPE) }.getOrElse { return fail(it.message ?: "Not allowed") }
    val hid = s.household.id
    val uid = s.uid
    val data = withTimeoutOrNull(20_000) { calendarHome.observe(hid, uid, s.me.role, s.me::canAccess, s.household.baseCurrency).first { it.loaded } }
        ?: return fail("Couldn't read your tasks")
    val app = data.tasks.filter { it.dueDate != null && (it.assignee == null || it.assignee == uid) }.map { CalItem("tasks/${it.id}", it.title, it.dueDate!!) } +
        data.events.map { CalItem("events/${it.id}", it.title, it.next, yearly = it.repeat == "Yearly") }
    return runCatching {
        // Another person signed in on this phone: start afresh.
        if (loadPref("calOwner") != uid) listOf("calId", "calSynced", "calSkipped").forEach { savePref(it, "") }
        var synced = savedSynced()
        var skipped = loadPref("calSkipped").orEmpty().split("\n").filter { it.isNotBlank() }.toSet()
        val api = Calendar(token)
        var calendarId = loadPref("calId")?.takeIf { it.isNotBlank() } ?: api.create()
        val google = try {
            api.events(calendarId)
        } catch (e: GoogleException) {
            if (e.code != 404) throw e
            // The calendar was deleted in Google: start again with a fresh one.
            calendarId = api.create()
            synced = emptyMap(); skipped = emptySet()
            emptyList()
        }
        val plan = planSync(app, google, synced, skipped)
        val state = plan.state.toMutableMap()
        plan.create.forEach { item -> state[item.haId] = Synced(api.insert(calendarId, item), item.title, item.date) }
        plan.update.forEach { (eventId, item) -> api.update(calendarId, eventId, item) }
        plan.delete.forEach { api.delete(calendarId, it) }
        plan.pull.forEach { (haId, title, date) -> pullIntoApp(hid, haId, title, date) }
        plan.adopt.forEach { g ->
            val date = g.date ?: return@forEach
            val ref = collection(hid, "tasks").document
            ref.set(
                mapOf(
                    "type" to "TASK", "title" to g.title, "due" to date.stamp(), "assignee" to uid, "done" to false,
                    "createdBy" to uid, "visibleTo" to Visibility.everyone, "createdAt" to FieldValue.serverTimestamp, "source" to "calendar",
                ),
            )
            api.link(calendarId, g.id, "tasks/${ref.id}")
            state["tasks/${ref.id}"] = Synced(g.id, g.title, date)
        }
        savePref("calOwner", uid)
        savePref("calId", calendarId)
        savePref("calSynced", JsonObject(state.mapValues { (_, v) -> buildJsonObject { put("e", v.eventId); put("t", v.title); put("d", v.date.toString()) } }).toString())
        savePref("calSkipped", (skipped + plan.forget).joinToString("\n"))
        savePref("calLast", com.houseaffairs.app.core.time.nowMillis().toString())
        savePref("calError", "")
        val pushed = plan.create.size + plan.update.size + plan.delete.size
        listOfNotNull(
            "$pushed sent to Google".takeIf { pushed > 0 },
            "${plan.pull.size} changed from Google".takeIf { plan.pull.isNotEmpty() },
            "${plan.adopt.size} new from Google".takeIf { plan.adopt.isNotEmpty() },
        ).joinToString(" · ").ifEmpty { "Up to date" }
    }.getOrElse { fail(it.message?.take(120) ?: "Sync failed") }
}

/** A title or date changed in Google Calendar, written back to the task or event. */
private suspend fun pullIntoApp(householdId: String, haId: String, title: String, date: LocalDate) {
    val (name, id) = haId.split("/", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return
    collection(householdId, name).document(id).update(
        "title" to title, (if (name == "tasks") "due" else "date") to date.stamp(), "updatedAt" to FieldValue.serverTimestamp,
    )
}
