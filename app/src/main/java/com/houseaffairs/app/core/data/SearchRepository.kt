package com.houseaffairs.app.core.data

import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Firestore's timestamps as plain days, so search can say "due tomorrow". */
private fun asDay(value: Any?): Any? =
    (value as? com.google.firebase.Timestamp)?.toDate()?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() ?: value

/** Reads everything a person may see, once, so search can look across all of it. */
@Singleton
class SearchRepository @Inject constructor(private val db: FirebaseFirestore) {
    /**
     * [collections] are the ones this person's role can open; a collection the rules refuse just
     * comes back empty. [ownMedicines] keeps teens to medicines that are for them.
     */
    suspend fun load(householdId: String, uid: String, collections: Collection<String>, ownMedicines: Boolean): List<Findable> = coroutineScope {
        // First names by uid, so "Riya" finds what's for her.
        val names = runCatching {
            db.collection("households").document(householdId).collection("members").get().await().documents
                .associate { it.id to (it.getString("displayName") ?: "").substringBefore(' ') }.filterValues { it.isNotBlank() }
        }.getOrDefault(emptyMap())
        collections.distinct().map { name ->
            async {
                runCatching {
                    val col = db.collection("households").document(householdId).collection(name)
                    val query = if (name == "medicines" && ownMedicines) col.whereArrayContains("forWhom", uid)
                    else col.whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid))
                    findablesOf(name, query.get().await().documents.map { d -> d.data.orEmpty().mapValues { (_, v) -> asDay(v) } + ("id" to d.id) }, names)
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }
}
