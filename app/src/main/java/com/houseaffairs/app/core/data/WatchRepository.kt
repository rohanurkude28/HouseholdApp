package com.houseaffairs.app.core.data

import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.BuildConfig
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import com.houseaffairs.app.feature.quickadd.ListTemplate

data class WatchItem(
    val id: String,
    val title: String,
    /** What the family picked ("Netflix", "Theatre"…), if anything. */
    val platform: String?,
    val rating: Double?,
    val ratingSource: String?,
    val poster: String?,
    /** Where it streams in India (from TMDB / JustWatch data), or the show's network. */
    val providers: List<String>,
    val kind: String?,
    val year: String?,
    val lookedUp: Boolean,
    /** Movies & shows, books or music. */
    val template: ListTemplate = ListTemplate.WATCH,
    /** IMDb rating ("8.6") and Rotten Tomatoes score ("93%"), from OMDb when a key is set. */
    val imdb: String? = null,
    val rottenTomatoes: String? = null,
    val status: String = "IDEA",
    val votes: List<String> = emptyList(),
    /** Author or artist, for books and music. */
    val creator: String? = null,
) {
    /** The best "where to watch" text: the family's pick, else what the lookup found. */
    val whereToWatch: String? get() = platform ?: providers.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/**
 * The media lists (movies & shows, books, music), with covers, ratings and where to find them
 * looked up once and saved. TMDB (needs a free key) covers movies and Indian streaming; TVMaze,
 * Open Library and iTunes need no key.
 */
@Singleton
class WatchRepository @Inject constructor(
    private val db: FirebaseFirestore,
) {
    private fun items(householdId: String) = db.collection("households").document(householdId).collection("listItems")

    fun observe(householdId: String, uid: String): Flow<List<WatchItem>> =
        items(householdId).whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid)).observeSnapshots()
            .map { snap ->
                snap.documents
                    .filter { ListTemplate.of(it.getString("template"))?.isMedia == true && it.getString("status") != "DONE" }
                    .map { d ->
                        WatchItem(
                            template = ListTemplate.of(d.getString("template")) ?: ListTemplate.WATCH,
                            creator = d.getString("creator"),
                            id = d.id,
                            title = d.getString("title").orEmpty(),
                            platform = d.getString("platform"),
                            rating = d.getDouble("rating"),
                            ratingSource = d.getString("ratingSource"),
                            poster = d.getString("poster"),
                            providers = (d.get("providers") as? List<*>).orEmpty().filterIsInstance<String>(),
                            kind = d.getString("kind"),
                            year = d.getString("year"),
                            lookedUp = d.getBoolean("lookedUp") == true,
                            imdb = d.getString("imdbRating"),
                            rottenTomatoes = d.getString("rtScore"),
                            status = d.getString("status") ?: "IDEA",
                            votes = (d.get("votes") as? List<*>).orEmpty().filterIsInstance<String>(),
                        )
                    }
                    .sortedByDescending { it.rating ?: 0.0 }
            }
            .catch { emit(emptyList()) }

    /** Fills in rating, poster and providers for items not looked up yet. Safe to call often. */
    suspend fun enrich(householdId: String, list: List<WatchItem>) = withContext(Dispatchers.IO) {
        list.filter { !it.lookedUp }.take(8).forEach { item ->
            val found = when (item.template) {
                ListTemplate.BOOKS -> runCatching { openLibrary(item.title) }.getOrNull()
                ListTemplate.MUSIC -> runCatching { itunes(item.title) }.getOrNull()
                else -> runCatching { if (BuildConfig.TMDB_API_KEY.isNotBlank()) tmdb(item.title) else null }.getOrNull()
                    ?: runCatching { tvmaze(item.title) }.getOrNull()
            }
            val fields = mutableMapOf<String, Any?>("lookedUp" to true)
            found?.let { f ->
                fields["rating"] = f.rating
                fields["ratingSource"] = f.source
                fields["poster"] = f.poster
                fields["providers"] = f.providers
                fields["kind"] = f.kind
                fields["year"] = f.year
                f.creator?.let { fields["creator"] = it }
            }
            items(householdId).document(item.id).update(fields)
        }
        // IMDb and Rotten Tomatoes, once per movie or show, when there's an OMDb key.
        if (BuildConfig.OMDB_API_KEY.isNotBlank()) {
            list.filter { it.template == ListTemplate.WATCH && it.imdb == null && it.rottenTomatoes == null }.take(8).forEach { item ->
                val scores = runCatching { omdb(item.title, item.year) }.getOrNull() ?: return@forEach
                items(householdId).document(item.id).update(scores)
            }
        }
    }

    /** IMDb rating and Rotten Tomatoes score for a title; "N/A" ratings are left out. */
    private fun omdb(title: String, year: String?): Map<String, Any?>? {
        val q = java.net.URLEncoder.encode(title, "UTF-8")
        val json = get("https://www.omdbapi.com/?apikey=${BuildConfig.OMDB_API_KEY}&t=$q" + (year?.let { "&y=$it" } ?: "")) ?: return null
        if (json.optString("Response") != "True") return mapOf("imdbRating" to "", "rtScore" to "")
        val rt = json.optJSONArray("Ratings")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("Source") == "Rotten Tomatoes" }?.optString("Value") }
        return mapOf("imdbRating" to json.optString("imdbRating").takeIf { it != "N/A" }.orEmpty(), "rtScore" to rt.orEmpty())
    }

    /** Returns an undo that puts the item back on its list. */
    fun markWatched(householdId: String, id: String): () -> Unit {
        val ref = items(householdId).document(id)
        ref.update(mapOf("status" to "DONE", "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
        return { ref.update("status", "IDEA") }
    }

    private data class Found(val rating: Double?, val source: String, val poster: String?, val providers: List<String>, val kind: String, val year: String?, val creator: String? = null)

    private fun get(url: String): JSONObject? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = 6_000
        return try {
            if (c.responseCode != 200) null else JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun tmdb(title: String): Found? {
        val key = BuildConfig.TMDB_API_KEY
        val hit = get("https://api.themoviedb.org/3/search/multi?api_key=$key&query=${enc(title)}&region=IN")
            ?.optJSONArray("results")?.let { arr -> (0 until arr.length()).map(arr::getJSONObject).firstOrNull { it.optString("media_type") in setOf("movie", "tv") } }
            ?: return null
        val type = hit.getString("media_type")
        val providers = get("https://api.themoviedb.org/3/$type/${hit.getInt("id")}/watch/providers?api_key=$key")
            ?.optJSONObject("results")?.optJSONObject("IN")
            ?.let { india -> listOf("flatrate", "free", "ads", "rent").flatMap { k -> india.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("provider_name") } }.orEmpty() } }
            ?.distinct()?.take(3).orEmpty()
        return Found(
            rating = hit.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
            source = "TMDB",
            poster = hit.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/w342$it" },
            providers = providers,
            kind = if (type == "tv") "Show" else "Movie",
            year = (hit.optString("release_date").ifBlank { hit.optString("first_air_date") }).take(4).ifBlank { null },
        )
    }

    private fun tvmaze(title: String): Found? {
        val show = get("https://api.tvmaze.com/singlesearch/shows?q=${enc(title)}") ?: return null
        // TVMaze returns its best guess even for movies; only trust a close name match.
        if (!sameIngredient(show.optString("name"), title)) return null
        val network = show.optJSONObject("webChannel")?.optString("name") ?: show.optJSONObject("network")?.optString("name")
        return Found(
            rating = show.optJSONObject("rating")?.optDouble("average")?.takeIf { !it.isNaN() },
            source = "TVMaze",
            poster = show.optJSONObject("image")?.optString("medium"),
            providers = listOfNotNull(network?.takeIf { it.isNotBlank() }),
            kind = "Show",
            year = show.optString("premiered").take(4).ifBlank { null },
        )
    }

    private fun openLibrary(title: String): Found? {
        val doc = get("https://openlibrary.org/search.json?q=${enc(title)}&limit=1&fields=title,author_name,first_publish_year,cover_i,ratings_average")
            ?.optJSONArray("docs")?.optJSONObject(0) ?: return null
        return Found(
            rating = doc.optDouble("ratings_average").takeIf { !it.isNaN() }?.let { it * 2 },
            source = "Open Library",
            poster = doc.optInt("cover_i", 0).takeIf { it > 0 }?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
            providers = emptyList(),
            kind = "Book",
            year = doc.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString(),
            creator = doc.optJSONArray("author_name")?.optString(0)?.takeIf { it.isNotBlank() },
        )
    }

    private fun itunes(title: String): Found? {
        val hit = get("https://itunes.apple.com/search?term=${enc(title)}&entity=song&country=in&limit=1")
            ?.optJSONArray("results")?.optJSONObject(0) ?: return null
        return Found(
            rating = null,
            source = "iTunes",
            poster = hit.optString("artworkUrl100").takeIf { it.isNotBlank() }?.replace("100x100", "300x300"),
            // Where most Indian families listen; the song is on all of them.
            providers = listOf("Spotify", "YouTube Music"),
            kind = "Song",
            year = hit.optString("releaseDate").take(4).ifBlank { null },
            creator = hit.optString("artistName").takeIf { it.isNotBlank() },
        )
    }
}
