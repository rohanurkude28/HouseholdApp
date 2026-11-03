package com.houseaffairs.app.feature.quickadd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class QuickAddParserTest {

    // Monday 2 Nov 2026
    private val parser = QuickAddParser(today = { LocalDate.of(2026, 11, 2) })

    private fun expense(text: String) = parser.parse(text) as QuickAddDraft.Expense

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals("amount", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun `leading amount is an expense with category and merchant`() {
        val draft = expense("450 groceries DMart")
        assertAmount("450", draft.amount)
        assertNull(draft.currency)
        assertEquals("Groceries", draft.category)
        assertEquals("DMart", draft.merchant)
    }

    @Test
    fun `currency symbol, thousands separator and merchant after at`() {
        val draft = expense("spent ₹1,200 on dinner at Toit")
        assertAmount("1200", draft.amount)
        assertEquals("INR", draft.currency)
        assertEquals("Dining", draft.category)
        assertEquals("Toit", draft.merchant)
    }

    @Test
    fun `k suffix means thousands`() {
        val draft = expense("1.5k rent")
        assertAmount("1500", draft.amount)
        assertEquals("Rent", draft.category)
        assertNull(draft.merchant)
    }

    @Test
    fun `currency code after amount and brand kept as merchant`() {
        val draft = expense("20 usd netflix")
        assertEquals("USD", draft.currency)
        assertEquals("Entertainment", draft.category)
        assertEquals("netflix", draft.merchant)
    }

    @Test
    fun `dollar prefix`() {
        val draft = expense("\$12.50 uber")
        assertAmount("12.50", draft.amount)
        assertEquals("USD", draft.currency)
        assertEquals("Transport", draft.category)
    }

    @Test
    fun `item over goes to shopping list`() {
        assertEquals(QuickAddDraft.Shopping("Milk"), parser.parse("milk over"))
        assertEquals(QuickAddDraft.Shopping("Rice"), parser.parse("rice is running low"))
    }

    @Test
    fun `out of goes to shopping list`() {
        assertEquals(QuickAddDraft.Shopping("Eggs"), parser.parse("we're out of eggs"))
    }

    @Test
    fun `need with a quantity goes to shopping list, not tasks`() {
        assertEquals(QuickAddDraft.Shopping("Peanuts", "500 g"), parser.parse("need 500g peanuts"))
        assertEquals(QuickAddDraft.Shopping("Peanuts", "500 g"), parser.parse("need 500 g of peanuts"))
        assertEquals(QuickAddDraft.Shopping("Rice", "2 kg"), parser.parse("buy 2kg rice"))
        assertEquals(QuickAddDraft.Shopping("Onions", "2 pcs"), parser.parse("need 2 onions"))
        assertEquals(QuickAddDraft.Shopping("Eggs", "12 pcs"), parser.parse("need a dozen eggs"))
        assertEquals(QuickAddDraft.Shopping("Milk", "1 L"), parser.parse("get 1L milk"))
        assertEquals(QuickAddDraft.Shopping("Peanuts", "500 g"), parser.parse("500g peanuts"))
        assertEquals(QuickAddDraft.Shopping("Paneer", "200 g"), parser.parse("add 200g paneer to shopping list"))
    }

    @Test
    fun `need to do something stays a task`() {
        assertTrue(parser.parse("need to call the plumber") is QuickAddDraft.Task)
        assertTrue(parser.parse("need 5000 for rent") !is QuickAddDraft.Shopping)
    }

    @Test
    fun `add to named list`() {
        assertEquals(QuickAddDraft.ListItem(ListTemplate.WATCH, "Dune 2"), parser.parse("add Dune 2 to watch list"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.TRAVEL, "Trip to Goa"), parser.parse("add trip to Goa to travel list"))
        assertEquals(QuickAddDraft.Shopping("Paneer"), parser.parse("add paneer to shopping list"))
    }

    @Test
    fun `time of day is not an amount`() {
        assertEquals(
            QuickAddDraft.Task("Riya dentist 5pm", LocalDate.of(2026, 11, 6)),
            parser.parse("Riya dentist Friday 5pm"),
        )
    }

    @Test
    fun `task with relative due date`() {
        assertEquals(
            QuickAddDraft.Task("Pay electricity bill", LocalDate.of(2026, 11, 3)),
            parser.parse("remind me to pay electricity bill tomorrow"),
        )
    }

    @Test
    fun `number without money context is a task`() {
        assertEquals(QuickAddDraft.Task("Call 2 plumbers", null), parser.parse("call 2 plumbers"))
    }

    @Test
    fun `blank input gives nothing`() {
        assertNull(parser.parse("   "))
    }

    @Test
    fun `frequency makes a subscription`() {
        val draft = parser.parse("Netflix 649 monthly") as QuickAddDraft.Subscription
        assertEquals("Netflix", draft.title)
        assertAmount("649", draft.amount)
        assertEquals("Monthly", draft.frequency)
        assertEquals("Yearly", (parser.parse("599 per year spotify") as QuickAddDraft.Subscription).frequency)
    }

    @Test
    fun `this month is not a subscription`() {
        assertEquals("Dining", expense("spent 500 this month on food").category)
    }

    @Test
    fun `birthday with day and month is an event`() {
        assertEquals(
            QuickAddDraft.Event("Riya birthday", "Birthday", LocalDate.of(2026, 12, 12)),
            parser.parse("Riya birthday 12 Dec"),
        )
        // Already passed this year, so next year.
        assertEquals(LocalDate.of(2027, 1, 5), (parser.parse("car insurance renewal on Jan 5") as QuickAddDraft.Event).date)
    }

    @Test
    fun `birthday gift with an amount is an expense`() {
        val draft = expense("Riya birthday gift 2000")
        assertAmount("2000", draft.amount)
        assertEquals("Gifts", draft.category)
    }

    @Test
    fun `medicine with person and expiry month`() {
        assertEquals(
            QuickAddDraft.Medicine("Crocin", "Riya", LocalDate.of(2027, 3, 31)),
            parser.parse("bought crocin for riya expires march 2027"),
        )
        assertEquals(QuickAddDraft.Medicine("Dolo 650", null, null), parser.parse("dolo 650"))
    }

    @Test
    fun `medicine running out goes to shopping`() {
        assertEquals(QuickAddDraft.Shopping("Crocin"), parser.parse("crocin over"))
    }

    @Test
    fun `grocery with quantity and expiry`() {
        assertEquals(
            QuickAddDraft.Grocery("Rice", "2 kg", LocalDate.of(2027, 6, 30)),
            parser.parse("bought 2kg rice expires june"),
        )
        assertEquals(LocalDate.of(2026, 11, 6), (parser.parse("paneer expires friday") as QuickAddDraft.Grocery).expiry)
    }

    @Test
    fun `bare month word is not a date`() {
        assertEquals(QuickAddDraft.Task("Riya may call", null), parser.parse("Riya may call"))
    }

    @Test
    fun `task with day and month`() {
        assertEquals(QuickAddDraft.Task("Pay school fees", LocalDate.of(2026, 12, 5)), parser.parse("pay school fees by 5th December"))
    }

    @Test
    fun `expired yesterday is a grocery dated yesterday`() {
        assertEquals(QuickAddDraft.Grocery("Milk", null, LocalDate.of(2026, 11, 1)), parser.parse("milk expired yesterday"))
    }

    @Test
    fun `made a dish is cooking`() {
        assertEquals(QuickAddDraft.Cooked("Paneer sabzi", null), parser.parse("made paneer sabzi today"))
        assertEquals(QuickAddDraft.Cooked("Dal", 4), parser.parse("I cooked dal for 4 for dinner"))
    }

    @Test
    fun `made a payment is not cooking`() {
        assertEquals("Rent", expense("made ₹15000 rent payment").category)
    }

    @Test
    fun `move money between envelopes`() {
        val m = parser.parse("move 2000 from gifting to travel") as QuickAddDraft.Move
        assertAmount("2000", m.amount)
        assertEquals("Gifts", m.from)
        assertEquals("Travel", m.to)
    }

    @Test
    fun `save money for a dream`() {
        val f = parser.parse("save 5k for Bali trip") as QuickAddDraft.Fund
        assertAmount("5000", f.amount)
        assertEquals("Bali trip", f.target)
        // Lists still win: this is a watch-list item, not money.
        assertEquals(QuickAddDraft.ListItem(ListTemplate.WATCH, "Dune 2"), parser.parse("add Dune 2 to watch list"))
    }

    @Test
    fun `gift card with earmark and expiry`() {
        assertEquals(
            QuickAddDraft.Credit("Amazon gift card", "Gift card", BigDecimal("2000"), "Gifts", LocalDate.of(2027, 3, 31)),
            parser.parse("got amazon gift card 2000 for gifting expires march 2027"),
        )
    }

    @Test
    fun `dream with amount and month`() {
        val d = parser.parse("dream: Bali trip 150000 by March") as QuickAddDraft.Dream
        assertEquals("Bali trip", d.title)
        assertEquals("Travel", d.kind)
        assertAmount("150000", d.amount!!)
        assertEquals(LocalDate.of(2027, 3, 31), d.date)
        assertEquals("Car", (parser.parse("want to buy a new car 8 lakh") as QuickAddDraft.Dream).kind)
    }

    @Test
    fun `questions for the assistant`() {
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.COOK, null), parser.parse("What can we cook tonight?"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.EXPIRING, null), parser.parse("what's expiring"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.LEFT, "dining"), parser.parse("how much is left for dining?"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.LEFT, null), parser.parse("how much budget left"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.AFFORD, "Bali trip"), parser.parse("can we afford the Bali trip?"))
    }

    @Test
    fun `where things are kept`() {
        assertEquals(QuickAddDraft.Kept("Gold necklace", "Bank locker"), parser.parse("gold necklace is in the bank locker"))
        assertEquals(QuickAddDraft.Kept("Spare car keys", "Blue drawer in the bedroom"), parser.parse("kept spare car keys in the blue drawer in the bedroom"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.WHERE, "car keys"), parser.parse("where are the car keys?"))
        // Not storage: still a task.
        assertEquals(QuickAddDraft.Task("Riya is at school", null), parser.parse("Riya is at school"))
    }

    @Test
    fun `household supplies`() {
        assertEquals(
            QuickAddDraft.Household("AAA battery", "5 units", LocalDate.of(2028, 12, 31), "Batteries"),
            parser.parse("AAA battery 5 units expiry 2028"),
        )
        assertEquals(QuickAddDraft.Household("Bulbs", "2 units", null, "Bulbs"), parser.parse("bought 2 bulbs"))
        // A priced purchase is still an expense.
        assertEquals("Other", expense("₹300 batteries").category)
    }

    @Test
    fun `investment interest, value and parking`() {
        assertEquals(QuickAddDraft.Growth(BigDecimal("1200"), "HDFC FD"), parser.parse("add interest 1200 to HDFC FD"))
        assertEquals(QuickAddDraft.Growth(BigDecimal("800"), "FD"), parser.parse("got 800 interest on fd"))
        assertEquals(QuickAddDraft.Revalue("Axis bluechip", BigDecimal("52000")), parser.parse("Axis bluechip is now worth 52000"))
        assertEquals(QuickAddDraft.Revalue("Gold", BigDecimal("75000")), parser.parse("update gold to 75000"))
        val park = parser.parse("park 20000 in FD") as QuickAddDraft.Fund
        assertEquals("FD", park.target)
    }

    @Test
    fun `going somewhere asks for nearby places`() {
        val nearby = QuickAddDraft.Question.NEARBY
        assertEquals(QuickAddDraft.Ask(nearby, "Indiranagar"), parser.parse("I'm going to Indiranagar"))
        assertEquals(QuickAddDraft.Ask(nearby, "Spar"), parser.parse("heading to Spar"))
        assertEquals(QuickAddDraft.Ask(nearby, null), parser.parse("what's near me?"))
        assertEquals(QuickAddDraft.Ask(nearby, "Koramangala"), parser.parse("anything to try around Koramangala"))
        // Plans, not trips.
        assertEquals(QuickAddDraft.Task("I'm going to pay rent", LocalDate.of(2026, 11, 3)), parser.parse("I'm going to pay rent tomorrow"))
    }

    @Test
    fun `sub-categories from words`() {
        val lunch = expense("1200 lunch at Toit")
        assertEquals("Dining", lunch.category)
        assertEquals("Lunch", lunch.subcategory)
        val cab = expense("450 uber")
        assertEquals("Transport", cab.category)
        assertEquals("Cab", cab.subcategory)
    }

    @Test
    fun `starting a trip`() {
        assertEquals(QuickAddDraft.Trip("Lonavala", LocalDate.of(2026, 11, 8)), parser.parse("start trip Lonavala this sunday"))
        assertEquals(QuickAddDraft.Trip("Goa", null), parser.parse("start a trip to Goa"))
    }

    @Test
    fun `symptoms ask for care`() {
        val q = QuickAddDraft.Question.SYMPTOM
        assertEquals(QuickAddDraft.Ask(q, "Fever"), parser.parse("I'm feeling feverish"))
        assertEquals(QuickAddDraft.Ask(q, "Loose motions", "Riya"), parser.parse("Riya has loose motions"))
        assertEquals(QuickAddDraft.Ask(q, "Burns"), parser.parse("burnt my hand"))
        // A medicine entry is still a medicine entry.
        assertEquals(QuickAddDraft.Medicine("Crocin", "Riya", LocalDate.of(2027, 3, 31)), parser.parse("bought crocin for riya expires march 2027"))
    }

    @Test
    fun `activities are logged`() {
        assertEquals(QuickAddDraft.Activity("Walk", 30, null), parser.parse("walked 30 min"))
        assertEquals(QuickAddDraft.Activity("Yoga", 45, null), parser.parse("45 minutes yoga"))
        assertEquals(QuickAddDraft.Activity("Gardening", 60, null), parser.parse("gardening for an hour"))
        assertEquals(QuickAddDraft.Activity("Walk", null, 8000), parser.parse("did 8,000 steps"))
        // Buying gym shoes is spending, not exercise.
        assertTrue(parser.parse("₹2000 gym shoes") is QuickAddDraft.Expense)
    }

    @Test
    fun `cosmetics with an expiry go to supplies`() {
        val d = parser.parse("sunscreen expires March 2027") as QuickAddDraft.Household
        assertEquals("Cosmetics & skincare", d.type)
        assertEquals(LocalDate.of(2027, 3, 31), d.expiry)
    }
}
