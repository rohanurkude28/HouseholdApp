package com.houseaffairs.app.feature.quickadd

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class KindClassifierTest {

    private val classifier = KindClassifier(SEED_EXAMPLES)
    private val parser = QuickAddParser()

    @Test
    fun `phrase key keeps only the words that matter`() {
        assertEquals("peanuts", phraseKey("Need 500g of Peanuts!"))
        assertEquals("peanut butter", phraseKey("get some peanut butter"))
    }

    @Test
    fun `food words read as shopping - appointments as events`() {
        assertEquals(LineKind.SHOPPING, classifier.classify("peanut butter")?.kind)
        assertEquals(LineKind.SHOPPING, classifier.classify("green chillies and ginger")?.kind)
        assertEquals(LineKind.EVENT, classifier.classify("riya dentist appointment")?.kind)
        assertEquals(LineKind.MEDICINE, classifier.classify("cough syrup")?.kind)
    }

    @Test
    fun `unknown words give no guess`() {
        assertNull(classifier.classify("zxqv"))
    }

    @Test
    fun `a family correction outweighs the starter examples`() {
        val taught = KindClassifier(SEED_EXAMPLES + List(3) { "chikki" to LineKind.SHOPPING })
        val guess = taught.classify("chikki")!!
        assertEquals(LineKind.SHOPPING, guess.kind)
        assertTrue(guess.confidence >= KindClassifier.CONFIDENT)
    }

    @Test
    fun `only catch-all tasks are unsure`() {
        assertTrue(parser.isUnsure("peanut butter"))
        assertFalse(parser.isUnsure("call the plumber"))
        assertFalse(parser.isUnsure("pay rent tomorrow"))
        assertFalse(parser.isUnsure("remind me to water plants"))
        assertFalse(parser.isUnsure("milk over"))
    }

    @Test
    fun `a line can be re-read as another kind`() {
        assertEquals(QuickAddDraft.Shopping("Peanut butter"), parser.asKind(LineKind.SHOPPING, "peanut butter"))
        assertEquals(QuickAddDraft.Shopping("Peanuts", "500 g"), parser.asKind(LineKind.SHOPPING, "need 500g peanuts"))
        assertTrue(parser.asKind(LineKind.EVENT, "school annual day") is QuickAddDraft.Event)
    }
}
