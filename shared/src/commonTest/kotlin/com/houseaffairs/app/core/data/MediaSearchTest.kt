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

    @Test
    fun `book ratings are pooled and the most rated come first`() {
        val ol = listOf(
            MediaHit("Atomic Habits (summary)", "2020", "Book", null, rating = 6.0, ratingCount = 3),
            MediaHit("Atomic Habits", "2018", "Book", null, rating = 8.0, ratingCount = 100),
        )
        val g = listOf(MediaHit("Atomic habits", "2018", "Book", "g.jpg", creator = "James Clear", rating = 9.0, ratingCount = 100))
        val merged = mergeBooks(ol, g)
        assertEquals("Atomic Habits", merged.first().title)
        assertEquals(8.5, merged.first().rating)
        assertEquals(200, merged.first().ratingCount)
        assertEquals("James Clear", merged.first().creator)
        assertEquals("g.jpg", merged.first().poster)
    }

    @Test
    fun `a rating without a count still counts`() {
        assertEquals(7.0, pooledRating(listOf(MediaHit("A", null, "Book", null, rating = 7.0))))
        assertEquals(null, pooledRating(listOf(MediaHit("A", null, "Book", null))))
    }
}
