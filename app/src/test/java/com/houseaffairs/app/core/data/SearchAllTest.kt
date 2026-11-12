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
        assertEquals("Nothing for Overdue · Tasks right now", foundLine("overdue tasks", emptyList()))
        assertEquals("Travel · Goa", home.first { it.id == "x1" }.detail)
        assertEquals("p1", home.first { it.id == "k1" }.photo)
    }

    @Test
    fun `a typo still finds it`() {
        assertEquals(setOf("k1"), ids("pasport"))
        assertEquals(true, oneEditApart("groceires", "groceries"))
        assertEquals(false, oneEditApart("train", "training"))
    }

    @Test
    fun `people are found by name, and a condition finds its medicines`() {
        val more = findablesOf("medicines", listOf(mapOf("id" to "m1", "title" to "Crocin 650", "forWhom" to listOf("u-riya"))), names = mapOf("u-riya" to "Riya")) +
            findablesOf("events", listOf(mapOf("id" to "e1", "title" to "Riya birthday")))
        assertEquals(setOf("m1", "e1"), searchAll("riya", more).flatMap { g -> g.items.map { it.id } }.toSet())
        assertEquals(setOf("m1"), searchAll("fever", more).flatMap { g -> g.items.map { it.id } }.toSet())
    }

    private val today = java.time.LocalDate.of(2026, 11, 12)
    private val spends = findablesOf("transactions", listOf(
        mapOf("id" to "s1", "title" to "Shell", "category" to "Transport", "subcategory" to "Fuel", "amountMinor" to 200000L, "currency" to "INR", "date" to today.minusDays(1)),
        mapOf("id" to "s2", "title" to "Swiggy", "category" to "Dining", "amountMinor" to 64000L, "currency" to "INR", "date" to today),
        mapOf("id" to "s3", "title" to "DMart", "category" to "Groceries", "amountMinor" to 234000L, "currency" to "INR", "date" to today.minusDays(3)),
    ))
    private val money: (Long, String?) -> String = { m, _ -> "₹${m / 100}" }

    @Test
    fun `a word finds its spending category`() {
        // "petrol" means Transport even though no entry says "petrol".
        val petrol = searchAll("petrol", spends, categoryOf = { if (it == "petrol") "Transport" else null }).flatMap { it.items }.map { it.id }
        assertEquals(listOf("s1"), petrol)
        assertEquals(setOf("s2", "s3"), searchAll("food", spends).flatMap { it.items }.map { it.id }.toSet())
    }

    @Test
    fun `each result says what it is`() {
        assertEquals("₹640 · Dining · today", spends.first { it.id == "s2" }.line(money, today))
        val task = findablesOf("tasks", listOf(mapOf("id" to "t", "title" to "Pay rent", "due" to today.plusDays(1)))).single()
        assertEquals("Due tomorrow", task.line(money, today))
        assertEquals("📍 Bedroom locker", home.first { it.id == "k1" }.line(money, today))
    }

    @Test
    fun `the summary totals the spending by category and counts the rest`() {
        val s = searchSummary(searchAll("food", spends + home))!!
        assertEquals(298000L, s.spentMinor)
        assertEquals(listOf("Groceries" to 234000L, "Dining" to 64000L), s.byCategory)
        val travel = searchSummary(searchAll("travel", home))!!
        assertEquals(true, travel.others.contains("1 trips"))
    }

    @Test
    fun `a dead end suggests the closest word, and recent searches stay short`() {
        assertEquals("passport", didYouMean("pasprts", home))
        // "part" is as close as "passport", but passport starts the same way.
        assertEquals("passport", didYouMean("pasprt", home + findablesOf("tasks", listOf(mapOf("id" to "p", "title" to "Car part")))))
        assertEquals(null, didYouMean("passports", home))
        assertEquals(listOf("goa", "milk"), rememberSearch(listOf("milk", "Goa"), "goa"))
    }
}
