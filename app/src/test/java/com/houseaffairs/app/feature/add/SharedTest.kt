package com.houseaffairs.app.feature.add

import com.houseaffairs.app.feature.quickadd.ListTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTest {

    @Test
    fun `reels and videos become recipes to try`() {
        val r = sharedToAdd("Crispy paneer pakoda in 10 min 🔥\nhttps://www.instagram.com/reel/abc123/?igsh=xyz")
        assertEquals(EntryType.RECIPE, r.type)
        assertEquals("Crispy paneer pakoda in 10 min 🔥", r.preset["title"])
        assertEquals("https://www.instagram.com/reel/abc123/?igsh=xyz", r.preset["link"])
        assertEquals("Recipe to try", sharedToAdd("https://youtu.be/xyz").preset["title"])
    }

    @Test
    fun `shop links become wishes, maps links places`() {
        val buy = sharedToAdd("https://www.amazon.in/dp/B0ABC", subject = "Philips air fryer")
        assertEquals(EntryType.WISH, buy.type)
        assertEquals(ListTemplate.BUY.label, buy.preset["template"])
        assertEquals("Philips air fryer", buy.preset["title"])
        assertEquals(EntryType.PLACE, sharedToAdd("Toit https://maps.app.goo.gl/abcd").type)
    }

    @Test
    fun `plain text goes to quick add`() {
        val r = sharedToAdd("milk, eggs and bread")
        assertEquals(null, r.type)
        assertEquals("milk, eggs and bread", r.preset[QUICK_TEXT])
    }
}
