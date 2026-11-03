package com.houseaffairs.app.feature.quickadd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AssistantTest {

    private val context = AssistContext(LocalDate.of(2026, 11, 4), listOf("Asha", "Riya"), "INR")

    @Test
    fun `shopping with a quantity`() {
        assertEquals(
            QuickAddDraft.Shopping("Peanuts", "500 g"),
            draftFromAi("""{"kind":"shopping","title":"peanuts","quantity":500,"unit":"g"}""", context),
        )
    }

    @Test
    fun `event with a date and a known person`() {
        val d = draftFromAi("""{"kind":"event","title":"Riya's PTM","eventKind":"Appointment","date":"2026-11-06","person":"Riya"}""", context)
        assertEquals(QuickAddDraft.Event("Riya's PTM", "Appointment", LocalDate.of(2026, 11, 6)), d)
    }

    @Test
    fun `expense keeps only known categories and needs an amount`() {
        val d = draftFromAi("""{"kind":"expense","title":"Chaat","amount":300,"category":"Street food"}""", context) as QuickAddDraft.Expense
        assertEquals(0, d.amount.compareTo(BigDecimal(300)))
        assertEquals("Other", d.category)
        assertNull(d.currency)
        assertNull(draftFromAi("""{"kind":"expense","title":"Chaat"}""", context))
    }

    @Test
    fun `list items map onto the app's lists`() {
        assertEquals(
            QuickAddDraft.ListItem(ListTemplate.WATCH, "Oppenheimer"),
            draftFromAi("""{"kind":"list_item","title":"Oppenheimer","list":"Watch"}""", context),
        )
    }

    @Test
    fun `unknown people, bad dates and code fences are handled`() {
        val d = draftFromAi("```json\n{\"kind\":\"medicine\",\"title\":\"Crocin\",\"person\":\"Bob\",\"date\":\"March\"}\n```", context)
        assertEquals(QuickAddDraft.Medicine("Crocin", null, null), d)
    }

    @Test
    fun `nonsense or unknown kinds give nothing`() {
        assertNull(draftFromAi("sorry, I can't help", context))
        assertNull(draftFromAi("""{"kind":"unknown","title":"hmm"}""", context))
        assertNull(draftFromAi("""{"kind":"shopping"}""", context))
    }

    @Test
    fun `the prompt carries today's date and the line safely quoted`() {
        val prompt = assistPrompt("need \"special\" atta", context)
        assertTrue("2026-11-04" in prompt)
        assertTrue("\"need \\\"special\\\" atta\"" in prompt)
    }

    @Test
    fun `picks keep only real options, once each, in the model's order`() {
        val options = listOf("Paneer sabzi", "Dal tadka", "Poha")
        val json = """{"picks":[
            {"title":"dal tadka","why":"Light and quick for a weeknight"},
            {"title":"Butter chicken","why":"Not in your kitchen"},
            {"title":"Dal tadka","why":"Again"},
            {"title":"Paneer sabzi","why":"Uses the paneer expiring tomorrow"}]}"""
        assertEquals(
            listOf(Pick("Dal tadka", "Light and quick for a weeknight"), Pick("Paneer sabzi", "Uses the paneer expiring tomorrow")),
            picksFromAi(json, options),
        )
        assertTrue(picksFromAi("not json", options).isEmpty())
    }

    @Test
    fun `coach tip is read and length-checked`() {
        assertEquals("Walk to the vegetable market tomorrow.", coachFromAi("""{"tip":"Walk to the vegetable market tomorrow."}"""))
        assertNull(coachFromAi("""{"tip":""}"""))
        assertNull(coachFromAi("""{"tip":"${"x".repeat(250)}"}"""))
    }
}
