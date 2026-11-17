package com.houseaffairs.app.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DreamKindTest {
    @Test
    fun `a dream name says its kind`() {
        assertEquals("Travel", dreamKindOf("Bali trip"))
        assertEquals("Gadget", dreamKindOf("New laptop"))
        assertEquals("Emergency", dreamKindOf("Emergency fund"))
        assertEquals("Festival", dreamKindOf("Diwali 2027"))
        assertNull(dreamKindOf("Something nice"))
    }
}
