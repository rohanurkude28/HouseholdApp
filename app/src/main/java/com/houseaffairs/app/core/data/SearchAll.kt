package com.houseaffairs.app.core.data

/** One saved thing search can find, whatever kind it is. [text] is everything else it says (category, list, place, note…). */
data class Findable(
    val id: String,
    val collection: String,
    /** The heading it's shown under ("Trips", "Spending"). */
    val group: String,
    val title: String,
    val detail: String,
    val text: List<String>,
    /** Household supplies share the pantry's collection but open a different form. */
    val supply: Boolean = false,
    /** The first photo the family added, shown instead of the kind's icon. */
    val photo: String? = null,
)

data class FoundGroup(val group: String, val items: List<Findable>)

/** How many hits a group shows before "See all". */
const val FOUND_PEEK = 4

/** Collections search looks in, and the heading each goes under. */
val SEARCH_GROUPS = mapOf(
    "trips" to "Trips", "dreams" to "Dreams", "listItems" to "Lists", "places" to "Places", "tasks" to "Tasks",
    "events" to "Events", "transactions" to "Spending", "recurring" to "Subscriptions", "credits" to "Gift cards & coupons",
    "stock" to "Pantry", "shopping" to "To buy", "recipes" to "Recipes", "medicines" to "Medicines",
    "keptAt" to "Where it's kept", "documents" to "Documents", "weeklyGoals" to "Goals",
)

/**
 * A topic is a word people search by and the words things about it are actually saved under:
 * nobody titles a passport "travel", so "travel" has to know about passports.
 */
private val TOPICS: List<Pair<Set<String>, Set<String>>> = listOf(
    setOf("travel", "trip", "holiday", "vacation", "tour") to setOf(
        "travel", "trip", "flight", "hotel", "passport", "visa", "luggage", "suitcase", "ticket", "airport", "holiday",
        "vacation", "itinerary", "booking", "train", "resort", "trek", "beach", "airbnb", "hostel", "forex", "backpack",
    ),
    setOf("health", "medical", "doctor") to setOf(
        "health", "medical", "medicine", "doctor", "hospital", "clinic", "pharmacy", "prescription", "insurance", "dentist",
        "checkup", "vaccine", "tablet", "syrup", "test", "report",
    ),
    setOf("car", "bike", "vehicle") to setOf(
        "car", "bike", "vehicle", "fuel", "petrol", "diesel", "service", "puc", "tyre", "parking", "licence", "license", "rc", "fastag",
    ),
    setOf("school", "study", "education", "kid") to setOf(
        "school", "study", "education", "fee", "tuition", "book", "uniform", "exam", "homework", "class", "course", "college",
    ),
    setOf("bill", "utility") to setOf(
        "bill", "electricity", "water", "gas", "internet", "wifi", "broadband", "recharge", "rent", "maintenance", "phone", "mobile",
    ),
    setOf("gift", "birthday", "party") to setOf("gift", "birthday", "anniversary", "party", "cake", "present", "surprise", "wedding"),
    setOf("pet", "dog", "cat") to setOf("pet", "dog", "cat", "vet", "kibble", "leash", "litter"),
    setOf("clean", "cleaning", "laundry") to setOf(
        "clean", "laundry", "detergent", "mop", "broom", "dishwash", "soap", "bleach", "vacuum", "wash", "ironing",
    ),
    setOf("food", "eat", "cook") to setOf(
        "food", "recipe", "restaurant", "cafe", "grocery", "breakfast", "lunch", "dinner", "snack", "dessert", "dining",
    ),
    setOf("fitness", "exercise", "gym") to setOf("fitness", "gym", "yoga", "run", "walk", "workout", "swim", "cycling", "protein"),
)

private val SEARCH_FILLER = setOf("the", "my", "our", "a", "an", "for", "of", "to", "in", "all", "show", "find", "search", "everything", "about", "related")

// Fields that are ids, links or switches rather than something a person would search by.
private val NOT_SEARCHED = setOf(
    "id", "title", "createdBy", "visibleTo", "assignee", "assignedBy", "declinedBy", "forWhom", "surpriseFor", "votes", "who",
    "currency", "status", "ask", "source", "photos", "link", "mapLink", "driveLink", "driveFileId", "poster", "phone",
    "frequency", "repeat", "unit", "time", "paymentMethod", "onRenew", "emoji",
)

