package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacrosTest {

    @Test
    fun `macros read from the recipe text`() {
        assertEquals(Macros(25.0, 30.0, 12.0, 4.0), parseMacros("25g · 30g · 12g · 4g"))
        assertEquals(Macros(10.5, 20.0, 5.0, 3.0), parseMacros("10.5 20 5 3"))
        assertNull(parseMacros("high protein"))
    }

    @Test
    fun `a day adds up the dishes eaten, own recipes first`() {
        val mine = Recipe("r", "Dal tadka", emptyList(), 2, emptyList(), null, macros = "12g · 30g · 5g · 8g")
        val day = dayMacros(listOf("Dal tadka", "Aloo tikki", "Mystery dish"), listOf(mine))!!
        assertEquals(Macros(16.0, 58.0, 14.0, 11.0), day)
        assertEquals(16 * 4 + 58 * 4 + 14 * 9, day.kcal)
    }
}
