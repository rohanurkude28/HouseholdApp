package com.houseaffairs.app.core.data

import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class KeptItem(
    val id: String,
    val title: String,
    val location: String,
    val kind: String?,
    val room: String?,
    val note: String?,
    /** For documents filed in Google Drive: the folder ("Insurance") and the link that opens the file. */
    val docType: String? = null,
    val link: String? = null,
    val photos: List<String> = emptyList(),
)

data class PlaceItem(
    val id: String,
    val title: String,
    val kind: String,
    val area: String?,
    val rating: String?,
    val goodFor: String?,
    val phone: String?,
    val mapLink: String?,
    val lat: Double? = null,
    val lng: Double? = null,
)

data class FinishedItem(val id: String, val collection: String, val title: String, val template: String?, val at: java.util.Date?)

/** "Where is it kept" and important places. */
@Singleton
class ThingsRepository @Inject constructor(
    private val db: FirebaseFirestore,
) {
    private fun visible(householdId: String, name: String, uid: String) =
        db.collection("households").document(householdId).collection(name)
            .whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid))

    fun observeKept(householdId: String, uid: String): Flow<List<KeptItem>> =
        visible(householdId, "keptAt", uid).observeSnapshots()
            .map { snap ->
                snap.documents.map { d ->
                    KeptItem(
                        d.id, d.getString("title").orEmpty(), d.getString("location").orEmpty(), d.getString("kind"), d.getString("room"), d.getString("note"),
                        docType = d.getString("docType"), link = d.getString("driveLink"),
                        photos = (d.get("photos") as? List<*>).orEmpty().filterIsInstance<String>(),
                    )
                }.sortedBy { it.title.lowercase() }
            }
            .catch { emit(emptyList()) }

    fun observePlaces(householdId: String, uid: String): Flow<List<PlaceItem>> =
        visible(householdId, "places", uid).observeSnapshots()
            .map { snap ->
                snap.documents.map { d ->
                    PlaceItem(
                        d.id, d.getString("title").orEmpty(), d.getString("kind") ?: "Other", d.getString("area"),
                        d.getString("rating"), d.getString("goodFor")?.ifBlank { null }, d.getString("phone"), d.getString("mapLink"),
                        lat = d.getDouble("lat") ?: latLngFromLink(d.getString("mapLink"))?.lat,
                        lng = d.getDouble("lng") ?: latLngFromLink(d.getString("mapLink"))?.lng,
                    )
                }.sortedBy { it.title.lowercase() }
            }
            .catch { emit(emptyList()) }

    /** Travel, eat-out, buy and gift wishes (books, music and shows live in [WatchRepository]). */
    fun observeWishes(householdId: String, uid: String): Flow<List<WishItem>> =
        visible(householdId, "listItems", uid).observeSnapshots()
            .map { snap ->
                snap.documents.filter { it.getString("status") != "DONE" }.mapNotNull { d ->
                    val template = d.getString("template") ?: return@mapNotNull null
                    if (com.houseaffairs.app.feature.quickadd.ListTemplate.of(template)?.isMedia == true) return@mapNotNull null
                    WishItem(
                        d.id, template, d.getString("title").orEmpty(), d.getString("place"),
                        d.getLong("budgetMinor")?.let { m -> d.getString("budgetCurrency")?.let { com.houseaffairs.app.core.util.formatMinor(m, it) } },
                        status = d.getString("status") ?: "IDEA",
                        votes = (d.get("votes") as? List<*>).orEmpty().filterIsInstance<String>(),
                        event = d.getString("event"),
                        link = d.getString("link")?.takeIf { it.isNotBlank() },
                    )
                }
            }
            .catch { emit(emptyList()) }

    /** Weekly goals, newest first. */
    fun observeGoals(householdId: String, uid: String): Flow<List<Goal>> =
        visible(householdId, "weeklyGoals", uid).observeSnapshots()
            .map { snap ->
                snap.documents.map { d ->
                    Goal(
                        d.id, d.getString("title").orEmpty(), d.getString("kind"),
                        (d.get("who") as? List<*>).orEmpty().filterIsInstance<String>(),
                        (d.getLong("progress") ?: 0).toInt(), d.localDate("by"), d.localDate("createdAt"),
                    )
                }.sortedByDescending { it.created }
            }
            .catch { emit(emptyList()) }

    fun setGoalProgress(householdId: String, id: String, progress: Int) {
        db.collection("households").document(householdId).collection("weeklyGoals").document(id).update("progress", progress.coerceIn(0, 100))
    }

    /** ❤️ a list item, or take the ❤️ back. */
    fun setVote(householdId: String, id: String, uid: String, on: Boolean) {
        db.collection("households").document(householdId).collection("listItems").document(id)
            .update("votes", if (on) com.google.firebase.firestore.FieldValue.arrayUnion(uid) else com.google.firebase.firestore.FieldValue.arrayRemove(uid))
    }

    /** Idea → Planned and back; "done" is the tick. */
    fun setStatus(householdId: String, id: String, status: String) {
        db.collection("households").document(householdId).collection("listItems").document(id).update("status", status)
    }

    /** Upcoming event titles, for "which occasion is this gift for?". */
    fun observeEventTitles(householdId: String, uid: String): Flow<List<String>> =
        visible(householdId, "events", uid).observeSnapshots()
            .map { snap -> snap.documents.mapNotNull { it.getString("title") }.distinct().sorted() }
            .catch { emit(emptyList()) }

    /** Ticked-off tasks and finished list items, newest first, so they can be brought back. */
    fun observeFinished(householdId: String, uid: String): Flow<List<FinishedItem>> =
        kotlinx.coroutines.flow.combine(
            visible(householdId, "tasks", uid).observeSnapshots().map { snap ->
                snap.documents.filter { it.getBoolean("done") == true }.map { d ->
                    FinishedItem(d.id, "tasks", d.getString("title").orEmpty(), null, d.getTimestamp("completedAt")?.toDate())
                }
            }.catch { emit(emptyList()) },
            visible(householdId, "listItems", uid).observeSnapshots().map { snap ->
                snap.documents.filter { it.getString("status") == "DONE" }.map { d ->
                    FinishedItem(d.id, "listItems", d.getString("title").orEmpty(), d.getString("template"), d.getTimestamp("updatedAt")?.toDate() ?: d.getTimestamp("createdAt")?.toDate())
                }
            }.catch { emit(emptyList()) },
        ) { a, b -> (a + b).sortedByDescending { it.at } }

    /** Puts a finished task or list item back on its list. */
    fun reopen(householdId: String, item: FinishedItem) {
        val ref = db.collection("households").document(householdId).collection(item.collection).document(item.id)
        if (item.collection == "tasks") ref.update(mapOf("done" to false, "completedAt" to com.google.firebase.firestore.FieldValue.delete()))
        else ref.update("status", "IDEA")
    }

    fun moveKept(householdId: String, id: String, newLocation: String) {
        db.collection("households").document(householdId).collection("keptAt").document(id).update("location", newLocation)
    }
}

