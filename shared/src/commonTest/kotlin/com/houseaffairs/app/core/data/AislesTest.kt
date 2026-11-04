package com.houseaffairs.app.core.data

import kotlin.test.assertEquals
import kotlin.test.Test

class AislesTest {
    @Test
    fun `common items land in the right aisle`() {
        assertEquals(Aisle.DAIRY, aisleOf("Milk"))
        assertEquals(Aisle.DAIRY, aisleOf("Eggs"))
        assertEquals(Aisle.VEGETABLES, aisleOf("Onions"))
        assertEquals(Aisle.FRUITS, aisleOf("Bananas"))
        assertEquals(Aisle.STAPLES, aisleOf("Toor dal"))
        assertEquals(Aisle.CLEANING, aisleOf("AAA batteries"))
        assertEquals(Aisle.CARE, aisleOf("Toothpaste"))
        assertEquals(Aisle.SNACKS, aisleOf("Bread"))
        assertEquals(Aisle.OTHER, aisleOf("Gift wrap"))
    }

    @Test
    fun `a pantry type wins over the name`() {
        assertEquals(Aisle.DAIRY, aisleOf("Amul", "Dairy"))
    }
}
