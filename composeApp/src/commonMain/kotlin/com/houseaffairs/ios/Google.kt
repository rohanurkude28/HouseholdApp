package com.houseaffairs.ios

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** The same narrow scopes as Android's: only the calendar and files this app made. */
const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.app.created"
const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** A Google access token for a scope, asking the first time (Swift's GoogleSignIn); null in previews. */
var googleToken: (suspend (String) -> String)? = null

/** Google answered with an error; [code] 404 means the calendar or file is gone. */
class GoogleException(val code: Int, message: String) : Exception(message)

private val google by lazy { HttpClient() }

/** One Google REST call with [token]; the reply's text (empty for "no content"). */
internal suspend fun googleCall(token: String, method: String, url: String, json: String? = null, bytes: ByteArray? = null, type: String? = null): String =
    googleBytes(token, method, url, json, bytes, type).decodeToString()

internal suspend fun googleBytes(token: String, method: String, url: String, json: String? = null, bytes: ByteArray? = null, type: String? = null): ByteArray {
    val reply = google.request(url) {
        this.method = HttpMethod.parse(method)
        header("Authorization", "Bearer $token")
        when {
            json != null -> setBody(TextContent(json, ContentType.Application.Json))
            bytes != null -> setBody(ByteArrayContent(bytes, ContentType.parse(type ?: "application/octet-stream")))
        }
    }
    if (!reply.status.isSuccess()) {
        val err = runCatching { reply.bodyAsText() }.getOrDefault("")
        throw GoogleException(
            reply.status.value,
            when {
                "accessNotConfigured" in err || "has not been used" in err -> "This Google service isn't switched on for the app yet."
                reply.status.value == 401 || reply.status.value == 403 -> "Google said no: check access is allowed for this app."
                else -> "Google said no (${reply.status.value})."
            },
        )
    }
    return reply.bodyAsBytes()
}

internal fun enc(s: String) = s.encodeURLParameter()

internal val googleJson = Json { ignoreUnknownKeys = true }

internal fun parseJson(text: String): JsonObject = if (text.isBlank()) JsonObject(emptyMap()) else googleJson.parseToJsonElement(text).jsonObject

/** Plain Kotlin values (maps, lists, strings, numbers, booleans, null) as JSON, and back. */
internal fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to toJson(x) })
    is List<*> -> JsonArray(v.map(::toJson))
    else -> JsonPrimitive(v.toString())
}

internal fun fromJson(e: JsonElement): Any? = when (e) {
    is JsonNull -> null
    is JsonObject -> e.entries.associate { (k, x) -> k to fromJson(x) }
    is JsonArray -> e.map(::fromJson)
    is JsonPrimitive -> when {
        e.isString -> e.content
        e.booleanOrNull != null -> e.booleanOrNull
        // Firestore stores whole numbers as 64-bit.
        e.longOrNull != null -> e.longOrNull
        else -> e.doubleOrNull
    }
}
