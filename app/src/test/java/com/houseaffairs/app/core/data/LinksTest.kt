package com.houseaffairs.app.core.data

import com.houseaffairs.app.feature.quickadd.ListTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    @Test
    fun `saved links are named by where they go`() {
        assertEquals("▶ YouTube", linkLabel("https://youtu.be/abc"))
        assertEquals("Instagram", linkLabel("https://www.instagram.com/reel/xyz/"))
        assertEquals("Map", linkLabel("https://maps.app.goo.gl/abc"))
        assertEquals("Hebbarskitchen", linkLabel("https://hebbarskitchen.com/paneer-tikka"))
    }

    @Test
    fun `a movie opens IMDb - its trailer and where to watch`() {
        val links = mediaLinks(ListTemplate.WATCH, "Dune", "2021", imdbId = "tt1160419")
        assertEquals(listOf("IMDb", "▶ Trailer", "Where to watch"), links.map { it.label })
        assertEquals("https://www.imdb.com/title/tt1160419/", links[0].url)
        // Without an id, IMDb's search.
        assertEquals("https://www.imdb.com/find/?q=Dune+2021", mediaLinks(ListTemplate.WATCH, "Dune", "2021")[0].url)
    }

    @Test
    fun `books go to Goodreads and the family's own link comes first`() {
        val links = mediaLinks(ListTemplate.BOOKS, "Atomic Habits", creator = "James Clear", savedLink = "https://www.amazon.in/dp/123")
        assertEquals(listOf("Amazon", "Goodreads", "Google Books"), links.map { it.label })
        assertEquals("https://www.goodreads.com/search?q=Atomic+Habits+James+Clear", links[1].url)
    }

    @Test
    fun `a dish links its reel and how to cook it - no duplicate YouTube`() {
        assertEquals(listOf("Instagram", "▶ How to cook"), recipeLinks("Paneer tikka", "https://instagram.com/reel/1").map { it.label })
        assertEquals(listOf("▶ YouTube"), recipeLinks("Paneer tikka", "https://youtube.com/watch?v=1").map { it.label })
        assertEquals(listOf("▶ How to cook"), recipeLinks("Paneer tikka", "not a link").map { it.label })
    }
}
