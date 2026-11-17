package com.houseaffairs.app.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DocTypeGuessTest {
    @Test
    fun `a file name picks its folder by whole words`() {
        assertEquals("IDs & passports", guessDocType("Riya_passport.pdf"))
        assertEquals("Insurance", guessDocType("car insurance policy 2026.pdf"))
        // "company" isn't a PAN card.
        assertEquals("Other", guessDocType("company letter.pdf"))
    }

    @Test
    fun `backup names read back as a date and time`() {
        val name = backupFileName(com.houseaffairs.app.core.time.localDate(2026, 11, 5), 9, 30)
        assertEquals("houseaffairs-backup-2026-11-05-0930.json", name)
        assertEquals("5 Nov 2026, 9:30 am", backupLabel(name))
        assertEquals(null, backupLabel("notes.json"))
    }
}
