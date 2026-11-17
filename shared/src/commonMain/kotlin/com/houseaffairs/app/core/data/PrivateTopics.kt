package com.houseaffairs.app.core.data

/**
 * Sensitive topics the owner can make private. A new entry on a private topic is saved visible
 * only to the chosen people (and whoever adds it), using the same `visibleTo` the security rules
 * already enforce. Pure, so the matching is tested.
 */
val PRIVATE_TOPICS = listOf("Period care", "Sexual wellness", "Health spending", "Cosmetics & skincare")

private val TOPIC_WORDS = mapOf(
    "Period care" to listOf("pad", "pads", "sanitary", "tampon", "menstrual", "period", "panty liner", "whisper", "stayfree"),
    "Sexual wellness" to listOf("condom", "lube", "lubricant", "contracept", "pregnancy test", "i-pill", "ipill", "morning after", "durex", "manforce"),
)

/**
 * Which private topic an entry is on, from its collection, name and category, or null. [chosen]
 * are the categories the owner made private (any kitchen or supply category), matched by name.
 */
fun privateTopicOf(collection: String, title: String, category: String?, chosen: Collection<String> = emptyList()): String? {
    val text = title.lowercase()
    TOPIC_WORDS.forEach { (topic, words) -> if (words.any { Regex("\\b${Regex.escape(it)}(?:s|es|ive|ives|ion)?\\b").containsMatchIn(text) }) return topic }
    val c = category?.trim().orEmpty()
    chosen.firstOrNull { c.isNotEmpty() && it.equals(c, ignoreCase = true) && it !in PRIVATE_TOPICS }?.let { return it }
    return when {
        collection == "transactions" && category == "Health" -> "Health spending"
        category == "Cosmetics & skincare" -> "Cosmetics & skincare"
        else -> null
    }
}

/**
 * What the owner can make private: the sensitive topics, then the categories the family uses in
 * the kitchen and supplies, then the usual ones not used yet. Each once, so a typed-in supply
 * type ("Baby care") can be made private too.
 */
fun privateChoices(inUse: List<String>, usual: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return (PRIVATE_TOPICS + inUse + usual).map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("Other", ignoreCase = true) && seen.add(it.lowercase()) }
}

/** Who still sees a private topic once it's hidden from [hidden]: everyone else, and always [me]. */
fun seenByAllBut(members: List<String>, hidden: Collection<String>, me: String): List<String> = (members.filter { it !in hidden } + me).distinct()

/** Who sees an entry on a private topic: the chosen people plus whoever adds it. */
fun privateVisibleTo(allowed: List<String>, creator: String): List<String> = (allowed + creator).distinct()
