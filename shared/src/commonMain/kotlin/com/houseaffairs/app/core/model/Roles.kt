package com.houseaffairs.app.core.model

enum class Role { OWNER, ADULT, TEEN, KID, HELPER }

/** Permission modules. [key] is what's stored in Firestore and checked by security rules. */
enum class AppModule(val key: String) {
    MONEY("money"),
    STOCK("stock"),
    MEDICINE("medicine"),
    PLANS("plans"),
    VAULT("vault"),
}

/**
 * Every household record carries `visibleTo`: ["*"] for the whole family, or a list of uids
 * for restricted/private items. Security rules enforce it on every read.
 */
object Visibility {
    const val EVERYONE = "*"
    val everyone: List<String> = listOf(EVERYONE)
}
