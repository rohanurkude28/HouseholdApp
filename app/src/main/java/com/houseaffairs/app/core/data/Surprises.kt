package com.houseaffairs.app.core.data

/**
 * Gift ideas and surprise trips must not be seen by the person they're for. Rules aren't
 * filters, so the entry is saved visible to everyone else instead. Pure, so it's tested.
 */

/** Who an entry is a surprise for: a gift idea's "For", or a trip's "Keep it a surprise from". */
fun surpriseFor(collection: String, isGift: Boolean, values: Map<String, Any?>): List<String> {
    val key = when {
        collection == "listItems" && isGift -> "forWhom"
        collection == "trips" -> "surpriseFor"
        else -> return emptyList()
    }
    return (values[key] as? List<*>).orEmpty().filterIsInstance<String>()
}

/**
 * [visibleTo] without the people it's a surprise for. "Everyone" becomes the other [members] by
 * name, since `*` can't leave someone out. Whoever adds it always sees it, even a gift for themself.
 */
fun hiddenFrom(visibleTo: List<String>, members: List<String>, surpriseFor: List<String>, creator: String, everyone: String = "*"): List<String> {
    val keepOut = surpriseFor.toSet() - creator
    if (keepOut.isEmpty()) return visibleTo
    val base = if (everyone in visibleTo) members else visibleTo
    return (listOf(creator) + base.filter { it !in keepOut && it != everyone }).distinct()
}
