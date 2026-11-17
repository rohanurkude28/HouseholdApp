package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.feature.quickadd.ListTemplate

// The family's things as both phones read them: recipes, the buy list, kept things, places,
// the watch list and the Money snapshot. Plain data, so the shared rules can use them.

data class Recipe(
    val id: String,
    val title: String,
    val ingredients: List<Ingredient>,
    val servings: Int,
    val mood: List<String>,
    val link: String?,
    /** Breakfast, Lunch, Dinner, Snack or Dessert, when known. */
    val meal: String? = null,
    /** Per serving, as "protein · carbs · fat · fibre" in grams ("25g · 30g · 12g · 4g"); approximate for library dishes. */
    val macros: String? = null,
    val photos: List<String> = emptyList(),
)

data class ShoppingEntry(val id: String, val title: String, val amount: Double?, val unit: String?, val aisleChoice: String? = null) {
    val aisle: Aisle get() = Aisle.from(aisleChoice) ?: aisleOf(title)
}

data class KeptItem(
    val id: String,
    val title: String,
    val location: String,
    val kind: String?,
    val room: String?,
    val note: String?,
    /** For documents filed in Google Drive: the folder ("Insurance") and the link that opens the file. */
    val docType: String? = null,
    val link: String? = null,
    val photos: List<String> = emptyList(),
)

data class PlaceItem(
    val id: String,
    val title: String,
    val kind: String,
    val area: String?,
    val rating: String?,
    val goodFor: String?,
    val phone: String?,
    val mapLink: String?,
    val lat: Double? = null,
    val lng: Double? = null,
)

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
            "https://music.youtube.com/search?q=" + com.houseaffairs.app.core.util.urlEncode(listOfNotNull(title, creator).joinToString(" "))
        } else {
            null
        }
}

/** Everything the Money tab and the vision board need, in one snapshot. */
data class MoneyState(
    val budget: Budget,
    val startedOn: LocalDate,
    val summary: BudgetSummary,
    val envelopes: EnvelopeState,
    val credits: List<Credit>,
    val dreams: List<DreamProgress>,
    val investments: List<InvestmentValue> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    /** Every expense since the budget started, for the monthly overview. */
    val txns: List<Txn> = emptyList(),
    /** Every move between envelopes, the gullak and dreams, for the list of moves. */
    val moves: List<Transfer> = emptyList(),
    /** Each card's closed statement (the bill) and open one (spends so far). */
    val cardBills: List<CardBill> = emptyList(),
)

/** Money that can be moved out of [pocket] right now. */
fun MoneyState.available(pocket: Pocket): Long = when (pocket) {
    Pocket.Pot -> envelopes.potMinor
    is Pocket.Category -> envelopes.envelopes.firstOrNull { it.category == pocket.name }?.availableMinor ?: 0
    is Pocket.Dream -> envelopes.dreamFunding[pocket.id] ?: 0
    is Pocket.Investment -> investments.firstOrNull { it.investment.id == pocket.id }?.currentMinor ?: 0
    Pocket.Growth -> Long.MAX_VALUE
}
