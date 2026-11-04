package com.houseaffairs.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.util.formatMinor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

enum class ActivityKind { EXPENSE, SHOPPING, TASK, LIST_ITEM, GROCERY, MEDICINE, SUBSCRIPTION, EVENT }

data class ActivityItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val amountMinor: Long?,
    val currency: String?,
    val createdBy: String,
    val createdAt: Date?,
)

data class ShoppingItem(val id: String, val name: String, val aisleChoice: String? = null) {
    val aisle: Aisle get() = Aisle.from(aisleChoice) ?: aisleOf(name)
}

data class TaskItem(
    val id: String,
    val title: String,
    val dueDate: LocalDate?,
    val assignee: String?,
    val remindDays: Int? = null,
    /** "Daily", "Weekly", "Monthly", or null when it doesn't repeat. */
    val repeat: String? = null,
    /** People who take turns, in order; empty when it isn't a rota. */
    val rotation: List<String> = emptyList(),
    /** "High", "Medium" or "Low" (also for older tasks with none, or "Normal" = Medium). */
    val priority: String = "Low",
)

data class StockItem(val id: String, val title: String, val expiry: LocalDate?, val level: Int?)

data class Renewal(
    val id: String,
    val title: String,
    /** The renewal owed next; on or before today means it's waiting to be logged. */
    val next: LocalDate,
    val amountText: String?,
    val remindDays: Int? = null,
    /** Logged as spent without asking. */
    val auto: Boolean = false,
    /** "Rarely" or "Not using" when the family said so. */
    val using: String? = null,
) {
    fun pending(today: LocalDate): Boolean = !next.isAfter(today)
}

data class EventItem(val id: String, val title: String, val kind: String, val next: LocalDate, val remindDays: Int? = null, val repeat: String? = null)

/** The form's "Remind" choice as days before; NO_REMINDER switches it off; null = default. */
const val NO_REMINDER = -1

fun remindDaysOf(choice: String?): Int? = when (choice) {
    "No reminder" -> NO_REMINDER
    "On the day" -> 0
    "1 day before" -> 1
    "3 days before" -> 3
    "1 week before" -> 7
    else -> null
}

data class HomeData(
    val loaded: Boolean = false,
    /** Spend this calendar month in the household's base currency. */
    val monthSpentMinor: Long = 0,
    val spentByCategory: List<Pair<String, Long>> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val pantry: List<StockItem> = emptyList(),
    val medicines: List<StockItem> = emptyList(),
    val renewals: List<Renewal> = emptyList(),
    val events: List<EventItem> = emptyList(),
    val recent: List<ActivityItem> = emptyList(),
)

