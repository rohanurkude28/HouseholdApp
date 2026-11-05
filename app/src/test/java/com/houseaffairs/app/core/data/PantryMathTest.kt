package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PantryMathTest {

    private val today = LocalDate.of(2026, 11, 3)
    private val pantry = listOf(
        PantryStock("p", "Paneer", 250.0, "g", 250.0, expiry = today.plusDays(1)),
        PantryStock("r", "Rice", 2.0, "kg", 5.0),
        PantryStock("m", "Milk", 1.0, "L", 1.0),
        PantryStock("t", "Tomatoes", 4.0, "pcs", 6.0),
    )

    @Test
    fun `units convert within a kind only`() {
        assertEquals(0.5, convert(500.0, "g", "kg")!!, 1e-9)
        assertEquals(30.0, convert(2.0, "tbsp", "ml")!!, 1e-9)
        assertNull(convert(1.0, "kg", "pcs"))
    }

    @Test
    fun `cooking deducts in the pantry's own units`() {
        val plan = planCooking(
            listOf(Ingredient("paneer", 200.0, "g"), Ingredient("rice", 500.0, "g"), Ingredient("tomato", 2.0, "pcs")),
            scale = 1.0,
            pantry = pantry,
        )
        val byId = plan.deductions.associateBy { it.stock.id }
        assertEquals(50.0, byId.getValue("p").after!!, 1e-9)
        assertEquals(1.5, byId.getValue("r").after!!, 1e-9)
        assertEquals(2.0, byId.getValue("t").after!!, 1e-9)
        assertTrue(plan.missing.isEmpty())
        // 50 g of 250 g is 20% → running low → goes on the shopping list.
        assertTrue(byId.getValue("p").runsLow)
        assertEquals(listOf("Paneer"), plan.toBuy)
    }

    @Test
    fun `double servings use double and can use things up`() {
        val plan = planCooking(listOf(Ingredient("milk", 500.0, "ml")), scale = 2.0, pantry = pantry)
        assertTrue(plan.deductions.single().usedUp)
        assertEquals(listOf("Milk"), plan.toBuy)
    }

    @Test
    fun `missing ingredients go to shopping`() {
        val plan = planCooking(listOf(Ingredient("Capsicum", 1.0, "pcs")), 1.0, pantry)
        assertEquals(listOf("Capsicum"), plan.toBuy)
    }

    @Test
    fun `recipe fit counts what's in stock and what's expiring`() {
        val fit = recipeFit(listOf(Ingredient("paneer", 200.0, "g"), Ingredient("peas", 100.0, "g")), pantry, today)
        assertEquals(1, fit.have)
        assertEquals(2, fit.total)
        assertFalse(fit.canCook)
        assertEquals(listOf("Paneer"), fit.usesExpiring)
    }

    @Test
    fun `a dish says what's missing, not how many you have`() {
        val dish = listOf(Ingredient("Paneer", 200.0, "g"), Ingredient("Peas", 100.0, "g"), Ingredient("Tomatoes", 2.0, "pcs"))
        val fit = recipeFit(dish, pantry, today)
        assertEquals(listOf("Peas"), fit.missing)
        assertEquals("Missing: peas", fit.summary)
        assertEquals("You have everything", recipeFit(listOf(Ingredient("Rice", 200.0, "g")), pantry, today).summary)
    }

    @Test
    fun `each ingredient is checked against the pantry`() {
        val checks = checkIngredients(listOf(Ingredient("Paneer", 400.0, "g"), Ingredient("Rice", 1.0, "kg"), Ingredient("Peas", 100.0, "g")), pantry)
        assertEquals(listOf(false, true, false), checks.map { it.have })
        assertEquals("250 g", checks[0].inPantry)
        assertEquals(null, checks[2].inPantry)
    }

    @Test
    fun `not enough reads as need more, not missing`() {
        val fit = recipeFit(listOf(Ingredient("Paneer", 400.0, "g"), Ingredient("Peas", 100.0, "g")), pantry, today)
        assertEquals("Missing: peas · need more paneer", fit.summary)
        assertEquals("Need more paneer", recipeFit(listOf(Ingredient("Paneer", 400.0, "g")), pantry, today).summary)
    }

    @Test
    fun `things are measured the way people buy them`() {
        assertEquals("g", naturalUnit("Paneer"))
        assertEquals("g", naturalUnit("Coriander"))
        assertEquals("ml", naturalUnit("Milk"))
        assertEquals("ml", naturalUnit("Mustard oil"))
        assertEquals("pcs", naturalUnit("Eggs"))
        assertEquals("pcs", naturalUnit("Bread"))
    }

    @Test
    fun `cooking takes grams off pieces and pieces off grams`() {
        assertEquals(2.0, convertFor("Onion", 300.0, "g", "pcs")!!, 1e-9)
        assertEquals(200.0, convertFor("Tomatoes", 2.0, "pcs", "g")!!, 1e-9)
        assertNull(convertFor("Paneer", 2.0, "pcs", "g"))
        val stock = listOf(PantryStock("o", "Onions", 3.0, "pcs", 3.0))
        val plan = planCooking(listOf(Ingredient("Onion", 150.0, "g")), 1.0, stock)
        assertEquals(2.0, plan.deductions.single().after!!, 1e-9)
    }

    @Test
    fun `the pantry decides how many people a dish can feed`() {
        val tikka = Recipe("t", "Paneer tikka", listOf(Ingredient("Paneer", 200.0, "g"), Ingredient("Salt", null, "g")), 2, emptyList(), null)
        val pantry = listOf(PantryStock("p", "Paneer", 250.0, "g", 250.0))
        // 100 g a person, 250 g at home.
        assertEquals(2, servingsPossible(tikka, pantry))
        assertEquals(0, servingsPossible(tikka, emptyList()))
        assertEquals(null, servingsPossible(tikka.copy(ingredients = listOf(Ingredient("Salt", null, "g"))), pantry))
    }
}
