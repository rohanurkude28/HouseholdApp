package com.houseaffairs.app.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandoffTest {
    private fun task(assignee: String?, by: String?, ask: String?, declinedBy: String? = null) =
        TaskItem("t", "Fix the tap", null, assignee, assignedBy = by, ask = ask, declinedBy = declinedBy)

    @Test
    fun askedAndWaitingAreTwoSidesOfOneRequest() {
        val t = task("ravi", "asha", "PENDING")
        assertEquals(Handoff.ASKED_ME, handoffOf(t, "ravi"))
        assertEquals(Handoff.WAITING, handoffOf(t, "asha"))
        assertEquals(Handoff.NONE, handoffOf(t, "mia"))
    }

    @Test
    fun aYesShowsToTheGiverOnly() {
        val t = task("ravi", "asha", "ACCEPTED")
        assertEquals(Handoff.ACCEPTED, handoffOf(t, "asha"))
        assertEquals(Handoff.NONE, handoffOf(t, "ravi"))
    }

    @Test
    fun aCantGoesBackToTheGiver() {
        val t = task("asha", "asha", "DECLINED", declinedBy = "ravi")
        assertEquals(Handoff.CAME_BACK, handoffOf(t, "asha"))
        assertEquals(Handoff.NONE, handoffOf(t, "ravi"))
        assertEquals(listOf(t), waitingOnMe(listOf(t, task("asha", "asha", null)), "asha"))
    }

    @Test
    fun onlySomeoneElseOutsideARotaIsAsked() {
        assertTrue(needsAnswer("ravi", "asha", rota = false))
        assertFalse(needsAnswer("asha", "asha", rota = false))
        assertFalse(needsAnswer(null, "asha", rota = false))
        assertFalse(needsAnswer("ravi", "asha", rota = true))
    }
}

class HandoffLineTest {
    private val names = mapOf("asha" to "Asha", "ravi" to "Ravi")

    @Test
    fun eachSideReadsItsOwnLine() {
        val asked = TaskItem("t", "Fix the tap", null, "ravi", assignedBy = "asha", ask = "PENDING")
        assertEquals("Asha asked you", handoffLine(asked, "ravi") { names.getValue(it) })
        assertEquals("Waiting for Ravi to say yes", handoffLine(asked, "asha") { names.getValue(it) })
        val back = TaskItem("t", "Fix the tap", null, "asha", assignedBy = "asha", ask = "DECLINED", declinedBy = "ravi", declineNote = "away Saturday")
        assertEquals("Ravi can't do it: away Saturday", handoffLine(back, "asha") { names.getValue(it) })
    }
}
