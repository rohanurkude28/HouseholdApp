package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FollowUpsTest {

    @Test
    fun `quantity chips fit the unit and read naturally`() {
        assertEquals(listOf("250 g", "500 g", "1 kg", "2 kg"), quantityChoices("g").map { (a, u) -> quantityLabel(a, u) })
        assertEquals(listOf("500 ml", "1 L", "2 L"), quantityChoices("ml").map { (a, u) -> quantityLabel(a, u) })
        assertEquals(listOf("1 pc", "6 pcs", "12 pcs"), quantityChoices("pcs").map { (a, u) -> quantityLabel(a, u) })
    }

    @Test
    fun `use-by chips count from today`() {
        val today = LocalDate.of(2026, 11, 4)
        assertEquals(today.plusWeeks(1), expiryChoices(today).toMap().getValue("1 week"))
    }

    @Test
    fun `bought with no unit offers the item's natural one`() {
        val f = boughtFollowUp("Paneer", "pcs", LocalDate.of(2026, 11, 4)) { _, _, _ -> }
        assertEquals(listOf("250 g", "500 g", "1 kg", "2 kg"), f.groups.first().choices.map { it.label })
        val milk = boughtFollowUp("Milk", "pcs", LocalDate.of(2026, 11, 4)) { _, _, _ -> }
        assertEquals(listOf("500 ml", "1 L", "2 L"), milk.groups.first().choices.map { it.label })
    }

    @Test
    fun `throwing some away offers the buy list and putting it back`() {
        val part = wasteFollowUp("Milk", 50, onList = false, add = {}, putBack = {})
        assertEquals("Milk · 50% thrown away", part.title)
        assertEquals(listOf("Add it to the buy list?", "Changed your mind?"), part.groups.map { it.label })
        val listed = wasteFollowUp("Milk", 100, onList = true, add = {}, putBack = {})
        assertEquals("Milk · thrown away", listed.title)
        assertEquals(listOf("Changed your mind?"), listed.groups.map { it.label })
    }
}
