package com.houseaffairs.app.core.model

/** Default module access per role. Keep in sync with `defaultAccess()` in firestore.rules. */
object RolePolicy {

    fun defaultAccess(role: Role): Map<String, Boolean> {
        val allowed = when (role) {
            Role.OWNER, Role.ADULT -> AppModule.entries.toSet()
            // Teens see medicines, but only their own (enforced in firestore.rules).
            Role.TEEN -> setOf(AppModule.STOCK, AppModule.PLANS, AppModule.MEDICINE)
            Role.KID -> setOf(AppModule.STOCK, AppModule.PLANS)
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
        Role.TEEN -> "Groceries, shopping list, plans and their own medicines. No money or documents"
        Role.KID -> "Shopping list and plans. No money, medicine or documents"
        Role.HELPER -> "Groceries and shopping list only"
    }

    val invitableRoles: List<Role> = Role.entries - Role.OWNER

    /** Teens only see medicines that are for them, so their medicine queries filter on `forWhom`. */
    fun ownMedicinesOnly(role: Role): Boolean = role == Role.TEEN
}