private fun String.wordsOf(): List<String> = lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

private fun String.stem(): String = if (length > 3) removeSuffix("s") else this

/** The words a search is really asking for ("show all my travel things" → "travel"). */
fun searchWords(query: String): List<String> =
    query.wordsOf().filter { it !in SEARCH_FILLER && it != "things" && it != "stuff" }.map { it.stem() }.filter { it.length > 1 }.distinct()

/** One collection's documents as things search can find. Used-up, binned and finished ones are left out. */
fun findablesOf(collection: String, rows: List<Map<String, Any?>>): List<Findable> {
    val heading = SEARCH_GROUPS[collection] ?: return emptyList()
    return rows.mapNotNull { row ->
        fun str(key: String) = (row[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        if (str("status") in GONE_STATUSES || str("status") == "DONE" || row["done"] == true) return@mapNotNull null
        val title = str("title") ?: str("merchant") ?: str("name") ?: str("category") ?: return@mapNotNull null
        val supply = collection == "stock" && str("type") == "HOUSEHOLD"
        val text = row.filterKeys { it !in NOT_SEARCHED }.values.flatMap { v ->
            when (v) {
                is String -> listOf(v)
                is List<*> -> v.filterIsInstance<String>()
                else -> emptyList()
            }
        }.map { it.trim() }.filter { it.isNotEmpty() && it != title && !it.startsWith("http") && it.length <= 200 }
        val detail = listOfNotNull(
            str("category"), str("template"), str("location") ?: str("keptAt"), str("docType"), str("trip"), str("area"),
            str("kind").takeIf { collection != "stock" },
        ).filter { !it.equals(title, ignoreCase = true) }.distinct().take(2).joinToString(" · ")
        Findable(
            row["id"] as? String ?: return@mapNotNull null, collection, if (supply) "Supplies" else heading, title, detail, text, supply,
            photo = (row["photos"] as? List<*>)?.filterIsInstance<String>()?.firstOrNull(),
        )
    }
}

/**
 * Everything about [query] across the home, grouped by kind, the closest group first. Every word
 * asked for must be found: in the title (best), elsewhere on the entry, or through a topic
 * ("travel" finds the passport and the Goa hotel booking). Pure, so it's tested.
 */
fun searchAll(query: String, items: List<Findable>): List<FoundGroup> {
    val words = searchWords(query)
    if (words.isEmpty()) return emptyList()
    val topics = words.associateWith { w -> TOPICS.filter { w in it.first }.flatMap { it.second }.toSet() }

    fun score(item: Findable): Int {
        val title = item.title.lowercase()
        val rest = item.text.map { it.lowercase() }
        val tokens = (item.title.wordsOf() + item.text.flatMap { it.wordsOf() }).map { it.stem() }.toSet()
        return words.sumOf { w ->
            when {
                title.contains(w) -> 3
                rest.any { it.contains(w) } -> 2
                // Whole words only for topics, so "train" doesn't find "training".
                topics[w].orEmpty().any { it in tokens } -> 1
                else -> return 0
            }.toInt()
        }
    }

    return items.map { it to score(it) }.filter { it.second > 0 }
        .groupBy { it.first.group }
        .map { (group, hits) -> Triple(group, hits.maxOf { it.second }, hits.sortedWith(compareByDescending<Pair<Findable, Int>> { it.second }.thenBy { it.first.title.lowercase() })) }
        .sortedWith(compareByDescending<Triple<String, Int, List<Pair<Findable, Int>>>> { it.second }.thenByDescending { it.third.size })
        .map { (group, _, hits) -> FoundGroup(group, hits.map { it.first }) }
}

/** "12 things about travel", for the top of the results. */
fun foundLine(query: String, groups: List<FoundGroup>): String {
    val n = groups.sumOf { it.items.size }
    val about = searchWords(query).joinToString(" ")
    return when (n) {
        0 -> "Nothing saved about \"$about\" yet"
        1 -> "1 thing about $about"
        else -> "$n things about $about"
    }
}
