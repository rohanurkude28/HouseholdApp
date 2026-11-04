package com.houseaffairs.ios

import com.houseaffairs.app.core.data.ActivityItem
import com.houseaffairs.app.core.data.ActivityKind
import com.houseaffairs.app.core.data.EventItem
import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.Renewal
import com.houseaffairs.app.core.data.ShoppingItem
import com.houseaffairs.app.core.data.StockItem
import com.houseaffairs.app.core.data.TaskItem
import com.houseaffairs.app.core.data.nextOccurrence
import com.houseaffairs.app.core.data.nextRenewal
import com.houseaffairs.app.core.data.remindDaysOf
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.core.time.localDate
import com.houseaffairs.app.core.time.toEpochDay
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).let { localDate(it.year, it.monthNumber, it.dayOfMonth) }

/**
 * What Home shows, read the same way as Android's HomeRepository: every query filters on
 * `visibleTo` so the rules can prove each result is visible, and modules the member can't
 * access are never queried.
 */
class HomeStore {
    private val db = Firebase.firestore

    fun observe(householdId: String, me: MemberInfo, baseCurrency: String): Flow<HomeData> {
        val canMoney = me.canAccess(AppModule.MONEY)
        val canStock = me.canAccess(AppModule.STOCK)
        val canPlans = me.canAccess(AppModule.PLANS)
        val canMedicine = me.canAccess(AppModule.MEDICINE)
        fun recentIf(allowed: Boolean, name: String) = if (allowed) recent(householdId, name, me.uid) else flowOf(emptyList())

        val month = Clock.System.todayIn(TimeZone.currentSystemDefault()).let { kotlinx.datetime.LocalDate(it.year, it.monthNumber, 1) }
        val sources = listOf(
            if (canMoney) visible(householdId, "transactions", me.uid).where { "date" greaterThanOrEqualTo startOfDay(month) }.docs() else flowOf(emptyList()),
            recentIf(canMoney, "transactions"),
            recentIf(canStock, "shopping"),
            recentIf(canPlans, "tasks"),
            // Teens only get the medicines that are for them.
            if (!canMedicine) {
                flowOf(emptyList())
            } else if (RolePolicy.ownMedicinesOnly(me.role)) {
                collection(householdId, "medicines").where { "forWhom" contains me.uid }.orderBy("createdAt", Direction.DESCENDING).limit(30).docs()
            } else {
                recent(householdId, "medicines", me.uid)
            },
            recentIf(canStock, "stock"),
            recentIf(canMoney, "recurring"),
            recentIf(canPlans, "events"),
        )

        return combine(sources) { r ->
            val (monthDocs, expenseDocs, shoppingDocs, taskDocs, medicineDocs) = r.take(5)
            val (stockDocs, recurringDocs, eventDocs) = r.drop(5)
            val today = today()
            // Planned spends haven't happened yet, so they're not "spent this month".
            val inBase = monthDocs.filter { it.str("currency") == baseCurrency && it.str("status") != "Planned" }
            HomeData(
                loaded = true,
                monthSpentMinor = inBase.sumOf { it.long("amountMinor") ?: 0L },
                spentByCategory = inBase.groupBy { it.str("category") ?: "Other" }
                    .mapValues { (_, docs) -> docs.sumOf { it.long("amountMinor") ?: 0L } }
                    .entries.sortedByDescending { it.value }.map { it.key to it.value },
                shopping = shoppingDocs.filter { it.bool("done") != true }.map { ShoppingItem(it.id, it.titleOr("name"), it.str("aisle")) },
                tasks = taskDocs.filter { it.bool("done") != true }.map {
                    TaskItem(
                        it.id, it.titleOr("title"), it.date("due"), it.str("assignee"), remindDaysOf(it.str("remind")),
                        repeat = it.str("repeat")?.takeIf { r -> r != "Never" },
                        rotation = it.strings("rotation"),
                        priority = when (it.str("priority")) { "High" -> "High"; "Medium", "Normal" -> "Medium"; else -> "Low" },
                    )
                }.sortedWith(compareBy<TaskItem, Long?>(nullsLast()) { it.dueDate?.toEpochDay() }.thenBy { listOf("High", "Medium", "Low").indexOf(it.priority) }),
                pantry = stockDocs.filter { it.str("status") != "USED_UP" }.map(::stockItem),
                medicines = medicineDocs.filter { it.str("status") != "USED_UP" }.map(::stockItem),
                renewals = recurringDocs.mapNotNull { doc ->
                    val next = doc.date("nextDate") ?: return@mapNotNull null
                    val amount = doc.long("amountMinor")?.let { minor -> doc.str("currency")?.let { formatMinor(minor, it) } }
                    Renewal(
                        doc.id, doc.titleOr("title"), nextRenewal(next, doc.str("frequency"), doc.date("lastLogged"), today), amount,
                        remindDaysOf(doc.str("remind")),
                        auto = doc.str("onRenew") == "Log it automatically",
                        using = doc.str("using")?.takeIf { it != "Yes" },
                    )
                }.sortedBy { it.next.toEpochDay() },
                events = eventDocs.mapNotNull { doc ->
                    val date = doc.date("date") ?: return@mapNotNull null
                    EventItem(doc.id, doc.titleOr("title"), doc.str("kind") ?: "Event", nextOccurrence(date, doc.str("repeat"), today), remindDaysOf(doc.str("remind")), doc.str("repeat"))
                }.filter { !it.next.isBefore(today) }.sortedBy { it.next.toEpochDay() },
                recent = (
                    expenseDocs.map { it.toActivity(ActivityKind.EXPENSE, it.str("merchant") ?: it.str("category").orEmpty()) } +
                        shoppingDocs.map { it.toActivity(ActivityKind.SHOPPING, it.titleOr("name")) } +
                        taskDocs.map { it.toActivity(ActivityKind.TASK, it.titleOr("title")) } +
                        stockDocs.map { it.toActivity(ActivityKind.GROCERY, it.titleOr("title")) } +
                        medicineDocs.map { it.toActivity(ActivityKind.MEDICINE, it.titleOr("title")) }
                    ).sortedByDescending { it.createdAtMillis }.take(8),
            )
        }
    }

    /** Ticks a task off. Repeating tasks aren't rolled forward on iOS yet; Android does that. */
    suspend fun completeTask(householdId: String, taskId: String) {
        collection(householdId, "tasks").document(taskId).update("done" to true, "completedAt" to FieldValue.serverTimestamp)
    }

    suspend fun markBought(householdId: String, itemId: String) {
        collection(householdId, "shopping").document(itemId).update("done" to true)
    }

    private fun stockItem(doc: DocumentSnapshot) = StockItem(doc.id, doc.titleOr("title"), doc.date("expiry"), doc.long("level")?.toInt())

    private fun collection(householdId: String, name: String) = db.collection("households").document(householdId).collection(name)

    private fun visible(householdId: String, name: String, uid: String): Query =
        collection(householdId, name).where { "visibleTo" containsAny listOf(Visibility.EVERYONE, uid) }

    private fun recent(householdId: String, name: String, uid: String) =
        visible(householdId, name, uid).orderBy("createdAt", Direction.DESCENDING).limit(30).docs()

    private fun Query.docs(): Flow<List<DocumentSnapshot>> = snapshots.map { it.documents }.catch { emit(emptyList()) }

    private fun DocumentSnapshot.toActivity(kind: ActivityKind, title: String) = ActivityItem(
        id = id, kind = kind, title = title, amountMinor = long("amountMinor"), currency = str("currency"),
        createdBy = str("createdBy").orEmpty(), createdAtMillis = createdMillis(),
    )
}
