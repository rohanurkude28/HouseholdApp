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
                            reach = it.getString("reach"),
                            phone = it.getString("phone")?.takeIf { p -> p.isNotBlank() },
                            time = it.getString("time")?.takeIf { t -> t.isNotBlank() },
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
                    ).sortedByDescending { it.createdAtMillis }.take(8),
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
        createdAtMillis = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
    )
}
