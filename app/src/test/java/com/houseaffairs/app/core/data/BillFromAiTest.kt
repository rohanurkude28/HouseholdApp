package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Gemini's reading of a bill (Android: GeminiAssistant). */
class BillFromAiTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun `gemini's reading of a bill is checked field by field`() {
        val json = """{"total": 530.5, "shop": "DMart", "date": "2026-11-09", "pharmacy": false,
            "items": [{"name": "Milk 1L", "qty": 2, "price": 68}, {"name": "?", "qty": 1}, {"name": "Rice", "qty": 900, "price": null}]}"""
        val bill = com.houseaffairs.app.feature.quickadd.billFromAi(json, today)!!
        assertEquals(java.math.BigDecimal("530.5"), bill.receipt.total)
        assertEquals(LocalDate.of(2026, 11, 9), bill.receipt.date)
        assertEquals(listOf("Milk 1L", "Rice"), bill.items.map { it.name })
        assertEquals(50, bill.items.last().quantity)
        assertNull(com.houseaffairs.app.feature.quickadd.billFromAi("""{"total": null, "items": []}""", today))
        assertNull(com.houseaffairs.app.feature.quickadd.billFromAi("not json", today))
    }
}
