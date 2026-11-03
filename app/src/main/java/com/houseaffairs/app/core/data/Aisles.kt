package com.houseaffairs.app.core.data

/**
 * Where something sits in the shop, and its category in the pantry. The shopping list and the
 * pantry both group and colour items by it.
 */
enum class Aisle(val label: String, val emoji: String, val argb: Long) {
    VEGETABLES("Vegetables", "🥬", 0xFF4CAF50),
    FRUITS("Fruits", "🍎", 0xFFE8664F),
    DAIRY("Dairy & eggs", "🥛", 0xFF4C9BE0),
    STAPLES("Grains & dals", "🌾", 0xFFD39B32),
    SPICES("Spices & masalas", "🌶️", 0xFFD9682B),
    MEAT("Meat, fish & poultry", "🍗", 0xFFC0504D),
    SNACKS("Snacks & drinks", "🍪", 0xFFA66BD6),
    FROZEN("Frozen", "🧊", 0xFF5AB4C8),
    CLEANING("Home & cleaning", "🧽", 0xFF26A69A),
    CARE("Personal care", "🧴", 0xFFE07AA8),
    HEALTH("Medicines", "💊", 0xFF6F7FE0),
    OTHER("Other", "🛒", 0xFF8E8E93),
    ;

    companion object {
        /** Older labels, from before spices and staples were split. */
        private val RENAMED = mapOf("Staples & spices" to STAPLES, "Meat & fish" to MEAT)

        fun from(label: String?): Aisle? = label?.let { l -> entries.firstOrNull { it.label == l } ?: RENAMED[l] }

        /** The categories a pantry item can have; household and medicine aisles belong elsewhere. */
        val FOOD = listOf(VEGETABLES, FRUITS, DAIRY, STAPLES, SPICES, MEAT, SNACKS, FROZEN, OTHER)
    }
}

private val AISLE_WORDS: List<Pair<Aisle, List<String>>> = listOf(
    Aisle.HEALTH to listOf("crocin", "dolo", "paracetamol", "tablet", "syrup", "vicks", "band-aid", "bandage", "ors", "medicine", "vitamin", "dettol liquid"),
    Aisle.CARE to listOf(
        "shampoo", "soap", "toothpaste", "toothbrush", "face wash", "lotion", "face cream", "deodorant", "razor", "sanitary", "pads", "diaper",
        "conditioner", "hair oil", "tissue", "sunscreen", "moisturiser", "moisturizer", "serum", "lipstick", "lip balm", "kajal", "eyeliner",
        "mascara", "foundation", "perfume", "nail polish", "face pack", "body wash", "cold cream",
    ),
    Aisle.CLEANING to listOf("detergent", "surf", "vim", "harpic", "lizol", "phenyl", "broom", "mop", "garbage bag", "dishwash", "scrub", "cleaner", "battery", "batteries", "bulb", "foil", "cling", "napkin", "toilet"),
    // Before vegetables and dairy: "chilli powder" and "coriander powder" are spices, "cream" in "ice cream" isn't dairy.
    Aisle.SPICES to listOf(
        "haldi", "turmeric", "masala", "jeera", "cumin", "mustard seeds", "rai", "pepper", "spice", "chilli powder", "red chilli", "coriander powder",
        "dhania powder", "hing", "asafoetida", "ajwain", "elaichi", "cardamom", "cloves", "laung", "cinnamon", "dalchini", "saunf", "fennel",
        "kasuri methi", "bay leaf", "tej patta", "methi seeds", "kalonji", "star anise", "nutmeg", "saffron", "kesar", "chaat masala",
    ),
    Aisle.FROZEN to listOf("frozen", "ice cream", "kulfi", "nuggets", "fries"),
    Aisle.DAIRY to listOf("milk", "curd", "dahi", "paneer", "butter", "ghee", "cheese", "cream", "yogurt", "egg", "eggs", "buttermilk", "lassi", "tofu"),
    Aisle.MEAT to listOf("chicken", "mutton", "fish", "prawn", "meat", "keema", "salami", "sausage", "bacon", "turkey", "duck", "crab", "poultry", "lamb", "pork"),
    Aisle.FRUITS to listOf("apple", "banana", "mango", "orange", "grapes", "papaya", "pomegranate", "watermelon", "guava", "pineapple", "kiwi", "berries", "strawberry", "chikoo", "pear", "fruit"),
    Aisle.VEGETABLES to listOf(
        "onion", "tomato", "potato", "aloo", "garlic", "ginger", "chilli", "chili", "coriander", "dhania", "mint", "spinach", "palak", "methi",
        "cabbage", "cauliflower", "gobi", "carrot", "beans", "peas", "capsicum", "brinjal", "baingan", "okra", "bhindi", "cucumber", "lauki",
        "pumpkin", "beetroot", "radish", "mushroom", "lemon", "curry leaves", "corn", "broccoli", "lettuce", "vegetable", "sabzi",
    ),
    Aisle.SNACKS to listOf("biscuit", "chips", "namkeen", "chocolate", "cookies", "juice", "coke", "soda", "tea", "coffee", "bread", "cake", "noodles", "maggi", "jam", "ketchup", "sauce", "cornflakes", "oats", "muesli"),
    Aisle.STAPLES to listOf(
        "rice", "atta", "flour", "maida", "dal", "toor", "moong", "chana", "rajma", "besan", "sugar", "salt", "oil", "jaggery", "poha", "rava", "suji",
        "sooji", "urad", "masoor", "vinegar", "honey", "nuts", "almond", "cashew", "raisins", "peanuts", "millet", "ragi", "quinoa",
    ),
)

/** Guesses the aisle from the item's name, or from a pantry type ("Dairy") when given. */
fun aisleOf(title: String, type: String? = null): Aisle {
    Aisle.from(type)?.let { return it }
    // Pantry types saved before they matched the aisles.
    when (type) {
        "Dairy" -> return Aisle.DAIRY
        "Staples" -> return Aisle.STAPLES
        "Spices" -> return Aisle.SPICES
        "Snacks", "Beverages" -> return Aisle.SNACKS
        "Personal care" -> return Aisle.CARE
    }
    val words = title.lowercase()
    return AISLE_WORDS.firstOrNull { (_, keys) -> keys.any { k -> Regex("\\b${Regex.escape(k)}(?:s|es)?\\b").containsMatchIn(words) } }?.first ?: Aisle.OTHER
}
