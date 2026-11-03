package com.houseaffairs.app.core.data

import com.houseaffairs.app.feature.quickadd.QuickAddDraft
import com.houseaffairs.app.feature.quickadd.QuickAddParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhereIsTest {

    private val kept = listOf(
        KeptItem("k1", "Car keys", "Blue drawer", "Keys", null, null),
        KeptItem("k2", "Passports", "Blue file in the wardrobe", "Documents", null, null),
    )
    private val stock = listOf(
        PantryStock("s1", "Jeera", 100.0, "g", 100.0, keptAt = "Spice box"),
        PantryStock("s2", "Crocin", 10.0, "tablets", 10.0, keptAt = "Medicine drawer"),
        PantryStock("s3", "Rice", 2.0, "kg", 5.0),
    )

    @Test
    fun `finds places across kept things and stock`() {
        assertEquals(listOf(Placed("Jeera", "Spice box")), whereIs("where is the jeera?", kept, stock))
        assertEquals(listOf(Placed("Crocin", "Medicine drawer")), whereIs("crocin", kept, stock))
        assertEquals(listOf(Placed("Car keys", "Blue drawer")), whereIs("where are the car keys", kept, stock))
    }

    @Test
    fun `items without a place aren't answers`() {
        assertTrue(whereIs("rice", kept, stock).isEmpty())
    }

    @Test
    fun `saying where something is reads as a place`() {
        val parser = QuickAddParser()
        assertEquals(QuickAddDraft.Kept("Jeera", "Spice box"), parser.parse("jeera is in the spice box"))
        assertEquals(QuickAddDraft.Kept("Paneer", "Fridge door"), parser.parse("paneer is in the fridge door"))
        assertEquals(QuickAddDraft.Kept("Passports", "Blue file in the wardrobe"), parser.parse("passports are in the blue file in the wardrobe"))
    }
}
