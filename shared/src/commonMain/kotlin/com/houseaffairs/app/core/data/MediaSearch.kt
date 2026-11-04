package com.houseaffairs.app.core.data

/**
 * Searching movies, shows, books and music by what someone typed or said, which is often a bit
 * off ("batman dark night"). OMDb only matches close titles, so a few variations are tried and the
 * results ranked by how many of the person's words they share. Pure parts, so they're tested.
 */
data class MediaHit(
    val title: String,
    val year: String?,
    /** "Movie", "Show", "Book" or "Song". */
    val kind: String,
    val poster: String?,
    val creator: String? = null,
    val imdbId: String? = null,
)

/** Words people mishear or misspell in titles, and what they usually mean. */
private val SPELLINGS = mapOf("night" to "knight", "knite" to "knight", "lord of ring" to "lord of the rings", "harry porter" to "harry potter", "avenger" to "avengers")

/** The query as typed, with common misspellings fixed, then with one word left out at a time. */
fun searchVariants(query: String): List<String> {
    val q = query.trim().lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
    val fixed = SPELLINGS.entries.fold(q) { acc, (from, to) -> acc.replace(Regex("\\b${Regex.escape(from)}\\b"), to) }
    val words = fixed.split(" ").filter { it.isNotEmpty() }
    val dropOne = if (words.size >= 3) words.indices.map { i -> words.filterIndexed { j, _ -> j != i }.joinToString(" ") } else emptyList()
    return (listOf(q, fixed) + dropOne).filter { it.isNotBlank() }.distinct()
}

private val TITLE_FILLER = setOf("the", "a", "an", "of", "and", "part", "to")

/**
 * Ranks hits by the query's words (misspellings fixed) their title shares, minus title words the
 * person didn't say: "The Dark Knight" beats "The Dark Knight Returns, Part 1". Ties keep their order.
 */
fun rankHits(query: String, hits: List<MediaHit>): List<MediaHit> {
    val words = searchVariants(query).getOrElse(1) { query.lowercase() }.split(" ").filter { it.length > 2 }.toSet()
    fun score(h: MediaHit): Int {
        val title = h.title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it !in TITLE_FILLER && it.toIntOrNull() == null }
        return words.count { w -> title.any { it.startsWith(w) } } - 2 * title.count { t -> words.none { t.startsWith(it) } }
    }
    return hits.distinctBy { it.title.lowercase() to it.year }
        .withIndex()
        .sortedWith(compareByDescending<IndexedValue<MediaHit>> { score(it.value) }.thenBy { it.index })
        .map { it.value }
}

/** Open Library first; a missing cover is filled from Google Books' same title, then Google-only titles follow. */
fun mergeBooks(openLibrary: List<MediaHit>, google: List<MediaHit>): List<MediaHit> {
    fun key(h: MediaHit) = h.title.lowercase().filter { it.isLetterOrDigit() }
    val covers = google.filter { it.poster != null }.associateBy(::key)
    val filled = openLibrary.map { if (it.poster == null) it.copy(poster = covers[key(it)]?.poster) else it }
    val seen = filled.map(::key).toSet()
    return filled + google.filter { key(it) !in seen }
}
