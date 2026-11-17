package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.Visibility
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the family has taught quick add: "peanuts" is shopping, "ptm" is an event. Shared by the
 * whole household, so one person's correction helps everyone. Keyed by the line's main words.
 */
class PhraseRepository {
    private fun phrases(householdId: String) = collection(householdId, "phrases")

    /** Phrase key → kind name. */
    fun observe(householdId: String): Flow<Map<String, String>> =
        phrases(householdId).docs().map { docs -> docs.mapNotNull { d -> d.str("kind")?.let { d.id to it } }.toMap() }

    fun remember(householdId: String, uid: String, key: String, kind: String) {
        if (key.isBlank() || key.length > 100) return
        Writes.later {
            phrases(householdId).document(key).set(
                mapOf("kind" to kind, "createdBy" to uid, "visibleTo" to Visibility.everyone, "updatedAt" to FieldValue.serverTimestamp),
            )
        }
    }
}
