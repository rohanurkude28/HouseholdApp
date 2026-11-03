package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    private val indiranagar = LatLng(12.9784, 77.6408)
    private fun place(id: String, kind: String, lat: Double?, lng: Double?, area: String? = null, rating: String? = null) =
        PlaceItem(id, id, kind, area, rating, null, null, null, lat, lng)

    @Test
    fun `distance between two Bengaluru spots`() {
        val koramangala = LatLng(12.9352, 77.6245)
        assertEquals(5.1, distanceKm(indiranagar, koramangala), 0.2)
    }

    @Test
    fun `coordinates from maps links`() {
        assertEquals(LatLng(12.9784, 77.6408), latLngFromLink("https://www.google.com/maps/place/Toit/@12.9784,77.6408,17z"))
        assertEquals(LatLng(12.97, 77.64), latLngFromLink("https://maps.google.com/?q=12.97,77.64"))
        assertNull(latLngFromLink("https://maps.app.goo.gl/abc"))
    }

    @Test
    fun `nearby keeps places in radius, skips destination, same need and disliked`() {
        val spar = place("spar", "Grocery", 12.9790, 77.6400)
        val places = listOf(
            place("toit", "Restaurant", 12.9790, 77.6410, rating = "❤️ Love it"),
            place("cafe", "Café", 12.9800, 77.6420),
            place("dmart", "Grocery", 12.9785, 77.6409),
            spar,
            place("far", "Restaurant", 13.05, 77.70),
            place("meh", "Restaurant", 12.9786, 77.6407, rating = "👎 Not great"),
            place("noCoords", "Salon", null, null, area = "Indiranagar 100ft road"),
        )
        // Heading to Spar: no other grocers, nothing far, nothing disliked.
        val ids = nearbyPlaces(places, LatLng(12.9790, 77.6400), "Indiranagar", 2.0, destination = spar).map { it.place.id }
        assertEquals(listOf("toit", "cafe", "noCoords").toSet(), ids.toSet())
        assertEquals("toit", ids.first()) // loved first
    }

    @Test
    fun `area text works without coordinates`() {
        val near = nearbyPlaces(listOf(place("x", "Café", null, null, area = "Indiranagar")), null, "indiranagar", 2.0)
        assertTrue(near.single().km == null)
    }
}
