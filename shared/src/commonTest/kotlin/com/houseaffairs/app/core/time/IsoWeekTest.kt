package com.houseaffairs.app.core.time

import kotlin.test.Test
import kotlin.test.assertEquals

class IsoWeekTest {
    @Test
    fun `iso weeks match java time`() {
        assertEquals("2026-W47", isoWeek(localDate(2026, 11, 16)))
        assertEquals("2026-W01", isoWeek(localDate(2025, 12, 29)))
        assertEquals("2020-W53", isoWeek(localDate(2021, 1, 3)))
        assertEquals("2027-W52", isoWeek(localDate(2027, 12, 31)))
    }
}
