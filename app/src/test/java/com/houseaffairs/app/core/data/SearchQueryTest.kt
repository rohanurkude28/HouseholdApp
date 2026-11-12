package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchQueryTest {
    private val today = LocalDate.of(2026, 11, 12) // a Thursday

    private val home = findablesOf("transactions", listOf(
        mapOf("id" to "u1", "title" to "Uber", "category" to "Transport", "amountMinor" to 42000L, "currency" to "INR", "date" to today.minusDays(2)),
        mapOf("id" to "u2", "title" to "Uber", "category" to "Transport", "amountMinor" to 39000L, "currency" to "INR", "date" to LocalDate.of(2026, 10, 20)),
        mapOf("id" to "d1", "title" to "Toit", "category" to "Dining", "amountMinor" to 185000L, "currency" to "INR", "date" to today.minusDays(4), "tags" to "goa friends"),
        mapOf("id" to "d2", "title" to "Swiggy", "category" to "Dining", "amountMinor" to 64000L, "currency" to "INR", "date" to today),
    )) + findablesOf("tasks", listOf(
        mapOf("id" to "t1", "title" to "Pay rent", "due" to today.minusDays(3)),
        mapOf("id" to "t2", "title" to "Book Goa hotel", "due" to today.plusDays(2), "tags" to listOf("goa")),
    )) + findablesOf("medicines", listOf(
        mapOf("id" to "m1", "title" to "Crocin", "expiry" to today.plusDays(5), "forWhom" to listOf("u-riya")),
        mapOf("id" to "m2", "title" to "Cough syrup", "expiry" to today.minusDays(1)),
    ), names = mapOf("u-riya" to "Riya"))

    private fun ids(q: String) = searchAll(q, home, today = today).flatMap { g -> g.items.map { it.id } }.toSet()

    @Test
    fun `a time is understood`() {
        assertEquals(setOf("u2"), ids("uber last month"))
        assertEquals(setOf("u1"), ids("uber this month"))
        assertEquals(setOf("u2"), ids("uber in october"))
        assertEquals(listOf("Last month"), parseSearch("uber last month", today).understood)
    }

    @Test
    fun `an amount is understood`() {
        assertEquals(setOf("d1"), ids("dining over 1000"))
        assertEquals(setOf("d2"), ids("dining under 1000"))
        assertEquals(setOf("d1"), ids("expenses above 1k"))
    }

    @Test
    fun `a kind on its own lists that kind, narrowed by what else is said`() {
        assertEquals(setOf("u1", "d1", "d2"), ids("spending this month"))
        assertEquals(setOf("m1"), ids("riya's medicines"))
        assertEquals(setOf("t1"), ids("overdue tasks"))
        assertEquals(setOf("m1"), ids("expiring"))
        assertEquals(setOf("m2"), ids("expired medicines"))
    }

    @Test
    fun `tags are strict, words are loose`() {
        assertEquals(setOf("d1", "t2"), ids("#goa"))
        assertEquals(setOf("t2"), ids("#goa tasks"))
        assertTrue("t2" in ids("goa"))
        assertEquals(listOf("goa", "friends"), tagsOf("#Goa, friends"))
    }

    @Test
    fun `a single trip word is still a word`() {
        assertEquals(emptySet<String>(), parseSearch("goa trip", today).kinds)
        assertEquals(setOf("trips"), parseSearch("goa trips", today).kinds)
    }
}
