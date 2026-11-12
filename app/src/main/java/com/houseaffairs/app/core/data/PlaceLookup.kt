package com.houseaffairs.app.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** What a place is, in a line or two, and a photo of it. */
data class PlaceInfo(val about: String, val photo: String?)

/**
 * Looks a place up on Wikipedia (free, no key): the best match for "[name] [near]", then its
 * summary and photo. Null when there's nothing or no connection; the stop is kept either way.
 */
suspend fun lookUpPlace(name: String, near: String?): PlaceInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val query = listOfNotNull(name, near?.takeIf { !name.contains(it, ignoreCase = true) }).joinToString(" ")
        val search = get("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=3&srsearch=" + URLEncoder.encode(query, "UTF-8"))
        val titles = Regex("\"title\":\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(search).map { unescapeJson(it.groupValues[1]) }.toList()
        // What it is from the best match; a photo from the first of the top three that has one.
        var best: PlaceInfo? = null
        for (title in titles) {
            val info = placeInfoFrom(get("https://en.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8"))) ?: continue
            best = best?.let { if (it.photo == null && info.photo != null) it.copy(photo = info.photo) else it } ?: info
            if (best.photo != null) break
        }
        best
    }.getOrNull()
}

private fun get(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 6000
    c.readTimeout = 6000
    c.setRequestProperty("User-Agent", "HouseAffairs/0.1 (family planner)")
    return c.inputStream.bufferedReader().use { it.readText() }
}

/** The summary's first two sentences (at most about 220 characters) and its photo. Pure, so it's tested. */
fun placeInfoFrom(json: String): PlaceInfo? {
    val extract = Regex("\"extract\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)?.let(::unescapeJson)?.trim()
        ?.takeIf { it.isNotEmpty() } ?: return null
    val sentences = Regex("(?<=[.!?])\\s+").split(extract).take(2).joinToString(" ")
    val about = if (sentences.length > 220) sentences.take(217).substringBeforeLast(' ') + "…" else sentences
    val photo = Regex("\"thumbnail\":\\{\"source\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)?.replace("\\/", "/")
    return PlaceInfo(about, photo)
}

private fun unescapeJson(s: String): String =
    Regex("\\\\u([0-9a-fA-F]{4})").replace(s) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace("\\\"", "\"").replace("\\/", "/").replace("\\n", " ").replace("\\\\", "\\")
