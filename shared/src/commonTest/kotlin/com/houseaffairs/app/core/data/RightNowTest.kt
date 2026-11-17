package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class RightNowTest {

    private val tuesday = localDate(2026, 11, 3)
    private val pantry = listOf(PantryStock("e", "Eggs", 6.0, "pcs", 6.0), PantryStock("o", "Onion", 3.0, "pcs", 3.0), PantryStock("t", "Tomato", 2.0, "pcs", 2.0))
    private val show = WatchItem("w", "Panchayat", null, 8.9, "TMDB", null, listOf("Prime Video"), "Show", "2020", true)
    private val wishes = listOf(WishItem("1", "Eat out", "Toit", "Indiranagar", null), WishItem("2", "Buy", "New sofa", null, "₹30,000"))

    @Test
    fun `weekday morning suggests breakfast you can make`() {
        val r = rightNow(tuesday, 8, pantry, SAMPLE_RECIPES, wishes, emptyList(), null)!!
        assertEquals("Breakfast ideas", r.headline)
        assertTrue(r.items.all { it.kind == SuggestionKind.FOOD })
        assertTrue(r.items.first().title in setOf("Masala omelette", "Egg bhurji"))
    }

    @Test
    fun `evening suggests dinner only the watch list has its own tab`() {
        val r = rightNow(tuesday, 19, pantry, SAMPLE_RECIPES, wishes, emptyList(), null)!!
        assertEquals("For dinner", r.headline)
        assertTrue(r.items.all { it.kind == SuggestionKind.FOOD })
    }

    @Test
    fun `weekend suggests eating out and shopping only with budget left`() {
        val saturday = localDate(2026, 11, 7)
        val noBudget = rightNow(saturday, 12, pantry, SAMPLE_RECIPES, wishes, emptyList(), null)!!
        assertEquals(listOf(SuggestionKind.EAT_OUT), noBudget.items.map { it.kind })
        val withBudget = rightNow(saturday, 12, pantry, SAMPLE_RECIPES, wishes, emptyList(), "₹4,000")!!
        assertTrue(withBudget.items.any { it.title == "New sofa" })
    }

    @Test
    fun `late night is quiet`() {
        assertNull(rightNow(tuesday, 23, pantry, SAMPLE_RECIPES, wishes, emptyList(), null))
    }
}
