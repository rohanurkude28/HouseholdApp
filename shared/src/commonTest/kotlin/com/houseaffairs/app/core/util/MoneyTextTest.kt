package com.houseaffairs.app.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTextTest {
    @Test
    fun `money text groups rupees in lakhs and drops whole decimals`() {
        assertEquals("₹1,23,456", moneyText(1_23_456_00, "INR"))
        assertEquals("$12.50", moneyText(1250, "USD"))
    }

    @Test
    fun `compact money matches Android's short form`() {
        assertEquals("₹39.9k", compactMoneyText(39_912_00, "INR"))
        assertEquals("₹1.2L", compactMoneyText(1_20_000_00, "INR"))
        assertEquals("₹450", compactMoneyText(450_00, "INR"))
        assertEquals("-$2.3M", compactMoneyText(-2_300_000_00, "USD"))
    }
}
