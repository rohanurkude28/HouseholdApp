package com.houseaffairs.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.core.util.toMinorUnits
import com.houseaffairs.app.feature.add.ChoiceSpec
import com.houseaffairs.app.feature.add.DateSpec
import com.houseaffairs.app.feature.add.EntryType
import com.houseaffairs.app.feature.add.FormValues
import com.houseaffairs.app.feature.add.IngredientsSpec
import com.houseaffairs.app.feature.add.LevelSpec
import com.houseaffairs.app.feature.add.MembersSpec
import com.houseaffairs.app.feature.add.MoneySpec
import com.houseaffairs.app.feature.add.MoneyValue
import com.houseaffairs.app.feature.add.QuantitySpec
import com.houseaffairs.app.feature.add.QuantityValue
import com.houseaffairs.app.feature.add.TextSpec
import com.houseaffairs.app.feature.add.isFilled
import java.time.LocalDate
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** A saved entry found by what someone said, for "delete the Uber expense" and the like. */
data class FoundEntry(
    val type: EntryType,
    val id: String,
    val title: String,
    val detail: String,
    val createdAt: Date?,
    val score: Int,
    /** The stored document, so a delete can be undone. */
    val data: Map<String, Any?>,
)

/** Saves any [EntryType] from its form values. */
@Singleton
class EntryRepository @Inject constructor(
    private val db: FirebaseFirestore,
) {
    /**
     * Not awaited: Firestore applies the write to the local cache at once and syncs when online,
     * so saving feels instant even without signal.
     */
    fun save(
        householdId: String,
        uid: String,
        type: EntryType,
        values: FormValues,
        visibleTo: List<String>,
        source: String,
    ): DocumentReference {
        val doc: MutableMap<String, Any?> = mutableMapOf(
            "type" to type.name,
            "createdBy" to uid,
            "visibleTo" to visibleTo,
            "createdAt" to FieldValue.serverTimestamp(),
            "source" to source,
        )
        doc += fieldsOf(type, values)
        when (type) {
            EntryType.TASK, EntryType.SHOPPING -> doc["done"] = false
            EntryType.WISH -> doc["status"] = "IDEA"
            EntryType.DREAM -> {
                doc["achieved"] = false
                if (doc["emoji"] == null) doc["emoji"] = com.houseaffairs.app.feature.add.DREAM_KINDS[values["kind"]] ?: "⭐"
            }
            EntryType.CREDIT -> doc["used"] = false
            EntryType.GROCERY, EntryType.MEDICINE, EntryType.HOUSEHOLD -> {
                doc["status"] = "IN_STOCK"
                // Remember the bought quantity so "how much is left" can be worked out after cooking.
                (values["quantity"] as? QuantityValue)?.parsed?.let { doc["initialAmount"] = it }
                if (doc["level"] == null) doc["level"] = 100
            }
            else -> Unit
        }
        if (type == EntryType.TASK) doc["assignedBy"] = uid

        val household = db.collection("households").document(householdId)
        val ref = household.collection(type.collection).document()
        val batch = db.batch()
        batch.set(ref, doc)
        // An investment's opening amount goes into its ledger: from the gullak, or as value it already had.
        if (type == EntryType.INVESTMENT) {
            (values["amount"] as? MoneyValue)?.takeIf { it.parsed != null }?.let { amount ->
                val from = if (values["fundedFrom"] == "Gullak") "pot" else "growth"
                batch.set(
                    household.collection("transfers").document(),
                    mapOf(
                        "from" to from, "to" to "inv:${ref.id}", "amountMinor" to toMinorUnits(amount.parsed!!, amount.currency),
                        "note" to if (from == "pot") "Opened from gullak" else "Value when added",
                        "date" to Timestamp.now(), "createdBy" to uid, "visibleTo" to visibleTo, "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
        }
        // A grocery, medicine or supply with a cost is also spending: it lands in the budget automatically.
        val cost = (values["cost"] as? MoneyValue)?.takeIf { it.parsed != null }
        val linked = if (cost != null && (type == EntryType.GROCERY || type == EntryType.MEDICINE || type == EntryType.HOUSEHOLD)) {
            household.collection("transactions").document().also { txn ->
                batch.set(
                    txn,
                    mapOf(
                        "type" to EntryType.EXPENSE.name,
                        "createdBy" to uid,
                        "visibleTo" to visibleTo,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "source" to "linked",
                        "linkedTo" to ref.path,
                        "amountMinor" to toMinorUnits(cost.parsed!!, cost.currency),
                        "currency" to cost.currency,
                        "category" to when (type) {
                            EntryType.MEDICINE -> "Health"
                            EntryType.HOUSEHOLD -> "Home"
                            else -> "Groceries"
                        },
                        "merchant" to (values["store"] as? String)?.takeIf { it.isNotBlank() },
                        "title" to doc["title"],
                        "status" to "Paid",
                        "date" to Timestamp.now(),
                    ),
                )
            }
        } else {
            null
        }
        batch.commit()
        if (linked != null) linkedRefs[ref.path] = linked
        return ref
    }

    /** Expenses created alongside an entry, so Undo removes both. */
    private val linkedRefs = mutableMapOf<String, DocumentReference>()

    /** The stored form of the fields the form shows (hidden or empty fields are left out). */
    private fun fieldsOf(type: EntryType, values: FormValues): Map<String, Any?> {
        val doc = mutableMapOf<String, Any?>()
        type.fields.filter { it.visibleWhen(values) }.forEach { spec ->
            val value = values[spec.key]
            if (!spec.isFilled(value)) return@forEach
            when (spec) {
                is TextSpec -> doc[spec.key] = (value as String).trim()
                is MoneySpec -> {
                    val money = value as MoneyValue
                    // The main amount keeps the plain names Home and Money already query.
                    val prefix = if (spec.key == "amount") "amount" else spec.key
                    doc["${prefix}Minor"] = toMinorUnits(money.parsed!!, money.currency)
                    doc[if (spec.key == "amount") "currency" else "${prefix}Currency"] = money.currency
                }
                is ChoiceSpec -> if (value != "None") doc[spec.key] = value
                is DateSpec -> doc[spec.key] = startOfDay(value as LocalDate)
                is QuantitySpec -> (value as QuantityValue).let { doc[spec.key] = mapOf("amount" to it.parsed, "unit" to it.unit) }
                is LevelSpec -> doc[spec.key] = value
                is MembersSpec -> doc[spec.key] = if (spec.multi) value else (value as List<*>).first()
                is IngredientsSpec -> doc[spec.key] = (value as List<*>).filterIsInstance<Ingredient>()
                    .filter { it.name.isNotBlank() }
                    .map { mapOf("name" to it.name.trim(), "amount" to it.amount, "unit" to it.unit) }
            }
        }
        type.titleKey?.let { key -> (values[key] as? String)?.takeIf { it.isNotBlank() }?.let { doc["title"] = it.trim() } }
        return doc
    }

    /** Every Firestore field a kind of entry can own, for clearing ones emptied while editing. */
    private fun ownedKeys(type: EntryType): Set<String> = type.fields.flatMap { spec ->
        when (spec) {
            is MoneySpec -> if (spec.key == "amount") listOf("amountMinor", "currency") else listOf("${spec.key}Minor", "${spec.key}Currency")
            else -> listOf(spec.key)
        }
    }.toSet() + "title"

    /** Saves edits to an existing entry. Who created it, when, and its status are kept. */
    fun update(householdId: String, type: EntryType, id: String, values: FormValues, visibleTo: List<String>) {
        val fields = fieldsOf(type, values)
        val changes = mutableMapOf<String, Any?>("visibleTo" to visibleTo, "updatedAt" to FieldValue.serverTimestamp())
        ownedKeys(type).forEach { key -> changes[key] = if (key in fields) fields[key] else FieldValue.delete() }
        if (type in setOf(EntryType.GROCERY, EntryType.MEDICINE, EntryType.HOUSEHOLD)) {
            (values["quantity"] as? QuantityValue)?.parsed?.let { changes["initialAmount"] = it }
        }
        db.collection("households").document(householdId).collection(type.collection).document(id).update(changes)
    }

    /** Reads an entry back into form values (and who it's visible to) for editing. */
    suspend fun load(householdId: String, type: EntryType, id: String): Pair<FormValues, List<String>>? {
        val doc = db.collection("households").document(householdId).collection(type.collection).document(id).get().await()
        if (!doc.exists()) return null
        val values = mutableMapOf<String, Any?>()
        type.fields.forEach { spec ->
            values[spec.key] = when (spec) {
                is TextSpec -> doc.getString(spec.key) ?: if (spec.key == type.titleKey) doc.getString("title") else null
                is MoneySpec -> {
                    val minor = doc.getLong(if (spec.key == "amount") "amountMinor" else "${spec.key}Minor")
                    val cur = doc.getString(if (spec.key == "amount") "currency" else "${spec.key}Currency")
                    if (minor != null && cur != null) {
                        val digits = java.util.Currency.getInstance(cur).defaultFractionDigits.coerceAtLeast(0)
                        MoneyValue(java.math.BigDecimal.valueOf(minor, digits).stripTrailingZeros().toPlainString(), cur)
                    } else {
                        values[spec.key] ?: return@forEach
                    }
                }
                is ChoiceSpec -> if (spec.multi) (doc.get(spec.key) as? List<*>)?.filterIsInstance<String>() else doc.getString(spec.key)
                is DateSpec -> doc.localDate(spec.key)
                is QuantitySpec -> (doc.get(spec.key) as? Map<*, *>)?.let { q ->
                    val amt = (q["amount"] as? Number)?.toDouble()
                    QuantityValue(amt?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "", (q["unit"] as? String) ?: spec.units.first())
                }
                is LevelSpec -> doc.getLong(spec.key)?.toInt()
                is MembersSpec -> (doc.get(spec.key) as? List<*>)?.filterIsInstance<String>() ?: doc.getString(spec.key)?.let { listOf(it) }
                is IngredientsSpec -> (doc.get(spec.key) as? List<*>)?.mapNotNull { raw ->
                    (raw as? Map<*, *>)?.let { Ingredient((it["name"] as? String).orEmpty(), (it["amount"] as? Number)?.toDouble(), (it["unit"] as? String) ?: "g") }
                }
            }
        }
        // Shopping items saved before titles existed used "name".
        if (type.titleKey != null && values[type.titleKey] == null) values[type.titleKey] = doc.getString("title") ?: doc.getString("name")
        val visibleTo = (doc.get("visibleTo") as? List<*>)?.filterIsInstance<String>() ?: listOf("*")
        return values.filterValues { it != null } to visibleTo
    }

    /**
     * Saved entries matching what was said ("Uber", "milk"), best matches first, newest first
     * among equals. An empty [query] returns the newest entries. Finished items are left out.
     */
    suspend fun find(householdId: String, uid: String, types: Collection<EntryType>, query: String): List<FoundEntry> {
        val words = query.lowercase().split(" ").map { it.trim('.', ',', '?', '!').removeSuffix("s") }.filter { it.length > 1 }
        val household = db.collection("households").document(householdId)
        val found = types.groupBy { it.collection }.flatMap { (collection, kinds) ->
            val docs = runCatching {
                household.collection(collection).whereArrayContainsAny("visibleTo", listOf(com.houseaffairs.app.core.model.Visibility.EVERYONE, uid)).get().await().documents
            }.getOrDefault(emptyList())
            docs.mapNotNull { d ->
                if (d.getBoolean("done") == true || d.getString("status") in setOf("DONE", "USED_UP") || d.getBoolean("closed") == true) return@mapNotNull null
                // Pantry and supplies share a collection; the document says which it is.
                val type = kinds.firstOrNull { it.name == d.getString("type") } ?: kinds.firstOrNull { it != EntryType.HOUSEHOLD } ?: kinds.first()
                if (type !in kinds) return@mapNotNull null
                val title = d.getString("title") ?: d.getString("merchant") ?: d.getString("name") ?: d.getString("category") ?: d.getString("kind") ?: return@mapNotNull null
                val haystack = listOfNotNull(title, d.getString("merchant"), d.getString("category"), d.getString("subcategory"), d.getString("note"), d.getString("kind"))
                    .joinToString(" ").lowercase()
                val score = words.count { haystack.contains(it) }
                if (words.isNotEmpty() && score == 0) return@mapNotNull null
                val amount = d.getLong("amountMinor")?.let { m -> d.getString("currency")?.let { com.houseaffairs.app.core.util.formatMinor(m, it) } }
                val date = d.localDate("date") ?: d.localDate("due") ?: d.localDate("nextDate")
                FoundEntry(
                    type = type, id = d.id, title = title,
                    detail = listOfNotNull(amount, d.getString("category")?.takeIf { it != title }, date?.let { com.houseaffairs.app.feature.add.formatDate(it) }).joinToString(" · "),
                    createdAt = d.getTimestamp("createdAt")?.toDate(),
                    score = score,
                    data = d.data.orEmpty(),
                )
            }
        }
        return found.sortedWith(compareByDescending<FoundEntry> { it.score }.thenByDescending { it.createdAt ?: java.util.Date(Long.MAX_VALUE) })
    }

    /** Deletes an entry; the returned undo puts it back exactly as it was. */
    fun remove(householdId: String, entry: FoundEntry): () -> Unit {
        val ref = db.collection("households").document(householdId).collection(entry.type.collection).document(entry.id)
        ref.delete()
        return { ref.set(entry.data) }
    }

    /** Deletes an entry by id; the returned undo puts it back. Null if it's already gone. */
    suspend fun removeById(householdId: String, type: EntryType, id: String): (() -> Unit)? {
        val ref = db.collection("households").document(householdId).collection(type.collection).document(id)
        val data = ref.get().await().data ?: return null
        ref.delete()
        return { ref.set(data) }
    }

    /** Changes a few fields of an entry; the returned undo restores their old values. */
    fun patch(householdId: String, entry: FoundEntry, changes: Map<String, Any?>): () -> Unit {
        val ref = db.collection("households").document(householdId).collection(entry.type.collection).document(entry.id)
        ref.update(changes)
        val before = changes.keys.associateWith { key -> entry.data[key] ?: FieldValue.delete() }
        return { ref.update(before) }
    }

    /** Sets where an existing item is kept; the returned undo puts the old place back. */
    fun setPlace(householdId: String, type: EntryType, id: String, place: String, before: String?): () -> Unit {
        val ref = db.collection("households").document(householdId).collection(type.collection).document(id)
        ref.update("keptAt", place)
        return { ref.update("keptAt", before ?: FieldValue.delete()) }
    }

    fun delete(ref: DocumentReference) {
        ref.delete()
        linkedRefs.remove(ref.path)?.delete()
    }

    private fun startOfDay(date: LocalDate): Timestamp =
        Timestamp(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))
}
