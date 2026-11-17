package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.util.words

private val DREAM_WORDS = listOf(
    "Emergency" to listOf("emergency", "rainy", "backup", "medical"),
    "Festival" to listOf("diwali", "festival", "eid", "christmas", "holi", "pongal", "onam", "navratri"),
    "Wedding" to listOf("wedding", "marriage", "shaadi", "engagement"),
    "Education" to listOf("college", "school", "course", "fees", "education", "degree", "tuition", "study"),
    "Car" to listOf("car", "bike", "scooter", "suv", "scooty"),
    "Home" to listOf("home", "house", "flat", "apartment", "renovation", "kitchen", "sofa", "furniture"),
    "Gadget" to listOf("phone", "iphone", "laptop", "macbook", "tv", "camera", "ipad", "watch", "console", "ps5"),
    "Travel" to listOf("trip", "travel", "holiday", "vacation", "bali", "goa", "europe", "dubai", "tour", "flight"),
    "Experience" to listOf("concert", "show", "match", "skydiving", "spa", "course"),
    "Savings" to listOf("savings", "save", "fund", "gullak", "piggy"),
)

/** The kind of dream its name says ("Bali trip" → Travel, "new laptop" → Gadget); null when unclear. */
fun dreamKindOf(name: String): String? {
    val w = name.lowercase().words().toSet()
    return DREAM_WORDS.firstOrNull { (_, keys) -> keys.any { it in w } }?.first
}
