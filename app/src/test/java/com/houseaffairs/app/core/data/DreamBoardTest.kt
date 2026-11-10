package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DreamBoardTest {
    private fun dream(id: String, order: Double? = null, by: LocalDate? = null) = Dream(id, id, "Travel", "✈️", 0, by, boardOrder = order)

    @Test
    fun `dragged dreams keep their place - new ones follow - soonest first`() {
        val list = listOf(dream("car", by = LocalDate.of(2028, 1, 1)), dream("bali", 1.0), dream("home", 0.0), dream("tv", by = LocalDate.of(2027, 1, 1)))
        assertEquals(listOf("home", "bali", "tv", "car"), boardOrdered(list).map { it.id })
    }

    @Test
    fun `moving a card shifts the rest`() {
        assertEquals(listOf("b", "c", "a"), moved(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), moved(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b"), moved(listOf("a", "b"), 0, 5))
    }

    @Test
    fun `only changed positions are saved`() {
        assertEquals(mapOf("c" to 0.0, "a" to 2.0), boardOrders(listOf("c", "b", "a"), mapOf("a" to 0.0, "b" to 1.0, "c" to 2.0)))
    }
}
