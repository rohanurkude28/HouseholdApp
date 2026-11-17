package com.houseaffairs.app.core.data

/** Best match for "where are the car keys?": every word of the question should appear in the item. */
fun findKept(items: List<KeptItem>, query: String): List<KeptItem> = bestMatches(items, query) { listOfNotNull(it.title, it.note) }

/** Something whose place is known: a kept thing, or a pantry, medicine or supply item with "Kept at". */
data class Placed(val title: String, val place: String, val note: String? = null)

/**
 * "Where is the jeera?": searches Where it's kept and every stock item that has a place, best
 * matches only, kept things first. Pure, so it's tested.
 */
fun whereIs(query: String, kept: List<KeptItem>, stock: List<PantryStock>): List<Placed> {
    val all = kept.map { Placed(it.title, it.location, it.note) } +
        stock.mapNotNull { s -> s.keptAt?.takeIf { it.isNotBlank() }?.let { Placed(s.title, it) } }
    return bestMatches(all, query) { listOfNotNull(it.title, it.note) }
}

/** The items whose [text] matches the most words of [query] ("where are the car keys" → "car", "key"). */
private fun <T> bestMatches(items: List<T>, query: String, text: (T) -> List<String>): List<T> {
    val words = query.lowercase().split(" ").map { it.trim('?', '.', ',').removeSuffix("s") }
        .filter { it.length > 1 && it !in setOf("the", "my", "our", "where", "is", "are", "kept") }
    if (words.isEmpty()) return emptyList()
    return items
        .map { item -> item to words.count { w -> text(item).any { it.lowercase().contains(w) } } }
        .filter { (_, score) -> score > 0 }
        .sortedByDescending { it.second }
        .let { scored -> scored.filter { it.second == scored.first().second } }
        .map { it.first }
}
