package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RenewalsTest {

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `a renewal today waits to be logged, then the next one is owed`() {
        val start = LocalDate.of(2026, 10, 4)
        assertEquals(today, pendingRenewal(start, "Monthly", LocalDate.of(2026, 10, 4), today))
        assertNull(pendingRenewal(start, "Monthly", today, today))
        assertEquals(LocalDate.of(2026, 12, 4), nextRenewal(start, "Monthly", today, today))
    }

    @Test
    fun `missed months aren't back-filled, only the latest waits`() {
        val start = LocalDate.of(2026, 6, 10)
        assertEquals(LocalDate.of(2026, 10, 10), pendingRenewal(start, "Monthly", null, today))
    }

    @Test
    fun `future start dates aren't pending`() {
        assertNull(pendingRenewal(LocalDate.of(2026, 11, 20), "Monthly", null, today))
        assertEquals(LocalDate.of(2026, 11, 20), nextRenewal(LocalDate.of(2026, 11, 20), "Monthly", null, today))
    }

    @Test
    fun `every frequency steps correctly, quarterly included`() {
        val d = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 4, 30), nextPeriod(d, "Quarterly"))
        assertEquals(LocalDate.of(2026, 3, 31), nextPeriod(d, "Every 2 months"))
        assertEquals(LocalDate.of(2026, 7, 31), nextPeriod(d, "Every 6 months"))
        assertEquals(LocalDate.of(2026, 2, 7), nextPeriod(d, "Weekly"))
        assertEquals(LocalDate.of(2027, 1, 31), nextPeriod(d, "Yearly"))
    }
}
