package com.houseaffairs.app.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class RolePolicyTest {

    @Test
    fun `every role defines every module`() {
        Role.entries.forEach { role ->
            assertEquals(AppModule.entries.map { it.key }.toSet(), RolePolicy.defaultAccess(role).keys)
        }
    }

    @Test
    fun `adults see everything`() {
        assertTrue(RolePolicy.defaultAccess(Role.ADULT).values.all { it })
    }

    @Test
    fun `kids never see money - medicine or documents`() {
        val access = RolePolicy.defaultAccess(Role.KID)
        assertFalse(access.getValue("money"))
        assertFalse(access.getValue("medicine"))
        assertFalse(access.getValue("vault"))
        assertTrue(access.getValue("stock"))
    }

    @Test
    fun `owner cannot be invited`() {
        assertFalse(Role.OWNER in RolePolicy.invitableRoles)
    }
}
