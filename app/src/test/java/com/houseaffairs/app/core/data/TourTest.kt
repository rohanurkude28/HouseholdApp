package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourTest {
    @Test
    fun `everyone sees welcome - reminders and the end`() {
        val ids = tourSteps { false }.map { it.id }
        assertEquals(listOf("welcome", "setup", "quickadd", "done"), ids)
    }

    @Test
    fun `steps follow what the role can open`() {
        val kid = tourSteps { it == AppModule.PLANS }.map { it.id }
        assertTrue("plans" in kid)
        assertFalse("budget" in kid)
        assertFalse("medicines" in kid)
        assertEquals(11, tourSteps { true }.size)
    }

    @Test
    fun `a tour left part-way resumes inside it`() {
        assertEquals(0, tourResumeAt(null, 5))
        assertEquals(3, tourResumeAt(3, 5))
        assertEquals(4, tourResumeAt(9, 5))
    }
}
