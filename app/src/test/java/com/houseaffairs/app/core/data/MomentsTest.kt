package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsTest {
    private fun thing(id: String) = MomentCard(id, CardKind.THING, "Today", id, "")
    private val inputs = MomentInputs(
        things = listOf("a", "b", "c", "d", "e").map(::thing),
        meal = Idea("Poha", "You have everything"),
        toBuy = listOf("Milk", "Eggs", "Bread", "Rice"),
        watch = Idea("Dark", "Netflix"),
        tomorrow = listOf("Pay rent"),
    )

    private fun kinds(moment: Moment, handled: Set<String> = emptySet(), with: MomentInputs = inputs) =
        momentCards(moment, with, handled).map { if (it.kind == CardKind.THING) it.id else it.kind.name }

    @Test
    fun `the hour picks the moment`() {
        assertEquals(Moment.NIGHT, Moment.at(2))
        assertEquals(Moment.MORNING, Moment.at(7))
        assertEquals(Moment.MIDDAY, Moment.at(13))
        assertEquals(Moment.EVENING, Moment.at(18))
        assertEquals(Moment.NIGHT, Moment.at(22))
    }

    @Test
    fun `morning leads with what needs doing then breakfast`() {
        assertEquals(listOf("a", "b", "MEAL", "SHOPPING", "c"), kinds(Moment.MORNING))
        assertEquals("Breakfast idea", momentCards(Moment.MORNING, inputs, emptySet())[2].eyebrow)
    }

    @Test
    fun `afternoon leads with lunch and errands`() {
        assertEquals(listOf("MEAL", "SHOPPING", "a", "b", "c"), kinds(Moment.MIDDAY))
        assertEquals("Milk, Eggs, Bread and 1 more", momentCards(Moment.MIDDAY, inputs, emptySet())[1].detail)
    }

    @Test
    fun `evening leads with dinner and ends with the spend question`() {
        assertEquals(listOf("MEAL", "a", "b", "c", "SHOPPING", "SPENDS"), kinds(Moment.EVENING))
        assertEquals(listOf("MEAL", "a", "b", "c", "SHOPPING"), kinds(Moment.EVENING, with = inputs.copy(spentToday = true)))
    }

    @Test
    fun `night looks back and winds down`() {
        assertEquals(listOf("SPENDS", "a", "WATCH", "TOMORROW"), kinds(Moment.NIGHT))
    }

    @Test
    fun `answered cards leave and things never refill past three`() {
        // "a" was done and "b" put off: one more thing today, not three.
        val handled = setOf("a", "b", "moment:MORNING:MEAL")
        assertEquals(listOf("c", "SHOPPING"), kinds(Moment.MORNING, handled))
        assertEquals(listOf("SHOPPING"), kinds(Moment.MORNING, handled + "c"))
    }

    @Test
    fun `a meal turned down in the morning is offered again at lunch`() {
        assertTrue("MEAL" in kinds(Moment.MIDDAY, setOf("moment:MORNING:MEAL")))
    }

    @Test
    fun `the spend question is asked once a day`() {
        assertTrue("SPENDS" !in kinds(Moment.NIGHT, setOf(SPEND_CARD)))
        assertTrue("SPENDS" !in kinds(Moment.EVENING, setOf(SPEND_CARD)))
    }

    @Test
    fun `the hero line counts the deck`() {
        assertEquals("5 things for you this morning", momentLine(Moment.MORNING, momentCards(Moment.MORNING, inputs, emptySet())))
        assertEquals("1 thing for you tonight", momentLine(Moment.NIGHT, listOf(thing("a"))))
        assertEquals("All sorted for evening 🎉", momentLine(Moment.EVENING, emptyList()))
        assertEquals("All sorted for tonight 🎉", momentLine(Moment.NIGHT, emptyList()))
    }
}
