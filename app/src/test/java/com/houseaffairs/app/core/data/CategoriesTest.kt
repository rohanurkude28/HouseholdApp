package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoriesTest {

    private fun stock(title: String, category: String? = null) = PantryStock(title, title, 1.0, "pcs", 1.0, category = category)

    @Test
    fun `pantry items group by food category, guessed from the name`() {
        assertEquals(Aisle.SPICES, pantryCategory(stock("Garam masala")))
        assertEquals(Aisle.SPICES, pantryCategory(stock("Chilli powder")))
        assertEquals(Aisle.VEGETABLES, pantryCategory(stock("Green chilli")))
        assertEquals(Aisle.STAPLES, pantryCategory(stock("Toor dal")))
        assertEquals(Aisle.MEAT, pantryCategory(stock("Chicken breast")))
        assertEquals(Aisle.FROZEN, pantryCategory(stock("Frozen peas")))
        assertEquals(Aisle.FROZEN, pantryCategory(stock("Vanilla ice cream")))
        // "-es" plurals too.
        assertEquals(Aisle.VEGETABLES, pantryCategory(stock("Tomatoes")))
        assertEquals(Aisle.VEGETABLES, pantryCategory(stock("Potatoes")))
        assertEquals(Aisle.FRUITS, pantryCategory(stock("Mangoes")))
        // A chosen category wins, and old saved types still work.
        assertEquals(Aisle.DAIRY, pantryCategory(stock("Amul", "Dairy & eggs")))
        assertEquals(Aisle.SPICES, pantryCategory(stock("Everest", "Spices")))
        // Non-food aisles aren't pantry categories.
        assertEquals(Aisle.OTHER, pantryCategory(stock("Shampoo")))
    }

    @Test
    fun `older shopping aisles still read`() {
        assertEquals(Aisle.STAPLES, Aisle.from("Staples & spices"))
        assertEquals(Aisle.MEAT, Aisle.from("Meat & fish"))
    }

    @Test
    fun `kept things are grouped by kind, guessed when not set`() {
        assertEquals("Jewellery", keptCategory(KeptItem("1", "Gold mangalsutra", "Locker", null, null, null)))
        assertEquals("Electricals", keptCategory(KeptItem("2", "Extension board", "Store", null, null, null)))
        assertEquals("Documents", keptCategory(KeptItem("3", "Passports", "Drawer", null, null, null)))
        assertEquals("Keys", keptCategory(KeptItem("4", "Spare scooter key", "Hook", "Keys", null, null)))
        assertEquals("Other", keptCategory(KeptItem("5", "Old photo album", "Loft", null, null, null)))
    }

    @Test
    fun `groups follow the given order, then the rest, with Other last`() {
        val items = listOf("a" to "Other", "b" to "Toiletries", "c" to "Pet care", "d" to "Cosmetics & skincare")
        val groups = grouped(items, SUPPLY_TYPES) { it.second }.map { it.first }
        assertEquals(listOf("Cosmetics & skincare", "Toiletries", "Pet care", "Other"), groups)
    }
}
