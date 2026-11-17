package com.houseaffairs.app.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class MovedFromStepsTest {
    @Test
    fun `steps answer did you move`() {
        assertEquals(2, movedFromSteps(8_200))
        assertEquals(1, movedFromSteps(4_000))
        assertEquals(0, movedFromSteps(900))
    }
}
