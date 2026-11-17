package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.localDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StockTextTest {
    private val today = localDate(2026, 11, 16)

    @Test
    fun dayLabelSaysNearDaysInWords() {
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Tomorrow", dayLabel(localDate(2026, 11, 17), today))
        assertEquals("Yesterday", dayLabel(localDate(2026, 11, 15), today))
        assertEquals("Thu 3 Dec", dayLabel(localDate(2026, 12, 3), today))
        assertEquals("5 Mar 2027", dayLabel(localDate(2027, 3, 5), today))
    }

    @Test
    fun quantityDropsNeedlessDecimals() {
        assertEquals("2 packs", qty(2.0, "packs"))
        assertEquals("1.5 kg", qty(1.5, "kg"))
        assertEquals("", qty(null, "kg"))
    }

    @Test
    fun detailShowsAmountDateAndPlace() {
        val crocin = PantryStock("c", "Crocin", 10.0, "tablets", 15.0, expiry = localDate(2027, 3, 5), keptAt = "medicine drawer")
        assertEquals("10 tablets · use by 5 Mar 2027 · 📍 medicine drawer", stockDetail(crocin, null, today))
        assertEquals("10 tablets · expired · 📍 medicine drawer", stockDetail(crocin, Flag.EXPIRED, today))
        assertEquals("10 tablets · 66% left · 📍 medicine drawer", stockDetail(crocin, Flag.LOW, today))
        assertEquals("In stock", stockDetail(PantryStock("b", "Bulb", null, "", null), null, today))
    }
}
