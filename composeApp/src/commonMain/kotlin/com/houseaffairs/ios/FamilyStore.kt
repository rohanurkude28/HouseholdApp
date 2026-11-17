package com.houseaffairs.ios

import com.houseaffairs.app.core.data.*
import com.houseaffairs.app.core.data.GONE_STATUSES
import com.houseaffairs.app.core.data.nextDue
import com.houseaffairs.app.core.data.nextInRotation
import com.houseaffairs.app.core.data.nextOccurrence
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.core.time.toEpochDay
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** One row in a tab's list: a title, a line under it, and when it matters. */
data class Entry(
    val id: String,
    val title: String,
    val detail: String = "",
    val date: LocalDate? = null,
    val amountMinor: Long? = null,
    val currency: String? = null,
    val group: String = "",
    val repeat: String? = null,
    val assignee: String? = null,
    val rotation: List<String> = emptyList(),
    /** Expired or overdue: shown in red. */
    val late: Boolean = false,
    /** One of the family's chores (a task anyone can do, set up in Plans → Chores). */
    val chore: Boolean = false,
)

/**
 * The lists behind the iPhone's tabs, read the way Android reads them: every query filters on
 * `visibleTo`, and modules this person can't open are never asked for.
 */
class FamilyStore(private val s: SessionState.Ready) {
    private val db get() = Firebase.firestore
    private val hid get() = s.household.id
    private val uid get() = s.uid
    private fun col(name: String) = db.collection("households").document(hid).collection(name)
    private fun visible(name: String): Query = col(name).where { "visibleTo" containsAny listOf(Visibility.EVERYONE, uid) }
    private fun Query.rows(map: (DocumentSnapshot) -> Entry?): Flow<List<Entry>> = snapshots.map { it.documents.mapNotNull(map) }.catch { emit(emptyList()) }
    private fun allowed(module: AppModule, flow: () -> Flow<List<Entry>>) = if (s.me.canAccess(module)) flow() else flowOf(emptyList())

    /** This month's spending, newest first. */
    fun expenses(): Flow<List<Entry>> = allowed(AppModule.MONEY) {
        val month = Clock.System.todayIn(TimeZone.currentSystemDefault()).let { kotlinx.datetime.LocalDate(it.year, it.monthNumber, 1) }
        visible("transactions").where { "date" greaterThanOrEqualTo startOfDay(month) }.rows { d ->
            if (d.str("status") == "Planned") return@rows null
            Entry(
                d.id, d.str("merchant") ?: d.str("category") ?: "Expense", detail = d.str("category") ?: "Other",
                date = d.date("date"), amountMinor = d.long("amountMinor"), currency = d.str("currency"), group = d.str("category") ?: "Other",
            )
        }.map { list -> list.sortedByDescending { it.date?.toEpochDay() ?: 0 } }
    }

    fun shopping(): Flow<List<Entry>> = allowed(AppModule.STOCK) {
        visible("shopping").rows { d -> if (d.bool("done") == true) null else Entry(d.id, d.titleOr("name"), group = d.str("aisle") ?: "") }
    }

    /** Groceries and supplies still at home ([type] GROCERY or HOUSEHOLD). */
    fun stock(type: String): Flow<List<Entry>> = allowed(AppModule.STOCK) {
        visible("stock").rows { d ->
            if (d.str("status") in GONE_STATUSES || (d.str("type") ?: "GROCERY") != type) return@rows null
            val expiry = d.date("expiry")
            Entry(
                d.id, d.titleOr("title"), detail = expiry?.let { useBy(it) }.orEmpty(), date = expiry,
                group = d.str(if (type == "HOUSEHOLD") "supplyType" else "groceryType") ?: "", late = expiry?.isBefore(today()) == true,
            )
        }.map { list -> list.sortedWith(compareBy<Entry> { it.date?.toEpochDay() ?: Long.MAX_VALUE }.thenBy { it.title.lowercase() }) }
    }

    /** Medicines; teens only get the ones that are for them. */
    fun medicines(): Flow<List<Entry>> = allowed(AppModule.MEDICINE) {
        val q = if (RolePolicy.ownMedicinesOnly(s.me.role)) col("medicines").where { "forWhom" contains uid } else visible("medicines")
        q.rows { d ->
            if (d.str("status") in GONE_STATUSES) return@rows null
            val expiry = d.date("expiry")
            Entry(d.id, d.titleOr("title"), detail = listOfNotNull(d.str("frequency"), expiry?.let { useBy(it) }).joinToString(" · "), date = expiry, late = expiry?.isBefore(today()) == true)
        }.map { list -> list.sortedBy { it.title.lowercase() } }
    }

