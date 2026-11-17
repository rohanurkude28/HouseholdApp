package com.houseaffairs.ios

import kotlinx.coroutines.flow.first

import com.houseaffairs.app.core.data.*
import com.houseaffairs.app.core.data.privateTopicOf
import com.houseaffairs.app.core.data.privateVisibleTo
import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.dayOfMonth
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.core.time.monthValue
import com.houseaffairs.app.core.time.year
import com.houseaffairs.app.core.util.Decimal
import com.houseaffairs.app.core.util.decimalOf
import com.houseaffairs.app.core.util.plainString
import com.houseaffairs.app.core.util.times
import com.houseaffairs.app.feature.quickadd.QuickAddDraft
import com.houseaffairs.app.feature.quickadd.QuickAddParser
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What a saved line says back, and how to take it back. */
data class Saved(val message: String, val undo: suspend () -> Unit)

/** Minutes after midnight now, so "at 9 am" said at 10 means tomorrow. */
fun nowMinute(): Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).let { it.hour * 60 + it.minute }

val parser = QuickAddParser(today = ::today, nowMinute = ::nowMinute)

/**
 * Saves what the shared parser read from a typed line, as the same documents Android's
 * EntryRepository writes (field names, `visibleTo`, private categories), so both phones see it.
 * Kinds iPhone can't save yet return null, and the screen says to use Android for those.
 */
class Saver(private val s: SessionState.Ready, private val members: List<MemberInfo>) {
    private val db get() = Firebase.firestore
    private val uid get() = s.uid
    private val currency get() = s.household.baseCurrency
    private fun col(name: String) = db.collection("households").document(s.household.id).collection(name)

    suspend fun save(draft: QuickAddDraft): Saved? {
        val refs = mutableListOf<DocumentReference>()
        val message = write(draft, refs) ?: return null
        return Saved(message) { refs.forEach { runCatching { it.delete() } } }
    }

    private suspend fun write(draft: QuickAddDraft, refs: MutableList<DocumentReference>): String? = when (draft) {
        is QuickAddDraft.Expense -> {
            val cur = draft.currency ?: currency
            // Spent while away: filed under that trip, as on Android.
            val trip = runCatching { com.houseaffairs.app.core.data.MoneyRepository().observeTrips(s.household.id, uid).first() }.getOrNull()
                ?.let { com.houseaffairs.app.core.data.tripOn(it, draft.date ?: today()) }?.title
            add(refs, "transactions", "EXPENSE", draft.merchant ?: draft.category, draft.category, mapOf(
                "amountMinor" to draft.amount.minor(), "currency" to cur, "category" to draft.category,
                "merchant" to draft.merchant, "subcategory" to draft.subcategory, "note" to draft.note.takeIf { it.isNotBlank() },
                "paymentMethod" to draft.paidWith, "status" to "Paid", "date" to day(draft.date ?: today()), "paidBy" to uid, "trip" to trip,
            ))
            listOfNotNull(formatMinor(draft.amount.minor(), cur), draft.category, draft.merchant, trip?.let { "🧳 $it" }).joinToString(" · ")
        }
        is QuickAddDraft.Shopping -> {
            add(refs, "shopping", "SHOPPING", draft.item, null, mapOf("title" to draft.item, "done" to false, "quantity" to quantity(draft.quantity)))
            "${draft.item} · on the buy list"
        }
        is QuickAddDraft.Task -> {
            val person = draft.forName?.let(::memberNamed)
            val assignee = person?.uid ?: uid
            // Someone outside the app keeps their name on the task ("Mom: call the plumber").
            val title = if (draft.forName != null && person == null) "${draft.forName}: ${draft.title}" else draft.title
            add(refs, "tasks", "TASK", title, null, mapOf(
                "title" to title, "due" to draft.due?.let(::day), "time" to draft.time, "repeat" to draft.repeat,
                "priority" to draft.priority, "reach" to draft.reach, "assignee" to assignee, "assignedBy" to uid, "done" to false,
                "ask" to if (assignee != uid) "PENDING" else null,
            ))
            listOfNotNull(title, draft.due?.format("EEE d MMM"), draft.time, person?.let { "for ${it.displayName.substringBefore(' ')}" }).joinToString(" · ")
        }
        is QuickAddDraft.Event -> {
            val yearly = draft.kind in setOf("Birthday", "Anniversary")
            add(refs, "events", "EVENT", draft.title, null, mapOf(
                "title" to draft.title, "kind" to draft.kind, "date" to (draft.date ?: today()).let(::day), "repeat" to if (yearly) "Yearly" else null,
            ))
            listOfNotNull(draft.title, draft.date?.format("EEE d MMM")).joinToString(" · ")
        }
        is QuickAddDraft.Grocery -> {
            stock(refs, "stock", "GROCERY", draft.title, draft.quantity, draft.expiry, emptyMap())
            listOfNotNull(draft.title, draft.quantity, draft.expiry?.let { "use by " + it.format("d MMM") }).joinToString(" · ") + " · in the pantry"
        }
        is QuickAddDraft.Household -> {
            stock(refs, "stock", "HOUSEHOLD", draft.title, draft.quantity, draft.expiry, mapOf("supplyType" to draft.type))
            "${draft.title} · in supplies"
        }
        is QuickAddDraft.Medicine -> {
            val forWhom = draft.forName?.let(::memberNamed)?.uid
            stock(refs, "medicines", "MEDICINE", draft.title, null, draft.expiry, mapOf(
                "forWhom" to listOfNotNull(forWhom).ifEmpty { null }, "frequency" to draft.frequency, "until" to draft.until?.let(::day),
            ))
            listOfNotNull(draft.title, draft.frequency, draft.expiry?.let { "use by " + it.format("d MMM") }).joinToString(" · ")
        }
        is QuickAddDraft.Subscription -> {
            val cur = draft.currency ?: currency
            add(refs, "recurring", "SUBSCRIPTION", draft.title, "Bills", mapOf(
                "title" to draft.title, "amountMinor" to draft.amount.minor(), "currency" to cur, "frequency" to draft.frequency, "nextDate" to day(today()),
            ))
            "${draft.title} · ${formatMinor(draft.amount.minor(), cur)} ${draft.frequency.lowercase()}"
        }
        is QuickAddDraft.ListItem -> {
            add(refs, "listItems", "WISH", draft.title, null, mapOf("title" to draft.title, "template" to draft.template.label, "status" to "IDEA"))
            "${draft.title} · ${draft.template.title}"
        }
        is QuickAddDraft.Kept -> {
            add(refs, "keptAt", "KEPT", draft.item, null, mapOf("title" to draft.item, "location" to draft.location))
            "${draft.item} · kept in ${draft.location}"
        }
        is QuickAddDraft.Goal -> {
            add(refs, "weeklyGoals", "GOAL", draft.title, null, mapOf("title" to draft.title))
            "${draft.title} · a goal this week"
        }
        is QuickAddDraft.Place -> {
            add(refs, "places", "PLACE", draft.title, null, mapOf("title" to draft.title, "kind" to draft.kind, "goodFor" to draft.goodFor))
            "${draft.title} · saved place"
        }
        is QuickAddDraft.Batch -> {
            val lines = draft.drafts.mapNotNull { write(it, refs) }
            if (lines.isEmpty()) null else "${lines.size} added: " + lines.joinToString("; ")
        }
        else -> null
    }

