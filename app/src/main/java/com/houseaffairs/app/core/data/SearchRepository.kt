package com.houseaffairs.app.core.data

import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Reads everything a person may see, once, so search can look across all of it. */
@Singleton
class SearchRepository @Inject constructor(private val db: FirebaseFirestore) {
    /**
     * [collections] are the ones this person's role can open; a collection the rules refuse just
     * comes back empty. [ownMedicines] keeps teens to medicines that are for them.
     */
    suspend fun load(householdId: String, uid: String, collections: Collection<String>, ownMedicines: Boolean): List<Findable> = coroutineScope {
        collections.distinct().map { name ->
            async {
                runCatching {
                    val col = db.collection("households").document(householdId).collection(name)
                    val query = if (name == "medicines" && ownMedicines) col.whereArrayContains("forWhom", uid)
                    else col.whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid))
                    findablesOf(name, query.get().await().documents.map { d -> d.data.orEmpty() + ("id" to d.id) })
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }
}
