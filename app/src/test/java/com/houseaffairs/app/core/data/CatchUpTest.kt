package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CatchUpTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `the last few days without a line - yesterday first`() {
        val missed = missedDays(today, nowHour = 9, written = setOf(today.minusDays(2)), skipped = emptySet())
        assertEquals(listOf(today.minusDays(1), today.minusDays(3)), missed)
    }

    @Test
    fun `today only in the evening - skipped days stay skipped`() {
        assertEquals(today, missedDays(today, nowHour = 21, written = emptySet(), skipped = emptySet()).first())
        assertEquals(emptyList<LocalDate>(), missedDays(today, 9, emptySet(), skipped = (1L..3L).map { today.minusDays(it) }.toSet()))
    }
}
