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
}
