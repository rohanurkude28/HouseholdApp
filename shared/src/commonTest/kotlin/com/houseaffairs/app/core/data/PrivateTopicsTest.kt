package com.houseaffairs.app.core.data

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class PrivateTopicsTest {

    @Test
    fun `topics are recognised by name or category`() {
        assertEquals("Period care", privateTopicOf("shopping", "Whisper sanitary pads", null))
        assertEquals("Sexual wellness", privateTopicOf("stock", "Durex", "Toiletries"))
        assertEquals("Health spending", privateTopicOf("transactions", "Dr Rao", "Health"))
        assertEquals("Cosmetics & skincare", privateTopicOf("stock", "Night serum", "Cosmetics & skincare"))
        assertNull(privateTopicOf("transactions", "Pharmacy", "Groceries"))
        assertNull(privateTopicOf("shopping", "Paddy rice", null))
    }

    @Test
    fun `the person adding it can always see it`() {
        assertEquals(listOf("asha", "ravi"), privateVisibleTo(listOf("asha", "ravi"), "ravi"))
        assertEquals(listOf("asha", "mia"), privateVisibleTo(listOf("asha"), "mia"))
    }
}
