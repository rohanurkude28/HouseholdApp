package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class ExportTest {
    @Test
    fun `json escapes text and keeps types`() {
        val json = jsonOf(mapOf("title" to "Say \"hi\"\nnow", "n" to 3L, "x" to 1.5, "ok" to true, "none" to null, "tags" to listOf("a", "b")))
        assertEquals("""{"title":"Say \"hi\"\nnow","n":3,"x":1.5,"ok":true,"none":null,"tags":["a","b"]}""", json)
    }

    @Test
    fun `export leaves out empty collections`() {
        val json = exportJson("Our home", "2026-11-05", mapOf("tasks" to emptyList(), "shopping" to listOf(mapOf("id" to "s1", "title" to "Milk"))))
        assertTrue(json.contains("\"shopping\":[{\"id\":\"s1\",\"title\":\"Milk\"}]"))
        assertFalse(json.contains("tasks"))
    }

    @Test
    fun `expenses csv uses plain amounts and member names oldest first`() {
        val csv = expensesCsv(
            listOf(
                mapOf("date" to "2026-11-03", "title" to "Dinner, Goa", "category" to "Dining", "amountMinor" to 125050L, "currency" to "INR", "paidBy" to "u1", "tags" to listOf("Trip")),
                mapOf("date" to "2026-11-01", "title" to "Milk", "category" to "Groceries", "amountMinor" to 6000L, "currency" to "INR", "paymentMethod" to "UPI"),
            ),
            names = mapOf("u1" to "Asha"),
        )
        val lines = csv.trimEnd().split("\r\n")
        assertEquals("Date,What,Category,Amount,Currency,Paid with,Paid by,Status,Tags,Note", lines[0])
        assertEquals("2026-11-01,Milk,Groceries,60.00,INR,UPI,,Paid,,", lines[1])
        assertEquals("2026-11-03,\"Dinner, Goa\",Dining,1250.50,INR,,Asha,Paid,Trip,", lines[2])
    }

    @Test
    fun `csv defuses spreadsheet formulas`() {
        val csv = expensesCsv(listOf(mapOf("date" to "2026-11-01", "title" to "=HYPERLINK(1)", "amountMinor" to 100L, "currency" to "JPY")), emptyMap())
        assertTrue(csv.contains(",'=HYPERLINK(1),"))
        assertTrue(csv.contains(",100,JPY,"))
    }
}
