package com.houseaffairs.app.feature.quickadd

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import com.houseaffairs.app.core.util.Decimal
import com.houseaffairs.app.core.util.compareValue
import com.houseaffairs.app.core.util.decimalOf
import com.houseaffairs.app.core.time.*

class QuickAddParserTest {

    // Monday 2 Nov 2026
    private val parser = QuickAddParser(today = { localDate(2026, 11, 2) })

    private fun expense(text: String) = parser.parse(text) as QuickAddDraft.Expense

    private fun assertAmount(expected: String, actual: Decimal) =
        assertEquals(0, decimalOf(expected)!!.compareValue(actual), "amount")

    @Test
    fun `leading amount is an expense with category and merchant`() {
        val draft = expense("450 groceries DMart")
        assertAmount("450", draft.amount)
        assertNull(draft.currency)
        assertEquals("Groceries", draft.category)
        assertEquals("DMart", draft.merchant)
    }

    @Test
    fun `currency symbol - thousands separator and merchant after at`() {
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
    fun `need with a quantity goes to shopping list - not tasks`() {
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
            QuickAddDraft.Task("Riya dentist", localDate(2026, 11, 6), time = "5:00 pm"),
            parser.parse("Riya dentist Friday 5pm"),
        )
    }

    @Test
    fun `task with relative due date`() {
        assertEquals(
            QuickAddDraft.Task("Pay electricity bill", localDate(2026, 11, 3)),
            parser.parse("remind me to pay electricity bill tomorrow"),
        )
    }

