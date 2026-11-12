package com.houseaffairs.app.core.data

/**
 * "#goa", "#kids" said anywhere in a line: taken out before the line is read (so they never end
 * up in a title) and kept as the entry's tags, lower case. Pure, so it's tested.
 */
fun splitTags(text: String): Pair<String, List<String>> {
    val tag = Regex("(?<![\\p{L}\\p{N}])#([\\p{L}\\p{N}_-]+)")
    val tags = tag.findAll(text).map { it.groupValues[1].lowercase() }.distinct().toList()
    val clean = tag.replace(text, "").replace(Regex("\\s+"), " ").trim()
    return clean to tags
}
