package com.houseaffairs.app.core.data

import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.BuildConfig
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    /** Set when picked from search results, so ratings come from exactly that title. */
    val imdbId: String? = null,
    /** A link the family saved (trailer, review, shop). */
    val link: String? = null,
    /** The family's own photos of it, used when nothing was looked up. */
    val photos: List<String> = emptyList(),
    /** Author or artist, for books and music. */
    val creator: String? = null,
) {
    /** The best "where to watch" text: the family's pick, else what the lookup found. */
    val whereToWatch: String? get() = platform ?: providers.takeIf { it.isNotEmpty() }?.joinToString(", ")

    /** Songs and albums open in YouTube Music (the app if installed); no key needed. */
    val youtubeUrl: String?
        get() = if (template == ListTemplate.MUSIC) {
            "https://music.youtube.com/search?q=" + java.net.URLEncoder.encode(listOfNotNull(title, creator).joinToString(" "), "UTF-8")
        } else {
            null
        }
}

/**
 * The media lists (movies & shows, books, music), with covers, ratings and where to find them
 * looked up once and saved. TMDB (needs a free key) covers movies and Indian streaming; TVMaze,
 * Open Library and iTunes need no key.
 */
@Singleton
class WatchRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val session: SessionManager,
) {
    /** The family's key from Connections (set by the owner), else the developer's local.properties one. */
    private fun key(name: String, fallback: String): String =
        (session.state.value as? SessionState.Ready)?.household?.integrations?.get(name)?.takeIf { it.isNotBlank() } ?: fallback

    private val tmdbKey get() = key("tmdb", BuildConfig.TMDB_API_KEY)
    private val omdbKey get() = key("omdb", BuildConfig.OMDB_API_KEY)

    /** Checks a key before it's saved: true if the service accepts it. */
    suspend fun testKey(service: String, key: String): Boolean = withContext(Dispatchers.IO) {
        val k = java.net.URLEncoder.encode(key.trim(), "UTF-8")
        runCatching {
            when (service) {
                "omdb" -> get("https://www.omdbapi.com/?apikey=$k&t=Inception")?.optString("Response") == "True"
                "tmdb" -> get("https://api.themoviedb.org/3/configuration?api_key=$k") != null
                else -> false
            }
        }.getOrDefault(false)
    }
    private fun items(householdId: String) = db.collection("households").document(householdId).collection("listItems")

    fun observe(householdId: String, uid: String): Flow<List<WatchItem>> =
        items(householdId).whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid)).observeSnapshots()
            .map { snap ->
                snap.documents
                    .filter { ListTemplate.of(it.getString("template"))?.isMedia == true && it.getString("status") != "DONE" }
                    // One odd document mustn't empty the whole list: skip it instead.
                    .mapNotNull { d ->
                        runCatching {
                        WatchItem(
                            template = ListTemplate.of(d.getString("template")) ?: ListTemplate.WATCH,
                            creator = d.getString("creator"),
                            id = d.id,
                            title = d.getString("title").orEmpty(),
                            platform = d.getString("platform"),
                            // Older items saved "👍 Liked it" here; only a number is a rating.
                            rating = (d.get("rating") as? Number)?.toDouble(),
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
                            imdbId = d.getString("imdbId"),
                            link = d.getString("link")?.takeIf { it.isNotBlank() },
                            photos = (d.get("photos") as? List<*>).orEmpty().filterIsInstance<String>(),
                        )
                        }.getOrNull()
                    }
                    .sortedByDescending { it.rating ?: 0.0 }
            }
            .catch { emit(emptyList()) }

    /** Fills in rating, poster and providers for items not looked up yet. Safe to call often. */
    suspend fun enrich(householdId: String, list: List<WatchItem>) = withContext(Dispatchers.IO) {
        coroutineScope { list.filter { !it.lookedUp }.take(8).map { item -> async { enrichOne(householdId, item) } }.awaitAll() }
        // IMDb and Rotten Tomatoes, once per movie or show, when there's an OMDb key.
        if (omdbKey.isNotBlank()) coroutineScope {
            list.filter { it.template == ListTemplate.WATCH && it.imdb == null && it.rottenTomatoes == null }.take(8).map { item ->
                async {
                    val scores = runCatching { omdb(item.title, item.year, item.imdbId) }.getOrNull() ?: return@async
                    items(householdId).document(item.id).update(scores)
                }
            }.awaitAll()
        }
    }

    private fun enrichOne(householdId: String, item: WatchItem) {
        val found = when (item.template) {
            ListTemplate.BOOKS -> runCatching { openLibrary(item.title) }.getOrNull()
            ListTemplate.MUSIC -> runCatching { itunes(item.title) }.getOrNull()
            else -> runCatching { if (tmdbKey.isNotBlank()) tmdb(item.title) else null }.getOrNull()
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

    /** IMDb rating and Rotten Tomatoes score for a title; "N/A" ratings are left out. */
    private fun omdb(title: String, year: String?, imdbId: String? = null): Map<String, Any?>? {
        val q = java.net.URLEncoder.encode(title, "UTF-8")
        val json = get("https://www.omdbapi.com/?apikey=$omdbKey&" + if (imdbId != null) "i=$imdbId" else "t=$q" + (year?.let { "&y=$it" } ?: "")) ?: return null
        if (json.optString("Response") != "True") return mapOf("imdbRating" to "", "rtScore" to "")
        val rt = json.optJSONArray("Ratings")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("Source") == "Rotten Tomatoes" }?.optString("Value") }
        return mapOf("imdbRating" to json.optString("imdbRating").takeIf { it != "N/A" }.orEmpty(), "rtScore" to rt.orEmpty())
    }

    /**
     * Movies, shows, books or songs matching what was typed, best first: OMDb (with a key) and
     * TVMaze for watching, Open Library for books, iTunes for music. Empty when offline.
     */
    suspend fun search(template: ListTemplate, query: String): List<MediaHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        // Every source at once rather than one after another: the slowest one sets the wait, not the sum.
        val hits = coroutineScope {
            when (template) {
                ListTemplate.BOOKS -> {
                    val ol = async { runCatching { searchBooks(query) }.getOrDefault(emptyList()) }
                    val g = async { runCatching { searchGoogleBooks(query) }.getOrDefault(emptyList()) }
                    mergeBooks(ol.await(), g.await())
                }
                ListTemplate.MUSIC -> runCatching { searchSongs(query) }.getOrDefault(emptyList())
                else -> {
                    val omdb = if (omdbKey.isBlank()) emptyList() else searchVariants(query).take(4).map { q -> async { runCatching { searchOmdb(q) }.getOrDefault(emptyList()) } }
                    val shows = async { runCatching { searchShows(query) }.getOrDefault(emptyList()) }
                    omdb.flatMap { it.await() } + shows.await()
                }
            }
        }
        rankHits(query, hits).take(6)
    }

    private fun searchOmdb(query: String): List<MediaHit> {
        val arr = get("https://www.omdbapi.com/?apikey=$omdbKey&s=${enc(query)}")?.optJSONArray("Search") ?: return emptyList()
        return (0 until arr.length()).map(arr::getJSONObject).filter { it.optString("Type") in setOf("movie", "series") }.map {
            MediaHit(
                it.optString("Title"), it.optString("Year").take(4).ifBlank { null },
                if (it.optString("Type") == "series") "Show" else "Movie",
                it.optString("Poster").takeIf { p -> p.startsWith("http") }, imdbId = it.optString("imdbID").ifBlank { null },
            )
        }
    }

    private fun searchShows(query: String): List<MediaHit> {
        val arr = getArray("https://api.tvmaze.com/search/shows?q=${enc(query)}") ?: return emptyList()
        return (0 until minOf(arr.length(), 4)).map { arr.getJSONObject(it).getJSONObject("show") }.map {
            MediaHit(it.optString("name"), it.optString("premiered").take(4).ifBlank { null }, "Show", it.optJSONObject("image")?.optString("medium"), imdbId = it.optJSONObject("externals")?.optString("imdb")?.ifBlank { null })
        }
    }

    private fun searchBooks(query: String): List<MediaHit> {
        val docs = get("https://openlibrary.org/search.json?q=${enc(query)}&limit=8&fields=title,author_name,first_publish_year,cover_i,ratings_average,ratings_count")?.optJSONArray("docs") ?: return emptyList()
        return (0 until docs.length()).map(docs::getJSONObject).map {
            MediaHit(
                it.optString("title"), it.optInt("first_publish_year", 0).takeIf { y -> y > 0 }?.toString(), "Book",
                it.optInt("cover_i", 0).takeIf { c -> c > 0 }?.let { c -> "https://covers.openlibrary.org/b/id/$c-M.jpg" },
                creator = it.optJSONArray("author_name")?.optString(0),
                rating = it.optDouble("ratings_average").takeIf { r -> !r.isNaN() && r > 0 }?.let { r -> r * 2 },
                ratingCount = it.optInt("ratings_count", 0).takeIf { n -> n > 0 },
            )
        }
    }

    // Goodreads closed its API in 2020; Google Books (no key needed) has covers and ratings Open
    // Library often lacks, especially for Indian editions.
    private fun searchGoogleBooks(query: String, max: Int = 8): List<MediaHit> {
        val arr = get("https://www.googleapis.com/books/v1/volumes?q=${enc(query)}&maxResults=$max&printType=books&orderBy=relevance")?.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).getJSONObject("volumeInfo") }.map {
            MediaHit(
                it.optString("title"), it.optString("publishedDate").take(4).ifBlank { null }, "Book",
                it.optJSONObject("imageLinks")?.let { l -> l.optString("thumbnail").ifBlank { l.optString("smallThumbnail") } }?.ifBlank { null }?.replace("http://", "https://")?.replace("&edge=curl", ""),
                creator = it.optJSONArray("authors")?.optString(0),
                rating = it.optDouble("averageRating").takeIf { r -> !r.isNaN() && r > 0 }?.let { r -> r * 2 },
                ratingCount = it.optInt("ratingsCount", 0).takeIf { n -> n > 0 },
            )
        }
    }

    private fun searchSongs(query: String): List<MediaHit> {
        val arr = get("https://itunes.apple.com/search?term=${enc(query)}&entity=song&country=in&limit=6")?.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map(arr::getJSONObject).map {
            MediaHit(it.optString("trackName"), it.optString("releaseDate").take(4).ifBlank { null }, "Song", it.optString("artworkUrl100").replace("100x100", "300x300").ifBlank { null }, creator = it.optString("artistName").ifBlank { null })
        }
    }

    /** Saves the picked search result onto the list item: exact title, poster, year; ratings follow. */
    fun applyHit(householdId: String, id: String, hit: MediaHit) {
        items(householdId).document(id).update(
            mapOf(
                "title" to hit.title, "poster" to hit.poster, "year" to hit.year, "kind" to hit.kind,
                "creator" to hit.creator, "imdbId" to hit.imdbId, "lookedUp" to true,
                "rating" to hit.rating, "ratingSource" to hit.rating?.let { if (hit.kind == "Book") "Books" else null },
                // Cleared so the next look-up fetches IMDb and Rotten Tomatoes for exactly this title.
                "imdbRating" to com.google.firebase.firestore.FieldValue.delete(), "rtScore" to com.google.firebase.firestore.FieldValue.delete(),
            ),
        )
    }

    /** "How was it?" after watching, reading or listening. */
    fun rate(householdId: String, id: String, liked: Boolean) {
        // Its own field: "rating" is the looked-up score, a number.
        items(householdId).document(id).update("verdict", if (liked) "👍 Liked it" else "👎 Not great")
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
        c.connectTimeout = 4_000
        c.readTimeout = 5_000
        return try {
            if (c.responseCode != 200) null else JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    /** Like [get], for endpoints that answer with a JSON list. */
    private fun getArray(url: String): org.json.JSONArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4_000
        c.readTimeout = 5_000
        return try {
            if (c.responseCode != 200) null else org.json.JSONArray(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun tmdb(title: String): Found? {
        val key = tmdbKey
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

    /** The best match from Open Library and Google Books together, so a cover or rating either lacks is filled. */
    private fun openLibrary(title: String): Found? {
        val hit = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            val ol = async { runCatching { searchBooks(title) }.getOrDefault(emptyList()) }
            val g = async { runCatching { searchGoogleBooks(title, 5) }.getOrDefault(emptyList()) }
            rankHits(title, mergeBooks(ol.await(), g.await())).firstOrNull()
        } ?: return null
        return Found(
            rating = hit.rating,
            source = "Open Library · Google Books",
            poster = hit.poster,
            providers = emptyList(),
            kind = "Book",
            year = hit.year,
            creator = hit.creator?.takeIf { it.isNotBlank() },
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
