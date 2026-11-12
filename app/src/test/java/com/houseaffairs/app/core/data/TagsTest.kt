package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {
    @Test
    fun `tags come out of the line and the rest is read as usual`() {
        assertEquals("dinner 1200" to listOf("goa", "friends"), splitTags("dinner 1200 #Goa #friends"))
        assertEquals("book hotel" to listOf("goa"), splitTags("#goa book hotel #GOA"))
        assertEquals("call mom" to emptyList<String>(), splitTags("call mom"))
        // A # inside a word isn't a tag.
        assertEquals("flat C#5" to emptyList<String>(), splitTags("flat C#5"))
    }
}
