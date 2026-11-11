package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SurprisesTest {
    private val family = listOf("asha", "ravi", "mia")

    @Test
    fun `a gift idea for Ravi is seen by everyone but Ravi`() {
        val forWhom = surpriseFor("listItems", isGift = true, mapOf("forWhom" to listOf("ravi")))
        assertEquals(listOf("asha", "mia"), hiddenFrom(listOf("*"), family, forWhom, "asha"))
    }

    @Test
    fun `only gifts and surprise trips hide anything`() {
        assertEquals(emptyList<String>(), surpriseFor("listItems", isGift = false, mapOf("forWhom" to listOf("ravi"))))
        assertEquals(emptyList<String>(), surpriseFor("transactions", isGift = false, mapOf("forWhom" to listOf("ravi"))))
        assertEquals(listOf("mia"), surpriseFor("trips", isGift = false, mapOf("surpriseFor" to listOf("mia"))))
        assertEquals(listOf("*"), hiddenFrom(listOf("*"), family, emptyList(), "asha"))
    }

    @Test
    fun `chosen people and only me stay as narrow as they were`() {
        assertEquals(listOf("asha", "mia"), hiddenFrom(listOf("asha", "ravi", "mia"), family, listOf("ravi"), "asha"))
        assertEquals(listOf("asha"), hiddenFrom(listOf("asha"), family, listOf("ravi"), "asha"))
    }

    @Test
    fun `whoever adds it still sees a gift for themself`() {
        assertEquals(listOf("*"), hiddenFrom(listOf("*"), family, listOf("asha"), "asha"))
        assertEquals(listOf("asha", "mia"), hiddenFrom(listOf("*"), family, listOf("asha", "ravi"), "asha"))
    }
}
