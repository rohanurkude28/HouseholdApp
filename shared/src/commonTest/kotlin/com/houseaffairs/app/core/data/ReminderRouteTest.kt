package com.houseaffairs.app.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderRouteTest {
    @Test
    fun `a tapped reminder opens where its item lives`() {
        assertEquals(CHECK_IN_ROUTE, routeForReminder("checkin:2026-11-12"))
        assertEquals("plans/TASKS", routeForReminder("task:abc:2026-11-12"))
        assertEquals("plans/TASKS", routeForReminder("snooze:chore:c1:2026-11-12:0"))
        assertEquals("plans/EVENTS", routeForReminder("event:e1"))
        assertEquals("vault/MEDICINES", routeForReminder("dose:m1:20400:480"))
        assertEquals("home", routeForReminder("something-else"))
    }
}
