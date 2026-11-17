package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.util.moneyText
import com.houseaffairs.app.feature.quickadd.ListTemplate
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** A ticked-off task or finished list item; [at] is when, in epoch milliseconds. */
data class FinishedItem(val id: String, val collection: String, val title: String, val template: String?, val at: Long?)

/** "Where is it kept", important places, wishes and goals. */
class ThingsRepository {
    fun observeKept(householdId: String, uid: String): Flow<List<KeptItem>> =
        visible(householdId, "keptAt", uid).docs().map { docs ->
            docs.map { d ->
                KeptItem(
                    d.id, d.str("title").orEmpty(), d.str("location").orEmpty(), d.str("kind"), d.str("room"), d.str("note"),
                    docType = d.str("docType"), link = d.str("driveLink"), photos = d.strings("photos"),
                )
            }.sortedBy { it.title.lowercase() }
        }

    fun observePlaces(householdId: String, uid: String): Flow<List<PlaceItem>> =
        visible(householdId, "places", uid).docs().map { docs ->
            docs.map { d ->
                PlaceItem(
                    d.id, d.str("title").orEmpty(), d.str("kind") ?: "Other", d.str("area"),
                    d.str("rating"), d.str("goodFor")?.ifBlank { null }, d.str("phone"), d.str("mapLink"),
                    lat = d.dbl("lat") ?: latLngFromLink(d.str("mapLink"))?.lat,
                    lng = d.dbl("lng") ?: latLngFromLink(d.str("mapLink"))?.lng,
                )
            }.sortedBy { it.title.lowercase() }
        }

    /** Travel, eat-out, buy and gift wishes (books, music and shows live in the watch list). */
    fun observeWishes(householdId: String, uid: String): Flow<List<WishItem>> =
        visible(householdId, "listItems", uid).docs().map { docs ->
            docs.filter { it.str("status") != "DONE" }.mapNotNull { d ->
                val template = d.str("template") ?: return@mapNotNull null
                if (ListTemplate.of(template)?.isMedia == true) return@mapNotNull null
                WishItem(
                    d.id, template, d.str("title").orEmpty(), d.str("place"),
                    d.long("budgetMinor")?.let { m -> d.str("budgetCurrency")?.let { moneyText(m, it) } },
                    status = d.str("status") ?: "IDEA",
                    votes = d.strings("votes"),
                    event = d.str("event"),
                    link = d.str("link")?.takeIf { it.isNotBlank() },
                    photos = d.strings("photos"),
                )
            }
        }

    /**
     * The watch list (movies & shows, books, music) as Android's WatchRepository reads it: what was
     * looked up (poster, ratings, where it streams) is on each document, so this phone just reads it.
     * Highest rated first; an odd document is skipped rather than emptying the list.
     */
    fun observeWatch(householdId: String, uid: String): Flow<List<WatchItem>> =
        visible(householdId, "listItems", uid).docs().map { docs ->
            docs.filter { ListTemplate.of(it.str("template"))?.isMedia == true && it.str("status") != "DONE" }.mapNotNull { d ->
                runCatching {
                    WatchItem(
                        template = ListTemplate.of(d.str("template")) ?: ListTemplate.WATCH,
                        creator = d.str("creator"),
                        id = d.id,
                        title = d.str("title").orEmpty(),
                        platform = d.str("platform"),
                        // Older items saved "👍 Liked it" here; only a number is a rating.
                        rating = runCatching { d.get<Double?>("rating") }.getOrNull() ?: d.long("rating")?.toDouble(),
                        ratingSource = d.str("ratingSource"),
                        poster = d.str("poster"),
                        providers = d.strings("providers"),
                        kind = d.str("kind"),
                        year = d.str("year"),
                        lookedUp = d.bool("lookedUp") == true,
                        imdb = d.str("imdbRating"),
                        rottenTomatoes = d.str("rtScore"),
                        status = d.str("status") ?: "IDEA",
                        votes = d.strings("votes"),
                        imdbId = d.str("imdbId"),
                        link = d.str("link")?.takeIf { it.isNotBlank() },
                        photos = d.strings("photos"),
                    )
                }.getOrNull()
            }.sortedByDescending { it.rating ?: 0.0 }
        }

    /** Weekly goals, newest first. */
    fun observeGoals(householdId: String, uid: String): Flow<List<Goal>> =
        visible(householdId, "weeklyGoals", uid).docs().map { docs ->
            docs.map { d ->
                Goal(d.id, d.str("title").orEmpty(), d.str("kind"), d.strings("who"), (d.long("progress") ?: 0).toInt(), d.date("by"), d.date("createdAt"), d.date("doneAt"))
            }.sortedByDescending { it.created }
        }

