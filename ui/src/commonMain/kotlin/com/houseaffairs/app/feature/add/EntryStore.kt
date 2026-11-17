package com.houseaffairs.app.feature.add

import com.houseaffairs.app.core.data.Ingredient
import com.houseaffairs.app.core.data.Writes
import com.houseaffairs.app.core.data.collection
import com.houseaffairs.app.core.data.date
import com.houseaffairs.app.core.data.forFirestore
import com.houseaffairs.app.core.data.long
import com.houseaffairs.app.core.data.needsAnswer
import com.houseaffairs.app.core.data.plainData
import com.houseaffairs.app.core.data.stamp
import com.houseaffairs.app.core.data.store
import com.houseaffairs.app.core.data.str
import com.houseaffairs.app.core.data.strings
import com.houseaffairs.app.core.data.updateFields
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.util.Decimal
import com.houseaffairs.app.core.util.plainString
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FieldValue

/** Minor units of a typed amount ("1250.5" rupees → 125050). */
fun minorOf(amount: Decimal, currency: String): Long {
    val (whole, fraction) = amount.plainString().removePrefix("-").split('.').let { it[0] to it.getOrElse(1) { "" } }
    val digits = if (currency in setOf("JPY", "KRW")) 0 else 2
    var minor = (whole.toLongOrNull() ?: 0L)
    repeat(digits) { minor *= 10 }
    val frac = fraction.padEnd(digits, '0').take(digits).toLongOrNull() ?: 0L
    return minor + frac
}

/** Minor units back as the text a money field shows ("1250.5"), trailing zeros dropped. */
private fun amountTextOf(minor: Long, currency: String): String {
    if (currency in setOf("JPY", "KRW")) return minor.toString()
    val whole = minor / 100
    val frac = (minor % 100).toString().padStart(2, '0').trimEnd('0')
    return if (frac.isEmpty()) whole.toString() else "$whole.$frac"
}

/**
 * Any [EntryType] saved from its form values and read back into them, the same documents
 * Android's EntryRepository writes (it stays Android's own for now; this is what the iPhone
 * uses). Writes don't wait for the server, so saving feels instant even without signal.
 */
