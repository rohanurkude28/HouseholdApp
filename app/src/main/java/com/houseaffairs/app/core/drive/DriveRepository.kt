package com.houseaffairs.app.core.drive

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.houseaffairs.app.core.data.BACKUP_COLLECTIONS
import com.houseaffairs.app.core.data.BackupCodec
import com.houseaffairs.app.core.data.DRIVE_BACKUPS
import com.houseaffairs.app.core.data.DRIVE_ROOT
import com.houseaffairs.app.core.data.backupFileName
import com.houseaffairs.app.core.data.documentFolders
import com.houseaffairs.app.core.data.driveLocation
import com.houseaffairs.app.core.data.restoredDoc
import com.houseaffairs.app.core.model.Visibility
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** A backup in Drive, with a readable date. */
data class DriveBackup(val fileId: String, val name: String, val label: String)

/**
 * Backups of the family's entries and documents uploaded into the person's Google Drive, all
 * under the "HouseAffairs" folder. Each call takes a fresh token from [DriveAuth].
 */
@Singleton
class DriveRepository @Inject constructor(
    private val db: FirebaseFirestore,
    @ApplicationContext private val context: Context,
) {
    private fun household(hid: String) = db.collection("households").document(hid)

    /** Writes every entry this person can see into one JSON file in HouseAffairs/Backups. */
    suspend fun backup(token: String, hid: String, uid: String): Int = withContext(Dispatchers.IO) {
        val collections = JSONObject()
        var count = 0
        for (name in BACKUP_COLLECTIONS) {
            // Rules aren't filters: ask only for what this person may read.
            val docs = runCatching {
                household(hid).collection(name).whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid)).get().await().documents
            }.getOrDefault(emptyList())
            if (docs.isEmpty()) continue
            val arr = JSONArray()
            docs.forEach { d -> arr.put(JSONObject().put("id", d.id).put("data", toJson(encode(d.data.orEmpty())))) }
            collections.put(name, arr)
            count += docs.size
        }
        val home = household(hid).get().await().data.orEmpty()
        val file = JSONObject()
            .put("app", "HouseAffairs").put("version", 1).put("householdId", hid)
            .put("exportedAt", System.currentTimeMillis())
            .put("household", toJson(encode(home.filterKeys { it in setOf("privateTopics", "hideCostsFrom", "baseCurrency", "name") })))
            .put("collections", collections)
        val drive = GoogleDrive(token)
        drive.upload(drive.folder(listOf(DRIVE_ROOT, DRIVE_BACKUPS)), backupFileName(LocalDateTime.now()), "application/json", file.toString().toByteArray())
        backupPhotos(drive)
        count
    }

    private val uploadedPhotos get() = context.getSharedPreferences("drive_photos", Context.MODE_PRIVATE)

    /** Photos on this phone not in Drive yet go to HouseAffairs/Photos, named by id; each only once. */
    private fun backupPhotos(drive: GoogleDrive) {
        val local = com.houseaffairs.app.core.photo.PhotoStore.dir(context).listFiles().orEmpty().filter { it.extension == "jpg" }
        val done = uploadedPhotos.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        val todo = local.filter { it.nameWithoutExtension !in done }
        if (todo.isEmpty()) return
        val folder = drive.folder(listOf(DRIVE_ROOT, com.houseaffairs.app.core.data.DRIVE_PHOTOS))
        todo.forEach { f ->
            runCatching { drive.upload(folder, f.name, "image/jpeg", f.readBytes()) }.onSuccess { done += f.nameWithoutExtension }
        }
        uploadedPhotos.edit().putStringSet("ids", done).apply()
    }

    /** Brings back photos the restored entries mention that this phone doesn't have. */
    private fun restorePhotos(drive: GoogleDrive, ids: Collection<String>) {
        val missing = ids.filter { !com.houseaffairs.app.core.photo.PhotoStore.has(context, it) }
        if (missing.isEmpty()) return
        val folder = drive.folder(listOf(DRIVE_ROOT, com.houseaffairs.app.core.data.DRIVE_PHOTOS))
        val done = uploadedPhotos.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        missing.forEach { id ->
            runCatching {
                val f = drive.find(folder, "$id.jpg") ?: return@forEach
                com.houseaffairs.app.core.photo.PhotoStore.file(context, id).writeBytes(drive.download(f.id))
                done += id
            }
        }
        uploadedPhotos.edit().putStringSet("ids", done).apply()
    }

    suspend fun backups(token: String): List<DriveBackup> = withContext(Dispatchers.IO) {
        val drive = GoogleDrive(token)
        drive.list(drive.folder(listOf(DRIVE_ROOT, DRIVE_BACKUPS))).mapNotNull { f ->
            com.houseaffairs.app.core.data.backupLabel(f.name)?.let { DriveBackup(f.id, f.name, it) }
        }
    }

    /**
     * Puts every entry in the backup back as it was. Entries added since stay; entries deleted
     * since come back. Returns how many were written.
     */
    suspend fun restore(token: String, backupId: String, hid: String, uid: String): Int = withContext(Dispatchers.IO) {
        val json = JSONObject(GoogleDrive(token).download(backupId).toString(Charsets.UTF_8))
        require(json.optString("app") == "HouseAffairs") { "That file isn't a HouseAffairs backup." }
        val collections = json.getJSONObject("collections")
        var written = 0
        val photos = mutableSetOf<String>()
        for (name in collections.keys()) {
            if (name !in BACKUP_COLLECTIONS) continue
            val col = household(hid).collection(name)
            val existing = runCatching {
                col.whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid)).get().await().documents.map { it.id }.toSet()
            }.getOrDefault(emptySet())
            val arr = collections.getJSONArray(name)
            (0 until arr.length()).map { arr.getJSONObject(it) }.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { o ->
                    @Suppress("UNCHECKED_CAST")
                    val data = decode(fromJson(o.getJSONObject("data"))) as Map<String, Any?>
                    val id = o.getString("id")
                    photos += com.houseaffairs.app.core.data.photoIdsIn(data)
                    batch.set(col.document(id), restoredDoc(data, id in existing, uid))
                }
                batch.commit().await()
                written += chunk.size
            }
        }
        restorePhotos(GoogleDrive(token), photos)
        written
    }

    /**
     * Uploads a file into HouseAffairs/Documents/<type> and saves it as a Documents entry with its
     * link, so it's listed, searchable ("where is the passport?") and opens with a tap.
     */
    suspend fun uploadDocument(
        token: String, hid: String, uid: String, uri: Uri, title: String, docType: String, shareByLink: Boolean, visibleTo: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read that file")
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val original = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val ext = original?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && it.length <= 5 }
        val drive = GoogleDrive(token)
        val file = drive.upload(drive.folder(documentFolders(docType)), title.trim() + (ext?.let { ".$it" } ?: ""), mime, bytes)
        if (shareByLink) drive.shareByLink(file.id)
        household(hid).collection("keptAt").add(
            mapOf(
                "type" to "KEPT", "title" to title.trim(), "kind" to "Documents", "docType" to docType,
                "location" to driveLocation(docType), "driveLink" to file.link, "driveFileId" to file.id,
                "createdBy" to uid, "visibleTo" to visibleTo, "createdAt" to FieldValue.serverTimestamp(), "source" to "drive",
            ),
        ).await()
        file.link.orEmpty()
    }

    private fun encode(v: Any?): Any? = BackupCodec.encode(v) { x ->
        when (x) {
            is Timestamp -> mapOf("__time" to x.toDate().time)
            is Date -> mapOf("__time" to x.time)
            is GeoPoint -> mapOf("__geo" to listOf(x.latitude, x.longitude))
            else -> null
        }
    }

    private fun decode(v: Any?): Any? = BackupCodec.decode(v) { m ->
        when {
            m.containsKey("__time") -> Timestamp(Date((m["__time"] as Number).toLong()))
            m.containsKey("__geo") -> (m["__geo"] as List<*>).let { GeoPoint((it[0] as Number).toDouble(), (it[1] as Number).toDouble()) }
            else -> null
        }
    }

    private fun toJson(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJson(x)) } }
        is List<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }
        else -> v
    }

    private fun fromJson(v: Any?): Any? = when (v) {
        JSONObject.NULL, null -> null
        is JSONObject -> v.keys().asSequence().associateWith { fromJson(v.get(it)) }
        is JSONArray -> (0 until v.length()).map { fromJson(v.get(it)) }
        // Firestore stores whole numbers as 64-bit.
        is Int -> v.toLong()
        else -> v
    }
}
