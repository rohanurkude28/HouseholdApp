package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CravingsTest {

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `a craving splits into parts with tastes`() {
        val parts = cravingParts("I want to eat something crispy with a savoury curry")
        assertEquals(listOf("Crispy", "Savoury curry"), parts.map { it.label })
        assertEquals(setOf("Crispy"), parts[0].tastes)
        assertTrue(parts[1].curry)
    }

    @Test
    fun `each part gets matching dishes, what you can make first`() {
        val pantry = listOf(
            PantryStock("p", "Potato", 6.0, "pcs", 6.0),
            PantryStock("pe", "Peas", 200.0, "g", 200.0),
            PantryStock("d", "Toor dal", 500.0, "g", 500.0),
            PantryStock("o", "Onion", 4.0, "pcs", 4.0),
            PantryStock("t", "Tomato", 4.0, "pcs", 4.0),
        )
        val ideas = cravingIdeas("crispy with a savoury curry", pantry, emptyList(), today).toMap()
        assertEquals("Aloo tikki", ideas.getValue("Crispy").first().recipe.title)
        val curries = ideas.getValue("Savoury curry").map { it.recipe.title }
        assertTrue(curries.first() in setOf("Dal tadka", "Aloo matar"))
        assertTrue(curries.none { it in setOf("Masala omelette", "Paneer bhurji") })
    }

    @Test
    fun `a named ingredient narrows the dishes`() {
        val ideas = cravingIdeas("something with paneer", emptyList(), emptyList(), today)
        assertTrue(ideas.single().second.all { it.recipe.ingredients.any { i -> i.name == "Paneer" } })
    }
}
