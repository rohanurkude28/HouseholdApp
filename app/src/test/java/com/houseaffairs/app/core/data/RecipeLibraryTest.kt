package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecipeLibraryTest {

    private val today = LocalDate.of(2026, 11, 3)
    private val pantry = listOf(
        PantryStock("p", "Paneer", 250.0, "g", 250.0, expiry = today.plusDays(1)),
        PantryStock("o", "Onion", 5.0, "pcs", 6.0),
        PantryStock("t", "Tomato", 4.0, "pcs", 6.0),
        PantryStock("c", "Capsicum", 2.0, "pcs", 2.0),
        PantryStock("m", "Milk", 1000.0, "ml", 1000.0, expiry = today.plusDays(10)),
    )

    @Test
    fun `suggests dishes that use the expiring paneer, cookable first`() {
        val ideas = useItUp(pantry, saved = SAMPLE_RECIPES, today = today)
        assertTrue(ideas.isNotEmpty())
        assertTrue(ideas.all { "Paneer" in it.rescues })
        assertEquals("Paneer bhurji", ideas.first().recipe.title) // everything in stock
    }

    @Test
    fun `family recipe wins a tie and library copy is hidden`() {
        val mine = Recipe("r1", "Paneer bhurji", listOf(Ingredient("Paneer", 200.0, "g"), Ingredient("Onion", 1.0, "pcs"), Ingredient("Tomato", 1.0, "pcs"), Ingredient("Capsicum", 1.0, "pcs")), 2, emptyList(), null)
        val ideas = useItUp(pantry, listOf(mine), today)
        assertEquals("r1", ideas.first().recipe.id)
        assertEquals(1, ideas.count { it.recipe.title == "Paneer bhurji" })
    }

    @Test
    fun `nothing expiring means no rescue ideas`() {
        assertTrue(useItUp(pantry.map { it.copy(expiry = null) }, emptyList(), today).isEmpty())
    }

    @Test
    fun `cook ideas respect mood`() {
        val sweet = cookIdeas(pantry, SAMPLE_RECIPES, today, mood = "Sweet")
        assertTrue(sweet.all { "Sweet" in it.recipe.mood })
    }

    @Test
    fun `recently cooked dishes drop, long-unmade ones rise`() {
        val ideas = cookIdeas(pantry, SAMPLE_RECIPES, today)
        val first = ideas.first().recipe.title
        val second = ideas[1].recipe.title
        val history = mapOf(first.lowercase() to today.minusDays(1), second.lowercase() to today.minusDays(30))
        val sorted = withHistory(ideas, history, today)
        assertEquals(second, sorted.first().recipe.title)
        assertEquals(first, sorted.last().recipe.title)
        assertEquals("Not made in 4 weeks", lastMadeText(today.minusDays(30), today))
    }
}
