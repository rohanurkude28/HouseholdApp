package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.time.withDayOfMonth
import com.houseaffairs.app.core.util.moneyText
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Everything the Home dashboard shows. Every query filters on `visibleTo` so security rules can
 * prove each result is visible; modules the member can't access are never queried.
 */
class HomeRepository {
    /** [canAccess] says which modules this person may open; the others are never queried. */
    fun observe(householdId: String, uid: String, role: Role, canAccess: (AppModule) -> Boolean, baseCurrency: String): Flow<HomeData> {
        val canMoney = canAccess(AppModule.MONEY)
        val canStock = canAccess(AppModule.STOCK)
        val canPlans = canAccess(AppModule.PLANS)
        val canMedicine = canAccess(AppModule.MEDICINE)
        fun recentIf(allowed: Boolean, name: String) = if (allowed) recent(householdId, name, uid) else flowOf(emptyList())

        val monthStart = systemToday().withDayOfMonth(1).stamp()
        val sources = listOf(
            if (canMoney) visible(householdId, "transactions", uid).where { "date" greaterThanOrEqualTo monthStart }.docs() else flowOf(emptyList()),
            recentIf(canMoney, "transactions"),
            recentIf(canStock, "shopping"),
            recentIf(canPlans, "tasks"),
            recentIf(canPlans, "listItems"),
            recentIf(canStock, "stock"),
            // Teens only get the medicines that are for them.
            if (!canMedicine) {
                flowOf(emptyList())
            } else if (RolePolicy.ownMedicinesOnly(role)) {
                collection(householdId, "medicines").where { "forWhom" contains uid }.orderBy("createdAt", Direction.DESCENDING).limit(30).docs()
            } else {
                recent(householdId, "medicines", uid)
            },
            recentIf(canMoney, "recurring"),
            recentIf(canPlans, "events"),
            // Every task, not just the newest 30: a chore's rhythm needs its history.
            if (canPlans) visible(householdId, "tasks", uid).docs() else flowOf(emptyList()),
        )

        return combine(sources) { results ->
            val (monthDocs, expenseDocs, shoppingDocs, taskDocs, listDocs) = results.take(5)
            val (stockDocs, medicineDocs, recurringDocs, eventDocs, allTaskDocs) = results.drop(5)
            val today = systemToday()
            // Planned spends haven't happened yet, so they're not "spent this month".
            val inBase = monthDocs.filter { it.str("currency") == baseCurrency && it.str("status") != "Planned" }
            HomeData(
                loaded = true,
                monthSpentMinor = inBase.sumOf { it.long("amountMinor") ?: 0L },
                spentByCategory = inBase
                    .groupBy { it.str("category") ?: "Other" }
                    .mapValues { (_, docs) -> docs.sumOf { it.long("amountMinor") ?: 0L } }
                    .entries.sortedByDescending { it.value }
                    .map { it.key to it.value },
                shopping = shoppingDocs
                    .filter { it.bool("done") != true }
                    .map { ShoppingItem(it.id, it.titleOr("name"), it.str("aisle")) },
                tasks = taskDocs
                    .filter { it.bool("done") != true }
                    .map {
                        TaskItem(
                            it.id, it.titleOr("title"), it.date("due"), it.str("assignee"), remindDaysOf(it.str("remind")),
                            repeat = it.str("repeat")?.takeIf { r -> r != "Never" },
                            rotation = it.strings("rotation"),
                            priority = when (it.str("priority")) { "High" -> "High"; "Medium", "Normal" -> "Medium"; else -> "Low" },
                            reach = it.str("reach"),
                            phone = it.str("phone")?.takeIf { p -> p.isNotBlank() },
                            time = it.str("time")?.takeIf { t -> t.isNotBlank() },
                            assignedBy = it.str("assignedBy"),
                            ask = it.str("ask"),
                            declinedBy = it.str("declinedBy"),
                            declineNote = it.str("declineNote")?.takeIf { n -> n.isNotBlank() },
                            chore = it.str("source") == "chore",
                        )
                    }
                    // Soonest first; on the same day, High before Medium before Low.
                    .sortedWith(compareBy<TaskItem, LocalDate?>(nullsLast()) { it.dueDate }.thenBy { listOf("High", "Medium", "Low").indexOf(it.priority) }),
                done = allTaskDocs.filter { it.bool("done") == true }.mapNotNull { d ->
                    val (day, minute) = d.dayAndMinute("completedAt") ?: return@mapNotNull null
                    DoneTask(d.titleOr("title"), day, minute)
                },
                pantry = stockDocs.filter { it.str("status") !in GONE_STATUSES }.map(::stockItem),
                medicines = medicineDocs.filter { it.str("status") !in GONE_STATUSES }.map(::stockItem),
                courses = medicineDocs.filter { it.str("status") !in GONE_STATUSES }.mapNotNull(::courseOf),
                renewals = recurringDocs.mapNotNull { doc ->
                    val next = doc.date("nextDate") ?: return@mapNotNull null
                    val owed = nextRenewal(next, doc.str("frequency"), doc.date("lastLogged"), today)
                    // The price for that renewal, after any change of price from a date.
                    val amount = doc.long("amountMinor")?.let { minor -> doc.str("currency")?.let { moneyText(priceOn(owed, minor, decodePrices(doc.plainData()["priceHistory"])), it) } }
                    Renewal(
                        doc.id, doc.titleOr("title"), owed, amount,
                        remindDaysOf(doc.str("remind")),
                        auto = doc.str("onRenew") == "Log it automatically",
                        using = doc.str("using")?.takeIf { it != "Yes" },
                    )
                }.sortedBy { it.next },
                events = eventDocs.mapNotNull { doc ->
                    val date = doc.date("date") ?: return@mapNotNull null
                    EventItem(doc.id, doc.titleOr("title"), doc.str("kind") ?: "Event", nextOccurrence(date, doc.str("repeat"), today), remindDaysOf(doc.str("remind")), doc.str("repeat"))
                }.filter { !it.next.isBefore(today) }.sortedBy { it.next },
                recent = (
                    expenseDocs.map { it.toActivity(ActivityKind.EXPENSE, it.str("merchant") ?: it.str("category").orEmpty()) } +
                        shoppingDocs.map { it.toActivity(ActivityKind.SHOPPING, it.titleOr("name")) } +
                        taskDocs.map { it.toActivity(ActivityKind.TASK, it.titleOr("title")) } +
                        listDocs.map { it.toActivity(ActivityKind.LIST_ITEM, it.titleOr("title")) } +
                        stockDocs.map { it.toActivity(ActivityKind.GROCERY, it.titleOr("title")) } +
                        medicineDocs.map { it.toActivity(ActivityKind.MEDICINE, it.titleOr("title")) } +
                        recurringDocs.map { it.toActivity(ActivityKind.SUBSCRIPTION, it.titleOr("title")) } +
                        eventDocs.map { it.toActivity(ActivityKind.EVENT, it.titleOr("title")) }
                    ).sortedByDescending { it.createdAtMillis }.take(20),
            )
        }
    }

