package com.houseaffairs.app.feature.stock

import com.houseaffairs.app.core.data.PantryStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchDishesTest {

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `search finds dishes by name or ingredient, makeable first`() {
        val pantry = listOf(PantryStock("p", "Paneer", 500.0, "g", 500.0), PantryStock("o", "Onion", 3.0, "pcs", 3.0), PantryStock("t", "Tomato", 3.0, "pcs", 3.0), PantryStock("c", "Capsicum", 1.0, "pcs", 1.0))
        val results = searchDishes("paneer", pantry, emptyList(), today)
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().fit.canCook)
        assertTrue(results.all { r -> r.recipe.title.lowercase().contains("paneer") || r.recipe.ingredients.any { it.name == "Paneer" } })
        assertEquals(emptyList<Any>(), searchDishes("p", pantry, emptyList(), today))
    }
}
