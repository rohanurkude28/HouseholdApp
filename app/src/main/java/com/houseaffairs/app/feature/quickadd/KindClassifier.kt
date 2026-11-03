package com.houseaffairs.app.feature.quickadd

import kotlin.math.exp
import kotlin.math.ln

/** What an unsure line most likely is. Other kinds have clear words the parser already catches. */
enum class LineKind(val label: String) { SHOPPING("To buy"), TASK("Task"), EVENT("Event"), MEDICINE("Medicine") }

data class KindGuess(val kind: LineKind, val confidence: Double)

/**
 * A tiny on-phone classifier (naive Bayes over words) for lines the rules can't place:
 * "peanut butter" → shopping, "dentist appointment" → event. Instant, offline, a few KB.
 * Trained on [SEED_EXAMPLES] plus the phrases the family has taught it. Pure, so it's tested.
 */
class KindClassifier(examples: List<Pair<String, LineKind>>) {
    private val wordCounts = LineKind.entries.associateWith { mutableMapOf<String, Int>() }
    private val totals = LineKind.entries.associateWith { 0 }.toMutableMap()
    private val docs = LineKind.entries.associateWith { 0 }.toMutableMap()
    private val vocabulary = mutableSetOf<String>()

    init {
        examples.forEach { (text, kind) ->
            docs[kind] = docs.getValue(kind) + 1
            phraseKey(text).split(" ").filter { it.isNotEmpty() }.forEach { w ->
                wordCounts.getValue(kind).merge(w, 1, Int::plus)
                totals[kind] = totals.getValue(kind) + 1
                vocabulary += w
            }
        }
    }

    /** The best kind, or null when no word of [text] has been seen before. */
    fun classify(text: String): KindGuess? {
        val words = phraseKey(text).split(" ").filter { it in vocabulary }
        if (words.isEmpty()) return null
        val all = docs.values.sum().coerceAtLeast(1)
        val scores = LineKind.entries.associateWith { kind ->
            val prior = ln((docs.getValue(kind) + 1.0) / (all + LineKind.entries.size))
            prior + words.sumOf { w ->
                ln((wordCounts.getValue(kind)[w] ?: 0) + 1.0) - ln(totals.getValue(kind) + vocabulary.size.toDouble())
            }
        }
        val best = scores.maxBy { it.value }
        val sum = scores.values.sumOf { exp(it - best.value) }
        return KindGuess(best.key, 1.0 / sum)
    }

    companion object {
        /** Below this, the guess isn't shown as the suggestion; the kind chips still are. */
        const val CONFIDENT = 0.6
    }
}

private val FILLER = setOf(
    "need", "needs", "needed", "buy", "get", "got", "grab", "order", "bring", "want", "some", "more", "a", "an", "the",
    "of", "to", "for", "and", "please", "add", "i", "we", "my", "our", "is", "are", "pick", "up", "new",
)
private val UNITS = setOf("g", "gm", "kg", "l", "ml", "pcs", "pack", "packs", "dozen", "units", "unit", "rolls", "boxes")

/**
 * The words that say what a line is about, for learning and lookup: "need 500g of Peanuts!" → "peanuts".
 * Lower-case, no numbers, units or filler words.
 */
fun phraseKey(text: String): String =
    text.lowercase()
        .replace(Regex("\\d+(?:\\.\\d+)?\\s?(?:g|gm|kg|l|ml|pcs)\\b"), " ")
        .split(Regex("[^\\p{L}]+"))
        .filter { it.isNotEmpty() && it !in FILLER && it !in UNITS }
        .joinToString(" ")

/** Starting knowledge; the family's corrections are added on top. */
val SEED_EXAMPLES: List<Pair<String, LineKind>> = buildList {
    listOf(
        "peanuts", "peanut butter", "atta", "toor dal", "moong dal", "onions", "tomatoes", "potatoes", "bread", "eggs",
        "curd", "ghee", "cooking oil", "sugar", "salt", "tea leaves", "coffee powder", "biscuits", "maggi", "noodles",
        "paneer", "butter", "cheese", "chicken", "fish", "basmati rice", "poha", "rava", "besan", "jaggery", "honey",
        "ketchup", "jam", "cornflakes", "oats", "almonds", "cashews", "dates", "apples", "bananas", "mangoes", "grapes",
        "garlic", "ginger", "coriander", "green chillies", "lemons", "spinach", "cauliflower", "cabbage", "carrots",
        "cucumber", "capsicum", "mushrooms", "milk packets", "yogurt", "ice cream", "juice", "soft drinks", "chips",
        "namkeen", "chocolate", "flour", "masala", "garam masala", "turmeric", "chilli powder", "mustard seeds",
        "cumin", "vinegar", "soy sauce", "pasta", "sauce", "mayonnaise", "frozen peas", "corn", "sprouts", "tofu",
    ).forEach { add(it to LineKind.SHOPPING) }
    listOf(
        "call the plumber", "fix the tap", "renew passport", "book gas cylinder", "pay electricity bill", "clean the fan",
        "service the car", "pick up dry cleaning", "submit school form", "email the school", "recharge phone",
        "iron clothes", "water the plants", "take out the trash", "send documents to ca", "get a haircut",
        "update kyc", "file taxes", "return amazon package", "get car washed", "paint the balcony", "change bedsheets",
        "sort old clothes", "get ac serviced", "pest control", "repair the fridge", "aadhaar update", "bank work",
        "pay maid salary", "get photos printed", "reply to landlord", "cancel subscription", "get bike puncture fixed",
    ).forEach { add(it to LineKind.TASK) }
    listOf(
        "parent teacher meeting", "dentist appointment", "doctor appointment", "wedding reception", "school annual day",
        "diwali party", "puja at home", "flight to delhi", "job interview", "concert", "cousin engagement", "baby shower",
        "house warming", "office party", "sports day", "exam", "vaccination", "eye checkup", "blood test",
        "holi celebration", "family dinner", "kitty party", "book club meet", "anniversary dinner", "farewell",
    ).forEach { add(it to LineKind.EVENT) }
    listOf(
        "cough syrup", "vitamin d", "antacid", "eye drops", "allergy tablets", "pain balm", "ors", "antiseptic cream",
        "iron supplements", "calcium tablets", "multivitamin", "nasal spray", "thermometer", "inhaler", "insulin",
        "bp tablets", "sugar tablets", "burn cream", "gripe water", "electral", "vaporub", "saline", "digestive tablets",
    ).forEach { add(it to LineKind.MEDICINE) }
}
