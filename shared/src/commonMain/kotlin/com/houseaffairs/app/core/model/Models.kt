package com.houseaffairs.app.core.model


data class Household(
    val id: String = "",
    val name: String = "",
    val baseCurrency: String = "USD",
    val budgetCycleStartDay: Int = 1,
    val createdBy: String = "",
    /** When it was made, epoch milliseconds. */
    val createdAt: Long? = null,
    /** Private topic → who may see new entries on it (see PrivateTopics.kt). Owner-set. */
    val privateTopics: Map<String, List<String>> = emptyMap(),
    /** Who doesn't see the family's costs: trips, dreams and lists still show, their amounts don't. Owner-set. */
    val hideCostsFrom: List<String> = emptyList(),
    /** Service keys the owner set for the whole family ("omdb", "tmdb"); readable by members. */
    val integrations: Map<String, String> = emptyMap(),
)

data class Member(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: Role = Role.ADULT,
    val moduleAccess: Map<String, Boolean> = emptyMap(),
    val restrictedCategories: List<String> = emptyList(),
    val inviteCode: String? = null,
    /** When they joined, epoch milliseconds. */
    val joinedAt: Long? = null,
) {
    fun canAccess(module: AppModule): Boolean = moduleAccess[module.key] == true
}

data class Invite(
    val code: String = "",
    val householdId: String = "",
    val role: Role = Role.ADULT,
    val createdBy: String = "",
    /** Epoch milliseconds. */
    val expiresAt: Long? = null,
)
