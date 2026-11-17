package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.Visibility
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Reads everything a person may see, once, so search can look across all of it. */
class SearchRepository {
    /**
     * [collections] are the ones this person's role can open; a collection the rules refuse just
     * comes back empty. [ownMedicines] keeps teens to medicines that are for them.
     */
    suspend fun load(householdId: String, uid: String, collections: Collection<String>, ownMedicines: Boolean): List<Findable> = coroutineScope {
        // First names by uid, so "Riya" finds what's for her.
        val names = runCatching {
            collection(householdId, "members").get().documents
                .associate { it.id to (it.str("displayName") ?: "").substringBefore(' ') }.filterValues { it.isNotBlank() }
        }.getOrDefault(emptyMap())
        collections.distinct().map { name ->
            async {
                runCatching {
                    val col = collection(householdId, name)
                    val query = if (name == "medicines" && ownMedicines) col.where { "forWhom" contains uid }
                    else col.where { "visibleTo" containsAny listOf(Visibility.EVERYONE, uid) }
                    // Dates come back as LocalDate, so search can say "due tomorrow".
                    findablesOf(name, query.get().documents.map { d -> d.plainData() + ("id" to d.id) }, names)
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }
}
