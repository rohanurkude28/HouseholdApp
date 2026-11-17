package com.houseaffairs.app.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TagSuggestTest {
    @Test
    fun `the trip the person and known tags are suggested`() {
        val v = mapOf("merchant" to "Beach shack lunch", "trip" to "Goa trip", "tags" to listOf("food"))
        assertEquals(listOf("goa", "beach"), suggestTags(v, known = listOf("beach", "food", "kids")))
        assertEquals(listOf("riya"), suggestTags(mapOf("title" to "Art set", "event" to "Riya's birthday"), emptyList()))
    }

    @Test
    fun `assistant replies become clean tags`() {
        assertEquals(listOf("goa", "food", "beach"), tagsFromAi("#Goa, food,\nbeach."))
        assertEquals("goa, kids", withTag("goa", "kids"))
        assertEquals(listOf("goa", "kids"), withTag(listOf("goa"), "kids"))
    }
}
