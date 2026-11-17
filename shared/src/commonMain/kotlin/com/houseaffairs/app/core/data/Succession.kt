package com.houseaffairs.app.core.data

/**
 * Who looks after the home when the owner deletes their account: the adult who joined first.
 * Null when no other adult is there, since a teen, kid or helper can't manage members.
 */
fun successorOf(members: List<com.houseaffairs.app.core.model.Member>, myUid: String): com.houseaffairs.app.core.model.Member? =
    members.filter { it.uid != myUid && it.role == com.houseaffairs.app.core.model.Role.ADULT }
        .minByOrNull { it.joinedAt ?: Long.MAX_VALUE }
