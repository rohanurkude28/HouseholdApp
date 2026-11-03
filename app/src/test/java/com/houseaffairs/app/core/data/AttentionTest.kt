package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AttentionTest {

    private val today = LocalDate.of(2026, 11, 3)

    private fun task(id: String, due: LocalDate?) = TaskItem(id, id, due, assignee = null)
    private fun stock(id: String, expiry: LocalDate?, level: Int? = null) = StockItem(id, id, expiry, level)

    @Test
    fun `urgent before today before soon, then by date`() {
        val data = HomeData(
            tasks = listOf(task("soon", today.plusDays(2)), task("overdue", today.minusDays(1)), task("today", today)),
            pantry = listOf(stock("expired milk", today.minusDays(1))),
        )
        val order = buildAttention(data, today).map { it.id }
        assertEquals(listOf("overdue", "expired milk", "today", "soon"), order)
    }

    @Test
    fun `far-off and undated tasks are not attention`() {
        val data = HomeData(tasks = listOf(task("later", today.plusDays(10)), task("someday", null)))
        assertTrue(buildAttention(data, today).isEmpty())
    }

    @Test
    fun `medicines warn a month ahead, groceries a week`() {
        val data = HomeData(
            pantry = listOf(stock("rice", today.plusDays(20))),
            medicines = listOf(stock("crocin", today.plusDays(20))),
        )
        val items = buildAttention(data, today)
        assertEquals(listOf("crocin"), items.map { it.id })
        assertEquals(Area.HEALTH, items.single().area)
    }

    @Test
    fun `low stock without expiry is soon`() {
        val item = buildAttention(HomeData(pantry = listOf(stock("oil", null, level = 25))), today).single()
        assertEquals(Priority.SOON, item.priority)
        assertEquals("Running low · 25% left", item.detail)
    }

    @Test
    fun `yearly events roll to their next date`() {
        assertEquals(LocalDate.of(2026, 11, 5), nextOccurrence(LocalDate.of(2020, 11, 5), "Yearly", today))
        assertEquals(LocalDate.of(2026, 11, 1), nextOccurrence(LocalDate.of(2026, 11, 1), "Never", today))
    }

    @Test
    fun `birthday today is a today item`() {
        val data = HomeData(events = listOf(EventItem("bday", "Riya birthday", "Birthday", today)))
        assertEquals("Birthday today 🎉", buildAttention(data, today).single().detail)
    }

    @Test
    fun `renewal within two days is today, within a week soon`() {
        val data = HomeData(
            renewals = listOf(
                Renewal("netflix", "Netflix", today.plusDays(1), "₹649"),
                Renewal("gym", "Gym", today.plusDays(5), null),
            ),
        )
        val items = buildAttention(data, today)
        assertEquals(listOf(Priority.TODAY, Priority.SOON), items.map { it.priority })
        assertEquals("₹649 due tomorrow", items.first().detail)
    }

    @Test
    fun `over-budget categories are urgent`() {
        val summary = summarize(
            Budget(10_000_00, "INR", categories = mapOf("Dining" to 1_000_00)),
            listOf(Txn("1", "Toit", "Dining", 1_500_00, today, planned = false)),
            emptyList(),
            today,
        )
        val item = buildAttention(HomeData(), today, summary) { "₹${it / 100}" }.first()
        assertEquals(Priority.URGENT, item.priority)
        assertEquals("Dining is over budget", item.title)
        assertEquals("₹500 over this month", item.detail)
    }

    @Test
    fun `today lists tasks with alerts, undated tasks last`() {
        val data = HomeData(
            tasks = listOf(task("someday", null), task("week", today.plusDays(5)), task("overdue", today.minusDays(1)), task("later", today.plusDays(10))),
            pantry = listOf(stock("expired milk", today.minusDays(1))),
        )
        val rows = buildToday(buildAttention(data, today), data.tasks, today)
        val ids = rows.map { (it as? TodayRow.Task)?.task?.id ?: (it as TodayRow.Alert).item.id }
        assertEquals(listOf("overdue", "expired milk", "week", "someday"), ids)
        assertTrue(rows.first() is TodayRow.Task)
    }

    @Test
    fun `events a few days out stay in coming up, today's show in today`() {
        val data = HomeData(
            events = listOf(
                EventItem("bday", "Riya birthday", "Birthday", today.plusDays(4)),
                EventItem("anniv", "Anniversary", "Anniversary", today),
            ),
        )
        val ids = buildToday(buildAttention(data, today), emptyList(), today).map { (it as TodayRow.Alert).item.id }
        assertEquals(listOf("anniv"), ids)
    }
}
