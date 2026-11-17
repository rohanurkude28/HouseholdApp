package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class DuplicateRecipeTest {
    private fun recipe(id: String, title: String, vararg ingredients: String) =
        Recipe(id, title, ingredients.map { Ingredient(it, 100.0, "g") }, 2, emptyList(), null)

    private val saved = listOf(recipe("r1", "Paneer tikka", "paneer", "curd", "spices"))

    @Test
    fun `same dish with the same ingredients is a duplicate`() {
        assertEquals("r1", duplicateRecipe("paneer  Tikka ", listOf("Spices", "Paneer", "curds"), saved)?.id)
    }

    @Test
    fun `other ingredients or another name is a new recipe`() {
        assertNull(duplicateRecipe("Paneer tikka", listOf("paneer", "curd", "spices", "capsicum"), saved))
        assertNull(duplicateRecipe("Paneer tikka masala", listOf("paneer", "curd", "spices"), saved))
    }

    @Test
    fun `editing a recipe doesn't clash with itself`() {
        assertNull(duplicateRecipe("Paneer tikka", listOf("paneer", "curd", "spices"), saved, exceptId = "r1"))
    }
}
