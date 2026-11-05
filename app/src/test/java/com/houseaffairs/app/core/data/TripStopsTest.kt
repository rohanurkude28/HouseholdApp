package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TripStopsTest {
    @Test
    fun `places said in a line become stops in order`() {
        assertEquals(listOf("Cubbon Park", "Lalbagh", "MTR"), stopsFrom("Cubbon Park, Lalbagh and MTR").map { it.title })
        assertEquals(listOf("Fort", "Beach"), stopsFrom("Fort -> Beach", day = 2).map { it.title })
        assertEquals(2, stopsFrom("Fort -> Beach", day = 2).first().day)
    }

    @Test
    fun `moving a stop stays within its day`() {
        val stops = listOf(Stop(1, "A"), Stop(1, "B"), Stop(2, "C"), Stop(1, "D"))
        assertEquals(listOf("B", "A", "C", "D"), moveStop(stops, 0, later = true).map { it.title })
        assertEquals(listOf("A", "D", "C", "B"), moveStop(stops, 3, later = false).map { it.title })
        // The last of day 1 can't go later; day 2's only stop can't move.
        assertEquals(stops, moveStop(stops, 3, later = true))
        assertEquals(stops, moveStop(stops, 2, later = true))
    }

    @Test
    fun `a route goes through the stops in order`() {
        assertNull(routeUrl(emptyList()))
        val url = routeUrl(listOf(Stop(1, "Cubbon Park"), Stop(1, "Lalbagh"), Stop(1, "MTR")))!!
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=Cubbon+Park&destination=MTR&waypoints=Lalbagh",
            url,
        )
    }

    @Test
    fun `an outing is one day and a trip runs start to end`() {
        val d = LocalDate.of(2026, 11, 8)
        assertEquals(1, Trip("t", "Sunday outing", d, null, null).dayCount())
        assertEquals(3, Trip("t", "Goa", d, d.plusDays(2), null).dayCount())
    }
}