    /** Open to-dos, soonest first, then those with no date. */
    fun tasks(): Flow<List<Entry>> = allowed(AppModule.PLANS) {
        visible("tasks").rows { d ->
            if (d.bool("done") == true) return@rows null
            val due = d.date("due")
            Entry(
                d.id, d.titleOr("title"), detail = listOfNotNull(d.str("time"), d.str("repeat")?.takeIf { it != "Never" }?.let { "repeats ${it.lowercase()}" }).joinToString(" · "),
                date = due, repeat = d.str("repeat")?.takeIf { it != "Never" }, assignee = d.str("assignee"), rotation = d.strings("rotation"),
                late = due?.isBefore(today()) == true, chore = d.str("source") == "chore",
            )
        }.map { list -> list.sortedWith(compareBy<Entry> { it.date?.toEpochDay() ?: Long.MAX_VALUE }) }
    }

    /** Birthdays and events still to come, next one first. */
    fun events(): Flow<List<Entry>> = allowed(AppModule.PLANS) {
        visible("events").rows { d ->
            val date = d.date("date") ?: return@rows null
            val next = nextOccurrence(date, d.str("repeat"), today())
            if (next.isBefore(today())) null else Entry(d.id, d.titleOr("title"), detail = d.str("kind") ?: "Event", date = next)
        }.map { list -> list.sortedBy { it.date?.toEpochDay() } }
    }

    /** Where things are kept, and documents. */
    fun kept(): Flow<List<Entry>> = allowed(AppModule.VAULT) {
        visible("keptAt").rows { d -> Entry(d.id, d.titleOr("title"), detail = d.str("location")?.let { "📍 $it" }.orEmpty()) }
            .map { list -> list.sortedBy { it.title.lowercase() } }
    }

    // ---- Actions, the same writes Android makes. Each returns its undo.

    /** Done; a repeating one comes back on its next day, as on Android. */
    suspend fun complete(task: Entry): suspend () -> Unit {
        val ref = col("tasks").document(task.id)
        ref.update("done" to true, "completedAt" to FieldValue.serverTimestamp)
        val next = nextDue(task.date, task.repeat, today())?.let { due ->
            val data = ref.get()
            col("tasks").document.also { nextRef ->
                val batch = db.batch()
                batch.set(nextRef, mapOf(
                    "type" to "TASK", "title" to task.title, "due" to day(due), "time" to data.str("time"), "repeat" to task.repeat,
                    "rotation" to task.rotation.ifEmpty { null }, "assignee" to nextInRotation(task.rotation, task.assignee),
                    "priority" to data.str("priority"), "assignedBy" to (data.str("assignedBy") ?: uid), "source" to data.str("source"),
                    "visibleTo" to data.strings("visibleTo").ifEmpty { listOf(Visibility.EVERYONE) },
                    "done" to false, "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp,
                ).filterValues { it != null })
                batch.commit()
            }
        }
        return {
            ref.update("done" to false, "completedAt" to FieldValue.delete)
            next?.delete()
        }
    }

    /** Off the buy list and into the pantry (unless it's already there). */
    suspend fun bought(item: Entry): suspend () -> Unit {
        val ref = col("shopping").document(item.id)
        ref.update("done" to true, "boughtAt" to FieldValue.serverTimestamp)
        val inPantry = runCatching {
            visible("stock").get().documents.any { it.titleOr("title").equals(item.title, ignoreCase = true) && it.str("status") !in GONE_STATUSES }
        }.getOrDefault(true)
        val added = if (inPantry) null else col("stock").document.also { stockRef ->
            val batch = db.batch()
            batch.set(stockRef, mapOf(
                "type" to "GROCERY", "title" to item.title, "status" to "IN_STOCK", "level" to 100,
                "createdBy" to uid, "visibleTo" to listOf(Visibility.EVERYONE), "createdAt" to FieldValue.serverTimestamp, "source" to "iphone",
            ))
            batch.commit()
        }
        return {
            ref.update("done" to false, "boughtAt" to FieldValue.delete)
            added?.delete()
        }
    }

    /** Finished, so it leaves the pantry or supplies; the buy list gets it back if asked. */
    suspend fun usedUp(collection: String, item: Entry): suspend () -> Unit {
        val ref = col(collection).document(item.id)
        ref.update("status" to "USED_UP", "usedUpAt" to FieldValue.serverTimestamp)
        return { ref.update("status" to "IN_STOCK", "usedUpAt" to FieldValue.delete) }
    }

