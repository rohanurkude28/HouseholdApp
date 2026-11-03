package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EnvelopesTest {

    private val today = LocalDate.of(2026, 11, 3)
    private val budget = Budget(
        limitMinor = 100_000_00,
        currency = "INR",
        categories = mapOf("Gifts" to 2_000_00, "Travel" to 4_000_00, "Dining" to 5_000_00),
    )
    private fun paid(category: String, amount: Long, date: LocalDate) = Txn("t$category$date", category, category, amount, date, planned = false)

    @Test
    fun `envelopes start fresh each month and leftovers drop into the gullak`() {
        // Tracked since September; dining overspent in October.
        val state = envelopes(
            budget, LocalDate.of(2026, 9, 1),
            txns = listOf(paid("Dining", 6_000_00, LocalDate.of(2026, 10, 5)), paid("Gifts", 500_00, today)),
            transfers = emptyList(), credits = emptyList(), today = today,
        )
        val gifts = state.envelopes.first { it.category == "Gifts" }
        assertEquals(2_000_00L - 500_00, gifts.availableMinor)
        assertEquals(5_000_00L, state.envelopes.first { it.category == "Dining" }.availableMinor)
        // September: 100k unspent. October: 100k − 6k.
        assertEquals(194_000_00L, state.potMinor)
        assertEquals(194_000_00L, state.savedThisYearMinor)
    }

    @Test
    fun `moves to a goal gullak come out of the main one`() {
        val state = envelopes(
            budget, LocalDate.of(2026, 10, 1),
            txns = listOf(paid("Shopping", 10_000_00, LocalDate.of(2026, 10, 10))),
            transfers = listOf(Transfer("m", "pot", "dream:bali", 30_000_00, today)),
            credits = emptyList(), today = today,
        )
        // October: 100k − 10k spent = 90k into the gullak; 30k moved to Bali.
        assertEquals(60_000_00L, state.potMinor)
        assertEquals(30_000_00L, state.dreamFunding["bali"])
    }

    @Test
    fun `a top-up from the gullak that isn't spent goes back at month end`() {
        val oct = LocalDate.of(2026, 10, 15)
        val state = envelopes(
            budget, LocalDate.of(2026, 10, 1),
            txns = listOf(paid("Dining", 6_000_00, oct)),
            transfers = listOf(Transfer("t", "pot", "cat:Dining", 3_000_00, oct)),
            credits = emptyList(), today = today,
        )
        // 100k + 3k topped up − 6k spent = 97k, less the 3k that left the gullak = 94k.
        assertEquals(94_000_00L, state.potMinor)
        // November's Dining envelope is fresh: the October top-up doesn't carry.
        assertEquals(5_000_00L, state.envelopes.first { it.category == "Dining" }.availableMinor)
    }

    @Test
    fun `moving between envelopes`() {
        val state = envelopes(
            budget, today, emptyList(),
            transfers = listOf(Transfer("m", "cat:Gifts", "cat:Travel", 1_500_00, today)),
            credits = emptyList(), today = today,
        )
        assertEquals(500_00L, state.envelopes.first { it.category == "Gifts" }.availableMinor)
        assertEquals(5_500_00L, state.envelopes.first { it.category == "Travel" }.availableMinor)
    }

    @Test
    fun `dream progress counts savings and earmarked credits, and pot affordability`() {
        val state = EnvelopeState(emptyList(), potMinor = 60_000_00, savedThisYearMinor = 0, dreamFunding = mapOf("bali" to 80_000_00))
        val bali = Dream("bali", "Bali trip", "Travel", "✈️", 1_50_000_00, LocalDate.of(2027, 3, 3))
        val credits = listOf(
            Credit("c", "MakeMyTrip voucher", "Voucher", 10_000_00, null, earmark = "bali trip"),
            Credit("x", "Expired", "Coupon", 5_000_00, LocalDate.of(2026, 1, 1), earmark = "Bali trip"),
        )
        val p = dreamProgress(listOf(bali), state, credits, today).single()
        assertEquals(90_000_00L, p.totalMinor)
        assertEquals(60_000_00L, p.remainingMinor)
        assertTrue(p.affordableNow) // pot covers the rest
        assertEquals(15_000_00L, p.monthlyNeededMinor) // 4 months to March
    }

    @Test
    fun `not affordable when pot is short`() {
        val car = Dream("car", "Car", "Car", "🚗", 8_00_000_00, null)
        val p = dreamProgress(listOf(car), EnvelopeState(emptyList(), 1_000_00, 0, emptyMap()), emptyList(), today).single()
        assertFalse(p.affordableNow)
        assertEquals(null, p.monthlyNeededMinor)
    }

    private val fd = Investment("fd", "HDFC FD", "Fixed deposit", ratePercent = 7.0, maturity = today.plusDays(365), forDream = "Bali trip")
    private fun t(from: String, to: String, amount: Long, days: Long = 0) = Transfer("$from$to$amount", from, to, amount, today.minusDays(days))

    @Test
    fun `investment value is money in, out and growth`() {
        val ledger = listOf(
            t("pot", "inv:fd", 50_000_00, 30),
            t("cat:Gifts", "inv:fd", 2_000_00, 20),
            t("growth", "inv:fd", 1_200_00, 10), // interest
            t("inv:fd", "pot", 3_000_00, 5), // withdrawal
        )
        val v = investmentValues(listOf(fd), ledger, today).single()
        assertEquals(49_000_00L, v.investedMinor)
        assertEquals(1_200_00L, v.growthMinor)
        assertEquals(50_200_00L, v.currentMinor)
        assertEquals(4, v.history.size)
        // Editing the value to 51,000 records an 800 gain.
        assertEquals(800_00L, revaluation(v, 51_000_00))
        assertEquals(53_714_00L, v.estimateAtMaturity(today)!! / 100 * 100)
    }

    @Test
    fun `money parked in an investment leaves the pot and counts toward its dream`() {
        val ledger = listOf(Transfer("p", "pot", "inv:fd", 30_000_00, today))
        val state = envelopes(budget, LocalDate.of(2026, 10, 1), emptyList(), ledger, emptyList(), today)
        assertEquals(100_000_00L - 30_000_00, state.potMinor)
        val values = investmentValues(listOf(fd), ledger, today)
        val bali = Dream("bali", "Bali trip", "Travel", "✈️", 1_00_000_00, null)
        assertEquals(30_000_00L, dreamProgress(listOf(bali), state, emptyList(), today, values).single().investedMinor)
    }
}
