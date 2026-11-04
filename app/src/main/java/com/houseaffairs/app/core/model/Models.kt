package com.houseaffairs.app.core.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

enum class Role { OWNER, ADULT, TEEN, KID, HELPER }

/** Permission modules. [key] is what's stored in Firestore and checked by security rules. */
enum class AppModule(val key: String) {
    MONEY("money"),
    STOCK("stock"),
    MEDICINE("medicine"),
    PLANS("plans"),
    VAULT("vault"),
}

data class Household(
    @DocumentId val id: String = "",
    val name: String = "",
    val baseCurrency: String = "USD",
    val budgetCycleStartDay: Int = 1,
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    /** Private topic → who may see new entries on it (see PrivateTopics.kt). Owner-set. */
    val privateTopics: Map<String, List<String>> = emptyMap(),
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

/**
 * Every household record carries `visibleTo`: ["*"] for the whole family, or a list of uids
 * for restricted/private items. Security rules enforce it on every read.
 */
object Visibility {
    const val EVERYONE = "*"
    val everyone: List<String> = listOf(EVERYONE)
}