    /** Returns an undo that puts the earlier progress back. */
    fun setGoalProgress(householdId: String, id: String, progress: Int, before: Int? = null): () -> Unit {
        val ref = collection(householdId, "weeklyGoals").document(id)
        fun write(value: Int) {
            val p = value.coerceIn(0, 100)
            // When it was reached, so it stays for this year's story and is cleared after.
            Writes.later { ref.update("progress" to p, "doneAt" to if (p >= 100) FieldValue.serverTimestamp else FieldValue.delete) }
        }
        write(progress)
        return { before?.let(::write) }
    }

    /** ❤️ a list item, or take the ❤️ back. */
    fun setVote(householdId: String, id: String, uid: String, on: Boolean) {
        Writes.later { collection(householdId, "listItems").document(id).update("votes" to if (on) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)) }
    }

    /** Idea → Planned and back; "done" is the tick. */
    fun setStatus(householdId: String, id: String, status: String) {
        Writes.later { collection(householdId, "listItems").document(id).update("status" to status) }
    }

    /** Ticked off (watched, read, bought…); `updatedAt` dates it for the year view and clearing. Undo makes it an idea again. */
    fun markDone(householdId: String, id: String, before: String = "IDEA"): () -> Unit {
        val ref = collection(householdId, "listItems").document(id)
        Writes.later { ref.update("status" to "DONE", "updatedAt" to FieldValue.serverTimestamp) }
        return { Writes.later { ref.update("status" to before) } }
    }

    /** Upcoming event titles, for "which occasion is this gift for?". */
    fun observeEventTitles(householdId: String, uid: String): Flow<List<String>> =
        visible(householdId, "events", uid).docs().map { docs -> docs.mapNotNull { it.str("title") }.distinct().sorted() }

    /** Ticked-off tasks and finished list items, newest first, so they can be brought back. */
    fun observeFinished(householdId: String, uid: String): Flow<List<FinishedItem>> =
        combine(
            visible(householdId, "tasks", uid).docs().map { docs ->
                docs.filter { it.bool("done") == true }.map { d -> FinishedItem(d.id, "tasks", d.str("title").orEmpty(), null, d.millis("completedAt")) }
            },
            visible(householdId, "listItems", uid).docs().map { docs ->
                docs.filter { it.str("status") == "DONE" }.map { d ->
                    FinishedItem(d.id, "listItems", d.str("title").orEmpty(), d.str("template"), d.millis("updatedAt") ?: d.millis("createdAt"))
                }
            },
        ) { a, b -> (a + b).sortedByDescending { it.at } }

    /**
     * Clears finished tasks, list items and goals once [shouldClear] says their year is over.
     * Only what this person can see; each member's phone clears what it can. Returns how many went.
     */
    suspend fun clearOldFinished(householdId: String, uid: String, today: LocalDate = systemToday()): Int {
        val stale = listOf<Pair<String, (DocumentSnapshot) -> LocalDate?>>(
            "tasks" to { d -> if (d.bool("done") == true) d.date("completedAt") else null },
            "listItems" to { d -> if (d.str("status") == "DONE") d.date("updatedAt") ?: d.date("createdAt") else null },
            "weeklyGoals" to { d -> if ((d.long("progress") ?: 0) >= 100) d.date("doneAt") ?: d.date("createdAt") else null },
        ).flatMap { (name, doneOn) ->
            // No Plans access (a helper): nothing to read, nothing to clear.
            runCatching { visible(householdId, name, uid).get().documents }.getOrDefault(emptyList())
                .filter { d -> doneOn(d)?.let { shouldClear(it, today) } == true }
        }
        stale.chunked(450).forEach { chunk -> store.batch().apply { chunk.forEach { delete(it.reference) } }.commit() }
        return stale.size
    }

    /** Puts a finished task or list item back on its list. */
    fun reopen(householdId: String, item: FinishedItem) {
        val ref = collection(householdId, item.collection).document(item.id)
        Writes.later {
            if (item.collection == "tasks") ref.update("done" to false, "completedAt" to FieldValue.delete) else ref.update("status" to "IDEA")
        }
    }

    fun moveKept(householdId: String, id: String, newLocation: String) {
        Writes.later { collection(householdId, "keptAt").document(id).update("location" to newLocation) }
    }
}
