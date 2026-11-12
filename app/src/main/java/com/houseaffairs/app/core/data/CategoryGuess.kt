package com.houseaffairs.app.core.data


/** Too short to mean anything yet ("dm" could be DMart or DMV). */
private const val MIN_TYPED = 3

private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

/**
 * What this family files each shop or person under, from what they've entered: the category
 * used most for that name, the latest one on a tie. "Other" teaches nothing.
 */
fun learnedCategories(txns: List<Txn>): Map<String, String> = txns
    .filter { it.title.isNotBlank() && it.category.isNotBlank() && it.category != "Other" }
    .groupBy { it.title.trim().lowercase() }
    .mapValues { (_, same) ->
        same.groupBy { it.category }
            .maxWith(compareBy({ it.value.size }, { e -> e.value.maxOf { it.date.toEpochDay() } }))
            .key
    }

/**
 * The category for what's being typed ("Where or who", then the note), or null when nothing
 * says. What the family taught wins over the built-in words, and a name still being typed
 * ("dma…") already counts once only one of their categories fits it. [known] is the parser's
 * word list (`QuickAddParser.categoryOfWord`).
 */
fun guessCategory(text: String, learned: Map<String, String> = emptyMap(), known: (String) -> String? = { null }): String? {
    val typed = text.trim().lowercase()
    if (typed.length < MIN_TYPED) return null
    learned[typed]?.let { return it }
    val typedWords = words(typed)
    // "Sharma medicals, Baner" still finds "sharma medicals".
    learned.entries.filter { (name, _) -> name.length >= MIN_TYPED && Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(typed) }
        .maxByOrNull { it.key.length }?.let { return it.value }
    learned.filterKeys { it.startsWith(typed) }.values.distinct().singleOrNull()?.let { return it }
    return typedWords.firstNotNullOfOrNull { known(it) ?: known(it.removeSuffix("s")) }
}