    /** A grocery, supply or medicine, in stock and full, as Android saves it. */
    private suspend fun stock(refs: MutableList<DocumentReference>, collection: String, type: String, title: String, qty: String?, expiry: LocalDate?, extra: Map<String, Any?>) {
        val q = quantity(qty)
        add(refs, collection, type, title, extra["supplyType"] as? String, mapOf(
            "title" to title, "quantity" to q, "initialAmount" to q?.get("amount"), "expiry" to expiry?.let(::day),
            "status" to "IN_STOCK", "level" to 100,
        ) + extra)
    }

    /** One document with the fields every entry carries; private categories narrow who sees it. */
    private suspend fun add(refs: MutableList<DocumentReference>, collection: String, type: String, title: String, category: String?, fields: Map<String, Any?>) {
        val topic = privateTopicOf(collection, title, category, s.household.privateTopics.keys)?.takeIf { it in s.household.privateTopics }
        val visibleTo = topic?.let { privateVisibleTo(s.household.privateTopics.getValue(it), uid) } ?: listOf(Visibility.EVERYONE)
        val ref = col(collection).document
        val batch = db.batch()
        batch.set(ref, mapOf("type" to type, "createdBy" to uid, "visibleTo" to visibleTo, "createdAt" to FieldValue.serverTimestamp, "source" to "iphone") + fields.filterValues { it != null })
        batch.commit()
        refs += ref
    }

    /** "Mom", "riya" → the family member with that first name. */
    private fun memberNamed(name: String): MemberInfo? =
        members.firstOrNull { it.displayName.substringBefore(' ').equals(name.trim(), ignoreCase = true) }
}

/** Decimal rupees → paise (minor units), as Android's toMinorUnits. */
fun Decimal.minor(): Long = (this * decimalOf(100L)).plainString().substringBefore('.').toLongOrNull() ?: 0L

/** "500 g", "2 pcs" → the {amount, unit} map Android stores; null when nothing was said. */
fun quantity(text: String?): Map<String, Any?>? {
    val m = Regex("""^\s*([\d.]+)\s*([a-zA-Z]*)""").find(text ?: return null) ?: return null
    val amount = m.groupValues[1].toDoubleOrNull() ?: return null
    return mapOf("amount" to amount, "unit" to m.groupValues[2].ifEmpty { "pcs" })
}

/** A day as Android stores it: the local start of that day. */
fun day(date: LocalDate): Timestamp = startOfDay(kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth))
