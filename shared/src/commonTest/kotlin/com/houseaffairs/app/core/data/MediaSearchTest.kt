package com.houseaffairs.app.core.data

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class MediaSearchTest {

    @Test
    fun `variants fix misspellings and drop one word at a time`() {
        val v = searchVariants("Batman dark night!")
        assertEquals("batman dark night", v[0])
        assertEquals("batman dark knight", v[1])
        assertTrue("dark knight" in v && "batman dark" in v)
    }

    @Test
    fun `best word matches come first`() {
        val hits = listOf(
            MediaHit("Dark Was the Night", "2014", "Movie", null),
            MediaHit("Batman: The Dark Knight Returns, Part 1", "2012", "Movie", null),
            MediaHit("The Dark Knight", "2008", "Movie", null),
        )
        val ranked = rankHits("batman dark night", hits).map { it.title }
        assertEquals("The Dark Knight", ranked.first())
        assertEquals("Dark Was the Night", ranked.last())
    }

    @Test
    fun `missing book covers are filled from google books`() {
        val ol = listOf(MediaHit("The White Tiger", "2008", "Book", null), MediaHit("Atomic Habits", "2018", "Book", "ol.jpg"))
        val g = listOf(MediaHit("The White Tiger", "2008", "Book", "g1.jpg"), MediaHit("Ikigai", "2016", "Book", "g2.jpg"), MediaHit("Atomic habits", "2018", "Book", "g3.jpg"))
        val merged = mergeBooks(ol, g)
        assertEquals(listOf("The White Tiger", "Atomic Habits", "Ikigai"), merged.map { it.title })
        assertEquals(listOf("g1.jpg", "ol.jpg", "g2.jpg"), merged.map { it.poster })
    }
}
