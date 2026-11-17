package com.houseaffairs.ios

import com.houseaffairs.app.core.data.HouseholdRepository
import com.houseaffairs.app.core.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Making or joining a family home on iPhone: the same shared repository Android uses. */
object Family {
    private val repo = HouseholdRepository()

    suspend fun create(name: String, currency: String) { repo.createHousehold(name, currency) }

    /** Joins the home a 6-letter invite code belongs to; the message says what went wrong. */
    suspend fun join(rawCode: String) {
        require(rawCode.trim().length == 6) { "The code has 6 letters and numbers. Check it and try again." }
        repo.joinWithCode(rawCode)
    }

    suspend fun invite(hid: String, role: Role): String = repo.createInvite(hid, role)

    /** Everyone in the home, for names on tasks and medicines. */
    fun membersOf(hid: String): Flow<List<MemberInfo>> =
        repo.observeMembers(hid).map { list -> list.map { MemberInfo(it.uid, it.displayName, it.role, it.moduleAccess) } }
}
