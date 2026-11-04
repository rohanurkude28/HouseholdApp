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

/** Which private topic an entry is on, from its collection, name and category, or null. */
fun privateTopicOf(collection: String, title: String, category: String?): String? {
    val text = title.lowercase()
    TOPIC_WORDS.forEach { (topic, words) -> if (words.any { Regex("\\b${Regex.escape(it)}(?:s|es|ive|ives|ion)?\\b").containsMatchIn(text) }) return topic }
    return when {
        collection == "transactions" && category == "Health" -> "Health spending"
        category == "Cosmetics & skincare" -> "Cosmetics & skincare"
        else -> null
    }
}

/** Who sees an entry on a private topic: the chosen people plus whoever adds it. */
fun privateVisibleTo(allowed: List<String>, creator: String): List<String> = (allowed + creator).distinct()
