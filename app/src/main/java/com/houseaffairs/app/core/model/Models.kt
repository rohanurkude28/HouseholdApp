package com.houseaffairs.app.core.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Household(
    @DocumentId val id: String = "",
    val name: String = "",
    val baseCurrency: String = "USD",
    val budgetCycleStartDay: Int = 1,
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    /** Private topic → who may see new entries on it (see PrivateTopics.kt). Owner-set. */
    val privateTopics: Map<String, List<String>> = emptyMap(),
    /** Service keys the owner set for the whole family ("omdb", "tmdb"); readable by members. */
    val integrations: Map<String, String> = emptyMap(),
)

data class Member(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: Role = Role.ADULT,
    val moduleAccess: Map<String, Boolean> = emptyMap(),
    val restrictedCategories: List<String> = emptyList(),
    val inviteCode: String? = null,
    val joinedAt: Timestamp? = null,
) {
    fun canAccess(module: AppModule): Boolean = moduleAccess[module.key] == true
}

data class Invite(
    @DocumentId val code: String = "",
    val householdId: String = "",
    val role: Role = Role.ADULT,
    val createdBy: String = "",
    val expiresAt: Timestamp? = null,
)