class EntryStore {
    /** Saves a new entry. Returns the undo: it deletes the entry and any expense saved with it. */
    fun save(householdId: String, uid: String, type: EntryType, values: FormValues, visibleTo: List<String>, source: String = "form"): () -> Unit {
        val doc: MutableMap<String, Any?> = mutableMapOf(
            "type" to type.name, "createdBy" to uid, "visibleTo" to visibleTo,
            "createdAt" to FieldValue.serverTimestamp, "source" to source,
        )
        doc += fieldsOf(type, values)
        when (type) {
            EntryType.TASK, EntryType.SHOPPING -> doc["done"] = false
            EntryType.WISH -> doc["status"] = "IDEA"
            EntryType.DREAM -> {
                doc["achieved"] = false
                // Not picked: the name says it ("Bali trip" is Travel), else Other.
                if (doc["kind"] == null) doc["kind"] = com.houseaffairs.app.core.data.dreamKindOf(doc["title"] as? String ?: "") ?: "Other"
                if (doc["emoji"] == null) doc["emoji"] = DREAM_KINDS[doc["kind"]] ?: "⭐"
            }
            EntryType.CREDIT -> doc["used"] = false
            EntryType.GROCERY, EntryType.MEDICINE, EntryType.HOUSEHOLD -> {
                doc["status"] = "IN_STOCK"
                // The bought quantity, so "how much is left" can be worked out after cooking.
                (values["quantity"] as? QuantityValue)?.parsed?.let { doc["initialAmount"] = it }
                if (doc["level"] == null) doc["level"] = 100
            }
            else -> Unit
        }
        if (type == EntryType.TASK) {
            doc["assignedBy"] = uid
            // Given to someone else: they're asked to say yes (rota turns were agreed already).
            if (needsAnswer(doc["assignee"] as? String, uid, (doc["rotation"] as? List<*>).orEmpty().isNotEmpty())) doc["ask"] = "PENDING"
        }
        val ref = collection(householdId, type.collection).document
        val batch = store.batch()
        batch.set(ref, doc)
        // An investment's opening amount goes into its ledger: from the gullak, or as value it already had.
        if (type == EntryType.INVESTMENT) {
            (values["amount"] as? MoneyValue)?.parsed?.let { amount ->
                val from = if (values["fundedFrom"] == "Gullak") "pot" else "growth"
                batch.set(
                    collection(householdId, "transfers").document,
                    mapOf(
                        "from" to from, "to" to "inv:${ref.id}", "amountMinor" to minorOf(amount, (values["amount"] as MoneyValue).currency),
                        "note" to if (from == "pot") "Opened from gullak" else "Value when added",
                        "date" to FieldValue.serverTimestamp, "createdBy" to uid, "visibleTo" to visibleTo, "createdAt" to FieldValue.serverTimestamp,
                    ),
                )
            }
        }
        // A grocery, medicine or supply with a cost is also spending: it lands in the budget automatically.
        val cost = (values["cost"] as? MoneyValue)?.takeIf { it.parsed != null }
        val linked: DocumentReference? = if (cost != null && type in setOf(EntryType.GROCERY, EntryType.MEDICINE, EntryType.HOUSEHOLD)) {
            collection(householdId, "transactions").document.also { txn ->
                batch.set(
                    txn,
                    mapOf(
                        "type" to EntryType.EXPENSE.name, "createdBy" to uid, "visibleTo" to visibleTo,
                        "createdAt" to FieldValue.serverTimestamp, "source" to "linked", "linkedTo" to ref.path,
                        "amountMinor" to minorOf(cost.parsed!!, cost.currency), "currency" to cost.currency,
                        "category" to when (type) { EntryType.MEDICINE -> "Health"; EntryType.HOUSEHOLD -> "Home"; else -> "Groceries" },
                        "merchant" to (values["store"] as? String)?.takeIf { it.isNotBlank() },
                        "title" to doc["title"], "status" to "Paid", "date" to FieldValue.serverTimestamp,
                    ),
                )
            }
        } else null
        Writes.later { batch.commit() }
        return { Writes.later { ref.delete(); linked?.delete() } }
    }

    /** Saves edits to an existing entry. Who created it, when, and its status are kept. */
    fun update(householdId: String, uid: String, type: EntryType, id: String, values: FormValues, visibleTo: List<String>) {
        val fields = fieldsOf(type, values)
        val changes = mutableMapOf<String, Any?>("visibleTo" to visibleTo, "updatedAt" to FieldValue.serverTimestamp)
        ownedKeys(type).forEach { key -> changes[key] = if (key in fields) fields[key] else FieldValue.delete }
        if (type in setOf(EntryType.GROCERY, EntryType.MEDICINE, EntryType.HOUSEHOLD)) {
            (values["quantity"] as? QuantityValue)?.parsed?.let { changes["initialAmount"] = it }
        }
        // The address or link may have changed: find the place on the map again.
        if (type == EntryType.PLACE) { changes["lat"] = FieldValue.delete; changes["lng"] = FieldValue.delete }
        val ref = collection(householdId, type.collection).document(id)
        Writes.later {
            val old = runCatching { ref.get() }.getOrNull()
            // A medicine counted again ("8 tablets left"): the count down starts from now.
            if (type == EntryType.MEDICINE && old != null) {
                val before = ((old.plainData()["quantity"] as? Map<*, *>)?.get("amount") as? Number)?.toDouble()
                val amount = (values["quantity"] as? QuantityValue)?.parsed
                if (amount != null && amount != before) changes["countedAt"] = FieldValue.serverTimestamp
            }
            // Given to someone new: that person is asked; given to yourself, nothing to ask.
            if (type == EntryType.TASK && old != null) {
                val assignee = fields["assignee"] as? String
                if (old.str("assignee") != assignee) {
                    changes["assignedBy"] = uid
                    changes["ask"] = if (needsAnswer(assignee, uid, (fields["rotation"] as? List<*>).orEmpty().isNotEmpty())) "PENDING" else FieldValue.delete
                    changes["declinedBy"] = FieldValue.delete
                    changes["declineNote"] = FieldValue.delete
                }
            }
            ref.updateFields(changes)
        }
    }

