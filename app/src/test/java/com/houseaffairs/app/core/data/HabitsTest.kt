package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitsTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun did(title: String, daysAgo: Long) = Purchase(title, today.minusDays(daysAgo))
    private val laundry = listOf(did("Do laundry", 13), did("do the laundry", 9), did("Do laundry", 5))

    @Test
    fun `a chore done every four days is asked about on the fifth`() {
        val due = usualTasks(laundry, emptyList(), today).single()
        assertEquals("Do laundry", due.title)
        assertEquals("You usually do this every 4 days · last done 5 days ago", due.why)
    }

    @Test
    fun `not when it's already a task, too early, or only done twice`() {
        assertTrue(usualTasks(laundry, listOf("Do the laundry"), today).isEmpty())
        assertTrue(usualTasks(laundry, emptyList(), today.minusDays(2)).isEmpty())
        assertTrue(usualTasks(laundry.take(2), emptyList(), today).isEmpty())
    }

    @Test
    fun `a chore left for three gaps was dropped, not forgotten`() {
        assertTrue(usualTasks(laundry, emptyList(), today.plusDays(10)).isEmpty())
    }

    @Test
    fun `the most overdue come first and only two are asked`() {
        val plants = listOf(did("Water plants", 30), did("Water plants", 20), did("Water plants", 10))
        val bins = listOf(did("Take bins out", 21), did("Take bins out", 14), did("Take bins out", 7))
        val bed = listOf(did("Change sheets", 60), did("Change sheets", 40), did("Change sheets", 20))
        assertEquals(listOf("Do laundry", "Water plants"), usualTasks(laundry + plants + bins + bed, emptyList(), today).map { it.title })
    }

    @Test
    fun `a loved place is offered, the one good for this meal first`() {
        fun place(title: String, rating: String?, goodFor: String? = null, kind: String = "Restaurant") = PlaceItem(title, title, kind, "Indiranagar", rating, goodFor, null, null)
        val places = listOf(place("Toit", "❤️ Love it", "dinner"), place("Third Wave", "❤️ Love it", "breakfast", "Café"), place("Meh", "👎 Not great"), place("Gym", "❤️ Love it", kind = "Other"))
        assertEquals(Idea("Toit", "You loved it last time · for dinner · Indiranagar"), likedBefore(places, "Dinner", today))
        assertEquals("Third Wave", likedBefore(places, "Breakfast", today)?.title)
        assertNull(likedBefore(places.drop(2), "Dinner", today))
    }
}
