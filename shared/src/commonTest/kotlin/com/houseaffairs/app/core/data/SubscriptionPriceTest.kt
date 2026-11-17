package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek

import kotlin.test.assertEquals
import kotlin.test.Test

class SubscriptionPriceTest {
    private val nov = localDate(2026, 11, 15)

    @Test
    fun `with no history the subscription's own price is used`() {
        assertEquals(649L, priceOn(nov, 649, emptyList()))
    }

    @Test
    fun `a new price from a date leaves earlier renewals at the old price`() {
        val h = changePrice(emptyList(), oldMinor = 649, newMinor = 799, from = localDate(2026, 12, 15))
        assertEquals(649L, priceOn(nov, 799, h))
        assertEquals(799L, priceOn(localDate(2026, 12, 15), 799, h))
        assertEquals(799L, priceOn(localDate(2027, 3, 15), 799, h))
    }

    @Test
    fun `a second change replaces anything planned after it and fixing a mistake clears it all`() {
        val first = changePrice(emptyList(), 649, 799, localDate(2027, 1, 1))
        val second = changePrice(first, 799, 699, localDate(2026, 12, 1))
        assertEquals(listOf(PriceChange(SINCE_START, 649), PriceChange(localDate(2026, 12, 1), 699)), second)
        assertEquals(emptyList<PriceChange>(), changePrice(second, 699, 499, from = null))
    }

    @Test
    fun `prices survive being saved and read back`() {
        val h = changePrice(emptyList(), 649, 799, localDate(2026, 12, 15))
        assertEquals(h, decodePrices(encodePrices(h)))
        assertEquals(PriceFrom.ALWAYS, PriceFrom.of("Every payment (fix a mistake)"))
    }
}
