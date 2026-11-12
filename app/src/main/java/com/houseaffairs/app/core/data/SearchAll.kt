package com.houseaffairs.app.core.data

import java.time.LocalDate

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
    /** For money: the amount and its currency. */
    val amountMinor: Long? = null,
    val currency: String? = null,
    /** The day that matters for it: when it was spent, when it's due, when it's on, when it goes off. */
    val date: java.time.LocalDate? = null,
    /** The spending category ("Dining"), so "food" or "petrol" finds the right spending. */
    val category: String? = null,
    /** The family's own tags ("goa", "kids"), lower case, without the "#". */
    val tags: List<String> = emptyList(),
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
    setOf("document", "documents", "papers", "id", "ids") to setOf(
        "passport", "aadhaar", "aadhar", "pan", "licence", "license", "voter", "insurance", "policy", "certificate", "deed", "agreement", "warranty", "bill", "receipt", "document",
    ),
    setOf("repair", "fix", "maintenance", "service") to setOf("plumber", "electrician", "carpenter", "repair", "service", "ac", "geyser", "ro", "purifier", "maintenance", "painter", "leak"),
    setOf("festival", "diwali", "holi", "christmas", "eid") to setOf("diwali", "holi", "christmas", "eid", "rakhi", "festival", "puja", "pooja", "sweets", "lights", "decor"),
    setOf("baby", "kids", "child") to setOf("baby", "diaper", "diapers", "formula", "school", "toys", "vaccine", "kids", "child"),
    setOf("money", "finance", "savings") to setOf("salary", "rent", "emi", "loan", "investment", "sip", "mutual", "fd", "savings", "gullak", "budget", "bill"),
) + CARE.map { care ->
    // A condition finds its medicines by their common names: "fever" finds the Crocin.
    (care.words + care.tag.lowercase()).flatMap { it.split(" & ", " ") }.filter { it.length > 2 }.toSet() to
        (care.words.toSet() + KNOWN_MEDICINES[care.tag].orEmpty().flatMap { it.split(" ") })
}

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

// Fields that hold people (uids): searched by their names, so "Riya" finds what's for Riya.
private val PEOPLE_FIELDS = setOf("forWhom", "assignee", "who", "assignedBy")

/**
 * One collection's documents as things search can find. Used-up, binned and finished ones are
 * left out. [names] turns the uids in people fields into first names.
 */
fun findablesOf(collection: String, rows: List<Map<String, Any?>>, names: Map<String, String> = emptyMap()): List<Findable> {
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
        }.map { it.trim() }.filter { it.isNotEmpty() && it != title && !it.startsWith("http") && it.length <= 200 } +
            PEOPLE_FIELDS.flatMap { key -> (row[key] as? List<*>)?.filterIsInstance<String>() ?: listOfNotNull(row[key] as? String) }.mapNotNull { names[it] }
        val detail = listOfNotNull(
            str("category"), str("template"), str("location") ?: str("keptAt"), str("docType"), str("trip"), str("area"),
            str("kind").takeIf { collection != "stock" },
        ).filter { !it.equals(title, ignoreCase = true) }.distinct().take(2).joinToString(" · ")
        Findable(
            row["id"] as? String ?: return@mapNotNull null, collection, if (supply) "Supplies" else heading, title, detail, text, supply,
            photo = (row["photos"] as? List<*>)?.filterIsInstance<String>()?.firstOrNull(),
            amountMinor = (row["amountMinor"] as? Number)?.toLong() ?: (row["budgetMinor"] as? Number)?.toLong()?.takeIf { collection == "trips" },
            currency = str("currency"),
            date = DATE_KEYS.firstNotNullOfOrNull { row[it] as? java.time.LocalDate },
            category = str("category"),
            tags = tagsOf(row["tags"]),
        )
    }
}

/**
 * Everything about [query] across the home, grouped by kind, the closest group first. Every word
 * asked for must be found: in the title (best), elsewhere on the entry, or through a topic
 * ("travel" finds the passport and the Goa hotel booking). Pure, so it's tested.
 */
