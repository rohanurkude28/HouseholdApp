package com.houseaffairs.app.feature.add

import com.houseaffairs.app.feature.quickadd.ListTemplate
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Something shared into the app from another app's share sheet ("Share → HouseAffairs"):
 * an Instagram reel or YouTube video becomes a recipe to try, a shop link a "buy someday" wish,
 * a Maps link a place. Anything else opens quick add with the text. Pure, so the routing is tested.
 */
fun sharedToAdd(text: String, subject: String? = null): AddRequest {
    val url = Regex("https?://\\S+").find(text)?.value
    val host = url?.let { Regex("https?://(?:www\\.|m\\.)?([^/]+)").find(it)?.groupValues?.get(1)?.lowercase() }.orEmpty()
    // Captions usually come before the link; the first line makes a fine title.
    val caption = (subject ?: text.replace(url.orEmpty(), "")).lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(60)
    return when {
        url == null -> AddRequest(preset = mapOf(QUICK_TEXT to text.trim()))
        host.endsWith("instagram.com") || host.endsWith("youtube.com") || host == "youtu.be" ->
            AddRequest(EntryType.RECIPE, mapOf("title" to (caption ?: "Recipe to try"), "link" to url))
        listOf("amazon.", "amzn.", "flipkart.", "myntra.", "ajio.", "nykaa.", "meesho.").any { host.contains(it) } ->
            AddRequest(EntryType.WISH, mapOf("template" to ListTemplate.BUY.label, "title" to (caption ?: "Something to buy"), "link" to url))
        host.startsWith("maps.") || host == "goo.gl" || host == "maps.app.goo.gl" || url.contains("google.com/maps") ->
            AddRequest(EntryType.PLACE, mapOf("title" to (caption ?: ""), "mapLink" to url))
        else -> AddRequest(preset = mapOf(QUICK_TEXT to text.trim()))
    }
}

/** Preset key that puts text in quick add instead of opening a form. */
const val QUICK_TEXT = "__quickText"

/** The latest share waiting for the app to show it; the main screen takes it once. */
object SharedInbox {
    val pending = MutableStateFlow<AddRequest?>(null)
}
