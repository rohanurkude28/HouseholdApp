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
}
