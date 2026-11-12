package com.houseaffairs.app.core.data

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import com.houseaffairs.app.core.time.*

class FirstAidTest {

    private val today = localDate(2026, 11, 3)

    @Test
    fun `symptoms map to care`() {
        assertEquals("Fever", careFor("I'm feeling feverish")?.tag)
        assertEquals("Headache", careFor("my head hurts")?.tag)
        assertEquals("Loose motions", careFor("Riya has loose motions")?.tag)
        assertEquals("Burns", careFor("burnt my hand on the tawa")?.tag)
        assertEquals("Sore throat", careFor("I have a sore throat")?.tag)
        assertNull(careFor("I'm feeling great"))
    }

    @Test
    fun `medicines for a tag - person's own first - expired left out`() {
        val meds = listOf(
            MedicineInfo("a", "Crocin", listOf("Fever", "Pain"), emptyList(), "1 after food", null),
            MedicineInfo("b", "Calpol syrup", listOf("Fever"), listOf("mia"), "5 ml", null),
            MedicineInfo("c", "Old dolo", listOf("Fever"), emptyList(), null, today.minusDays(1)),
            MedicineInfo("d", "Digene", listOf("Acidity"), emptyList(), null, null),
        )
        assertEquals(listOf("b", "a"), medicinesFor("Fever", meds, "mia", today).map { it.id })
        assertEquals(listOf("a"), medicinesFor("Pain", meds, null, today).map { it.id })
    }

    @Test
    fun severalConditionsAreFoundInTheOrderSaid() {
        assertEquals(listOf("Fever", "Cold & cough"), caresFor("I have fever and cold").map { it.tag })
        assertEquals(listOf("Sore throat"), caresFor("my sore throat hurts").map { it.tag })
    }

    @Test
    fun aMedicineIsFoundByItsNameWithoutAnyTag() {
        val cabinet = listOf(
            MedicineInfo("1", "Crocin 650", emptyList(), emptyList(), null, null),
            MedicineInfo("2", "Sinarest", emptyList(), emptyList(), null, null),
        )
        assertEquals(listOf("1"), medicinesFor("Fever", cabinet, null, today).map { it.id })
        assertEquals(listOf("2"), medicinesFor("Cold & cough", cabinet, null, today).map { it.id })
    }
}
