package com.houseaffairs.app.core.calendar

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GoogleCalendarTest {

    @Test
    fun `events read their app link, title and date`() {
        val allDay = JSONObject("""{"id":"g1","status":"confirmed","summary":"Pay rent","start":{"date":"2026-11-10"},"extendedProperties":{"private":{"haId":"tasks/t1"}}}""")
        assertEquals(GEvent("g1", "tasks/t1", "Pay rent", LocalDate.of(2026, 11, 10)), parseEvent(allDay))
        val timed = JSONObject("""{"id":"g2","summary":"Dentist","start":{"dateTime":"2026-11-12T17:00:00+05:30"}}""")
        assertEquals(GEvent("g2", null, "Dentist", LocalDate.of(2026, 11, 12)), parseEvent(timed))
        val deleted = JSONObject("""{"id":"g3","status":"cancelled"}""")
        assertEquals(true, parseEvent(deleted).cancelled)
    }
}
