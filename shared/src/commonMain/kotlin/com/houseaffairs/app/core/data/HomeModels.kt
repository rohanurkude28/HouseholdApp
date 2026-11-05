package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*

/** What Home shows, read from Firestore by each platform's repository. */

/** Stock statuses that take an item off its list: finished, or thrown away. */
val GONE_STATUSES = setOf("USED_UP", "WASTED")

enum class ActivityKind { EXPENSE, SHOPPING, TASK, LIST_ITEM, GROCERY, MEDICINE, SUBSCRIPTION, EVENT }

data class ActivityItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val amountMinor: Long?,
    val currency: String?,
    val createdBy: String,
    /** Epoch millis; Firestore's estimate while the server time is pending. */
    val createdAtMillis: Long?,
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
    /** "Call", "WhatsApp" or "SMS" for "call mom" / "message Ravi"; the row then opens that app. */
    val reach: String? = null,
    val phone: String? = null,
    /** "5:30 pm" when the task has a time; it then reminds at that time on its due date. */
    val time: String? = null,
    /** Who gave the task; with [ask], whether the person it's for has said yes yet. */
    val assignedBy: String? = null,
    /** "PENDING", "ACCEPTED" or "DECLINED" when it was given to someone else; null otherwise. */
    val ask: String? = null,
    /** Who said they can't do it, and why; it's back with [assignedBy] then. */
    val declinedBy: String? = null,
    val declineNote: String? = null,
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
    /** Medicines taken on a schedule, for dose reminders and "runs out soon". */
    val courses: List<MedicineCourse> = emptyList(),
)

