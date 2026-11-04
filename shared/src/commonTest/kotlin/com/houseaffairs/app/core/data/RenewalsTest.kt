package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class RenewalsTest {

    private val today = localDate(2026, 11, 4)

    @Test
    fun `a renewal today waits to be logged - then the next one is owed`() {
        val start = localDate(2026, 10, 4)
        assertEquals(today, pendingRenewal(start, "Monthly", localDate(2026, 10, 4), today))
        assertNull(pendingRenewal(start, "Monthly", today, today))
        assertEquals(localDate(2026, 12, 4), nextRenewal(start, "Monthly", today, today))
    }

    @Test
    fun `missed months aren't back-filled - only the latest waits`() {
        val start = localDate(2026, 6, 10)
        assertEquals(localDate(2026, 10, 10), pendingRenewal(start, "Monthly", null, today))
    }

    @Test
    fun `future start dates aren't pending`() {
        assertNull(pendingRenewal(localDate(2026, 11, 20), "Monthly", null, today))
        assertEquals(localDate(2026, 11, 20), nextRenewal(localDate(2026, 11, 20), "Monthly", null, today))
    }

    @Test
    fun `every frequency steps correctly - quarterly included`() {
        val d = localDate(2026, 1, 31)
        assertEquals(localDate(2026, 4, 30), nextPeriod(d, "Quarterly"))
        assertEquals(localDate(2026, 3, 31), nextPeriod(d, "Every 2 months"))
        assertEquals(localDate(2026, 7, 31), nextPeriod(d, "Every 6 months"))
        assertEquals(localDate(2026, 2, 7), nextPeriod(d, "Weekly"))
        assertEquals(localDate(2027, 1, 31), nextPeriod(d, "Yearly"))
    }
}
