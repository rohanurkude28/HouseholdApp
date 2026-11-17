package com.houseaffairs.app.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceKindTest {
    @Test
    fun `a place's kind comes from its name`() {
        assertEquals("Doctor", placeKindOf("Dr. Mehta's clinic"))
        assertEquals("Café", placeKindOf("Third Wave Coffee"))
        assertEquals("Restaurant", placeKindOf("Meghana's Biryani"))
        assertEquals("Grocery", placeKindOf("DMart Whitefield"))
        assertEquals("Pharmacy", placeKindOf("Apollo Pharmacy"))
        assertEquals("Service", placeKindOf("Ramesh the plumber"))
        assertNull(placeKindOf("Cubbon Park"))
    }
}
