package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAllTest {
    private val home = findablesOf("trips", listOf(mapOf("id" to "t1", "title" to "Goa", "places" to "Baga beach, Panjim"))) +
        findablesOf("transactions", listOf(
            mapOf("id" to "x1", "merchant" to "IndiGo", "title" to "IndiGo", "category" to "Travel", "trip" to "Goa"),
            mapOf("id" to "x2", "merchant" to "DMart", "title" to "DMart", "category" to "Groceries"),
        )) +
        findablesOf("listItems", listOf(mapOf("id" to "l1", "title" to "Ladakh in June", "template" to "Travel"))) +
        findablesOf("keptAt", listOf(mapOf("id" to "k1", "title" to "Passports", "location" to "Bedroom locker", "photos" to listOf("p1", "p2")))) +
        findablesOf("dreams", listOf(mapOf("id" to "d1", "title" to "Japan", "kind" to "Travel"))) +
        findablesOf("tasks", listOf(
            mapOf("id" to "a1", "title" to "Book hotel for Goa"),
            mapOf("id" to "a2", "title" to "Finish training module"),
            mapOf("id" to "a3", "title" to "Renew travel insurance", "done" to true),
        )) +
        findablesOf("stock", listOf(
            mapOf("id" to "s1", "title" to "Milk", "category" to "Dairy"),
            mapOf("id" to "s2", "title" to "Travel pillow", "type" to "HOUSEHOLD"),
            mapOf("id" to "s3", "title" to "Travel snacks", "status" to "USED_UP"),
        ))

    private fun ids(query: String) = searchAll(query, home).flatMap { g -> g.items.map { it.id } }.toSet()

    @Test
    fun `travel finds everything about travelling whatever kind it is`() {
        assertEquals(setOf("t1", "x1", "l1", "k1", "d1", "a1", "s2"), ids("travel"))
        assertEquals(ids("travel"), ids("show all my travel things"))
    }

    @Test
    fun `finished and used-up things are left out`() {
        assertTrue("a3" !in ids("travel") && "s3" !in ids("travel"))
    }

    @Test
    fun `a topic matches whole words only`() {
        assertTrue("a2" !in ids("travel"))
    }

    @Test
    fun `things that say the word come before things found through the topic`() {
        val groups = searchAll("travel", home).map { it.group }
        assertEquals("Supplies", groups.first())
        assertTrue(groups.indexOf("Spending") < groups.indexOf("Where it's kept"))
        assertEquals("Supplies", findablesOf("stock", listOf(mapOf("id" to "s", "title" to "Mop", "type" to "HOUSEHOLD"))).single().group)
    }

    @Test
    fun `every word asked for must be found`() {
        assertEquals(setOf("x1", "a1"), ids("goa travel") - "t1")
        assertEquals(setOf("a1"), ids("goa hotel"))
        assertEquals(emptySet<String>(), ids("goa milk"))
    }

    @Test
    fun `plain words still search by name and place`() {
        assertEquals(setOf("s1"), ids("milk"))
        assertEquals(setOf("k1"), ids("locker"))
    }

    @Test
    fun `the line counts what was found`() {
        assertEquals("7 things about travel", foundLine("travel", searchAll("travel", home)))
        assertEquals("Nothing saved about \"yacht\" yet", foundLine("yacht", searchAll("yacht", home)))
        assertEquals("Travel · Goa", home.first { it.id == "x1" }.detail)
        assertEquals("p1", home.first { it.id == "k1" }.photo)
    }
}
