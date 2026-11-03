package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DigestTest {

    private val today = LocalDate.of(2026, 11, 3)
    private val fmt: (Long) -> String = { "₹${it / 100}" }

    @Test
    fun `remind on the day, on the chosen day before, and never when off`() {
        assertTrue(remindToday(today, null, today, 1))
        assertTrue(remindToday(today.plusDays(1), null, today, 1))
        assertFalse(remindToday(today.plusDays(2), null, today, 1))
        assertTrue(remindToday(today.plusDays(7), 7, today, 1))
        assertFalse(remindToday(today.plusDays(1), 7, today, 1))
        assertTrue(remindToday(today.minusDays(2), 3, today, 1)) // overdue keeps nagging
        assertFalse(remindToday(today, NO_REMINDER, today, 1))
    }

    @Test
    fun `nothing due means no notification`() {
        val data = HomeData(tasks = listOf(TaskItem("t", "Someday", today.plusDays(10), null)))
        assertNull(buildDigest(data, null, null, DigestPrefs(), today, fmt))
    }

    @Test
    fun `urgent first, then today and tomorrow`() {
        val data = HomeData(
            tasks = listOf(TaskItem("a", "Pay rent", today, null), TaskItem("b", "Call plumber", today.minusDays(1), null)),
            events = listOf(EventItem("e", "Mia birthday", "Birthday", today.plusDays(1))),
            pantry = listOf(StockItem("p", "Paneer", today.plusDays(1), null)),
            renewals = listOf(Renewal("n", "Netflix", today.plusDays(1), "₹649")),
        )
        val d = buildDigest(data, null, null, DigestPrefs(), today, fmt)!!
        assertEquals("5 things for today", d.title)
        assertEquals("Call plumber · due overdue", d.lines.first())
        assertTrue("Mia birthday · birthday tomorrow" in d.lines)
        assertTrue("Netflix ₹649 due tomorrow" in d.lines)
    }

    @Test
    fun `areas can be switched off`() {
        val data = HomeData(pantry = listOf(StockItem("p", "Milk", today.minusDays(1), null)))
        assertNull(buildDigest(data, null, null, DigestPrefs(kitchen = false), today, fmt))
        assertEquals("Milk has expired", buildDigest(data, null, null, DigestPrefs(), today, fmt)!!.title)
    }

    @Test
    fun `at most six lines`() {
        val data = HomeData(tasks = (1..9).map { TaskItem("$it", "Task $it", today, null) })
        val d = buildDigest(data, null, null, DigestPrefs(), today, fmt)!!
        assertEquals(6, d.lines.size)
        assertEquals(3, d.more)
    }
}
