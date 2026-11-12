package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HabitTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `a streak counts days in a row ending today`() {
        assertEquals(3, streakOf(setOf("2026-11-11", "2026-11-10", "2026-11-09", "2026-11-07"), today))
        assertEquals(0, streakOf(setOf("2026-11-10"), today))
    }

    @Test
    fun `old days are dropped and today is kept`() {
        val kept = keptDays(setOf("2026-01-01", "2026-11-10", "junk"), today)
        assertEquals(setOf("2026-11-10", "2026-11-11"), kept)
    }

    @Test
    fun `round numbers get a cheer`() {
        assertNull(streakLine(1))
        assertEquals("🔥 4 days in a row", streakLine(4))
        assertEquals("🎉 7 days in a row!", streakLine(7))
    }

    @Test
    fun `the feed puts other people first and drops old things`() {
        val now = 10 * 86_400_000L
        val hour = 3_600_000L
        val recent = listOf(
            ActivityItem("1", ActivityKind.SHOPPING, "Milk", null, null, "ravi", now - hour),
            ActivityItem("2", ActivityKind.TASK, "Call plumber", null, null, "me", now - 2 * hour),
            ActivityItem("3", ActivityKind.EXPENSE, "DMart", 5000, "INR", "asha", now - 5 * 86_400_000L),
        )
        val feed = familyFeed(recent, "me", now, { if (it == "ravi") "Ravi" else "Asha" }, max = 1)
        assertEquals(listOf("Ravi added Milk to the shopping list"), feed.map { it.text })
        assertEquals(2, familyFeed(recent, "me", now, { "Ravi" }).size)
    }

    @Test
    fun `how long ago reads naturally`() {
        val now = 100 * 3_600_000L
        assertEquals("just now", agoText(now, now))
        assertEquals("3h ago", agoText(now - 3 * 3_600_000L, now))
        assertEquals("yesterday", agoText(now - 30 * 3_600_000L, now))
    }

    @Test
    fun `the evening reminder only comes when a streak is at stake`() {
        assertEquals("Keep your 2-day streak 🔥", streakNudge(setOf("2026-11-10", "2026-11-09"), today)?.title)
        assertNull(streakNudge(setOf("2026-11-11", "2026-11-10", "2026-11-09"), today))
        assertNull(streakNudge(setOf("2026-11-10"), today))
    }
}
