package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class RepeatsTest {

    private val today = localDate(2026, 11, 4)

    @Test
    fun `next due keeps the rhythm and lands in the future`() {
        assertEquals(localDate(2026, 11, 11), nextDue(today, "Weekly", today))
        assertEquals(localDate(2026, 11, 5), nextDue(null, "Daily", today))
        assertEquals(localDate(2026, 12, 1), nextDue(localDate(2026, 11, 1), "Monthly", today))
        // Three weeks late: the next one is the first weekly date after today, same weekday.
        assertEquals(localDate(2026, 11, 9), nextDue(localDate(2026, 10, 19), "Weekly", today))
        assertNull(nextDue(today, "Never", today))
        assertNull(nextDue(today, null, today))
    }

    @Test
    fun `turns pass around the rotation`() {
        val rota = listOf("rohan", "prachi", "mia")
        assertEquals("prachi", nextInRotation(rota, "rohan"))
        assertEquals("rohan", nextInRotation(rota, "mia"))
        assertEquals("rohan", nextInRotation(rota, "someone-else"))
        assertEquals("asha", nextInRotation(emptyList(), "asha"))
    }
}
