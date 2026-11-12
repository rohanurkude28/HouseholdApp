package com.houseaffairs.app.core.data

import com.houseaffairs.app.feature.quickadd.ListTemplate
import java.net.URI
import java.net.URLEncoder

/** A one-tap way out: "IMDb", "▶ Trailer", "Goodreads". Android opens the right app when it's installed. */
data class ItemLink(val label: String, val url: String)

private fun q(text: String) = URLEncoder.encode(text.trim(), "UTF-8")

/** "▶ YouTube", "Instagram", "Map" or the site's name, for a link someone saved. */
fun linkLabel(url: String): String {
    val host = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return "Open link"
    return when {
        host.endsWith("youtube.com") || host == "youtu.be" -> "▶ YouTube"
        host.endsWith("instagram.com") -> "Instagram"
        host.endsWith("facebook.com") || host == "fb.watch" -> "Facebook"
        host.endsWith("pinterest.com") || host == "pin.it" -> "Pinterest"
        host.contains("maps.google") || host == "maps.app.goo.gl" || host == "goo.gl" -> "Map"
        host.startsWith("amazon.") || host == "amzn.to" || host == "amzn.in" -> "Amazon"
        host.endsWith("imdb.com") -> "IMDb"
        host.endsWith("goodreads.com") -> "Goodreads"
        else -> host.substringBeforeLast('.').substringAfterLast('.').replaceFirstChar { it.uppercaseChar() }.ifEmpty { "Open link" }
    }
}

/** Only real web links are offered; a typo'd or empty one isn't. */
private fun saved(url: String?): ItemLink? = url?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { ItemLink(linkLabel(it), it) }

/**
 * Where a movie, show, book or song can be read about, watched or played: the family's own saved
 * link first, then the usual places. Books go to Goodreads' website (its API is closed, its pages
 * aren't). Pure, so it's tested.
 */
fun mediaLinks(template: ListTemplate, title: String, year: String? = null, creator: String? = null, imdbId: String? = null, savedLink: String? = null): List<ItemLink> {
    val named = listOfNotNull(title, creator).joinToString(" ")
    val links = when (template) {
        ListTemplate.WATCH -> listOf(
            ItemLink("IMDb", imdbId?.takeIf { it.startsWith("tt") }?.let { "https://www.imdb.com/title/$it/" } ?: "https://www.imdb.com/find/?q=${q(listOfNotNull(title, year).joinToString(" "))}"),
            ItemLink("▶ Trailer", "https://www.youtube.com/results?search_query=${q(listOfNotNull(title, year, "trailer").joinToString(" "))}"),
            ItemLink("Where to watch", "https://www.justwatch.com/in/search?q=${q(title)}"),
        )
        ListTemplate.BOOKS -> listOf(
            ItemLink("Goodreads", "https://www.goodreads.com/search?q=${q(named)}"),
            ItemLink("Google Books", "https://www.google.com/search?tbm=bks&q=${q(named)}"),
        )
        ListTemplate.MUSIC -> listOf(
            ItemLink("▶ YouTube Music", "https://music.youtube.com/search?q=${q(named)}"),
            ItemLink("Spotify", "https://open.spotify.com/search/${q(named).replace("+", "%20")}"),
        )
        else -> emptyList()
    }
    return listOfNotNull(saved(savedLink)) + links.filter { l -> saved(savedLink)?.label != l.label }
}

/** A dish: its saved reel or recipe page, and a YouTube search to watch how it's made. */
fun recipeLinks(title: String, savedLink: String?): List<ItemLink> =
    listOfNotNull(saved(savedLink)) + if (saved(savedLink)?.label == "▶ YouTube") emptyList() else listOf(ItemLink("▶ How to cook", "https://www.youtube.com/results?search_query=${q("$title recipe")}"))

/** A wish (trip, place to eat, thing to buy, gift): its saved link, if any. */
fun wishLinks(savedLink: String?): List<ItemLink> = listOfNotNull(saved(savedLink))
