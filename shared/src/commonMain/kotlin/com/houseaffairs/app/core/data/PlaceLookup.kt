package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.util.urlEncode

/**
 * Looks a place up on Wikipedia (free, no key): the best match for "[name] [near]", then its
 * summary and photo. Null when there's nothing or no connection; the stop is kept either way.
 * [get] fetches a URL as text with the app's User-Agent; each phone brings its own.
 */
suspend fun lookUpPlace(name: String, near: String?, get: suspend (String) -> String): PlaceInfo? = runCatching {
    val query = listOfNotNull(name, near?.takeIf { !name.contains(it, ignoreCase = true) }).joinToString(" ")
    val search = get("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=3&srsearch=" + urlEncode(query))
    val titles = Regex("\"title\":\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(search).map { unescapeJson(it.groupValues[1]) }.toList()
    // What it is from the best match; a photo from the first of the top three that has one.
    var best: PlaceInfo? = null
    for (title in titles) {
        val info = placeInfoFrom(get("https://en.wikipedia.org/api/rest_v1/page/summary/" + urlEncode(title.replace(' ', '_')))) ?: continue
        best = best?.let { if (it.photo == null && info.photo != null) it.copy(photo = info.photo) else it } ?: info
        if (best.photo != null) break
    }
    best
}.getOrNull()

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