/**
 * Everything the Home dashboard shows. Every query filters on `visibleTo` so security rules can
 * prove each result is visible; modules the member can't access are never queried.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val db: FirebaseFirestore,
) {
    fun observe(householdId: String, me: Member, baseCurrency: String): Flow<HomeData> {
        val canMoney = me.canAccess(AppModule.MONEY)
        val canStock = me.canAccess(AppModule.STOCK)
        val canPlans = me.canAccess(AppModule.PLANS)
        val canMedicine = me.canAccess(AppModule.MEDICINE)
        fun recentIf(allowed: Boolean, name: String) = if (allowed) recent(householdId, name, me.uid) else flowOf(emptyList())

        val monthStart = Timestamp(Date.from(LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant()))
        val sources = listOf(
            if (canMoney) visible(householdId, "transactions", me.uid).whereGreaterThanOrEqualTo("date", monthStart).docs() else flowOf(emptyList()),
            recentIf(canMoney, "transactions"),
            recentIf(canStock, "shopping"),
            recentIf(canPlans, "tasks"),
            recentIf(canPlans, "listItems"),
            recentIf(canStock, "stock"),
            // Teens only get the medicines that are for them.
            if (!canMedicine) {
                flowOf(emptyList())
            } else if (com.houseaffairs.app.core.model.RolePolicy.ownMedicinesOnly(me.role)) {
                collection(householdId, "medicines").whereArrayContains("forWhom", me.uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(30).docs()
            } else {
                recent(householdId, "medicines", me.uid)
            },
            recentIf(canMoney, "recurring"),
            recentIf(canPlans, "events"),
        )

        return combine(sources) { results ->
            val (monthDocs, expenseDocs, shoppingDocs, taskDocs, listDocs) = results.take(5)
            val (stockDocs, medicineDocs, recurringDocs, eventDocs) = results.drop(5)
            val today = LocalDate.now()
            // Planned spends haven't happened yet, so they're not "spent this month".
            val inBase = monthDocs.filter { it.getString("currency") == baseCurrency && it.getString("status") != "Planned" }
            HomeData(
                loaded = true,
                monthSpentMinor = inBase.sumOf { it.getLong("amountMinor") ?: 0L },
                spentByCategory = inBase
                    .groupBy { it.getString("category") ?: "Other" }
                    .mapValues { (_, docs) -> docs.sumOf { it.getLong("amountMinor") ?: 0L } }
                    .entries.sortedByDescending { it.value }
                    .map { it.key to it.value },
                shopping = shoppingDocs
                    .filter { it.getBoolean("done") != true }
                    .map { ShoppingItem(it.id, it.titleOr("name"), it.getString("aisle")) },
                tasks = taskDocs
                    .filter { it.getBoolean("done") != true }
                    .map {
                        TaskItem(
                            it.id, it.titleOr("title"), it.localDate("due"), it.getString("assignee"), remindDaysOf(it.getString("remind")),
                            repeat = it.getString("repeat")?.takeIf { r -> r != "Never" },
                            rotation = (it.get("rotation") as? List<*>).orEmpty().filterIsInstance<String>(),
                            priority = when (it.getString("priority")) { "High" -> "High"; "Medium", "Normal" -> "Medium"; else -> "Low" },
                        )
                    }
                    // Soonest first; on the same day, High before Medium before Low.
                    .sortedWith(compareBy<TaskItem, LocalDate?>(nullsLast()) { it.dueDate }.thenBy { listOf("High", "Medium", "Low").indexOf(it.priority) }),
                pantry = stockDocs.filter { it.getString("status") != "USED_UP" }.map(::stockItem),
                medicines = medicineDocs.filter { it.getString("status") != "USED_UP" }.map(::stockItem),
                renewals = recurringDocs.mapNotNull { doc ->
                    val next = doc.localDate("nextDate") ?: return@mapNotNull null
                    val amount = doc.getLong("amountMinor")?.let { minor -> doc.getString("currency")?.let { formatMinor(minor, it) } }
                    Renewal(
                        doc.id, doc.titleOr("title"), nextRenewal(next, doc.getString("frequency"), doc.localDate("lastLogged"), today), amount,
                        remindDaysOf(doc.getString("remind")),
                        auto = doc.getString("onRenew") == "Log it automatically",
                        using = doc.getString("using")?.takeIf { it != "Yes" },
                    )
                }.sortedBy { it.next },
                events = eventDocs.mapNotNull { doc ->
                    val date = doc.localDate("date") ?: return@mapNotNull null
                    EventItem(doc.id, doc.titleOr("title"), doc.getString("kind") ?: "Event", nextOccurrence(date, doc.getString("repeat"), today), remindDaysOf(doc.getString("remind")), doc.getString("repeat"))
                }.filter { !it.next.isBefore(today) }.sortedBy { it.next },
                recent = (
                    expenseDocs.map { it.toActivity(ActivityKind.EXPENSE, it.getString("merchant") ?: it.getString("category").orEmpty()) } +
                        shoppingDocs.map { it.toActivity(ActivityKind.SHOPPING, it.titleOr("name")) } +
                        taskDocs.map { it.toActivity(ActivityKind.TASK, it.titleOr("title")) } +
                        listDocs.map { it.toActivity(ActivityKind.LIST_ITEM, it.titleOr("title")) } +
                        stockDocs.map { it.toActivity(ActivityKind.GROCERY, it.titleOr("title")) } +
                        medicineDocs.map { it.toActivity(ActivityKind.MEDICINE, it.titleOr("title")) } +
                        recurringDocs.map { it.toActivity(ActivityKind.SUBSCRIPTION, it.titleOr("title")) } +
                        eventDocs.map { it.toActivity(ActivityKind.EVENT, it.titleOr("title")) }
                    ).sortedByDescending { it.createdAt }.take(8),
            )
        }
    }

    private fun stockItem(doc: DocumentSnapshot) =
        StockItem(doc.id, doc.titleOr("title"), doc.localDate("expiry"), doc.getLong("level")?.toInt())

    private fun DocumentSnapshot.localDate(field: String): LocalDate? =
        getTimestamp(field)?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

    /**
     * Ticks a task off. A repeating one comes back as a new task, due next time and with the
     * next person in its rota. The returned undo puts the task back and removes the next one.
     */
    fun completeTask(householdId: String, uid: String, task: TaskItem, today: LocalDate = LocalDate.now()): () -> Unit {
        val tasks = collection(householdId, "tasks")
        val ref = tasks.document(task.id)
        ref.update(mapOf("done" to true, "completedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
        val nextRef = nextDue(task.dueDate, task.repeat, today)?.let { due ->
            tasks.document().also { next ->
                ref.get().addOnSuccessListener { doc ->
                    val data = doc.data ?: return@addOnSuccessListener
                    next.set(
                        data - "completedAt" + mapOf(
                            "done" to false,
                            "due" to com.google.firebase.Timestamp(java.util.Date.from(due.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())),
                            "assignee" to nextInRotation(task.rotation, task.assignee),
                            // Rules want the creator to be whoever writes it.
                            "createdBy" to uid,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        ),
                    )
                }
            }
        }
        return {
            ref.update(mapOf("done" to false, "completedAt" to com.google.firebase.firestore.FieldValue.delete()))
            nextRef?.delete()
        }
    }

    fun markBought(householdId: String, itemId: String) {
        collection(householdId, "shopping").document(itemId).update("done", true)
    }

    private fun collection(householdId: String, name: String) =
        db.collection("households").document(householdId).collection(name)

    private fun visible(householdId: String, name: String, uid: String): Query =
        collection(householdId, name).whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid))

    private fun recent(householdId: String, name: String, uid: String) =
        visible(householdId, name, uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(30).docs()

    private fun Query.docs(): Flow<List<DocumentSnapshot>> =
        observeSnapshots().map { it.documents }.catch { emit(emptyList()) }

    /** Early shopping items stored their text as `name`; everything now uses `title`. */
    private fun DocumentSnapshot.titleOr(legacy: String): String = getString("title") ?: getString(legacy).orEmpty()

    private fun DocumentSnapshot.toActivity(kind: ActivityKind, title: String) = ActivityItem(
        id = id,
        kind = kind,
        title = title,
        amountMinor = getLong("amountMinor"),
        currency = getString("currency"),
        createdBy = getString("createdBy").orEmpty(),
        // Just-saved items have no server time yet; use the local estimate so they show at the top.
        createdAt = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
    )
}