    private fun stockItem(doc: DocumentSnapshot) =
        StockItem(doc.id, doc.titleOr("title"), doc.date("expiry"), doc.long("level")?.toInt())

    /** A task for today, for whoever asked, from a chore Home noticed. Returns the undo. */
    fun addTask(
        householdId: String, uid: String, title: String, today: LocalDate = systemToday(),
        due: LocalDate? = today, time: String? = null, repeat: String? = null, source: String = "habit",
        /** Whose it is; null for a chore anyone can do. */
        assignee: String? = uid,
    ): () -> Unit {
        val ref = collection(householdId, "tasks").document
        Writes.later {
            ref.set(
                mapOf(
                    "type" to "TASK", "title" to title, "done" to false, "assignee" to assignee, "priority" to "Low",
                    "createdBy" to uid, "visibleTo" to Visibility.everyone,
                    "createdAt" to FieldValue.serverTimestamp, "source" to source,
                ) + listOfNotNull(due?.let { "due" to it.stamp() }, time?.let { "time" to it }, repeat?.let { "repeat" to it }),
            )
        }
        return { Writes.later { ref.delete() } }
    }

    /** Takes a task (a chore taken off the list) away; the undo writes it back as it was. */
    fun removeTask(householdId: String, id: String): () -> Unit {
        val ref = collection(householdId, "tasks").document(id)
        var saved: Map<String, Any?>? = null
        Writes.later { saved = ref.get().plainData().forFirestore(); ref.delete() }
        return { saved?.let { Writes.later { ref.set(it) } } }
    }

    /** A chore's new rhythm ("Every 3 days"); undo puts the old one back. */
    fun setRepeat(householdId: String, id: String, repeat: String, before: String?): () -> Unit {
        val ref = collection(householdId, "tasks").document(id)
        Writes.later { ref.update("repeat" to repeat) }
        return { Writes.later { ref.update("repeat" to (before ?: FieldValue.delete)) } }
    }

    /** Moved to another day (or to no date): only the due date changes; undo puts the old one back. */
    fun moveTask(householdId: String, id: String, to: LocalDate?, from: LocalDate?): () -> Unit {
        val ref = collection(householdId, "tasks").document(id)
        Writes.later { ref.update("due" to (to?.stamp() ?: FieldValue.delete)) }
        return { Writes.later { ref.update("due" to (from?.stamp() ?: FieldValue.delete)) } }
    }

    /** "Done ✓" on a chore reminder: a ticked-off task, so the family's rhythm for it carries on. */
    fun logDone(householdId: String, uid: String, title: String) {
        Writes.later {
            collection(householdId, "tasks").document.set(
                mapOf(
                    "type" to "TASK", "title" to title, "done" to true, "assignee" to uid, "priority" to "Low",
                    "completedAt" to FieldValue.serverTimestamp,
                    "createdBy" to uid, "visibleTo" to Visibility.everyone,
                    "createdAt" to FieldValue.serverTimestamp, "source" to "habit",
                ),
            )
        }
    }

