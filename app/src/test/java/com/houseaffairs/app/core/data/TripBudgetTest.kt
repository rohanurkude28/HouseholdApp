package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripBudgetTest {
    private val money: (Long) -> String = { "₹$it" }

    @Test
    fun `a trip's budget reads fine, nearing, then over, and over is allowed`() {
        assertEquals(BudgetLevel.FINE, tripBudget(5_000, 20_000, money)!!.level)
        assertEquals("Nearing the budget · ₹3000 left of ₹20000", tripBudget(17_000, 20_000, money)!!.line)
        val over = tripBudget(23_000, 20_000, money)!!
        assertEquals(BudgetLevel.OVER, over.level)
        assertEquals("₹3000 over the ₹20000 budget", over.line)
        assertNull(tripBudget(5_000, null, money))
    }

    @Test
    fun `saving into a trip says where it stands`() {
        assertEquals("₹15000 left of Goa's budget", tripSavedNote("Goa", 5_000, 20_000, money))
        assertEquals("Goa is nearing its budget: ₹1000 left", tripSavedNote("Goa", 19_000, 20_000, money))
        assertEquals("Goa is ₹500 over budget", tripSavedNote("Goa", 20_500, 20_000, money))
        assertEquals("Goa so far: ₹5000", tripSavedNote("Goa", 5_000, null, money))
    }

    @Test
    fun `a place's map search is narrowed to the trip`() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=Baga+Beach%2C+Goa", routeUrl(listOf(Stop(1, "Baga Beach")), area = "Goa"))
        assertEquals("https://www.google.com/maps/search/?api=1&query=Old+Goa+Church", routeUrl(listOf(Stop(1, "Old Goa Church")), area = "Goa"))
    }

    @Test
    fun `a place's summary becomes two short sentences and a photo`() {
        val json = """{"title":"Baga Beach","thumbnail":{"source":"https:\/\/upload.wikimedia.org\/baga.jpg","width":320},"extract":"Baga is a beach in North Goa. It is known for its nightlife. It has many shacks.","coordinates":{"lat":15.5}}"""
        val info = placeInfoFrom(json)!!
        assertEquals("Baga is a beach in North Goa. It is known for its nightlife.", info.about)
        assertEquals("https://upload.wikimedia.org/baga.jpg", info.photo)
        assertNull(placeInfoFrom("""{"title":"Nothing"}"""))
    }
}