fun searchAll(query: String, items: List<Findable>, categoryOf: (String) -> String? = { null }, today: java.time.LocalDate = java.time.LocalDate.now()): List<FoundGroup> {
    // "Uber last month", "dining over 1000", "#goa": the filters said, then the words left.
    val q = parseSearch(query, today)
    val words = q.words.filter { it.length > 1 }
    if (words.isEmpty() && !q.hasFilters) return emptyList()
    val candidates = items.filter { q.fits(it, today) }
    if (words.isEmpty()) return group(candidates.map { it to 1 })
    val topics = words.associateWith { w -> TOPICS.filter { w in it.first }.flatMap { it.second }.toSet() }
    // The spending categories a word means: "petrol" → Transport, "food" → Dining and Groceries.
    val categories = words.associateWith { w -> (listOfNotNull(categoryOf(w)) + CATEGORY_WORDS[w].orEmpty()).map { it.lowercase() }.toSet() }

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
                item.category?.lowercase()?.let { it in categories[w].orEmpty() } == true -> 1
                // One slip of the thumb ("pasport", "groceires") still finds it.
                w.length >= 5 && tokens.any { it.length >= 4 && oneEditApart(w, it) } -> 1
                else -> return 0
            }.toInt()
        }
    }

    return group(candidates.map { it to score(it) }.filter { it.second > 0 })
}

private fun group(scored: List<Pair<Findable, Int>>): List<FoundGroup> {
    return scored
        .groupBy { it.first.group }
        .map { (group, hits) -> Triple(group, hits.maxOf { it.second }, hits.sortedWith(compareByDescending<Pair<Findable, Int>> { it.second }.thenBy { it.first.title.lowercase() })) }
        .sortedWith(compareByDescending<Triple<String, Int, List<Pair<Findable, Int>>>> { it.second }.thenByDescending { it.third.size })
        .map { (group, _, hits) -> FoundGroup(group, hits.map { it.first }) }
}

/** Keys holding the day that matters, in order of preference; dates arrive as LocalDate. */
private val DATE_KEYS = listOf("date", "due", "next", "nextDate", "start", "expiry", "by", "createdAt")

/** Everyday words for spending categories that aren't the category's own name. */
private val CATEGORY_WORDS = mapOf(
    "food" to listOf("Dining", "Groceries"), "eating" to listOf("Dining"), "eat" to listOf("Dining"), "restaurant" to listOf("Dining"),
    "commute" to listOf("Transport"), "travel" to listOf("Travel", "Transport"), "car" to listOf("Transport"), "fuel" to listOf("Transport"),
    "medical" to listOf("Health"), "doctor" to listOf("Health"), "utility" to listOf("Bills"), "utilitie" to listOf("Bills"),
    "fun" to listOf("Entertainment"), "house" to listOf("Home", "Rent"), "kid" to listOf("Bills"), "present" to listOf("Gifts"),
)

/** One line saying what a result is: "₹640 · Dining · Wed 11 Nov", "Due tomorrow", "📍 Blue file". */
fun Findable.line(money: (Long, String?) -> String, today: java.time.LocalDate): String {
    fun day(d: java.time.LocalDate) = when (java.time.temporal.ChronoUnit.DAYS.between(today, d)) {
        0L -> "today"; 1L -> "tomorrow"; -1L -> "yesterday"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "d MMM yyyy"))
    }
    val parts = when (collection) {
        "transactions" -> listOfNotNull(amountMinor?.let { money(it, currency) }, category, date?.let(::day))
        "recurring" -> listOfNotNull(amountMinor?.let { money(it, currency) }, date?.let { "next ${day(it)}" })
        "tasks" -> listOfNotNull(date?.let { if (it.isBefore(today)) "Was due ${day(it)}" else "Due ${day(it)}" } ?: "No date", detail.ifBlank { null })
        "events" -> listOfNotNull(date?.let(::day), detail.ifBlank { null })
        "trips" -> listOfNotNull(date?.let { "from ${day(it)}" }, amountMinor?.let { "budget ${money(it, currency)}" })
        "stock", "medicines" -> listOfNotNull(detail.ifBlank { null }, date?.let { if (it.isBefore(today)) "expired ${day(it)}" else "use by ${day(it)}" })
        "keptAt", "documents" -> listOfNotNull(detail.takeIf { it.isNotBlank() }?.let { "📍 $it" })
        else -> listOfNotNull(detail.ifBlank { null })
    }
    return (parts + tags.take(3).map { "#$it" }).joinToString(" · ")
}

/** The top of the results: spending found (total, how many, by category) and what else turned up. */
data class SearchSummary(val spentMinor: Long, val currency: String?, val expenses: Int, val byCategory: List<Pair<String, Long>>, val others: String)

