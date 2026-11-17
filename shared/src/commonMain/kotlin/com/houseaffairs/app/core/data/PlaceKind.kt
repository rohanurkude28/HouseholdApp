package com.houseaffairs.app.core.data

/**
 * A place's kind from its name ("Dr. Mehta's clinic" → Doctor, "Third Wave Coffee" → Café), so
 * saving one is just its name; null when the name doesn't say. Pure, so it's tested.
 */
fun placeKindOf(name: String): String? {
    val n = name.lowercase()
    fun has(vararg words: String) = words.any { Regex("\\b${Regex.escape(it)}").containsMatchIn(n) }
    return when {
        has("hospital", "nursing home") -> "Hospital"
        has("dr", "dr.", "clinic", "dental", "dentist", "doctor", "physio", "diagnostic", "lab") -> "Doctor"
        has("pharmacy", "medical", "chemist", "apollo", "medplus", "1mg") -> "Pharmacy"
        has("cafe", "café", "coffee", "starbucks", "bakery", "tea", "chai") -> "Café"
        has("restaurant", "dhaba", "biryani", "kitchen", "bar", "grill", "pizza", "dosa", "mess", "hotel", "toit", "darshini") -> "Restaurant"
        has("mall", "forum", "phoenix", "nexus") -> "Mall"
        has("market", "mandi", "bazaar", "bazar") -> "Market"
        has("dmart", "more", "supermarket", "kirana", "grocer", "store", "mart", "spar", "nature's basket", "reliance fresh") -> "Grocery"
        has("salon", "parlour", "parlor", "spa", "barber", "naturals") -> "Salon"
        has("plumber", "electrician", "tailor", "laundry", "dry clean", "repair", "service", "carpenter", "mechanic", "garage") -> "Service"
        else -> null
    }
}

/** The kinds of place a family saves, in the order their chips show. */
val PLACE_KINDS = listOf("Restaurant", "Café", "Grocery", "Pharmacy", "Doctor", "Hospital", "Market", "Mall", "Salon", "Service", "Other")
