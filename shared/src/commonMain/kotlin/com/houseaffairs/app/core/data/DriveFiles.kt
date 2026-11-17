package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.core.time.localDate

/**
 * Where the app keeps things in the person's Google Drive, on both phones: one "HouseAffairs"
 * folder, documents filed by type under it, and backups beside them. The app can only see files
 * it made there (the drive.file scope), never the rest of the Drive. Pure, so it's tested.
 */
const val DRIVE_ROOT = "HouseAffairs"
const val DRIVE_DOCUMENTS = "Documents"
const val DRIVE_BACKUPS = "Backups"
const val DRIVE_PHOTOS = "Photos"

/** Photo ids an entry mentions (its "photos" list), for fetching the ones this phone hasn't got. */
fun photoIdsIn(data: Map<String, Any?>): List<String> = (data["photos"] as? List<*>).orEmpty().filterIsInstance<String>()

/** The folders a document of [docType] goes in, from the top: HouseAffairs / Documents / Insurance. */
fun documentFolders(docType: String?): List<String> {
    val name = docType?.trim()?.takeIf { it.isNotEmpty() }?.replace(Regex("[/\\\\]"), "-") ?: "Other"
    return listOf(DRIVE_ROOT, DRIVE_DOCUMENTS, name)
}

/** How a Drive document's place reads in lists and "where is…?" answers. */
fun driveLocation(docType: String?): String = "Google Drive · " + documentFolders(docType).joinToString(" / ")

private fun two(n: Int) = n.toString().padStart(2, '0')

/** "houseaffairs-backup-2026-11-05-0930.json": sorts by date, so the newest is easy to find. */
fun backupFileName(day: LocalDate, hour: Int, minute: Int): String = "houseaffairs-backup-$day-${two(hour)}${two(minute)}.json"

/** "5 Nov 2026, 9:30 am" from a backup's file name; null for files that aren't backups. */
fun backupLabel(fileName: String): String? {
    val m = Regex("houseaffairs-backup-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})(\\d{2})\\.json").matchEntire(fileName) ?: return null
    val (y, mo, d, h, mi) = m.destructured
    val day = runCatching { localDate(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull() ?: return null
    val hour = h.toInt()
    val clock = "${if (hour % 12 == 0) 12 else hour % 12}:$mi ${if (hour < 12) "am" else "pm"}"
    return "${day.format("d MMM yyyy")}, $clock"
}

/** Every household collection a backup covers: the ones in firestore.rules' moduleOf(). */
val BACKUP_COLLECTIONS = listOf(
    "transactions", "budgets", "transfers", "credits", "investments", "trips", "recurring", "goals", "paymentMethods",
    "stock", "shopping", "recipes", "cookLog", "medicines",
    "tasks", "events", "lists", "listItems", "weeklyGoals", "places", "dreams", "activities", "checkins", "wellPlans", "diary",
    "documents", "keptAt",
)

/**
 * Firestore values to plain maps, lists, strings, numbers and booleans that fit in JSON, and back.
 * Types JSON can't hold (timestamps, map points) go through [special] as a one-key map such as
 * {"__time": 1762300000000}.
 */
object BackupCodec {
    fun encode(value: Any?, special: (Any) -> Map<String, Any>?): Any? = when (value) {
        null, is String, is Boolean, is Number -> value
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to encode(v, special) }
        is List<*> -> value.map { encode(it, special) }
        else -> special(value) ?: value.toString()
    }

    fun decode(value: Any?, special: (Map<*, *>) -> Any?): Any? = when (value) {
        is Map<*, *> -> if (value.size == 1 && value.keys.first().toString().startsWith("__")) special(value) else value.entries.associate { (k, v) -> k.toString() to decode(v, special) }
        is List<*> -> value.map { decode(it, special) }
        else -> value
    }
}

/**
 * What restoring one saved entry writes: the backup as it was, except a creator who isn't the
 * restorer when the entry no longer exists, since only you can create entries as yourself.
 * [existing] is whether the entry is still there.
 */
fun restoredDoc(data: Map<String, Any?>, existing: Boolean, me: String): Map<String, Any?> =
    if (existing || data["createdBy"] == me) data else data + ("createdBy" to me) + ("restoredFrom" to data["createdBy"])
