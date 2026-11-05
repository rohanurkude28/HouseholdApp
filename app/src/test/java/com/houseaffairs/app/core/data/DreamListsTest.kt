package com.houseaffairs.app.core.data

import com.houseaffairs.app.feature.quickadd.ListTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class DreamListsTest {
    private fun dream(title: String, kind: String, achieved: Boolean = false) = Dream(title, title, kind, "⭐", 1_000_00, null, achieved)

    @Test
    fun `dreams show in the list they belong to - achieved and savings goals don't`() {
        val lists = dreamsByList(
            listOf(dream("Bali trip", "Travel"), dream("New car", "Car"), dream("Laptop", "Gadget"), dream("Rainy day", "Emergency"), dream("Goa", "Travel", achieved = true)),
        )
        assertEquals(listOf("Bali trip"), lists[ListTemplate.TRAVEL]?.map { it.title })
        assertEquals(listOf("New car", "Laptop"), lists[ListTemplate.BUY]?.map { it.title })
        assertEquals(setOf(ListTemplate.TRAVEL, ListTemplate.BUY), lists.keys)
    }
}
