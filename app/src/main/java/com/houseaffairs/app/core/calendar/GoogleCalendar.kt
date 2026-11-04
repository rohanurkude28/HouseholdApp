package com.houseaffairs.app.core.calendar

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

/** Google Calendar returned an error; [code] 404 means the calendar or event is gone. */
class CalendarException(val code: Int, message: String) : Exception(message)

/**
 * A thin client for the Calendar REST API, used with the `calendar.app.created` scope: it can only
 * see and change calendars this app made, never the person's other calendars. Blocking: call it
 * off the main thread.
 */
class GoogleCalendar(private val token: String) {

    /** Makes the person's "HouseAffairs" calendar and returns its id. */
    fun createCalendar(): String =
        call("POST", "calendars", JSONObject().put("summary", "HouseAffairs").put("timeZone", ZoneId.systemDefault().id))!!.getString("id")

    /** Every event in [calendarId], including ones deleted in Google (so deletes can be noticed). */
    fun events(calendarId: String): List<GEvent> {
        val out = mutableListOf<GEvent>()
        var page: String? = null
        do {
            val query = "showDeleted=true&maxResults=2500" + (page?.let { "&pageToken=" + enc(it) } ?: "")
            val json = call("GET", "calendars/${enc(calendarId)}/events?$query") ?: break
            val items = json.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) out += parseEvent(items.getJSONObject(i))
            page = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (page != null)
        return out
    }

    fun insert(calendarId: String, item: CalItem): String = call("POST", "calendars/${enc(calendarId)}/events", body(item))!!.getString("id")

    fun update(calendarId: String, eventId: String, item: CalItem) {
        call("PATCH", "calendars/${enc(calendarId)}/events/${enc(eventId)}", body(item))
    }

    /** Tags an event made in Google with the app item it became, so it's synced from now on. */
    fun link(calendarId: String, eventId: String, haId: String) {
        call("PATCH", "calendars/${enc(calendarId)}/events/${enc(eventId)}", JSONObject().put("extendedProperties", privateProps(haId)))
    }

    fun delete(calendarId: String, eventId: String) {
        runCatching { call("DELETE", "calendars/${enc(calendarId)}/events/${enc(eventId)}") }
            .onFailure { if (it !is CalendarException || it.code !in setOf(404, 410)) throw it }
    }

    private fun body(item: CalItem) = JSONObject()
        .put("summary", item.title)
        // All-day: Google's end date is the day after.
        .put("start", JSONObject().put("date", item.date.toString()))
        .put("end", JSONObject().put("date", item.date.plusDays(1).toString()))
        .put("recurrence", if (item.yearly) JSONArray().put("RRULE:FREQ=YEARLY") else JSONArray())
        .put("extendedProperties", privateProps(item.haId))

    private fun privateProps(haId: String) = JSONObject().put("private", JSONObject().put("haId", haId))

    private fun call(method: String, path: String, body: JSONObject? = null): JSONObject? {
        val c = URL("https://www.googleapis.com/calendar/v3/$path").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        // HttpURLConnection doesn't know PATCH; Google accepts this override.
        if (method == "PATCH") {
            c.requestMethod = "POST"
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            c.requestMethod = method
        }
        c.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw CalendarException(code, c.errorStream?.bufferedReader()?.readText()?.take(300) ?: "HTTP $code")
            val text = c.inputStream.bufferedReader().readText()
            return if (text.isBlank()) null else JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/** One event from the API: its app link, title and all-day (or timed) date. Pure, so it's tested. */
fun parseEvent(o: JSONObject): GEvent {
    val start = o.optJSONObject("start")
    val date = start?.optString("date")?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: start?.optString("dateTime")?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    return GEvent(
        id = o.getString("id"),
        haId = o.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("haId")?.takeIf { it.isNotEmpty() },
        title = o.optString("summary"),
        date = date,
        cancelled = o.optString("status") == "cancelled",
    )
}
