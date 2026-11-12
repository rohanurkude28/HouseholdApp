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

    @Test
    fun `any category the owner picks is private`() {
        assertEquals("Snacks & drinks", privateTopicOf("stock", "Beer", "snacks & drinks", listOf("Snacks & drinks")))
        assertEquals("Baby care", privateTopicOf("stock", "Diapers", "Baby care", listOf("Baby care")))
        assertNull(privateTopicOf("stock", "Diapers", "Baby care", emptyList()))
        // Words still win, so pads are period care whatever aisle they're in.
        assertEquals("Period care", privateTopicOf("stock", "Stayfree pads", "Toiletries", listOf("Toiletries")))
    }

    @Test
    fun `choices are the topics, then what the family uses, then the usual ones`() {
        val c = privateChoices(listOf("Baby care", "Toiletries"), listOf("Toiletries", "Other", "Cleaning"))
        assertEquals(listOf("Period care", "Sexual wellness", "Health spending", "Cosmetics & skincare", "Baby care", "Toiletries", "Cleaning"), c)
    }

    @Test
    fun `hiding from some keeps everyone else and the owner`() {
        assertEquals(listOf("asha", "ravi"), seenByAllBut(listOf("asha", "ravi", "mia"), listOf("mia"), "asha"))
        assertEquals(listOf("ravi", "asha"), seenByAllBut(listOf("asha", "ravi"), listOf("asha"), "asha"))
    }
}
