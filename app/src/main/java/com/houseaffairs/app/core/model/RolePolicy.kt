package com.houseaffairs.app.core.model

/** Default module access per role. Keep in sync with `defaultAccess()` in firestore.rules. */
object RolePolicy {

    fun defaultAccess(role: Role): Map<String, Boolean> {
        val allowed = when (role) {
            Role.OWNER, Role.ADULT -> AppModule.entries.toSet()
            Role.TEEN, Role.KID -> setOf(AppModule.STOCK, AppModule.PLANS)
            Role.HELPER -> setOf(AppModule.STOCK)
        }
        return AppModule.entries.associate { it.key to (it in allowed) }
    }

    fun canManageMembers(role: Role): Boolean = role == Role.OWNER

    fun label(role: Role): String = when (role) {
        Role.OWNER -> "Owner"
        Role.ADULT -> "Adult"
        Role.TEEN -> "Teen"
        Role.KID -> "Kid"
        Role.HELPER -> "Helper"
    }

    fun describe(role: Role): String = when (role) {
        Role.OWNER -> "Everything, including managing the family"
        Role.ADULT -> "Everything except managing members"
        Role.TEEN -> "Groceries, shopping list and plans. No money, medicine or documents"
        Role.KID -> "Shopping list and plans. No money, medicine or documents"
        Role.HELPER -> "Groceries and shopping list only"
    }

    val invitableRoles: List<Role> = Role.entries - Role.OWNER
}
