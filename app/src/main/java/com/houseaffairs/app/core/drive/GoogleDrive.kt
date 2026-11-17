package com.houseaffairs.app.core.drive

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks Google for access to files this app puts in Drive (and nothing else). The first time,
 * Google shows a consent screen ([NeedsConsent]); after that a token comes back silently.
 */
@Singleton
class DriveAuth @Inject constructor(@ApplicationContext private val context: Context) {

    sealed interface Result {
        data class Token(val value: String) : Result
        data class NeedsConsent(val intent: PendingIntent) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun authorize(): Result = runCatching {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        when {
            result.hasResolution() -> Result.NeedsConsent(result.pendingIntent!!)
            result.accessToken != null -> Result.Token(result.accessToken!!)
            else -> Result.Failed("Google didn't return access")
        }
    }.getOrElse { Result.Failed(it.message ?: "Couldn't reach Google") }

    companion object {
        /** Only files and folders this app made: it can't see anything else in the person's Drive. */
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}

/** Google Drive returned an error; 403 often means the Drive API isn't switched on for the project. */
class DriveException(val code: Int, message: String) : Exception(message)

data class DriveFile(val id: String, val name: String, val link: String?, val createdTime: String?)

/** A thin client for the Drive v3 REST API. Blocking: call it off the main thread. */
class GoogleDrive(private val token: String) {

    /** The folder at [path] ("HouseAffairs", "Documents", "Medical"), made where missing; returns its id. */
    fun folder(path: List<String>): String {
        var parent = "root"
        for (name in path) {
            val q = "mimeType='$FOLDER' and name='${name.replace("'", "\\'")}' and '$parent' in parents and trashed=false"
            val found = call("GET", "files?q=${enc(q)}&fields=files(id)&pageSize=1")?.optJSONArray("files")?.optJSONObject(0)?.optString("id")
            parent = found ?: call("POST", "files?fields=id", JSONObject().put("name", name).put("mimeType", FOLDER).put("parents", JSONArray().put(parent)))!!.getString("id")
        }
        return parent
    }

    /** Uploads [bytes] as [name] into [folderId]; returns the file with its "open in Drive" link. */
    fun upload(folderId: String, name: String, mime: String, bytes: ByteArray): DriveFile {
        val boundary = "houseaffairs" + System.nanoTime()
        val meta = JSONObject().put("name", name).put("parents", JSONArray().put(folderId)).toString()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--".toByteArray())
        }.toByteArray()
        val json = request(
            "POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink,createdTime",
            body, "multipart/related; boundary=$boundary",
        )!!.let(::JSONObject)
        return file(json)
    }

    /** Files in [folderId], newest first. */
    fun list(folderId: String): List<DriveFile> {
        val q = "'$folderId' in parents and trashed=false"
        val arr = call("GET", "files?q=${enc(q)}&orderBy=createdTime%20desc&fields=files(id,name,webViewLink,createdTime)&pageSize=50")?.optJSONArray("files") ?: return emptyList()
        return (0 until arr.length()).map { file(arr.getJSONObject(it)) }
    }

    /** The file called [name] in [folderId], if there is one. */
    fun find(folderId: String, name: String): DriveFile? {
        val q = "'$folderId' in parents and name='${name.replace("'", "\\'")}' and trashed=false"
        return call("GET", "files?q=${enc(q)}&fields=files(id,name,webViewLink,createdTime)&pageSize=1")?.optJSONArray("files")?.optJSONObject(0)?.let(::file)
    }

    fun download(fileId: String): ByteArray = request("GET", "$API/files/${enc(fileId)}?alt=media", null, null, raw = true)!!.toByteArray(Charsets.ISO_8859_1)

    /** Lets anyone with the link open the file (read only), so the family can tap it too. */
    fun shareByLink(fileId: String) {
        call("POST", "files/${enc(fileId)}/permissions", JSONObject().put("type", "anyone").put("role", "reader"))
    }

    private fun file(j: JSONObject) = DriveFile(j.getString("id"), j.optString("name"), j.optString("webViewLink").ifEmpty { null }, j.optString("createdTime").ifEmpty { null })

    private fun call(method: String, path: String, body: JSONObject? = null): JSONObject? =
        request(method, "$API/$path", body?.toString()?.toByteArray(), if (body != null) "application/json; charset=utf-8" else null)?.let { if (it.isBlank()) null else JSONObject(it) }

    private fun request(method: String, url: String, body: ByteArray?, contentType: String?, raw: Boolean = false): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
                throw DriveException(code, if ("accessNotConfigured" in err || "has not been used" in err) "Google Drive isn't switched on for this app yet." else "Google Drive said no ($code).")
            }
            // Downloads keep their bytes as-is: ISO-8859-1 maps each byte to one char and back.
            return if (raw) c.inputStream.readBytes().toString(Charsets.ISO_8859_1) else c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val FOLDER = "application/vnd.google-apps.folder"
    }
}
