package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AddMenuTest {
    private val allowed = listOf("EXPENSE", "SUBSCRIPTION", "GROCERY", "MEDICINE", "SHOPPING", "TASK", "RECIPE", "TRIP")
    private val defaults = listOf("EXPENSE", "SHOPPING", "TASK", "MEDICINE")

    @Test
    fun `a new phone shows the everyday kinds`() {
        assertEquals(defaults, usualKinds(emptyMap(), allowed, defaults))
    }

    @Test
    fun `what this person adds most moves to the front`() {
        val counts = mapOf("RECIPE" to 6, "TRIP" to 2, "TASK" to 9, "SUBSCRIPTION" to 1)
        assertEquals(listOf("TASK", "RECIPE", "TRIP", "EXPENSE"), usualKinds(counts, allowed, defaults))
    }

    @Test
    fun `kinds they can't add never show`() {
        val kid = listOf("EXPENSE", "TASK")
        assertEquals(listOf("EXPENSE", "TASK"), usualKinds(mapOf("RECIPE" to 9), kid, defaults))
    }

    @Test
    fun `the groups repeat nothing and drop empty ones`() {
        val groups = listOf("Money" to listOf("EXPENSE", "SUBSCRIPTION"), "Kitchen" to listOf("SHOPPING"), "Plans" to listOf("TASK", "TRIP", "GOAL"))
        assertEquals(
            listOf("Money" to listOf("SUBSCRIPTION"), "Plans" to listOf("TRIP")),
            addGroups(groups, allowed, shown = listOf("EXPENSE", "SHOPPING", "TASK")),
        )
    }

    @Test
    fun `counts survive a round trip and junk is skipped`() {
        val counts = mapOf("EXPENSE" to 5, "TASK" to 2)
        assertEquals(counts, parseCounts(encodeCounts(counts)))
        assertEquals(mapOf("TASK" to 2), parseCounts("TASK:2,broken,X:y"))
    }
}
