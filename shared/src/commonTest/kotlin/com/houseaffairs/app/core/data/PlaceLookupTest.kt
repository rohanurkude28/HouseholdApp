package com.houseaffairs.app.core.data

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/** A place's Wikipedia summary (Android: PlaceLookup). */
class PlaceLookupTest {
    @Test
    fun `a place summary becomes two short sentences and a photo`() {
        val json = """{"title":"Baga Beach","thumbnail":{"source":"https:\/\/upload.wikimedia.org\/baga.jpg","width":320},"extract":"Baga is a beach in North Goa. It is known for its nightlife. It has many shacks.","coordinates":{"lat":15.5}}"""
        val info = placeInfoFrom(json)!!
        assertEquals("Baga is a beach in North Goa. It is known for its nightlife.", info.about)
        assertEquals("https://upload.wikimedia.org/baga.jpg", info.photo)
        assertNull(placeInfoFrom("""{"title":"Nothing"}"""))
    }
}
