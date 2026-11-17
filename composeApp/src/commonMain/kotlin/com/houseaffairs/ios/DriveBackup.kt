package com.houseaffairs.ios

import com.houseaffairs.app.core.data.BACKUP_COLLECTIONS
import com.houseaffairs.app.core.data.DRIVE_BACKUPS
import com.houseaffairs.app.core.data.DRIVE_ROOT
import com.houseaffairs.app.core.data.backupData
import com.houseaffairs.app.core.data.backupFileName
import com.houseaffairs.app.core.data.backupLabel
import com.houseaffairs.app.core.data.collection
import com.houseaffairs.app.core.data.restoredDoc
import com.houseaffairs.app.core.data.restoredValue
import com.houseaffairs.app.core.data.visible
import com.houseaffairs.app.core.time.nowMillis
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.datetime.toLocalDateTime

/**
 * Backups of the family's entries into the person's Google Drive, the same file as Android's
 * (HouseAffairs/Backups/houseaffairs-backup-….json), so a backup made on one phone restores on
 * the other. Photos stay on the phone that took them, so the iPhone backs up entries only.
 */
private const val DRIVE = "https://www.googleapis.com/drive/v3"
private const val FOLDER = "application/vnd.google-apps.folder"

/** A backup in Drive, with a readable date. */
internal data class Backup(val fileId: String, val label: String)

private suspend fun driveToken(): String = (googleToken ?: error("Google isn't available here"))(DRIVE_SCOPE)

/** The folder at [path], made where missing; its id. */
private suspend fun folder(token: String, path: List<String>): String {
    var parent = "root"
    for (name in path) {
        val q = "mimeType='$FOLDER' and name='${name.replace("'", "\\'")}' and '$parent' in parents and trashed=false"
        val found = parseJson(googleCall(token, "GET", "$DRIVE/files?q=${enc(q)}&fields=files(id)&pageSize=1"))["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        parent = found ?: parseJson(
            googleCall(token, "POST", "$DRIVE/files?fields=id", buildJsonObject { put("name", name); put("mimeType", FOLDER); put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parent)) }) }.toString()),
        )["id"]!!.jsonPrimitive.content
    }
    return parent
}

/** Writes every entry this person can see into one JSON file in HouseAffairs/Backups; how many. */
internal suspend fun backUp(s: SessionState.Ready): Int {
    val token = driveToken()
    val (count, file) = backupFile(s)
    val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val name = backupFileName(today(), now.hour, now.minute)
    val parent = folder(token, listOf(DRIVE_ROOT, DRIVE_BACKUPS))
    val boundary = "houseaffairs" + nowMillis()
    val meta = buildJsonObject { put("name", name); put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parent)) }) }.toString()
    val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n$file\r\n--$boundary--"
    googleCall(token, "POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", bytes = body.encodeToByteArray(), type = "multipart/related; boundary=$boundary")
    savePref("driveLast", nowMillis().toString())
    return count
}

/** The backup file's text and how many entries are in it (Android's format). */
internal suspend fun backupFile(s: SessionState.Ready): Pair<Int, String> {
    val hid = s.household.id
    var count = 0
    val collections = mutableMapOf<String, Any?>()
    for (name in BACKUP_COLLECTIONS) {
        // Rules aren't filters: ask only for what this person may read.
        val docs = runCatching { visible(hid, name, s.uid).get().documents }.getOrDefault(emptyList())
        if (docs.isEmpty()) continue
        collections[name] = docs.map { d -> mapOf("id" to d.id, "data" to d.backupData()) }
        count += docs.size
    }
    val home = runCatching { Firebase.firestore.collection("households").document(hid).get().backupData() }.getOrDefault(emptyMap())
    val file = toJson(
        mapOf(
            "app" to "HouseAffairs", "version" to 1, "householdId" to hid, "exportedAt" to nowMillis(),
            "household" to home.filterKeys { it in setOf("privateTopics", "hideCostsFrom", "baseCurrency", "name") },
            "collections" to collections,
        ),
    ).toString()
    return count to file
}

