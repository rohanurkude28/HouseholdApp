package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RetentionTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun `cleared a year after it was done`() {
        assertEquals(d(2027, 6, 10), clearsOn(d(2026, 6, 10)))
        assertFalse(shouldClear(d(2026, 6, 10), d(2027, 6, 9)))
        assertTrue(shouldClear(d(2026, 6, 10), d(2027, 6, 10)))
    }

    @Test
    fun `early January finishes wait for last year's story`() {
        // Done 5 Jan 2026: the 2026 story is read in January 2027, so it stays until February.
        assertEquals(d(2027, 2, 1), clearsOn(d(2026, 1, 5)))
        assertFalse(shouldClear(d(2026, 1, 5), d(2027, 1, 20)))
    }
}
