package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickAnswersTest {
    private val today = LocalDate.of(2026, 11, 12) // a Thursday

    @Test
    fun `do we have eggs says how many, when they go off and where they are`() {
        val stock = listOf(PantryStock("1", "Eggs", 6.0, "pcs", 12.0, expiry = today.plusDays(3), keptAt = "Fridge door"))
        val yes = haveAnswer("Eggs", stock, emptyList(), today)
        assertEquals("Yes, you have eggs", yes.title)
        assertEquals("Eggs · 6 pcs left · use by Sun 15 Nov · in 3 days · 📍 Fridge door", yes.lines.single())
        val no = haveAnswer("Milk", stock, listOf("Milk"), today)
        assertEquals("No milk at home", no.title)
        assertEquals("It's already on the buy list.", no.lines.single())
    }

    @Test
    fun `when is finds the event by its words`() {
        val events = listOf(EventItem("e", "Riya's birthday", "Birthday", today.plusDays(4)), EventItem("a", "Anniversary", "Anniversary", today.plusDays(100)))
        assertEquals("Riya's birthday: Mon 16 Nov · in 4 days", whenAnswer("Riya's birthday", events, emptyList(), emptyList(), today).title)
        assertTrue(whenAnswer("Diwali", events, emptyList(), emptyList(), today).title.startsWith("I couldn't find"))
    }

    @Test
    fun `the agenda lists what's due, overdue first`() {
        val tasks = listOf(TaskItem("1", "Pay rent", today.minusDays(1), null), TaskItem("2", "Call plumber", today, null, time = "5:00 pm"), TaskItem("3", "Gym", today.plusDays(2), null))
        val day = agendaAnswer("today", tasks, emptyList(), emptyList(), today)
        assertEquals("2 things today", day.title)
        assertEquals(listOf("⚠️ Pay rent · was due Wed 11 Nov", "✅ Call plumber · 5:00 pm"), day.lines)
        assertEquals(3, agendaAnswer("this week", tasks, emptyList(), emptyList(), today).lines.size)
        assertEquals("Nothing due tomorrow 🎉", agendaAnswer("tomorrow", tasks, emptyList(), emptyList(), today).title)
    }

    @Test
    fun `the buy list in one answer`() {
        assertEquals("2 things to buy", toBuyAnswer(listOf("Milk", "Eggs")).title)
        assertEquals("Nothing on the buy list", toBuyAnswer(emptyList()).title)
    }
}
