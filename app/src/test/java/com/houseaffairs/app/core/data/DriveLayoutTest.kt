package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class DriveLayoutTest {
    @Test
    fun `documents are filed by type under HouseAffairs`() {
        assertEquals(listOf("HouseAffairs", "Documents", "Insurance"), documentFolders("Insurance"))
        assertEquals(listOf("HouseAffairs", "Documents", "Other"), documentFolders(null))
        assertEquals(listOf("HouseAffairs", "Documents", "Car-bike"), documentFolders("Car/bike"))
        assertEquals("Google Drive · HouseAffairs / Documents / Medical", driveLocation("Medical"))
    }

    @Test
    fun `backup names sort by date and read back nicely`() {
        val name = backupFileName(LocalDateTime.of(2026, 11, 5, 9, 30))
        assertEquals("houseaffairs-backup-2026-11-05-0930.json", name)
        assertEquals("5 Nov 2026, 9:30 am", backupLabel(name))
        assertNull(backupLabel("passport.pdf"))
    }

    private class Time(val millis: Long)

    @Test
    fun `values JSON can't hold survive a round trip`() {
        val doc = mapOf("title" to "Rent", "amountMinor" to 1500L, "due" to Time(42), "tags" to listOf("a", Time(7)), "q" to mapOf("unit" to "g"))
        val encoded = BackupCodec.encode(doc) { (it as? Time)?.let { t -> mapOf("__time" to t.millis) } } as Map<*, *>
        assertEquals(mapOf("__time" to 42L), encoded["due"])
        val decoded = BackupCodec.decode(encoded) { m -> Time((m["__time"] as Number).toLong()) } as Map<*, *>
        assertEquals(42L, (decoded["due"] as Time).millis)
        assertEquals(7L, ((decoded["tags"] as List<*>)[1] as Time).millis)
        assertEquals(mapOf("unit" to "g"), decoded["q"])
    }

    @Test
    fun `a restored entry that's gone is recreated as yours`() {
        val data = mapOf("title" to "Milk", "createdBy" to "ravi")
        assertEquals(data, restoredDoc(data, existing = true, me = "asha"))
        val back = restoredDoc(data, existing = false, me = "asha")
        assertEquals("asha", back["createdBy"])
        assertEquals("ravi", back["restoredFrom"])
    }

    @Test
    fun `photo ids come from an entry's photos list only`() {
        assertEquals(listOf("a1", "b2"), photoIdsIn(mapOf("photos" to listOf("a1", 3, "b2"))))
        assertEquals(emptyList<String>(), photoIdsIn(mapOf("title" to "Keys")))
    }
}
