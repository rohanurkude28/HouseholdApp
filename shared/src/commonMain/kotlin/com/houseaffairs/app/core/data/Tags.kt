package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek

/**
 * "#goa", "#kids" said anywhere in a line: taken out before the line is read (so they never end
 * up in a title) and kept as the entry's tags, lower case. Pure, so it's tested.
 */
fun splitTags(text: String): Pair<String, List<String>> {
    val tags = mutableListOf<String>()
    val clean = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        // A tag starts at "#" that isn't inside a word ("C#" isn't a tag) and has a word after it.
        val starts = c == '#' && (i == 0 || !text[i - 1].isLetterOrDigit()) && i + 1 < text.length && text[i + 1].let { it.isLetterOrDigit() || it == '_' }
        if (!starts) { clean.append(c); i++; continue }
        var j = i + 1
        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '-')) j++
        tags += text.substring(i + 1, j).lowercase()
        i = j
    }
    return clean.toString().replace(Regex("\\s+"), " ").trim() to tags.distinct()
}

/**
 * Tags worth adding to an entry, so search finds it later: the trip it's part of ("goa"), the
 * person a gift or date is for ("riya"), and the family's own tags that the words already say.
 * Ones it has are left out; at most [max]. Pure, so it's tested.
 */
fun suggestTags(values: Map<String, Any?>, known: List<String>, max: Int = 4): List<String> {
    val current = tagsOf(values["tags"]).toSet()
    fun firstWord(s: String?) = s?.lowercase()?.words()?.firstOrNull { it.length > 1 }
    val text = listOf("title", "merchant", "note", "place", "category", "subcategory", "kind", "location")
        .mapNotNull { values[it] as? String }.joinToString(" ").lowercase().words().toSet()
    return buildList {
        firstWord(values["trip"] as? String)?.let(::add)
        firstWord((values["event"] as? String)?.substringBefore("'"))?.let(::add)
        known.map { it.lowercase().trim('#') }.filter { it in text }.forEach(::add)
    }.filter { it !in current }.distinct().take(max)
}

/** What's asked of the assistant for tags: the entry in words, and the family's tags to reuse. */
fun tagPrompt(about: String, known: List<String>): String =
    "Suggest up to 4 short search tags (single lowercase words) for this household entry: \"$about\". " +
        (if (known.isNotEmpty()) "Prefer these existing family tags when they fit: ${known.take(30).joinToString(", ")}. " else "") +
        "Reply with the tags only, comma separated."

/** The assistant's reply as tags: single words, lower case, no "#", at most 4. Anything else is dropped. */
fun tagsFromAi(reply: String): List<String> =
    reply.split(',', '\n', ' ').map { it.trim().trim('#', '.', '"', '\'').lowercase() }
        .filter { it.length in 2..20 && it.all { c -> c.isLetterOrDigit() || c == '-' } }
        .distinct().take(4)

/** [value] (a tag field's list or "goa, kids" text) with [tag] added. */
fun withTag(value: Any?, tag: String): Any = when (value) {
    is List<*> -> (value.filterIsInstance<String>() + tag).distinct()
    else -> (tagsOf(value) + tag).distinct().joinToString(", ")
}
