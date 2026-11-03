package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ListOrganizerTest {

    private val today = LocalDate.of(2026, 11, 4)

    private fun row(title: String, category: String, flag: Flag? = null, extra: String = "") = ListRow(title, title, title, category, flag, extra)

    private val pantry = buildList {
        repeat(12) { add(row("Spice $it", "Spices & masalas")) }
        add(row("Turmeric", "Spices & masalas", Flag.LOW))
        add(row("Milk", "Dairy & eggs", Flag.EXPIRING))
        add(row("Curd", "Dairy & eggs", Flag.EXPIRED))
        add(row("Onions", "Vegetables"))
        add(row("Mystery jar", "Other"))
    }
    private val order = listOf("Vegetables", "Dairy & eggs", "Spices & masalas")

    @Test
    fun `stock flags expired, expiring and low`() {
        assertEquals(Flag.EXPIRED, stockFlag(PantryStock("a", "a", 1.0, "pcs", 1.0, expiry = today.minusDays(1)), today))
        assertEquals(Flag.EXPIRING, stockFlag(PantryStock("a", "a", 1.0, "pcs", 1.0, expiry = today.plusDays(7)), today))
        assertEquals(Flag.LOW, stockFlag(PantryStock("a", "a", 50.0, "g", 250.0), today))
        assertNull(stockFlag(PantryStock("a", "a", 200.0, "g", 250.0, expiry = today.plusDays(30)), today))
    }

    @Test
    fun `big groups start closed, small ones open, needs-a-look comes first`() {
        val o = organize(pantry, order = order)
        assertEquals(listOf("Vegetables", "Dairy & eggs", "Spices & masalas", "Other"), o.sections.map { it.category })
        assertFalse(o.sections.first { it.category == "Spices & masalas" }.open)
        assertTrue(o.sections.first { it.category == "Dairy & eggs" }.open)
        // Most urgent first; a closed group still says what's inside needs a look.
        assertEquals(listOf("Curd", "Milk", "Turmeric"), o.needsLook.map { it.title })
        assertEquals(1, o.sections.first { it.category == "Spices & masalas" }.flagged)
        assertEquals(17, o.total)
    }

    @Test
    fun `flipping a group overrides its default`() {
        val o = organize(pantry, order = order, toggled = setOf("Spices & masalas", "Vegetables"))
        assertTrue(o.sections.first { it.category == "Spices & masalas" }.open)
        assertFalse(o.sections.first { it.category == "Vegetables" }.open)
    }

    @Test
    fun `search matches every word, in titles, categories and extra text, and opens results`() {
        val kept = listOf(row("Gold necklace", "Jewellery", extra = "bank locker"), row("Car keys", "Keys", extra = "blue drawer"))
        val o = organize(kept, query = "gold locker")
        assertEquals(listOf("Gold necklace"), o.sections.flatMap { it.rows }.map { it.title })
        assertTrue(o.sections.all { it.open })
        assertTrue(o.needsLook.isEmpty())
    }

    @Test
    fun `filters and category chips narrow the list`() {
        assertEquals(listOf("Milk", "Curd"), organize(pantry, filter = ListFilter.EXPIRING).sections.flatMap { it.rows }.map { it.title })
        assertEquals(listOf("Turmeric"), organize(pantry, filter = ListFilter.LOW).sections.flatMap { it.rows }.map { it.title })
        assertEquals(13, organize(pantry, category = "Spices & masalas").sections.single().rows.size)
        // Chips always count everything, so they don't shrink while filtering.
        assertEquals(13, organize(pantry, category = "Vegetables", order = order).categories.toMap()["Spices & masalas"])
    }
}