    /**
     * Ticks a task off. A repeating one comes back as a new task, due next time and with the
     * next person in its rota. The returned undo puts the task back and removes the next one.
     */
    fun completeTask(householdId: String, uid: String, task: TaskItem, today: LocalDate = systemToday()): () -> Unit {
        val tasks = collection(householdId, "tasks")
        val ref = tasks.document(task.id)
        Writes.later { ref.update("done" to true, "completedAt" to FieldValue.serverTimestamp) }
        // A chore is next due an interval after it was actually done; other repeats keep their rhythm.
        val nextRef = nextDue(if (task.chore) today else task.dueDate, task.repeat, today)?.let { due ->
            tasks.document.also { next ->
                Writes.later {
                    val data = ref.get().plainData()
                    if (data.isEmpty()) return@later
                    next.set(
                        // The next one is agreed already: no question carried over.
                        (data - "completedAt" - "ask" - "declinedBy" - "declineNote" - "answeredAt" - "updatedAt").forFirestore() + mapOf(
                            "done" to false,
                            "due" to due.stamp(),
                            "assignee" to nextInRotation(task.rotation, task.assignee),
                            // Rules want the creator to be whoever writes it.
                            "createdBy" to uid,
                            "createdAt" to FieldValue.serverTimestamp,
                        ),
                    )
                }
            }
        }
        return {
            Writes.later {
                ref.update("done" to false, "completedAt" to FieldValue.delete)
                nextRef?.delete()
            }
        }
    }

    /**
     * The answer to a task given to me: yes, or "can't do it" ([note] says why), which hands it
     * back to whoever gave it. Returns the undo.
     */
    fun answerTask(householdId: String, uid: String, task: TaskItem, accept: Boolean, note: String? = null): () -> Unit {
        val ref = collection(householdId, "tasks").document(task.id)
        Writes.later {
            if (accept) ref.update("ask" to "ACCEPTED", "answeredAt" to FieldValue.serverTimestamp)
            else ref.update(
                "ask" to "DECLINED", "declinedBy" to uid, "declineNote" to note?.trim()?.takeIf { it.isNotEmpty() },
                "assignee" to task.assignedBy, "answeredAt" to FieldValue.serverTimestamp,
            )
        }
        return {
            Writes.later { ref.update("ask" to "PENDING", "assignee" to task.assignee, "declinedBy" to FieldValue.delete, "declineNote" to FieldValue.delete) }
        }
    }

    /** "I'll do it": a task that came back stays with me and stops asking. */
    fun keepTask(householdId: String, task: TaskItem) {
        Writes.later { collection(householdId, "tasks").document(task.id).update("ask" to FieldValue.delete, "declinedBy" to FieldValue.delete, "declineNote" to FieldValue.delete) }
    }

    fun markBought(householdId: String, itemId: String) {
        Writes.later { collection(householdId, "shopping").document(itemId).update("done" to true) }
    }

    private fun recent(householdId: String, name: String, uid: String) =
        visible(householdId, name, uid).orderBy("createdAt", Direction.DESCENDING).limit(30).docs()

    private fun DocumentSnapshot.toActivity(kind: ActivityKind, title: String) = ActivityItem(
        id = id,
        kind = kind,
        title = title,
        amountMinor = long("amountMinor"),
        currency = str("currency"),
        createdBy = str("createdBy").orEmpty(),
        // Just-saved items have no server time yet; use the local estimate so they show at the top.
        createdAtMillis = millis("createdAt"),
    )
}

/**
 * A medicine with a schedule ("Twice a day"), or null when it's taken as needed. The count runs
 * from when it was last counted (an edit to how much is left), else when it was bought.
 */
fun courseOf(doc: DocumentSnapshot): MedicineCourse? {
    val frequency = doc.str("frequency")
    val times = doseTimes(frequency, doc.strings("doseTimes"))
    if (times.isEmpty()) return null
    val raw = doc.plainData()
    val quantity = raw["quantity"] as? Map<*, *>
    return MedicineCourse(
        id = doc.id,
        title = doc.str("title").orEmpty(),
        forWhom = doc.strings("forWhom"),
        createdBy = doc.str("createdBy"),
        times = times,
        perDose = doseAmount(doc.str("perDose")),
        unit = (quantity?.get("unit") as? String) ?: "tablets",
        left = (quantity?.get("amount") as? Number)?.toDouble(),
        since = doc.date("countedAt") ?: doc.date("boughtOn") ?: doc.date("createdAt"),
        until = doc.date("until"),
        remindDoses = doc.str("remindDose") != "No",
        restock = doc.str("restock").let { it != null && it != "Off" },
        skipped = (raw["skipped"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toLong() }.toSet(),
    )
}