/** Best match for "where are the car keys?": every word of the question should appear in the item. */
fun findKept(items: List<KeptItem>, query: String): List<KeptItem> = bestMatches(items, query) { listOfNotNull(it.title, it.note) }

/** Something whose place is known: a kept thing, or a pantry, medicine or supply item with "Kept at". */
data class Placed(val title: String, val place: String, val note: String? = null)

/**
 * "Where is the jeera?": searches Where it's kept and every stock item that has a place, best
 * matches only, kept things first. Pure, so it's tested.
 */
fun whereIs(query: String, kept: List<KeptItem>, stock: List<PantryStock>): List<Placed> {
    val all = kept.map { Placed(it.title, it.location, it.note) } +
        stock.mapNotNull { s -> s.keptAt?.takeIf { it.isNotBlank() }?.let { Placed(s.title, it) } }
    return bestMatches(all, query) { listOfNotNull(it.title, it.note) }
}

/** The items whose [text] matches the most words of [query] ("where are the car keys" → "car", "key"). */
private fun <T> bestMatches(items: List<T>, query: String, text: (T) -> List<String>): List<T> {
    val words = query.lowercase().split(" ").map { it.trim('?', '.', ',').removeSuffix("s") }
        .filter { it.length > 1 && it !in setOf("the", "my", "our", "where", "is", "are", "kept") }
    if (words.isEmpty()) return emptyList()
    return items
        .map { item -> item to words.count { w -> text(item).any { it.lowercase().contains(w) } } }
        .filter { (_, score) -> score > 0 }
        .sortedByDescending { it.second }
        .let { scored -> scored.filter { it.second == scored.first().second } }
        .map { it.first }
}