    /** Back on the buy list. */
    suspend fun buyAgain(title: String): suspend () -> Unit {
        val ref = col("shopping").document
        val batch = db.batch()
        batch.set(ref, mapOf(
            "type" to "SHOPPING", "title" to title, "done" to false, "createdBy" to uid,
            "visibleTo" to listOf(Visibility.EVERYONE), "createdAt" to FieldValue.serverTimestamp, "source" to "iphone",
        ))
        batch.commit()
        return { ref.delete() }
    }

    suspend fun delete(collection: String, id: String) = col(collection).document(id).delete()
    // ---- Budget: the same summary Android shows, from the same shared rules.

    /** This budget month: what's left, what can still be spent, per category. Null without a budget. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun budget(): Flow<com.houseaffairs.app.core.data.BudgetSummary?> = if (!s.me.canAccess(AppModule.MONEY)) flowOf(null) else
        col("budgets").document("main").snapshots.flatMapLatest { d ->
            if (!d.exists) return@flatMapLatest flowOf(null)
            val b = com.houseaffairs.app.core.data.Budget(
                limitMinor = d.long("limitMinor") ?: 0, currency = d.str("currency") ?: s.household.baseCurrency,
                cycleStartDay = d.long("cycleStartDay")?.toInt() ?: 1, savingsGoalMinor = d.long("savingsGoalMinor") ?: 0,
                categories = runCatching { d.get<Map<String, Long>?>("categories") }.getOrNull().orEmpty(),
            )
            val cycle = com.houseaffairs.app.core.data.cycleFor(today(), b.cycleStartDay)
            val txns = visible("transactions").where { "date" greaterThanOrEqualTo day(cycle.start) }.snapshots.map { snap ->
                snap.documents.mapNotNull { t ->
                    com.houseaffairs.app.core.data.Txn(
                        t.id, t.str("merchant") ?: t.str("category") ?: "", t.str("category") ?: "Other", t.long("amountMinor") ?: return@mapNotNull null,
                        t.date("date") ?: return@mapNotNull null, planned = t.str("status") == "Planned", trip = t.str("trip"),
                    ).takeIf { (t.str("currency") ?: b.currency) == b.currency }
                }
            }
            val trips = visible("trips").snapshots.map { snap ->
                snap.documents.mapNotNull { t ->
                    com.houseaffairs.app.core.data.TripMoney(t.id, t.titleOr("title"), t.date("start") ?: return@mapNotNull null, t.date("end"), t.long("budgetMinor"), t.bool("done") == true)
                }
            }
            kotlinx.coroutines.flow.combine(txns, trips) { list, tripList ->
                // A trip's budget is the one place its money is planned, as on Android.
                val real = list.filterNot { com.houseaffairs.app.core.data.coveredByTrip(it, tripList) }
                com.houseaffairs.app.core.data.summarize(b, real + com.houseaffairs.app.core.data.tripReserves(real, tripList, today()), emptyList(), today())
            }
        }.catch { emit(null) }

    // ---- Chores and editing

    /** A chore anyone can do, due today and then every [repeat] after it's done (as Android). */
    suspend fun addChore(title: String, repeat: String): suspend () -> Unit {
        val ref = col("tasks").document
        val batch = db.batch()
        batch.set(ref, mapOf(
            "type" to "TASK", "title" to title.trim(), "done" to false, "priority" to "Low", "repeat" to repeat, "due" to day(today()),
            "createdBy" to uid, "visibleTo" to listOf(Visibility.EVERYONE), "createdAt" to FieldValue.serverTimestamp, "source" to "chore",
        ))
        batch.commit()
        return { ref.delete() }
    }

    /** The saved document, to fill the edit form. */
    suspend fun load(collection: String, id: String): DocumentSnapshot = col(collection).document(id).get()

    /** Saves edited fields; a null value clears that field. */
    suspend fun update(collection: String, id: String, fields: List<Pair<String, Any?>>) {
        val changes = fields.map { (k, v) -> k to (v ?: FieldValue.delete) } + ("updatedAt" to FieldValue.serverTimestamp)
        col(collection).document(id).update(*changes.toTypedArray())
    }
}

private fun useBy(date: LocalDate): String {
    val t = today()
    return if (date.isBefore(t)) "Expired ${date.format("d MMM")}" else "Use by ${date.format("d MMM")}"
}