    @Test
    fun `number without money context is a task`() {
        assertEquals(QuickAddDraft.Task("Call 2 plumbers", localDate(2026, 11, 2), reach = "Call"), parser.parse("call 2 plumbers"))
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
            QuickAddDraft.Event("Riya birthday", "Birthday", localDate(2026, 12, 12)),
            parser.parse("Riya birthday 12 Dec"),
        )
        // Already passed this year, so next year.
        assertEquals(localDate(2027, 1, 5), (parser.parse("car insurance renewal on Jan 5") as QuickAddDraft.Event).date)
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
            QuickAddDraft.Medicine("Crocin", "Riya", localDate(2027, 3, 31)),
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
            QuickAddDraft.Grocery("Rice", "2 kg", localDate(2027, 6, 30)),
            parser.parse("bought 2kg rice expires june"),
        )
        assertEquals(localDate(2026, 11, 6), (parser.parse("paneer expires friday") as QuickAddDraft.Grocery).expiry)
    }

    @Test
    fun `bare month word is not a date`() {
        assertEquals(QuickAddDraft.Task("Riya may call", null), parser.parse("Riya may call"))
    }

    @Test
    fun `task with day and month`() {
        assertEquals(QuickAddDraft.Task("Pay school fees", localDate(2026, 12, 5)), parser.parse("pay school fees by 5th December"))
    }

    @Test
    fun `expired yesterday is a grocery dated yesterday`() {
        assertEquals(QuickAddDraft.Grocery("Milk", null, localDate(2026, 11, 1)), parser.parse("milk expired yesterday"))
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
            QuickAddDraft.Credit("Amazon gift card", "Gift card", decimalOf("2000")!!, "Gifts", localDate(2027, 3, 31)),
            parser.parse("got amazon gift card 2000 for gifting expires march 2027"),
        )
    }

    @Test
    fun `dream with amount and month`() {
        val d = parser.parse("dream: Bali trip 150000 by March") as QuickAddDraft.Dream
        assertEquals("Bali trip", d.title)
        assertEquals("Travel", d.kind)
        assertAmount("150000", d.amount!!)
        assertEquals(localDate(2027, 3, 31), d.date)
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
            QuickAddDraft.Household("AAA battery", "5 units", localDate(2028, 12, 31), "Batteries"),
            parser.parse("AAA battery 5 units expiry 2028"),
        )
        assertEquals(QuickAddDraft.Household("Bulbs", "2 units", null, "Bulbs"), parser.parse("bought 2 bulbs"))
        // A priced purchase is still an expense.
        assertEquals("Other", expense("₹300 batteries").category)
    }

    @Test
    fun `investment interest - value and parking`() {
        assertEquals(QuickAddDraft.Growth(decimalOf("1200")!!, "HDFC FD"), parser.parse("add interest 1200 to HDFC FD"))
        assertEquals(QuickAddDraft.Growth(decimalOf("800")!!, "FD"), parser.parse("got 800 interest on fd"))
        assertEquals(QuickAddDraft.Revalue("Axis bluechip", decimalOf("52000")!!), parser.parse("Axis bluechip is now worth 52000"))
        assertEquals(QuickAddDraft.Revalue("Gold", decimalOf("75000")!!), parser.parse("update gold to 75000"))
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
        assertEquals(QuickAddDraft.Task("I'm going to pay rent", localDate(2026, 11, 3)), parser.parse("I'm going to pay rent tomorrow"))
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
        assertEquals(QuickAddDraft.Trip("Lonavala", localDate(2026, 11, 8)), parser.parse("start trip Lonavala this sunday"))
        assertEquals(QuickAddDraft.Trip("Goa", null), parser.parse("start a trip to Goa"))
    }

    @Test
    fun `symptoms ask for care`() {
        val q = QuickAddDraft.Question.SYMPTOM
        assertEquals(QuickAddDraft.Ask(q, "Fever"), parser.parse("I'm feeling feverish"))
        assertEquals(QuickAddDraft.Ask(q, "Loose motions", "Riya"), parser.parse("Riya has loose motions"))
        assertEquals(QuickAddDraft.Ask(q, "Burns"), parser.parse("burnt my hand"))
        // A medicine entry is still a medicine entry.
        assertEquals(QuickAddDraft.Medicine("Crocin", "Riya", localDate(2027, 3, 31)), parser.parse("bought crocin for riya expires march 2027"))
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
        assertEquals(localDate(2027, 3, 31), d.expiry)
    }

    @Test
    fun `repeating tasks`() {
        val weekly = parser.parse("water the plants every week") as QuickAddDraft.Task
        assertEquals("Water the plants", weekly.title)
        assertEquals("Weekly", weekly.repeat)
        val sunday = parser.parse("take out the trash every sunday") as QuickAddDraft.Task
        assertEquals("Take out the trash", sunday.title)
        assertEquals("Weekly", sunday.repeat)
        assertEquals(kotlinx.datetime.DayOfWeek.SUNDAY, sunday.due?.dayOfWeek)
        assertEquals("Daily", (parser.parse("feed the cat daily") as QuickAddDraft.Task).repeat)
        assertEquals(null, (parser.parse("call the plumber") as QuickAddDraft.Task).repeat)
    }

    @Test
    fun `cravings are understood - symptoms still are too`() {
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.CRAVING, "something crispy with a savoury curry"), parser.parse("I want to eat something crispy with a savoury curry"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.CRAVING, "something spicy"), parser.parse("feel like eating something spicy"))
        assertEquals(QuickAddDraft.Question.CRAVING, (parser.parse("something tangy for dinner") as QuickAddDraft.Ask).question)
        assertEquals(QuickAddDraft.Question.SYMPTOM, (parser.parse("I'm feeling feverish") as QuickAddDraft.Ask).question)
    }

    @Test
    fun `task priority from the words`() {
        val urgent = parser.parse("urgent call the bank tomorrow") as QuickAddDraft.Task
        assertEquals("High", urgent.priority)
        assertEquals("Call the bank", urgent.title)
        assertEquals("Medium", (parser.parse("book car service medium priority") as QuickAddDraft.Task).priority)
        assertEquals(null, (parser.parse("call the plumber") as QuickAddDraft.Task).priority)
    }

    @Test
    fun `want to watch - read or visit goes to that list - not a dream`() {
        assertEquals(QuickAddDraft.ListItem(ListTemplate.WATCH, "Oppenheimer"), parser.parse("I want to watch Oppenheimer"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.BOOKS, "Atomic Habits"), parser.parse("want to read Atomic Habits"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.MUSIC, "Kesariya"), parser.parse("want to listen to Kesariya"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.TRAVEL, "Ladakh"), parser.parse("we want to visit Ladakh"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.BUY, "An air fryer"), parser.parse("want to buy an air fryer"))
    }

    @Test
    fun `nothing named means pick from the list`() {
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.PICK, "WATCH"), parser.parse("want to watch a movie"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.PICK, "WATCH"), parser.parse("I want to watch something funny tonight"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.PICK, "BOOKS"), parser.parse("want to read a good book"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.PICK, "MUSIC"), parser.parse("want to listen to some music"))
    }

    @Test
    fun `dreams and cravings still work`() {
        assertTrue(parser.parse("want to buy a car 500000") is QuickAddDraft.Dream)
        assertTrue(parser.parse("dream Bali trip 150000 by March") is QuickAddDraft.Dream)
        assertEquals(QuickAddDraft.Question.CRAVING, (parser.parse("I want to eat something spicy") as QuickAddDraft.Ask).question)
    }

    @Test
    fun `title then to watch or read`() {
        assertEquals(QuickAddDraft.ListItem(ListTemplate.WATCH, "Batman dark night"), parser.parse("batman dark night to watch together"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.BOOKS, "Atomic Habits"), parser.parse("Atomic Habits to read"))
        // Tasks with "to" stay tasks.
        assertTrue(parser.parse("call mom to wish her") is QuickAddDraft.Task)
    }

    @Test
    fun `learning goes on the study list and studying is an activity`() {
        assertEquals(QuickAddDraft.ListItem(ListTemplate.STUDY, "Spanish"), parser.parse("want to learn Spanish"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.STUDY, "Guitar basics"), parser.parse("guitar basics to learn"))
        val a = parser.parse("studied 45 min") as QuickAddDraft.Activity
        assertEquals("Study", a.kind)
        assertEquals(45, a.minutes)
    }

    @Test
    fun `call and message tasks open the dialer or whatsapp and are due today`() {
        val call = parser.parse("call mom") as QuickAddDraft.Task
        assertEquals("Call", call.reach)
        assertEquals(localDate(2026, 11, 2), call.due)
        val msg = parser.parse("message Ravi tomorrow about the keys") as QuickAddDraft.Task
        assertEquals("WhatsApp", msg.reach)
        assertEquals(localDate(2026, 11, 3), msg.due)
        assertEquals("SMS", (parser.parse("text the plumber") as QuickAddDraft.Task).reach)
        assertEquals(null, (parser.parse("pay rent tomorrow") as QuickAddDraft.Task).reach)
    }

    @Test
    fun `whatsapp numbers get a country code`() {
        assertEquals("919876543210", com.houseaffairs.app.core.util.whatsAppNumber("98765 43210"))
        assertEquals("919876543210", com.houseaffairs.app.core.util.whatsAppNumber("098765-43210"))
        assertEquals("447700900123", com.houseaffairs.app.core.util.whatsAppNumber("+44 7700 900123"))
        assertEquals("919876543210", com.houseaffairs.app.core.util.whatsAppNumber("0091 98765 43210"))
    }

    private val pickBooks = QuickAddDraft.Ask(QuickAddDraft.Question.PICK, "BOOKS")

    @Test
    fun `wanting to read goes to books - never the gullak`() {
        assertEquals(pickBooks, parser.parse("want to read a book"))
        assertEquals(pickBooks, parser.parse("I want to read a book."))
        assertEquals(pickBooks, parser.parse("i want to read"))
        assertEquals(pickBooks, parser.parse("I'd like to read a book"))
        assertEquals(pickBooks, parser.parse("I want to start reading a book"))
        assertEquals(pickBooks, parser.parse("want to read more books"))
        assertEquals(pickBooks, parser.parse("I want to finish this book"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.BOOKS, "Sapiens"), parser.parse("reading list Sapiens"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.WATCH, "Dune"), parser.parse("watchlist: Dune"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.WATCH, "Dune"), parser.parse("I'd love to watch Dune"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.PICK, "WATCH"), parser.parse("want to watch"))
    }

    @Test
    fun `plain wishes are tasks - not dreams`() {
        val call = parser.parse("I want to call mom") as QuickAddDraft.Task
        assertEquals("Call mom", call.title)
        assertEquals("Call", call.reach)
        assertEquals("Pay rent", (parser.parse("want to pay rent") as QuickAddDraft.Task).title)
        assertEquals("Start yoga", (parser.parse("want to start yoga") as QuickAddDraft.Task).title)
        assertEquals("Cook biryani", (parser.parse("want to cook biryani") as QuickAddDraft.Task).title)
        assertEquals("Get fit", (parser.parse("want to get fit") as QuickAddDraft.Task).title)
        assertEquals("Visit grandma", (parser.parse("want to visit grandma") as QuickAddDraft.Task).title)
        assertEquals("Try yoga", (parser.parse("want to try yoga") as QuickAddDraft.Task).title)
    }

    @Test
    fun `saving up is still a dream`() {
        val car = parser.parse("want to buy a car 5 lakh") as QuickAddDraft.Dream
        assertEquals("Buy a car", car.title)
        assertAmount("500000", car.amount!!)
        assertTrue(parser.parse("want to save for a house") is QuickAddDraft.Dream)
        assertTrue(parser.parse("want to go on a holiday") is QuickAddDraft.Dream)
    }

    @Test
    fun `wishes land on the right list`() {
        assertEquals(QuickAddDraft.ListItem(ListTemplate.TRAVEL, "Ladakh"), parser.parse("we want to go to Ladakh next year"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.BUY, "A book"), parser.parse("want to buy a book"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.BUY, "A new phone"), parser.parse("want to get a new phone"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.PICK, "EAT_OUT"), parser.parse("want to try a new restaurant"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.PICK, "MUSIC"), parser.parse("want to listen to a podcast"))
        // A wish with no title isn't a book called "I need".
        assertTrue(parser.parse("I need to read") is QuickAddDraft.Task)
    }

    @Test
    fun `household help - insurance and fees have categories`() {
        val cook = expense("cook 5000")
        assertEquals("Home", cook.category)
        assertEquals("Help", cook.subcategory)
        assertEquals("Cash", expense("paid maid 3000 cash").paidWith)
        assertEquals("Insurance", expense("insurance premium 12000").subcategory)
        assertEquals("Education", expense("school fees 25000").subcategory)
        val book = expense("bought a novel 399 on card")
        assertEquals("Shopping", book.category)
        assertEquals("Card", book.paidWith)
        assertTrue(parser.parse("cooked dal for 4") is QuickAddDraft.Cooked)
    }

    @Test
    fun `lakh and crore are amounts`() {
        assertAmount("150000", expense("1.5 lakh for car service").amount)
        assertAmount("20000000", (parser.parse("dream house 2 crore") as QuickAddDraft.Dream).amount!!)
    }

    @Test
    fun `a time said with a task is kept for its reminder`() {
        val call = parser.parse("remind me to call mom at 5:30 pm") as QuickAddDraft.Task
        assertEquals("Call mom", call.title)
        assertEquals("5:30 pm", call.time)
        assertEquals(localDate(2026, 11, 2), call.due)
        val rent = parser.parse("pay rent tomorrow 9am") as QuickAddDraft.Task
        assertEquals("Pay rent", rent.title)
        assertEquals("9:00 am", rent.time)
        assertEquals(localDate(2026, 11, 3), rent.due)
        assertEquals("8:00 pm", (parser.parse("call Ravi tonight") as QuickAddDraft.Task).time)
        assertEquals("6:00 pm", (parser.parse("call the bank at 18:00") as QuickAddDraft.Task).time)
        // Gone already today, so tomorrow.
        val late = QuickAddParser(today = { localDate(2026, 11, 2) }, nowMinute = { 11 * 60 })
        assertEquals(localDate(2026, 11, 3), (late.parse("call mom at 9 am") as QuickAddDraft.Task).due)
        assertNull((parser.parse("call 2 plumbers") as QuickAddDraft.Task).time)
    }
}
