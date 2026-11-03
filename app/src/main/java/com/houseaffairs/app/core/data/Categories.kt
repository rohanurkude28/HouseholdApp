package com.houseaffairs.app.core.data

/**
 * Categories for the Things lists: where things are kept, supplies and medicines. Each item's own
 * choice wins; older items without one are placed by their name. Pure, so it's tested.
 */

/** Kinds of kept things, in the order the list shows them. Stored values stay stable. */
val KEPT_KINDS = listOf("Jewellery", "Documents", "Keys", "Cash", "Electricals", "Gadgets", "Clothes", "Seasonal", "Other")

/** How a kept kind is titled in the list. */
fun keptKindLabel(kind: String): String = when (kind) {
    "Jewellery" -> "Jewellery & ornaments"
    "Cash" -> "Cash & valuables"
    "Electricals" -> "Electricals & tools"
    "Clothes" -> "Clothes & linen"
    "Seasonal" -> "Seasonal & festive"
    else -> kind
}

/** Guesses what kind of thing [title] is: "gold necklace" → Jewellery, "extension board" → Electricals. */
fun keptKindOf(title: String): String? {
    val l = title.lowercase()
    fun has(vararg words: String) = words.any { Regex("\\b${Regex.escape(it)}").containsMatchIn(l) }
    return when {
        has("gold", "silver", "necklace", "ring", "bangle", "earring", "chain", "jewel", "ornament", "mangalsutra", "anklet", "payal", "pendant", "nose pin") -> "Jewellery"
        has("key") -> "Keys"
        has("passport", "certificate", "papers", "deed", "aadhaar", "pan card", "document", "policy", "warranty", "marksheet", "will", "registration", "rc book") -> "Documents"
        has("cash", "money", "dollars", "coins", "foreign currency") -> "Cash"
        has("extension", "drill", "screwdriver", "toolkit", "tool", "torch", "bulb", "cable", "wire", "adapter", "plug", "inverter", "fuse", "multimeter") -> "Electricals"
        has("charger", "laptop", "phone", "hard disk", "pendrive", "camera", "remote", "headphone", "earphone", "router", "tablet", "kindle", "speaker") -> "Gadgets"
        has("saree", "suit", "lehenga", "blazer", "bedsheet", "towel", "curtain", "clothes") -> "Clothes"
        has("sweater", "blanket", "quilt", "razai", "diya", "rangoli", "decoration", "lights", "umbrella", "raincoat", "heater", "christmas") -> "Seasonal"
        else -> null
    }
}

/** A kept thing's kind for grouping: its own, or a guess from its name. */
fun keptCategory(item: KeptItem): String = item.kind?.takeIf { it in KEPT_KINDS } ?: keptKindOf(item.title) ?: "Other"

/** Supply types, in the order the list shows them. */
val SUPPLY_TYPES = listOf(
    "Cosmetics & skincare", "Toiletries", "Cleaning", "Laundry", "Kitchen", "Batteries", "Bulbs", "Stationery", "First aid", "Other",
)

/** A medicine's group: what it's mostly used for ("Fever", "Cold & cough"…), or "Other". */
fun medicineCategory(stock: PantryStock): String = stock.category?.takeIf { it.isNotBlank() } ?: "Other"

/** A supply's group: its type, or "Other". Custom types the family typed in keep their own group. */
fun supplyCategory(stock: PantryStock): String = stock.category?.takeIf { it.isNotBlank() } ?: "Other"

/** A pantry item's category: its chosen type, else guessed from the name. Only food categories. */
fun pantryCategory(stock: PantryStock): Aisle = aisleOf(stock.title, stock.category).takeIf { it in Aisle.FOOD } ?: Aisle.OTHER

/** [items] grouped by [categoryOf], groups in [order] first (then others alphabetically), "Other" last. */
fun <T> grouped(items: List<T>, order: List<String>, categoryOf: (T) -> String): List<Pair<String, List<T>>> =
    items.groupBy(categoryOf).toList().sortedWith(
        compareBy<Pair<String, List<T>>> { it.first == "Other" }
            .thenBy { order.indexOf(it.first).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .thenBy { it.first },
    )
