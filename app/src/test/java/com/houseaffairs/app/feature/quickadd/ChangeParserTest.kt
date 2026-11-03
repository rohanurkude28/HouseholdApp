package com.houseaffairs.app.feature.quickadd

import com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ChangeParserTest {
    private val parser = QuickAddParser()

    @Test
    fun `several shopping items in one line`() {
        assertEquals(
            QuickAddDraft.Batch(listOf(QuickAddDraft.Shopping("Milk"), QuickAddDraft.Shopping("Eggs"), QuickAddDraft.Shopping("Bread"))),
            parser.parse("add milk, eggs and bread to shopping list"),
        )
    }

    @Test
    fun `media titles only split on commas`() {
        assertEquals(QuickAddDraft.ListItem(ListTemplate.WATCH, "Tom and Jerry"), parser.parse("add Tom and Jerry to watch list"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.BOOKS, "Atomic Habits"), parser.parse("add Atomic Habits to reading list"))
        assertEquals(QuickAddDraft.ListItem(ListTemplate.MUSIC, "Kesariya"), parser.parse("add Kesariya to playlist"))
    }

    @Test
    fun `two expenses at once`() {
        val batch = parser.parse("spent 200 on uber and 500 on groceries") as QuickAddDraft.Batch
        assertEquals(2, batch.drafts.size)
        val (a, b) = batch.drafts.map { it as QuickAddDraft.Expense }
        assertEquals(BigDecimal("200"), a.amount)
        assertEquals("Transport", a.category)
        assertEquals(BigDecimal("500"), b.amount)
        assertEquals("Groceries", b.category)
    }

    @Test
    fun `a task with and stays one task`() {
        assertTrue(parser.parse("call Riya and Aman tomorrow") is QuickAddDraft.Task)
    }

    @Test
    fun `delete by name and kind`() {
        assertEquals(QuickAddDraft.Change(ChangeAction.DELETE, "Uber", "expense"), parser.parse("delete the Uber expense"))
        assertEquals(QuickAddDraft.Change(ChangeAction.DELETE, "milk", "shopping"), parser.parse("remove milk from the shopping list"))
        assertEquals(QuickAddDraft.Change(ChangeAction.DELETE, "", "expense"), parser.parse("delete the last expense"))
    }

    @Test
    fun `mark done`() {
        assertEquals(QuickAddDraft.Change(ChangeAction.DONE, "milk", "shopping"), parser.parse("mark milk bought"))
        assertEquals(QuickAddDraft.Change(ChangeAction.DONE, "pay rent", "task"), parser.parse("mark pay rent task as done"))
        assertEquals(QuickAddDraft.Change(ChangeAction.DONE, "homework", null), parser.parse("homework done"))
    }

    @Test
    fun `edit with a new value`() {
        assertEquals(QuickAddDraft.Change(ChangeAction.EDIT, "rent", "task", "tomorrow"), parser.parse("change rent task to tomorrow"))
        assertEquals(QuickAddDraft.Change(ChangeAction.EDIT, "uber", "expense", "250"), parser.parse("update uber expense to 250"))
    }

    @Test
    fun `link an expense to a trip`() {
        assertEquals(QuickAddDraft.Change(ChangeAction.LINK, "", "expense", "Goa trip"), parser.parse("add this expense to Goa trip"))
        assertEquals(QuickAddDraft.Change(ChangeAction.LINK, "dinner", "expense", "Lonavala outing"), parser.parse("put the dinner expense in Lonavala outing"))
    }

    @Test
    fun `helpers read dates and amounts`() {
        assertEquals(BigDecimal("250"), parser.amountIn("250"))
        assertTrue(parser.dateIn("tomorrow") != null)
    }

    @Test
    fun `ending a trip`() {
        assertEquals(QuickAddDraft.Change(ChangeAction.DONE, "sunday", "trip"), parser.parse("mark sunday outing done"))
        assertEquals(QuickAddDraft.Change(ChangeAction.DONE, "Goa", "trip"), parser.parse("end the Goa trip"))
        assertEquals(QuickAddDraft.Change(ChangeAction.DONE, "Sunday", "trip"), parser.parse("Sunday outing is over"))
    }

    @Test
    fun `everyday ways of asking to delete`() {
        assertEquals(QuickAddDraft.Change(ChangeAction.DELETE, "Netflix", "subscription"), parser.parse("can you remove the Netflix subscription"))
        assertEquals(QuickAddDraft.Change(ChangeAction.DELETE, "Netflix rate", null), parser.parse("Netflix rate can you remove it"))
        assertEquals(QuickAddDraft.Change(ChangeAction.DELETE, "Uber", "expense"), parser.parse("please delete the Uber expense"))
        assertEquals(QuickAddDraft.Change(ChangeAction.DELETE, "milk", null), parser.parse("get rid of milk"))
    }

    private fun del(target: String, kind: String?) = QuickAddDraft.Change(ChangeAction.DELETE, target, kind)
    private fun edit(target: String, kind: String?, to: String) = QuickAddDraft.Change(ChangeAction.EDIT, target, kind, to)

    @Test
    fun `more ways to delete`() {
        assertEquals(del("uber", "expense"), parser.parse("trash the uber expense"))
        assertEquals(del("milk", "shopping"), parser.parse("drop milk from shopping list"))
        assertEquals(del("dinner", "expense"), parser.parse("scratch the dinner expense"))
        assertEquals(del("uber", "expense"), parser.parse("wipe out the uber expense"))
        assertEquals(del("uber", "expense"), parser.parse("uber expense delete karo"))
        assertEquals(del("milk", null), parser.parse("milk ko hata do"))
        assertEquals(del("dentist", "event"), parser.parse("dentist appointment cancel kar do"))
    }

    @Test
    fun `everyday verbs that are really tasks stay tasks`() {
        assertTrue(parser.parse("drop the kids at school") is QuickAddDraft.Task)
        assertTrue(parser.parse("fix the tap tomorrow") is QuickAddDraft.Task)
        assertTrue(parser.parse("make dal tomorrow") !is QuickAddDraft.Change)
    }

    @Test
    fun `more ways to change an amount`() {
        assertEquals(edit("uber", "expense", "300"), parser.parse("fix the uber expense to 300"))
        assertEquals(edit("uber", null, "300"), parser.parse("correct the uber amount to 300"))
        assertEquals(edit("uber", "expense", "300"), parser.parse("make the uber expense 300"))
        assertEquals(edit("uber", "expense", "250"), parser.parse("can you update the uber expense to 250"))
    }

    private fun expense(text: String) = parser.parse(text) as QuickAddDraft.Expense

    @Test
    fun `more ways to add an expense, with clean names`() {
        listOf(
            "can you add 500 uber expense", "add 500 for uber", "log 500 uber", "record an expense of 500 for uber",
            "I spent 500 on uber", "paid 500 for uber", "uber 500", "500 rupees uber", "put 500 uber", "note 500 uber",
            "please add an expense of 500 rupees for uber",
        ).forEach { phrase ->
            val e = expense(phrase)
            assertEquals(phrase, BigDecimal("500"), e.amount)
            assertEquals(phrase, "Transport", e.category)
            assertEquals(phrase, "uber", e.merchant?.lowercase())
        }
        assertEquals("Shopping", expense("shelled out 2000 for shoes").category)
        // Saving toward a dream still works.
        assertTrue(parser.parse("add 500 to Bali trip") is QuickAddDraft.Fund)
    }

    @Test
    fun `new goal gullaks by voice`() {
        assertEquals(QuickAddDraft.Dream("Diwali", "Festival", BigDecimal("20000"), null), parser.parse("new gullak for Diwali 20000"))
        assertEquals("Emergency", (parser.parse("start a gullak for emergency fund") as QuickAddDraft.Dream).kind)
        assertTrue(parser.parse("save 5000 for Bali trip") is QuickAddDraft.Fund)
    }

    @Test
    fun `how it was paid`() {
        val card = parser.parse("spent 2000 on shoes by card") as QuickAddDraft.Expense
        assertEquals("Card", card.paidWith)
        assertEquals("Shopping", card.category)
        val hdfc = parser.parse("paid 1200 at Toit on HDFC credit card") as QuickAddDraft.Expense
        assertEquals("Card", hdfc.paidWith)
        assertEquals("HDFC", hdfc.card)
        assertEquals("Toit", hdfc.merchant)
        assertEquals("UPI", (parser.parse("450 groceries DMart gpay") as QuickAddDraft.Expense).paidWith)
        assertEquals("Cash", (parser.parse("auto 80 cash") as QuickAddDraft.Expense).paidWith)
    }

    @Test
    fun `paying the card bill is not new spending`() {
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.CARD_BILL, "paid"), parser.parse("paid the credit card bill 25000"))
        assertEquals(QuickAddDraft.Ask(QuickAddDraft.Question.CARD_BILL, null), parser.parse("how much is my card bill?"))
    }
}
