package com.houseaffairs.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the family has taught quick add: "peanuts" is shopping, "ptm" is an event. Shared by the
 * whole household, so one person's correction helps everyone. Keyed by the line's main words.
 */
@Singleton
class PhraseRepository @Inject constructor(private val db: FirebaseFirestore) {

    private fun phrases(householdId: String) = db.collection("households").document(householdId).collection("phrases")

    /** Phrase key → kind name. */
    fun observe(householdId: String): Flow<Map<String, String>> =
        phrases(householdId).observeSnapshots()
            .map { snap -> snap.documents.mapNotNull { d -> d.getString("kind")?.let { d.id to it } }.toMap() }
            .catch { emit(emptyMap()) }

    fun remember(householdId: String, uid: String, key: String, kind: String) {
        if (key.isBlank() || key.length > 100) return
        phrases(householdId).document(key).set(
            mapOf(
                "kind" to kind,
                "createdBy" to uid,
                "visibleTo" to Visibility.everyone,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }
}
