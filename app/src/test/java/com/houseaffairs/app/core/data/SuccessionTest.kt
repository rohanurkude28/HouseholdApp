package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who takes over the home when the owner leaves (Android: uses the Firebase-backed Member). */
class SuccessionTest {
    private fun member(uid: String, role: com.houseaffairs.app.core.model.Role, joined: Long) =
        com.houseaffairs.app.core.model.Member(uid = uid, role = role, joinedAt = joined)

    @Test
    fun `the adult who joined first takes over`() {
        val members = listOf(
            member("me", com.houseaffairs.app.core.model.Role.OWNER, 1),
            member("teen", com.houseaffairs.app.core.model.Role.TEEN, 2),
            member("late", com.houseaffairs.app.core.model.Role.ADULT, 9),
            member("early", com.houseaffairs.app.core.model.Role.ADULT, 5),
        )
        assertEquals("early", successorOf(members, "me")?.uid)
    }

    @Test
    fun `no adult means nobody`() {
        val members = listOf(member("me", com.houseaffairs.app.core.model.Role.OWNER, 1), member("kid", com.houseaffairs.app.core.model.Role.KID, 2))
        assertEquals(null, successorOf(members, "me"))
    }
}
