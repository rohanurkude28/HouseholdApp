package com.houseaffairs.app.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarSyncTest {

    private val d = LocalDate.of(2026, 11, 10)
    private val task = CalItem("tasks/t1", "Pay school fees", d)

    @Test
    fun `new app items are created in Google`() {
        val plan = planSync(listOf(task), emptyList(), emptyMap(), emptySet())
        assertEquals(listOf(task), plan.create)
    }

    @Test
    fun `an edit in the app goes to Google`() {
        val moved = task.copy(date = d.plusDays(2))
        val plan = planSync(listOf(moved), listOf(GEvent("g1", "tasks/t1", task.title, d)), mapOf("tasks/t1" to Synced("g1", task.title, d)), emptySet())
        assertEquals(listOf("g1" to moved), plan.update)
        assertTrue(plan.pull.isEmpty())
    }

    @Test
    fun `an edit only in Google comes back to the app`() {
        val plan = planSync(listOf(task), listOf(GEvent("g1", "tasks/t1", "Pay fees (term 2)", d.plusDays(1))), mapOf("tasks/t1" to Synced("g1", task.title, d)), emptySet())
        assertEquals(listOf(Triple("tasks/t1", "Pay fees (term 2)", d.plusDays(1))), plan.pull)
        assertEquals(Synced("g1", "Pay fees (term 2)", d.plusDays(1)), plan.state["tasks/t1"])
    }

    @Test
    fun `when both changed, the app wins`() {
        val moved = task.copy(date = d.plusDays(3))
        val plan = planSync(listOf(moved), listOf(GEvent("g1", "tasks/t1", "Renamed in Google", d)), mapOf("tasks/t1" to Synced("g1", task.title, d)), emptySet())
        assertEquals(listOf("g1" to moved), plan.update)
        assertTrue(plan.pull.isEmpty())
    }

    @Test
    fun `done in the app removes it from Google, deleted in Google stops syncing`() {
        val gone = planSync(emptyList(), listOf(GEvent("g1", "tasks/t1", task.title, d)), mapOf("tasks/t1" to Synced("g1", task.title, d)), emptySet())
        assertEquals(listOf("g1"), gone.delete)
        val deletedInGoogle = planSync(listOf(task), listOf(GEvent("g1", "tasks/t1", task.title, d, cancelled = true)), mapOf("tasks/t1" to Synced("g1", task.title, d)), emptySet())
        assertEquals(setOf("tasks/t1"), deletedInGoogle.forget)
        assertTrue(deletedInGoogle.create.isEmpty() && deletedInGoogle.update.isEmpty())
        // Once forgotten, it isn't created again.
        assertTrue(planSync(listOf(task), emptyList(), emptyMap(), setOf("tasks/t1")).create.isEmpty())
    }

    @Test
    fun `new events in the HouseAffairs calendar become tasks`() {
        val plan = planSync(emptyList(), listOf(GEvent("g9", null, "Car service", d), GEvent("g8", null, "", d)), emptyMap(), emptySet())
        assertEquals(listOf("g9"), plan.adopt.map { it.id })
    }
}