    /** The kind a stored entry is ("GROCERY" or "HOUSEHOLD" share the stock collection). */
    suspend fun kindOf(householdId: String, collection: String, id: String): String? =
        runCatching { collection(householdId, collection).document(id).get() }.getOrNull()?.str("type")

    /** Reads an entry back into form values (and who it's visible to) for editing. */
    suspend fun load(householdId: String, type: EntryType, id: String): Pair<FormValues, List<String>>? {
        val doc = runCatching { collection(householdId, type.collection).document(id).get() }.getOrNull() ?: return null
        if (!doc.exists) return null
        val plain = doc.plainData()
        val values = mutableMapOf<String, Any?>()
        type.fields.forEach { spec ->
            values[spec.key] = when (spec) {
                is TextSpec -> doc.str(spec.key) ?: if (spec.key == type.titleKey) doc.str("title") else null
                is MoneySpec -> {
                    val minor = doc.long(if (spec.key == "amount") "amountMinor" else "${spec.key}Minor")
                    val cur = doc.str(if (spec.key == "amount") "currency" else "${spec.key}Currency")
                    if (minor != null && cur != null) MoneyValue(amountTextOf(minor, cur), cur) else values[spec.key] ?: return@forEach
                }
                is ChoiceSpec -> if (spec.multi) doc.strings(spec.key).takeIf { plain[spec.key] != null } else doc.str(spec.key)
                is DateSpec -> doc.date(spec.key)
                is QuantitySpec -> (plain[spec.key] as? Map<*, *>)?.let { q ->
                    val amt = (q["amount"] as? Number)?.toDouble()
                    QuantityValue(amt?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "", (q["unit"] as? String) ?: spec.units.first())
                }
                is LevelSpec -> doc.long(spec.key)?.toInt()
                is MembersSpec -> (plain[spec.key] as? List<*>)?.filterIsInstance<String>() ?: doc.str(spec.key)?.let { listOf(it) }
                is PhotosSpec -> (plain[spec.key] as? List<*>)?.filterIsInstance<String>()
                is IngredientsSpec -> (plain[spec.key] as? List<*>)?.mapNotNull { raw ->
                    (raw as? Map<*, *>)?.let { Ingredient((it["name"] as? String).orEmpty(), (it["amount"] as? Number)?.toDouble(), (it["unit"] as? String) ?: "g") }
                }
            }
        }
        // Shopping items saved before titles existed used "name".
        type.titleKey?.let { key -> if (values[key] == null) values[key] = doc.str("title") ?: doc.str("name") }
        val visibleTo = doc.strings("visibleTo").ifEmpty { listOf("*") }
        return values.filterValues { it != null } to visibleTo
    }

    /** Deletes an entry; the returned undo puts it back exactly as it was. Null if it's already gone. */
    suspend fun remove(householdId: String, type: EntryType, id: String): (() -> Unit)? {
        val ref = collection(householdId, type.collection).document(id)
        val doc = runCatching { ref.get() }.getOrNull()?.takeIf { it.exists } ?: return null
        val data = doc.plainData().forFirestore()
        Writes.later { ref.delete() }
        return { Writes.later { ref.set(data) } }
    }

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
                    doc["${prefix}Minor"] = minorOf(money.parsed!!, money.currency)
                    doc[if (spec.key == "amount") "currency" else "${prefix}Currency"] = money.currency
                }
                is ChoiceSpec -> if (value != "None") doc[spec.key] = value
                is DateSpec -> doc[spec.key] = (value as LocalDate).stamp()
                is QuantitySpec -> (value as QuantityValue).let { doc[spec.key] = mapOf("amount" to it.parsed, "unit" to it.unit) }
                is LevelSpec -> doc[spec.key] = value
                is MembersSpec -> doc[spec.key] = if (spec.multi) value else (value as List<*>).first()
                is PhotosSpec -> doc[spec.key] = (value as List<*>).filterIsInstance<String>()
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
}
