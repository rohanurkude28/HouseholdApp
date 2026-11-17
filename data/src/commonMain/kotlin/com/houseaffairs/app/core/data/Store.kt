package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.Visibility
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The Firestore every repository uses: Google's own SDK underneath, on either phone. */
val store: FirebaseFirestore get() = Firebase.firestore

fun household(householdId: String): DocumentReference = store.collection("households").document(householdId)

fun collection(householdId: String, name: String): CollectionReference = household(householdId).collection(name)

/** The entries of [name] this person may see: rules aren't filters, so every list asks this way. */
fun visible(householdId: String, name: String, uid: String): Query =
    collection(householdId, name).where { "visibleTo" containsAny listOf(Visibility.EVERYONE, uid) }

/** A live list; a failed read (offline, a permission race) gives an empty list, not a stuck screen. */
fun Query.docs(): Flow<List<DocumentSnapshot>> = snapshots.map { it.documents }.catch { emit(emptyList()) }

fun DocumentReference.doc(): Flow<DocumentSnapshot?> = snapshots.map<DocumentSnapshot, DocumentSnapshot?> { it }.catch { emit(null) }

/**
 * Writes that shouldn't wait for the server: they land in the phone's copy at once (so the
 * screen updates and offline works), and reach the server when it can. Started unconfined, so
 * writes are handed to Firestore in the order they're made.
 */
object Writes {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    fun later(write: suspend () -> Unit) {
        scope.launch { runCatching { write() } }
    }
}

/**
 * Every field of a document as plain values (dates as LocalDate, nested maps and lists kept).
 * GitLive keeps the raw data internal; it's read here deliberately (GitLive is pinned at 2.1.0,
 * so the call can't move under us) because search and export need every field.
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun DocumentSnapshot.plainData(): Map<String, Any?> =
    (runCatching { encodedData() }.getOrNull() as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to plainValue(v) }.orEmpty()

private fun plainValue(v: Any?): Any? = when (v) {
    is Map<*, *> -> v.entries.associate { (k, x) -> k.toString() to plainValue(x) }
    is List<*> -> v.map(::plainValue)
    is String, is Boolean, is Long, is Int, is Double, null -> v
    else -> platformValue(v)
}

/** A phone's own value types (timestamps, iOS numbers) as plain Kotlin ones; dates become LocalDate. */
internal expect fun platformValue(v: Any): Any?

/**
 * Every field for a backup, as Android's backup writes it: timestamps keep their exact time as
 * {"__time": millis} and map points as {"__geo": [lat, lng]} ([BackupCodec]), so restoring puts
 * back exactly what was there.
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun DocumentSnapshot.backupData(): Map<String, Any?> =
    (runCatching { encodedData() }.getOrNull() as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to backupValue(v) }.orEmpty()

private fun backupValue(v: Any?): Any? = when (v) {
    is Map<*, *> -> v.entries.associate { (k, x) -> k.toString() to backupValue(x) }
    is List<*> -> v.map(::backupValue)
    is String, is Boolean, is Long, is Int, is Double, null -> v
    else -> platformBackupValue(v)
}

internal expect fun platformBackupValue(v: Any): Any?

/** A backed-up value back into what Firestore stores ({"__time": …} → a timestamp). */
fun restoredValue(v: Any?): Any? = BackupCodec.decode(v) { m ->
    when {
        m.containsKey("__time") -> (m["__time"] as Number).toLong().let { ms -> dev.gitlive.firebase.firestore.Timestamp(ms.floorDiv(1000L), (ms.mod(1000L) * 1_000_000).toInt()) }
        m.containsKey("__geo") -> (m["__geo"] as List<*>).let { dev.gitlive.firebase.firestore.GeoPoint((it[0] as Number).toDouble(), (it[1] as Number).toDouble()) }
        else -> null
    }
}

fun DocumentSnapshot.dbl(field: String): Double? = runCatching { get<Double?>(field) }.getOrNull() ?: long(field)?.toDouble()

/** Several fields at once, from a map (a null value is written as null; use FieldValue.delete to remove). */
fun dev.gitlive.firebase.firestore.WriteBatch.updateFields(ref: DocumentReference, fields: Map<String, Any?>) =
    update(ref, *fields.toList().toTypedArray())

suspend fun DocumentReference.updateFields(fields: Map<String, Any?>) = update(*fields.toList().toTypedArray())
