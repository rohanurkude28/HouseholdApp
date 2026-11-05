package com.houseaffairs.app.core.data

import com.houseaffairs.app.feature.quickadd.ListTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetCardsTest {
    private val today = LocalDate.of(2026, 11, 5)
    private fun watch(id: String, title: String, template: ListTemplate = ListTemplate.WATCH, votes: Int = 0, status: String = "IDEA") =
        WatchItem(id = id, title = title, platform = "Netflix", rating = 8.0, ratingSource = null, poster = null, providers = emptyList(), kind = null, year = null, lookedUp = true, template = template, status = status, votes = List(votes) { "u$it" })

    @Test
    fun `a busy day rotates through what matters`() {
        val data = HomeData(
            loaded = true,
            tasks = listOf(TaskItem("t1", "Call mom", today, null), TaskItem("t2", "Pay rent", today, null)),
            events = listOf(EventItem("e", "Riya's birthday", "Birthday", today.plusDays(2))),
        )
        val wheel = Wheel(listOf(Habit(Pillar.entries.first(), "Walk", 0.5f, "", "Walk 20 minutes after dinner")))
        val cards = widgetCards(data, wheel, null, listOf(watch("a", "Dune"), watch("b", "Panchayat", votes = 2), watch("c", "Sapiens", ListTemplate.BOOKS)), today)
        assertEquals(listOf("✅", "🎂", "🌿", "🎬", "📚"), cards.map { it.emoji })
        assertEquals("2 things today", cards[0].title)
        assertEquals("In 2 days", cards[1].line)
        assertEquals("Live well · 50/100", cards[2].title)
        // The family's favourite comes up first.
        assertEquals("Watch together: Panchayat", cards[3].title)
    }

    @Test
    fun `an empty home still shows something kind`() {
        val cards = widgetCards(HomeData(loaded = true), null, null, emptyList(), today)
        assertEquals(1, cards.size)
        assertTrue(cards.single().title.startsWith("All calm"))
    }
}
