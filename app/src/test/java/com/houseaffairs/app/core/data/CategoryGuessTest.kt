package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CategoryGuessTest {
    private val parser = com.houseaffairs.app.feature.quickadd.QuickAddParser()
    private fun guessCategory(text: String, learned: Map<String, String> = emptyMap()) = guessCategory(text, learned, parser::categoryOfWord)
    private val day = LocalDate.of(2026, 11, 1)
    private fun txn(title: String, category: String, daysAgo: Long = 0) = Txn("t", title, category, 100, day.minusDays(daysAgo), planned = false)

    @Test
    fun `built-in words are recognised while typing`() {
        assertEquals("Transport", guessCategory("Uber"))
        assertEquals("Dining", guessCategory("coffee with Ravi"))
        assertEquals("Home", guessCategory("Plumber"))
        assertEquals("Entertainment", guessCategory("movie tickets"))
        assertNull(guessCategory("ub"))
        assertNull(guessCategory("Sharma"))
    }

    @Test
    fun `the family's own filing wins`() {
        val learned = learnedCategories(listOf(txn("Sharma Stores", "Groceries"), txn("sharma stores ", "Groceries"), txn("Sharma Stores", "Home"), txn("Amazon", "Gifts")))
        assertEquals("Groceries", guessCategory("Sharma stores", learned))
        // The parser says Shopping; this family buys presents there.
        assertEquals("Gifts", guessCategory("amazon", learned))
        assertEquals("Groceries", guessCategory("Sharma Stores, Baner", learned))
    }

    @Test
    fun `a tie goes to the latest and Other teaches nothing`() {
        val learned = learnedCategories(listOf(txn("Ravi", "Dining", daysAgo = 9), txn("Ravi", "Gifts", daysAgo = 1), txn("Kirana", "Other")))
        assertEquals("Gifts", learned["ravi"])
        assertNull(learned["kirana"])
    }

    @Test
    fun `a name half typed counts once only one category fits`() {
        val learned = learnedCategories(listOf(txn("Sharma Stores", "Groceries"), txn("Shanti Clinic", "Health")))
        assertNull(guessCategory("sha", learned))
        assertEquals("Groceries", guessCategory("shar", learned))
    }
}