fun searchSummary(groups: List<FoundGroup>): SearchSummary? {
    val spends = groups.flatMap { it.items }.filter { it.collection == "transactions" && it.amountMinor != null }
    val others = groups.filter { g -> g.items.none { it.collection == "transactions" } }
        .joinToString(" · ") { g -> "${g.items.size} ${g.group.lowercase()}" }
    if (spends.isEmpty() && others.isEmpty()) return null
    // One currency per summary: the one most of the spending is in.
    val currency = spends.groupBy { it.currency }.maxByOrNull { it.value.size }?.key
    val same = spends.filter { it.currency == currency }
    val byCategory = same.groupBy { it.category ?: "Other" }.mapValues { (_, l) -> l.sumOf { it.amountMinor ?: 0 } }
        .entries.sortedByDescending { it.value }.map { it.key to it.value }
    return SearchSummary(same.sumOf { it.amountMinor ?: 0 }, currency, same.size, byCategory, others)
}

/**
 * Nothing found: the closest word the home does have ("pasprt" → "passport"), within two letters,
 * so the search isn't a dead end. Null when nothing is close.
 */
fun didYouMean(query: String, items: List<Findable>): String? {
    val words = searchWords(query).ifEmpty { return null }
    val vocabulary = items.flatMap { it.title.wordsOf() + it.text.flatMap { t -> t.wordsOf() } }.filter { it.length >= 4 && it.none(Char::isDigit) }.map { it.stem() }.toSet()
    var changed = false
    val fixed = words.map { w ->
        if (w in vocabulary || w.length < 4) w
        // Closest first; on a tie, the word that starts the same way ("pasprt" → passport, not part).
        else vocabulary.map { it to editDistance(w, it) }.filter { it.second <= 2 }
            .minWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenByDescending { it.first.commonPrefixWith(w).length }.thenBy { kotlin.math.abs(it.first.length - w.length) })
            ?.first?.also { changed = true } ?: w
    }
    return fixed.joinToString(" ").takeIf { changed }
}

/** Letters added, dropped or changed to turn [a] into [b]. */
fun editDistance(a: String, b: String): Int {
    val row = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = row[0]
        row[0] = i
        for (j in 1..b.length) {
            val keep = row[j]
            row[j] = minOf(row[j] + 1, row[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = keep
        }
    }
    return row[b.length]
}

/** The last few searches, newest first, kept per phone; one added moves to the front. */
fun rememberSearch(recent: List<String>, query: String, keep: Int = 6): List<String> {
    val q = query.trim().takeIf { it.length >= 2 } ?: return recent
    return (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(keep)
}

/** Tags as saved ("goa kids", or a list), lower case, without "#". */
fun tagsOf(raw: Any?): List<String> = when (raw) {
    is String -> raw.split(Regex("[\\s,#]+"))
    is List<*> -> raw.filterIsInstance<String>()
    else -> emptyList()
}.map { it.trim().trim('#').lowercase() }.filter { it.isNotEmpty() }.distinct()

/** "12 things about travel", for the top of the results. */
fun foundLine(query: String, groups: List<FoundGroup>, today: LocalDate = LocalDate.now()): String {
    val n = groups.sumOf { it.items.size }
    val q = parseSearch(query, today)
    // A filter-only search ("overdue tasks", "#goa") reads back what it understood, not the words.
    if (q.hasFilters && q.words.isEmpty()) {
        val asked = q.understood.joinToString(" · ")
        return when (n) {
            0 -> "Nothing for $asked right now"
            1 -> "1 thing · $asked"
            else -> "$n things · $asked"
        }
    }
    val about = searchWords(query).joinToString(" ").ifEmpty { query.trim() }
    return when (n) {
        0 -> "Nothing saved about \"$about\" yet"
        1 -> "1 thing about $about"
        else -> "$n things about $about"
    }
}

/** True when [a] and [b] differ by one letter added, dropped, changed or two swapped. */
fun oneEditApart(a: String, b: String): Boolean {
    if (a == b) return false
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    if (a.length == b.length) {
        val diff = a.indices.filter { a[it] != b[it] }
        return diff.size == 1 || (diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]])
    }
    val (short, long) = if (a.length < b.length) a to b else b to a
    var i = 0
    while (i < short.length && short[i] == long[i]) i++
    return short.substring(i) == long.substring(i + 1)
}