/** Backups in Drive, newest first. */
internal suspend fun backups(): List<Backup> {
    val token = driveToken()
    val parent = folder(token, listOf(DRIVE_ROOT, DRIVE_BACKUPS))
    val q = "'$parent' in parents and trashed=false"
    return parseJson(googleCall(token, "GET", "$DRIVE/files?q=${enc(q)}&orderBy=createdTime%20desc&fields=files(id,name)&pageSize=50"))["files"]?.jsonArray.orEmpty().mapNotNull { f ->
        val o = f.jsonObject
        val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        backupLabel(name)?.let { Backup(o["id"]!!.jsonPrimitive.content, it) }
    }
}

/** Puts every entry in the backup back as it was: entries added since stay, deleted ones come back. How many. */
internal suspend fun restore(s: SessionState.Ready, backup: Backup): Int {
    val token = driveToken()
    val json = parseJson(googleCall(token, "GET", "$DRIVE/files/${enc(backup.fileId)}?alt=media"))
    require(json["app"]?.jsonPrimitive?.contentOrNull == "HouseAffairs") { "That file isn't a HouseAffairs backup." }
    val hid = s.household.id
    var written = 0
    for ((name, list) in (json["collections"] as? JsonObject).orEmpty()) {
        if (name !in BACKUP_COLLECTIONS) continue
        val existing = runCatching { visible(hid, name, s.uid).get().documents.map { it.id }.toSet() }.getOrDefault(emptySet())
        list.jsonArray.chunked(400).forEach { chunk ->
            val batch = Firebase.firestore.batch()
            chunk.forEach { e ->
                val o = e.jsonObject
                val id = o["id"]!!.jsonPrimitive.content
                @Suppress("UNCHECKED_CAST")
                val data = restoredValue(fromJson(o["data"]!!)) as Map<String, Any?>
                batch.set(collection(hid, name).document(id), restoredDoc(data, id in existing, s.uid))
            }
            batch.commit()
            written += chunk.size
        }
    }
    return written
}

/** A file's type from its name, for Drive. */
private fun mimeOf(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "heic" -> "image/heic"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    else -> "application/octet-stream"
}

/**
 * Uploads a file into HouseAffairs/Documents/<type> and saves it as a Documents entry with its
 * link, as Android's `uploadDocument`, so it's listed, found by "where is…?" and opens with a tap.
 */
internal suspend fun uploadDocument(s: SessionState.Ready, bytes: ByteArray, fileName: String, title: String, docType: String, byLink: Boolean, visibleTo: List<String>) {
    val token = driveToken()
    val parent = folder(token, com.houseaffairs.app.core.data.documentFolders(docType))
    val ext = fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }
    val boundary = "houseaffairs" + nowMillis()
    val meta = buildJsonObject { put("name", title.trim() + (ext?.let { ".$it" } ?: "")); put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parent)) }) }.toString()
    val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: ${mimeOf(fileName)}\r\n\r\n".encodeToByteArray() +
        bytes + "\r\n--$boundary--".encodeToByteArray()
    val file = parseJson(googleCall(token, "POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink", bytes = body, type = "multipart/related; boundary=$boundary"))
    val id = file["id"]!!.jsonPrimitive.content
    if (byLink) googleCall(token, "POST", "$DRIVE/files/${enc(id)}/permissions", buildJsonObject { put("type", "anyone"); put("role", "reader") }.toString())
    collection(s.household.id, "keptAt").add(
        mapOf(
            "type" to "KEPT", "title" to title.trim(), "kind" to "Documents", "docType" to docType,
            "location" to com.houseaffairs.app.core.data.driveLocation(docType), "driveLink" to file["webViewLink"]?.jsonPrimitive?.contentOrNull, "driveFileId" to id,
            "createdBy" to s.uid, "visibleTo" to visibleTo, "createdAt" to dev.gitlive.firebase.firestore.FieldValue.serverTimestamp, "source" to "drive",
        ),
    )
}
