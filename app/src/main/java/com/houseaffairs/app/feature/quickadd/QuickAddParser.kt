package com.houseaffairs.app.feature.quickadd

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

enum class ListTemplate(
    /** Stored on each item; kept stable so older entries still match. */
    val label: String,
    internal val aliases: Set<String>,
    /** What the list is called on screen. */
    val title: String,
    val emoji: String,
    /** The button that ticks an item off. */
    val doneLabel: String,
) {
    WATCH("Watch together", setOf("watch", "watchlist", "movie", "movies", "film", "films", "series", "shows"), "Movies & shows", "🎬", "Watched"),
    BOOKS("Books", setOf("read", "reading", "reading list", "book", "books", "to read"), "Books", "📚", "Read it"),
    MUSIC("Music", setOf("song", "songs", "music", "playlist", "album", "albums", "listen"), "Music", "🎵", "Heard it"),
    TRAVEL("Travel", setOf("travel", "trip", "trips", "bucket", "bucket list", "places"), "Travel", "🧳", "Been there"),
    EAT_OUT("Eat out", setOf("eat out", "eatout", "restaurant", "restaurants", "food"), "Eat out", "🍽️", "Went"),
    BUY("Buy", setOf("buy", "wishlist", "wish", "home"), "To buy someday", "🛍️", "Bought"),
    GIFTS("Gifts", setOf("gift", "gifts"), "Gift ideas", "🎁", "Given"),
    ;

    /** The action that ticks an item off, worded as something to do ("Mark watched"), not a state. */
    val markLabel: String
        get() = when (this) {
            WATCH -> "Mark watched"
            BOOKS -> "Mark read"
            MUSIC -> "Mark heard"
            TRAVEL, EAT_OUT -> "Mark visited"
            BUY -> "Mark bought"
            GIFTS -> "Mark given"
        }

    /** Lists whose items are looked up online for a cover, rating and where to find them. */
    val isMedia: Boolean get() = this == WATCH || this == BOOKS || this == MUSIC

    companion object {
        /** "WATCH" is how the earliest items were saved. */
        fun of(stored: String?): ListTemplate? = entries.firstOrNull { it.label == stored || it.name == stored }
    }
}

val EXPENSE_CATEGORIES = listOf(
    "Rent", "Groceries", "Dining", "Transport", "Bills", "Health",
    "Shopping", "Entertainment", "Home", "Travel", "Gifts", "Other",
)

sealed interface QuickAddDraft {
    data class Expense(
        val amount: BigDecimal,
        /** ISO code if the user typed one (₹, $, usd…); null means household base currency. */
        val currency: String?,
        val category: String,
        val merchant: String?,
        val note: String,
        val subcategory: String? = null,
        /** "UPI", "Card", "Cash" or "Bank transfer" when said ("by card", "on gpay"). */
        val paidWith: String? = null,
        /** The card's name when said ("on HDFC card"). */
        val card: String? = null,
    ) : QuickAddDraft

    /** "Walked 30 min", "45 minutes yoga", "gardening for an hour", "did 8000 steps". */
    data class Activity(val kind: String, val minutes: Int?, val steps: Int?) : QuickAddDraft

    /** "Start trip Goa" / "outing: Sunday picnic this sunday". */
    data class Trip(val title: String, val start: LocalDate?) : QuickAddDraft

    data class Subscription(val title: String, val amount: BigDecimal, val currency: String?, val frequency: String) : QuickAddDraft
    /** [quantity] is "500 g", "2 pcs"…, or null when none was said. */
    data class Shopping(val item: String, val quantity: String? = null) : QuickAddDraft
    /** [repeat] is "Daily", "Weekly" or "Monthly" for "water plants every week", else null. */
    data class Task(val title: String, val due: LocalDate?, val repeat: String? = null, val priority: String? = null) : QuickAddDraft
    data class Event(val title: String, val kind: String, val date: LocalDate?) : QuickAddDraft
    data class Medicine(val title: String, val forName: String?, val expiry: LocalDate?) : QuickAddDraft
    data class Grocery(val title: String, val quantity: String?, val expiry: LocalDate?) : QuickAddDraft
    data class ListItem(val template: ListTemplate, val title: String) : QuickAddDraft

    /** Several things said at once: "add milk, eggs and bread to shopping", "200 uber and 500 groceries". */
    data class Batch(val drafts: List<QuickAddDraft>) : QuickAddDraft

    /**
     * Changes something already saved: "delete the Uber expense", "mark milk bought",
     * "change rent task to tomorrow", "add this expense to Goa trip". [target] is what to look
     * for (empty means the latest one); [kind] narrows it ("expense", "task", "shopping"…);
     * [to] is the new value, or where to link it.
     */
    data class Change(val action: ChangeAction, val target: String, val kind: String? = null, val to: String? = null) : QuickAddDraft

    enum class ChangeAction { DELETE, DONE, EDIT, LINK }

    /** "Made paneer sabzi today" / "cooked dal for 4": updates the pantry from the recipe. */
    data class Cooked(val dish: String, val servings: Int?) : QuickAddDraft

    /** "Move 2000 from gifts to travel". */
    data class Move(val amount: BigDecimal, val from: String, val to: String) : QuickAddDraft

    /** "Save 5000 for Bali": gullak → a dream (or an envelope). */
    data class Fund(val amount: BigDecimal, val target: String) : QuickAddDraft

    /** "Got Amazon gift card 2000 for gifting". */
    data class Credit(val title: String, val kind: String, val amount: BigDecimal, val forWhat: String?, val expiry: LocalDate?) : QuickAddDraft

    /** "Dream: Bali trip 150000 by March". */
    data class Dream(val title: String, val kind: String, val amount: BigDecimal?, val date: LocalDate?) : QuickAddDraft

    /** A question for the assistant rather than something to save. */
    data class Ask(val question: Question, val subject: String?, val person: String? = null) : QuickAddDraft

    enum class Question {
        COOK, EXPIRING, LEFT, AFFORD, WHERE,

        /** "Going to Indiranagar" / "what's near me": saved places around there (subject null = here). */
        NEARBY,

        /** "I'm feeling feverish" / "Riya has a cold": medicines at home and first aid (subject = care tag). */
        SYMPTOM,

        /** "How much is the card bill?" / "paid the credit card bill": bills aren't new spending. */
        CARD_BILL,

        /** "I want to eat something crispy with a savoury curry" (subject = what they want). */
        CRAVING,
    }

    /** "Gold necklace is in the bank locker" / "kept spare keys in the blue drawer". */
    data class Kept(val item: String, val location: String) : QuickAddDraft

    /** Household supplies: "bought 5 AAA batteries expiry 2028". */
    data class Household(val title: String, val quantity: String?, val expiry: LocalDate?, val type: String?) : QuickAddDraft

    /** Interest or gains on an investment: "add interest 1200 to HDFC FD". */
    data class Growth(val amount: BigDecimal, val target: String) : QuickAddDraft

    /** "HDFC FD is now worth 52000": set an investment's current value. */
    data class Revalue(val target: String, val amount: BigDecimal) : QuickAddDraft
}

/**
 * Turns one line of typed or spoken text into a draft, e.g.
 * "450 groceries DMart", "Netflix 649 monthly", "milk over", "crocin for Riya expires March",
 * "Riya birthday 12 Dec", "add Dune 2 to watch list", "pay rent tomorrow".
 *
 * Rule-based and on-device for the beta. Drafts only pre-fill a form; nothing is saved unseen.
 */
class QuickAddParser(private val today: () -> LocalDate = LocalDate::now) {

    fun parse(input: String): QuickAddDraft? {
        val text = input.trim().replace(WHITESPACE, " ")
        if (text.isEmpty()) return null
        return parseAsk(text)
            ?: parseChange(text)
            ?: parseBatch(text)
            ?: parseSingle(text)
    }

    /**
     * True when [input] only became a task because nothing else matched: no "remind me", no due
     * date, no doing-word like "call" or "pay". "Peanut butter" is unsure; "call plumber" isn't.
     * These are the lines worth a second opinion from the classifier or the assistant.
     */
    fun isUnsure(input: String): Boolean {
        val text = input.trim().replace(WHITESPACE, " ")
        val draft = parse(text) as? QuickAddDraft.Task ?: return false
        if (draft.due != null || TASK_PREFIX.containsMatchIn(text)) return false
        return text.split(" ").first().normalizedWord() !in TASK_VERBS
    }

    /** [input] read as [kind], for when the family (or a guess) says what it really is. */
    fun asKind(kind: LineKind, input: String): QuickAddDraft {
        val text = input.trim().replace(WHITESPACE, " ")
        return when (kind) {
            LineKind.SHOPPING -> shoppingOf(text.replace(BUY_VERB, "")) ?: QuickAddDraft.Shopping(text.replace(BUY_VERB, "").capitalized())
            LineKind.TASK -> parseTask(text)
            LineKind.MEDICINE -> parseMedicine(text) ?: QuickAddDraft.Medicine(text.capitalized(), null, null)
            LineKind.EVENT -> {
                val words = text.split(" ").toMutableList()
                val date = extractDate(words)
                val title = words.joinToString(" ").trim().capitalized().ifEmpty { text }
                QuickAddDraft.Event(title, EVENT_KINDS.entries.firstOrNull { (w, _) -> w in text.lowercase() }?.value ?: "Other", date)
            }
        }
    }

    /** [input] as something new to save, ignoring change commands ("update passport" as a task). */
    fun parseAsNew(input: String): QuickAddDraft? = parseSingle(input.trim().replace(WHITESPACE, " "))

    /** Everything but questions, changes and batches; also used for each part of a batch. */
    private fun parseSingle(text: String): QuickAddDraft? {
        return parseActivity(text)
            ?: parseTrip(text)
            ?: parseAddTo(text)
            ?: parseMove(text)
            ?: parseGrowth(text)
            ?: parseRevalue(text)
            ?: parseCredit(text)
            ?: parseFund(text)
            ?: parseKept(text)
            ?: parseHousehold(text)
            ?: parseDream(text)
            ?: parseCooked(text)
            ?: parseRunningOut(text)
            ?: parseEvent(text)
            ?: parseMedicine(text)
            ?: parseMoney(text)
            ?: parseGrocery(text)
            ?: parseMeasured(text)
            ?: parseTask(text)
    }

    private fun parseAddTo(text: String): QuickAddDraft? {
        val match = ADD_TO.matchEntire(text) ?: return null
        val what = match.groupValues[1].trim()
        val target = match.groupValues[2].trim().lowercase()
        val template = ListTemplate.entries.firstOrNull { target in it.aliases }
        if (target !in SHOPPING_ALIASES && template == null) return null
        // "milk, eggs and bread": several items. Titles like "Tom and Jerry" only split on commas.
        val parts = (if (template?.isMedia == true) what.split(",") else what.split(LIST_SPLIT))
            .map { it.trim().removePrefix("and ").trim() }.filter { it.isNotEmpty() }
        val drafts = parts.map { item ->
            if (template == null) shoppingOf(item) ?: QuickAddDraft.Shopping(item.capitalized()) else QuickAddDraft.ListItem(template, item.capitalized())
        }
        return drafts.singleOrNull() ?: QuickAddDraft.Batch(drafts)
    }

    /**
     * "200 uber and 500 groceries" or "milk over, bought eggs": each part must stand on its own
     * as something to save. Plain tasks don't count, so "call Riya and Aman" stays one task.
     */
    private fun parseBatch(text: String): QuickAddDraft.Batch? {
        val parts = text.split(BATCH_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val drafts = parts.map { parseSingle(it) ?: return null }
        if (drafts.any { it is QuickAddDraft.Task || it is QuickAddDraft.Batch || it is QuickAddDraft.Cooked || it is QuickAddDraft.Move }) return null
        return QuickAddDraft.Batch(drafts)
    }

    private fun parseChange(input: String): QuickAddDraft.Change? {
        // "Can you please delete…", "pls remove…": politeness first, the command after.
        var text = POLITE.replace(input, "").trim().trimEnd('?', '.', '!')
        // "Netflix, can you remove it" / "the Uber expense, delete that": the thing comes first.
        DELETE_IT_TAIL.matchEntire(text)?.let { m -> text = "delete ${m.groupValues[1].trim().trimEnd(',', '-')}" }
        CHANGE_LINK.matchEntire(text)?.let { m ->
            val (target, kind) = changeTarget(m.groupValues[1])
            return QuickAddDraft.Change(QuickAddDraft.ChangeAction.LINK, target, kind ?: "expense", m.groupValues[2].trim())
        }
        (CHANGE_DELETE.matchEntire(text) ?: DELETE_HINGLISH.matchEntire(text))?.let { m ->
            val (what, from) = splitFrom(m.groupValues[1])
            val (target, kind) = changeTarget(what)
            val listKind = from?.let(::kindOfList)
            // "Drop the kids at school" is a task; "drop milk from the list" or "scratch the Uber expense" isn't.
            val risky = RISKY_DELETE_VERBS.any { text.lowercase().startsWith(it) }
            if (!risky || kind != null || listKind != null) {
                return QuickAddDraft.Change(QuickAddDraft.ChangeAction.DELETE, target, listKind ?: kind)
            }
        }
        CHANGE_END_TRIP.matchEntire(text)?.let { m ->
            val (target, _) = changeTarget(m.groupValues[1].ifEmpty { m.groupValues[2] })
            return QuickAddDraft.Change(QuickAddDraft.ChangeAction.DONE, target, "trip")
        }
        (CHANGE_DONE.matchEntire(text) ?: CHANGE_DONE_TAIL.matchEntire(text))?.let { m ->
            val (target, kind) = changeTarget(m.groupValues[1])
            val word = m.groupValues.getOrNull(2)?.lowercase()
            val implied = when (word) {
                "bought", "purchased" -> "shopping"
                "watched", "read" -> "list"
                "paid" -> "expense"
                else -> null
            }
            return QuickAddDraft.Change(QuickAddDraft.ChangeAction.DONE, target, kind ?: implied)
        }
        // "Fix the uber expense to 300", "make the uber amount 300": only when it's clearly about a
        // saved entry (a kind word, or amount/date), so "fix the tap tomorrow" stays a task.
        CHANGE_SET.matchEntire(text)?.let { m ->
            val what = m.groupValues[1]
            val (target, kind) = changeTarget(what)
            val aboutEntry = kind != null || what.lowercase().split(" ").any { it in VALUE_WORDS }
            if (aboutEntry && target.isNotEmpty()) return QuickAddDraft.Change(QuickAddDraft.ChangeAction.EDIT, target, kind, m.groupValues[2].trim())
        }
        CHANGE_EDIT.matchEntire(text)?.let { m ->
            val what = m.groupValues[1]
            // "Update HDFC FD to 52000" is an investment value, handled elsewhere.
            if (INVESTMENT_WORDS.any { what.lowercase().split(" ").contains(it) }) return null
            val (target, kind) = changeTarget(what)
            return QuickAddDraft.Change(QuickAddDraft.ChangeAction.EDIT, target, kind, m.groupValues[2].trim().ifEmpty { null })
        }
        return null
    }

    /** "milk from the shopping list" → ("milk", "shopping list"). */
    private fun splitFrom(text: String): Pair<String, String?> {
        val m = FROM_LIST.matchEntire(text) ?: return text to null
        return m.groupValues[1] to m.groupValues[2]
    }

    private fun kindOfList(name: String): String? {
        val n = name.lowercase().removeSuffix(" list").trim()
        return when {
            n in SHOPPING_ALIASES -> "shopping"
            ListTemplate.entries.any { n in it.aliases } -> "list"
            else -> CHANGE_KINDS[n]
        }
    }

    /** Strips articles and the kind word: "the last Uber expense" → ("uber", "expense"). */
    private fun changeTarget(text: String): Pair<String, String?> {
        val words = text.trim().split(" ").filter { it.isNotBlank() }.toMutableList()
        var kind: String? = null
        words.removeAll { w ->
            val n = w.lowercase().trim('.', ',', '?', '!', '\'', '’')
            val k = CHANGE_KINDS[n] ?: CHANGE_KINDS[n.removeSuffix("s")]
            when {
                k != null -> { kind = kind ?: k; true }
                n in CHANGE_FILLERS -> true
                else -> false
            }
        }
        return words.joinToString(" ").trim() to kind
    }

    /** The first date in [text] ("tomorrow", "12 Dec"), for changes like "move rent to friday". */
    fun dateIn(text: String): LocalDate? = extractDate(text.split(" ").toMutableList())

    /** [text] read as just an amount ("250", "₹2k"). */
    fun amountIn(text: String): BigDecimal? = justAmount(text)

    private fun amountOf(text: String): BigDecimal? {
        val match = AMOUNT.find(text) ?: return null
        var amount = match.groupValues[2].replace(",", "").toBigDecimalOrNull() ?: return null
        if (match.groups[3] != null) amount = amount.multiply(BigDecimal(1000))
        return amount
    }

    /** True when [text] is just an amount ("2000", "₹2,000", "2k"). */
    private fun justAmount(text: String): BigDecimal? =
        AMOUNT.matchEntire(text.trim())?.let { amountOf(text) }

    private fun parseAsk(text: String): QuickAddDraft.Ask? {
        val t = text.trim().trimEnd('?', '.', '!').trim()
        // Paying a card bill moves money you already counted when you spent it, so it isn't an expense.
        if (CARD_BILL.containsMatchIn(t)) return QuickAddDraft.Ask(QuickAddDraft.Question.CARD_BILL, if (Regex("\\b(paid|pay|paying|cleared|settled)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) "paid" else null)
        ASK_COOK.matchEntire(t)?.let { return QuickAddDraft.Ask(QuickAddDraft.Question.COOK, null) }
        // Before symptoms: "I feel like eating something spicy" is hunger, not illness.
        (CRAVING.matchEntire(t) ?: CRAVING_TASTE.matchEntire(t))?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.CRAVING, m.groupValues[1].trim()) }
        ASK_EXPIRING.matchEntire(t)?.let { return QuickAddDraft.Ask(QuickAddDraft.Question.EXPIRING, null) }
        ASK_LEFT.matchEntire(t)?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.LEFT, m.groupValues[1].trim().ifEmpty { null }) }
        ASK_AFFORD.matchEntire(t)?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.AFFORD, m.groupValues[1].trim()) }
        ASK_WHERE.matchEntire(t)?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.WHERE, m.groupValues[1].trim()) }
        SYMPTOM_SENTENCE.matchEntire(t)?.let { m ->
            val care = com.houseaffairs.app.core.data.careFor(t) ?: return@let
            val who = m.groupValues[1].trim().takeIf { it.isNotEmpty() && it.lowercase() !in setOf("i", "we", "i'm", "im", "my", "me") }
            return QuickAddDraft.Ask(QuickAddDraft.Question.SYMPTOM, care.tag, who?.replaceFirstChar { it.uppercaseChar() })
        }
        (GOING_TO.matchEntire(t) ?: NEAR.matchEntire(t))?.let { m ->
            val place = m.groupValues[1].trim().trim(*PUNCTUATION)
            val first = place.substringBefore(' ').lowercase()
            // "I'm going to pay rent tomorrow" is a task, not a trip.
            if (first in NOT_A_PLACE || words(place).any { dueWord(it) }) return null
            val here = place.lowercase() in setOf("me", "here", "my location", "us")
            return QuickAddDraft.Ask(QuickAddDraft.Question.NEARBY, if (here) null else place.replaceFirstChar { it.uppercaseChar() })
        }
        return null
    }

    private fun words(text: String) = text.split(" ").map { it.normalizedWord() }

    private fun dueWord(w: String) = w in setOf("today", "tonight", "tomorrow", "tmrw") || w in WEEKDAYS

    private fun parseActivity(text: String): QuickAddDraft.Activity? {
        if (CURRENCY_MARKER.containsMatchIn(text)) return null
        val lower = " " + text.lowercase() + " "
        val kind = ACTIVITY_WORDS.entries.firstOrNull { (word, _) -> lower.contains(" $word") }?.value
        val steps = STEPS.find(lower)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        if (kind == null && steps == null) return null
        val minutes = DURATION.find(lower)?.let { m ->
            val n = m.groupValues[1].let { if (it == "an" || it == "a") 1.0 else it.toDoubleOrNull() } ?: return@let null
            (if (m.groupValues[2].startsWith("h")) n * 60 else n).toInt()
        }
        // A bare activity word with no duration or steps ("buy yoga mat") isn't a log.
        if (minutes == null && steps == null) return null
        return QuickAddDraft.Activity(kind ?: "Walk", minutes, steps)
    }

    private fun parseTrip(text: String): QuickAddDraft.Trip? {
        val m = TRIP_START.matchEntire(text) ?: return null
        val words = (m.groupValues[1].ifEmpty { m.groupValues[2] }).split(" ").toMutableList()
        val start = extractDate(words)
        val title = words.joinToString(" ").trim().trim(*PUNCTUATION).capitalized().ifEmpty { return null }
        return QuickAddDraft.Trip(title, start)
    }

    private fun parseMove(text: String): QuickAddDraft.Move? {
        val m = MOVE.matchEntire(text) ?: return null
        val amount = justAmount(m.groupValues[1]) ?: return null
        return QuickAddDraft.Move(amount, pocketName(m.groupValues[2]), pocketName(m.groupValues[3]))
    }

    private fun parseFund(text: String): QuickAddDraft.Fund? {
        // "Add 500 for uber" is spending; "add 500 to the Bali trip" is saving.
        if (ADD_FOR.containsMatchIn(text)) return null
        val m = FUND.matchEntire(text) ?: return null
        val amount = justAmount(m.groupValues[1]) ?: return null
        return QuickAddDraft.Fund(amount, pocketName(m.groupValues[2]))
    }

    private fun parseCredit(text: String): QuickAddDraft.Credit? {
        val lower = text.lowercase()
        val (keyword, kind) = CREDIT_WORDS.entries.firstOrNull { lower.contains(it.key) }?.toPair() ?: return null
        val words = text.split(" ").toMutableList()
        val expiry = extractExpiry(words)
        var rest = words.joinToString(" ")
        val forMatch = FOR_TAIL.find(rest)
        val forWhat = forMatch?.groupValues?.get(1)?.trim()?.let(::pocketName)
        if (forMatch != null) rest = rest.removeRange(forMatch.range)
        val amountMatch = AMOUNT.find(rest) ?: return null
        val amount = amountOf(rest) ?: return null
        rest = rest.removeRange(amountMatch.range)
        val title = rest.split(" ")
            .filter { it.isNotBlank() && it.normalizedWord() !in setOf("got", "received", "have", "a", "an", "new", "add", "worth", "of") }
            .joinToString(" ")
            .ifEmpty { keyword }
        return QuickAddDraft.Credit(title.capitalized(), kind, amount, forWhat, expiry)
    }

    private fun parseGrowth(text: String): QuickAddDraft.Growth? {
        GROWTH_ADD.matchEntire(text)?.let { m ->
            val amount = justAmount(m.groupValues[1]) ?: return null
            return QuickAddDraft.Growth(amount, pocketName(m.groupValues[2]))
        }
        GROWTH_GOT.matchEntire(text)?.let { m ->
            val amount = justAmount(m.groupValues[1]) ?: return null
            return QuickAddDraft.Growth(amount, pocketName(m.groupValues[2]))
        }
        return null
    }

    private fun parseRevalue(text: String): QuickAddDraft.Revalue? {
        REVALUE.matchEntire(text)?.let { m ->
            val amount = justAmount(m.groupValues[2]) ?: return null
            return QuickAddDraft.Revalue(pocketName(m.groupValues[1]), amount)
        }
        REVALUE_UPDATE.matchEntire(text)?.let { m ->
            val amount = justAmount(m.groupValues[2]) ?: return null
            return QuickAddDraft.Revalue(pocketName(m.groupValues[1]), amount)
        }
        return null
    }

    private fun parseKept(text: String): QuickAddDraft.Kept? {
        val m = KEPT_VERB.matchEntire(text)
            ?: KEPT_IS.matchEntire(text)?.takeIf { match ->
                val where = match.groupValues[2].lowercase()
                match.groupValues[0].contains(Regex("\\b(kept|stored|placed)\\b", RegexOption.IGNORE_CASE)) ||
                    STORAGE_WORDS.any { where.contains(it) }
            }
            ?: return null
        if (m.groupValues[1].any(Char::isDigit) && AMOUNT.containsMatchIn(m.groupValues[1])) return null
        val item = m.groupValues[1].trim().trim(*PUNCTUATION)
        val where = m.groupValues[2].trim().trim(*PUNCTUATION)
        if (item.isEmpty() || where.isEmpty()) return null
        return QuickAddDraft.Kept(item.capitalized(), where.capitalized())
    }

    private fun parseHousehold(text: String): QuickAddDraft.Household? {
        if (CURRENCY_MARKER.containsMatchIn(text)) return null
        val lower = text.lowercase()
        val type = SUPPLY_WORDS.entries.firstOrNull { (word, _) -> Regex("\\b$word\\b").containsMatchIn(lower) }?.value ?: return null
        val words = text.split(" ").toMutableList()
        val expiry = extractExpiry(words)
        var rest = words.filterNot { it.normalizedWord() in ACQUIRE_VERBS }.joinToString(" ")
        val quantity = (QUANTITY.find(rest) ?: COUNT.find(rest))?.let { m ->
            rest = rest.removeRange(m.range)
            "${m.groupValues[1]} ${m.groupValues.getOrNull(2)?.lowercase()?.ifEmpty { null } ?: "units"}"
        }
        val title = rest.trim().replace(WHITESPACE, " ").capitalized().ifEmpty { type }
        return QuickAddDraft.Household(title, quantity, expiry, type)
    }

    private fun parseDream(text: String): QuickAddDraft.Dream? {
        val body = (DREAM_PREFIX.matchEntire(text) ?: return null).groupValues[1]
        val words = body.split(" ").toMutableList()
        val date = extractDate(words, endOfMonth = true)
        var rest = words.joinToString(" ")
        val amountMatch = AMOUNT.find(rest)
        val amount = amountMatch?.let { amountOf(rest) }
        if (amountMatch != null) rest = rest.removeRange(amountMatch.range)
        val title = rest.trim().trim(*PUNCTUATION).replace(WHITESPACE, " ").capitalized()
        if (title.isEmpty()) return null
        val lower = title.lowercase()
        val kind = DREAM_KIND_WORDS.entries.firstOrNull { (word, _) -> lower.contains(word) }?.value ?: "Other"
        return QuickAddDraft.Dream(title, kind, amount, date)
    }

    /** "gifting" → "Gifts", "trip" → "Travel"; anything else is kept (it may be a dream's name). */
    private fun pocketName(raw: String): String {
        val cleaned = raw.trim().trim(*PUNCTUATION).removeSuffix(" fund").removeSuffix(" budget").removeSuffix(" envelope").trim()
        return POCKET_SYNONYMS[cleaned.lowercase()] ?: cleaned.capitalized()
    }

    private fun parseCooked(text: String): QuickAddDraft.Cooked? {
        if (CURRENCY_MARKER.containsMatchIn(text)) return null
        var dish = COOKED.matchEntire(text)?.groupValues?.get(1) ?: return null
        var servings: Int? = null
        // Peel off trailing "today", "for dinner", "for 4" in any order.
        while (true) {
            val tail = COOKED_TAIL.find(dish) ?: break
            tail.groupValues[1].toIntOrNull()?.let { servings = it }
            dish = dish.removeRange(tail.range).trim()
        }
        if (dish.isEmpty()) return null
        return QuickAddDraft.Cooked(dish.capitalized(), servings)
    }

    private fun parseRunningOut(text: String): QuickAddDraft? {
        val item = (OUT_OF.matchEntire(text) ?: ITEM_OVER.matchEntire(text))?.groupValues?.get(1)?.trim() ?: return null
        // "need to call the plumber" is a task, not something to buy.
        if (item.startsWith("to ", ignoreCase = true)) return null
        return shoppingOf(item)
    }

    /** "500g peanuts", "get 2 kg rice": a pack size only makes sense for something to buy. */
    private fun parseMeasured(text: String): QuickAddDraft.Shopping? {
        if (QUANTITY.find(text) == null || CURRENCY_MARKER.containsMatchIn(text)) return null
        return shoppingOf(text.replace(BUY_VERB, ""))
    }

    /**
     * An item to buy with its quantity peeled off: "500g of peanuts", "2 onions", "a dozen eggs".
     * Null when numbers are left over ("need 5000 for rent"), so money and dates parse elsewhere.
     */
    private fun shoppingOf(raw: String): QuickAddDraft.Shopping? {
        if (CURRENCY_MARKER.containsMatchIn(raw)) return null
        var item = raw.trim()
        val quantity = QUANTITY.find(item)?.let { m ->
            item = item.removeRange(m.range)
            "${m.groupValues[1]} ${SHOPPING_UNITS[m.groupValues[2].lowercase()] ?: "pcs"}"
        } ?: LEADING_COUNT.find(item)?.let { m ->
            item = item.removeRange(m.range)
            val n = m.groupValues[1].let { if (it.equals("a dozen", ignoreCase = true)) "12" else it }
            "$n pcs"
        }
        item = item.trim().removePrefix("of ").trim().replace(WHITESPACE, " ")
        if (item.isEmpty() || item.any(Char::isDigit)) return null
        return QuickAddDraft.Shopping(item.capitalized(), quantity)
    }

    private fun parseEvent(text: String): QuickAddDraft.Event? {
        // "Riya birthday gift 2000" is spending, not an event.
        if (CURRENCY_MARKER.containsMatchIn(text) || text.first().isDigit() || hasBareAmount(text)) return null
        val words = text.split(" ").toMutableList()
        val kind = words.firstNotNullOfOrNull { EVENT_KINDS[it.normalizedWord()] } ?: return null
        val date = extractDate(words)
        return QuickAddDraft.Event(words.joinToString(" ").trim().capitalized().ifEmpty { kind }, kind, date)
    }

    /** A 3+ digit number that isn't a year right after a month ("March 2027") reads as money. */
    private fun hasBareAmount(text: String): Boolean {
        val words = text.split(" ").map { it.normalizedWord() }
        return words.withIndex().any { (i, w) ->
            w.length >= 3 && w.all(Char::isDigit) && !(w.length == 4 && words.getOrNull(i - 1) in MONTHS)
        }
    }

    private fun parseMedicine(text: String): QuickAddDraft.Medicine? {
        val words = text.split(" ").toMutableList()
        if (words.none { it.normalizedWord() in MEDICINE_WORDS }) return null
        if (CURRENCY_MARKER.containsMatchIn(text)) return null // "₹120 crocin" is an expense
        val expiry = extractExpiry(words)
        val forName = extractFor(words)
        val title = words.filterNot { it.normalizedWord() in ACQUIRE_VERBS }.joinToString(" ").trim().capitalized()
        return QuickAddDraft.Medicine(title.ifEmpty { "Medicine" }, forName, expiry)
    }

    private fun parseGrocery(text: String): QuickAddDraft.Grocery? {
        val words = text.split(" ").toMutableList()
        val hasExpiry = words.any { it.normalizedWord() in EXPIRY_WORDS }
        val bought = words.firstOrNull()?.normalizedWord() in ACQUIRE_VERBS
        if (!hasExpiry && !bought) return null
        val expiry = extractExpiry(words)
        var rest = words.filterNot { it.normalizedWord() in ACQUIRE_VERBS }.joinToString(" ")
        val quantity = QUANTITY.find(rest)?.let { m ->
            rest = rest.removeRange(m.range)
            "${m.groupValues[1]} ${m.groupValues[2].lowercase()}"
        }
        return QuickAddDraft.Grocery(rest.trim().replace(WHITESPACE, " ").capitalized().ifEmpty { "Item" }, quantity, expiry)
    }

    /** Expenses, or subscriptions when a frequency is mentioned ("Netflix 649 monthly"). */
    private fun parseMoney(text: String): QuickAddDraft? {
        val match = AMOUNT.find(text) ?: return null
        val currencyToken = match.groups[1]?.value ?: match.groups[4]?.value
        var amount = match.groupValues[2].replace(",", "").toBigDecimalOrNull() ?: return null
        if (match.groups[3] != null) amount = amount.multiply(BigDecimal(1000))

        val said = text.removeRange(match.range).split(" ").filter { it.isNotBlank() }
        val (paidWith, card, payWords) = paymentIn(said)
        val words = said.filterIndexed { i, _ -> i !in payWords }
        val normalized = words.map { it.normalizedWord() }
        val category = normalized.firstNotNullOfOrNull { CATEGORY_BY_KEYWORD[it] }
        val frequency = frequencyIn(normalized)

        // A bare number inside a sentence ("call 2 plumbers") isn't money unless something says so.
        val looksLikeMoney = currencyToken != null ||
            match.range.first == 0 ||
            normalized.firstOrNull() in SPEND_VERBS ||
            normalized.any { it in MONEY_WORDS } ||
            category != null ||
            frequency != null
        if (!looksLikeMoney) return null

        val currency = currencyToken?.let(::currencyCode)
        val merchant = merchantFrom(words)
        if (frequency != null) {
            return QuickAddDraft.Subscription((merchant ?: category ?: "Subscription").capitalized(), amount, currency, frequency)
        }
        val sub = normalized.firstNotNullOfOrNull { SUBCATEGORY_BY_WORD[it] }?.takeIf { (cat, _) -> category == null || cat == category }
        return QuickAddDraft.Expense(amount, currency, category ?: sub?.first ?: "Other", merchant, text, sub?.second, paidWith, card)
    }

    /**
     * "by card", "on HDFC card", "via gpay", "cash": how it was paid, the card's name if said, and
     * which words to leave out of the merchant.
     */
    private fun paymentIn(words: List<String>): Triple<String?, String?, Set<Int>> {
        val n = words.map { it.normalizedWord() }
        n.indexOfFirst { it in UPI_WORDS }.takeIf { it >= 0 }?.let { return Triple("UPI", null, setOf(it)) }
        n.indexOfFirst { it == "cash" }.takeIf { it >= 0 }?.let { return Triple("Cash", null, setOf(it)) }
        n.indexOfFirst { it in setOf("netbanking", "neft", "imps") }.takeIf { it >= 0 }?.let { return Triple("Bank transfer", null, setOf(it)) }
        val i = n.indexOfFirst { it == "card" || it == "cc" }
        if (i < 0) return Triple(null, null, emptySet())
        val drop = mutableSetOf(i)
        var name: String? = null
        var j = i - 1
        while (j >= 0 && n[j] in setOf("credit", "debit")) { drop += j; j-- }
        if (j >= 0 && n[j] !in setOf("my", "the", "by", "on", "with", "via", "a", "using", "from") && n[j].none { it.isDigit() }) {
            name = words[j].trim(*PUNCTUATION).replaceFirstChar { it.uppercaseChar() }
            drop += j
        }
        return Triple("Card", name, drop)
    }

    private fun parseTask(text: String): QuickAddDraft.Task {
        var rest = TASK_PREFIX.replace(text, "")
        // "every sunday" repeats weekly from the coming Sunday; "daily", "every month"… just repeat.
        val repeat = TASK_REPEAT.find(rest)?.let { m ->
            val unit = m.groupValues[1].ifEmpty { m.groupValues[2] }.lowercase()
            rest = rest.removeRange(m.range).trim() + if (unit in WEEKDAYS) " $unit" else ""
            when (unit) {
                "day", "daily", "everyday" -> "Daily"
                "month", "monthly" -> "Monthly"
                else -> "Weekly"
            }
        }
        // "urgent", "asap", "high priority" make it High; "medium priority" Medium.
        val priority = TASK_PRIORITY.find(rest)?.let { m ->
            rest = rest.removeRange(m.range).trim()
            if (m.value.lowercase().startsWith("medium")) "Medium" else "High"
        }
        val words = rest.split(" ").filter { it.isNotEmpty() }.toMutableList()
        val due = extractDate(words)
        val title = words.joinToString(" ").trim().trimEnd(',', '-').capitalized()
        return QuickAddDraft.Task(title.ifEmpty { text }, due, repeat, priority)
    }

    // -----------------------------------------------------------------------------------------
    // Pieces

    private fun frequencyIn(words: List<String>): String? {
        words.forEachIndexed { i, w ->
            FREQUENCY_WORDS[w]?.let { return it }
            val unit = FREQUENCY_UNITS[w]
            if (unit != null && i > 0 && words[i - 1] in setOf("per", "every", "a", "each", "/")) return unit
        }
        return null
    }

    /** Removes "expires …" (and the date after it) from [words] and returns that date. */
    private fun extractExpiry(words: MutableList<String>): LocalDate? {
        val index = words.indexOfFirst { it.normalizedWord() in EXPIRY_WORDS }
        if (index < 0) return null
        val tail = words.subList(index + 1, words.size).toMutableList()
        val date = extractDate(tail, endOfMonth = true)
        while (words.size > index) words.removeAt(index)
        words.addAll(tail)
        return date
    }

    /** Removes "for <Name>" from [words] and returns the name. */
    private fun extractFor(words: MutableList<String>): String? {
        val index = words.indexOfFirst { it.equals("for", ignoreCase = true) }
        if (index < 0 || index == words.lastIndex) return null
        val name = words[index + 1].trim(*PUNCTUATION).capitalized()
        words.removeAt(index + 1)
        words.removeAt(index)
        return name
    }

    /**
     * Finds the first date expression in [words], removes it (with a leading "on/by/next") and
     * returns it. Understands today, tomorrow, weekdays, "12 Dec", "Dec 12", "12th December",
     * "March 2027" and "March". Month-only dates mean the month's last day when [endOfMonth].
     */
    private fun extractDate(words: MutableList<String>, endOfMonth: Boolean = false): LocalDate? {
        val today = today()
        for (i in words.indices) {
            val w = words[i].normalizedWord()
            val simple = when (w) {
                "today", "tonight" -> today
                "tomorrow", "tmrw", "tmr" -> today.plusDays(1)
                "yesterday" -> today.minusDays(1)
                else -> WEEKDAYS[w]?.let { today.with(TemporalAdjusters.nextOrSame(it)) }
            }
            if (simple != null) return simple.also { consume(words, i, 1) }

            // "expiry 2028" (batteries, documents): the end of that year.
            if (endOfMonth && w.length == 4 && w.all(Char::isDigit) && w.toInt() in 2000..2100 && words.getOrNull(i - 1)?.normalizedWord() !in MONTHS) {
                return LocalDate.of(w.toInt(), 12, 31).also { consume(words, i, 1) }
            }
            val month = MONTHS[w] ?: continue
            val before = words.getOrNull(i - 1)?.let(::dayOfMonth)
            val after = words.getOrNull(i + 1)?.let(::dayOfMonth)
            val year = words.getOrNull(i + 1)?.normalizedWord()?.let(::yearOf)
            when {
                before != null && year != null -> return safeDate(year, month, before).also { consume(words, i - 1, 3) }
                before != null -> return nextOccurrence(month, before).also { consume(words, i - 1, 2) }
                year != null -> return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth()).also { consume(words, i, 2) }
                after != null -> return nextOccurrence(month, after).also { consume(words, i, 2) }
                // A bare month ("may", "march") is too ambiguous except right after "expires".
                endOfMonth -> return nextOccurrence(month, 1).with(TemporalAdjusters.lastDayOfMonth()).also { consume(words, i, 1) }
            }
        }
        return null
    }

    private fun consume(words: MutableList<String>, start: Int, count: Int) {
        repeat(count) { words.removeAt(start) }
        if (start > 0 && words[start - 1].lowercase() in DATE_PREPOSITIONS) words.removeAt(start - 1)
    }

    private fun dayOfMonth(word: String): Int? =
        DAY_OF_MONTH.matchEntire(word.normalizedWord())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }

    private fun yearOf(word: String): Int? = when (word.length) {
        4 -> word.toIntOrNull()?.takeIf { it in 2000..2100 }
        2 -> word.toIntOrNull()?.takeIf { it in 20..99 }?.plus(2000)
        else -> null
    }

    /** 31 Feb becomes the month's last day rather than an error. */
    private fun safeDate(year: Int, month: Month, day: Int): LocalDate =
        runCatching { LocalDate.of(year, month, day) }.getOrElse { LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth()) }

    private fun nextOccurrence(month: Month, day: Int): LocalDate {
        val today = today()
        val thisYear = safeDate(today.year, month, day)
        return if (thisYear.isBefore(today)) safeDate(today.year + 1, month, day) else thisYear
    }

    private fun merchantFrom(words: List<String>): String? {
        val atIndex = words.indexOfLast { it.equals("at", ignoreCase = true) || it.equals("from", ignoreCase = true) }
        val candidates = if (atIndex >= 0) {
            // "at Toit on HDFC card": the card words are gone, so drop the "on" left hanging.
            words.drop(atIndex + 1).dropLastWhile { it.normalizedWord() in STOP_WORDS }
        } else {
            words.filter { word -> word.normalizedWord().let { it !in STOP_WORDS && it !in GENERIC_WORDS } }
        }
        return candidates.joinToString(" ").trim(*PUNCTUATION).ifEmpty { null }
    }

    private fun currencyCode(token: String): String = when (token.lowercase().removeSuffix(".")) {
        "₹", "rs", "inr" -> "INR"
        "\$", "usd" -> "USD"
        "€", "eur" -> "EUR"
        "£", "gbp" -> "GBP"
        "¥", "jpy" -> "JPY"
        else -> token.uppercase()
    }

    private fun String.normalizedWord() = lowercase().trim(*PUNCTUATION)
    private fun String.capitalized() = replaceFirstChar { it.uppercaseChar() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':', '"', '\'')

        const val CURRENCY = "(₹|\\\$|€|£|¥|rs\\.?|inr|usd|eur|gbp|jpy|aed|sgd|aud|cad)"
        val CURRENCY_MARKER = Regex("(₹|\\\$|€|£|¥|\\brs\\.?\\s?\\d|\\b(inr|usd|eur|gbp)\\b)", RegexOption.IGNORE_CASE)

        // Groups: 1 prefix currency, 2 number, 3 "k" (thousands), 4 suffix currency.
        // Possessive quantifiers stop "5pm", "12:30" and "5kg" from matching as a shorter number.
        val AMOUNT = Regex(
            "(?<![\\w.:])$CURRENCY?\\s?(\\d[\\d,]*+(?:\\.\\d+)?+)(k(?![a-z]))?" +
                "(?:\\s?$CURRENCY(?![a-z]))?(?!\\s?(?:am|pm|%|st|nd|rd|th|kg|g|gm|l|ml|pcs|mg)\\b)(?!:)",
            RegexOption.IGNORE_CASE,
        )
        val QUANTITY = Regex("(\\d+(?:\\.\\d+)?)\\s?(kg|g|gm|l|ml|pcs|pack|packs|dozen|units|unit|rolls|boxes)\\b", RegexOption.IGNORE_CASE)
        // "2 onions", "a dozen eggs": a small leading count. "need 500 for rent" stays money.
        val LEADING_COUNT = Regex("^(\\d{1,2}|a dozen)\\s+(?=\\D)", RegexOption.IGNORE_CASE)
        val BUY_VERB = Regex("^(?:please\\s+)?(?:get|grab|order|pick up|bring|buy|need|want)\\s+(?:some\\s+)?", RegexOption.IGNORE_CASE)
        /** Parser units mapped onto the shopping form's unit chips. */
        val SHOPPING_UNITS = mapOf(
            "g" to "g", "gm" to "g", "kg" to "kg", "l" to "L", "ml" to "ml", "pcs" to "pcs", "units" to "pcs", "unit" to "pcs",
            "pack" to "pack", "packs" to "pack", "dozen" to "pcs", "rolls" to "pcs", "boxes" to "pack",
        )
        val COUNT = Regex("(?<![\\w.])(\\d{1,3})(?![\\d.,])()")
        val DAY_OF_MONTH = Regex("(\\d{1,2})(?:st|nd|rd|th)?")

        val ADD_TO = Regex(
            "^(?:please )?add (.+) (?:to|in|on)(?: the| my| our)? (.+?)(?: list)?$",
            RegexOption.IGNORE_CASE,
        )
        val OUT_OF = Regex(
            "^(?:we(?:['’]re| are) |i(?:['’]m| am) )?(?:ran out of|out of|no more|need to buy|need|buy) (.+)$",
            RegexOption.IGNORE_CASE,
        )
        val ITEM_OVER = Regex(
            "^(.+?) (?:is |are |has |have )?(?:over|finished|empty|khatam|running low|almost over|ran out|run out)$",
            RegexOption.IGNORE_CASE,
        )
        val CRAVING = Regex(
            "^(?:i\\s+|we\\s+)?(?:want|wanna|would like|feel like|am craving|'m craving|craving|in the mood for)\\s+(?:to\\s+)?(?:eat|eating|have|having|for)\\s+(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val CRAVING_TASTE = Regex(
            "^((?:something|anything)\\s+(?:crispy|crunchy|spicy|tangy|sweet|savoury|savory|healthy|light)\\b.*)$",
            RegexOption.IGNORE_CASE,
        )
        val ASK_COOK = Regex(
            "^(?:(?:what|which)(?: dish(?:es)?)? (?:can|should|shall|could) (?:i|we) (?:cook|make)|what to (?:cook|make)|suggest (?:a |some )?(?:dish|dishes|recipe|recipes|something to cook)|(?:any )?(?:dinner|lunch|breakfast) ideas)\\b.*$",
            RegexOption.IGNORE_CASE,
        )
        val ASK_EXPIRING = Regex("^what(?:'s| is| all is)? (?:expiring|about to expire|going (?:bad|off)).*$", RegexOption.IGNORE_CASE)
        val ASK_LEFT = Regex(
            "^how much(?: money| budget)?(?: is| do (?:we|i) have)?(?: left| remaining| available)?(?: (?:for|in|on)(?: the| my| our)? (.+))?$",
            RegexOption.IGNORE_CASE,
        )
        // "I'm feeling feverish", "I have a headache", "Riya has loose motions", "burnt my hand", "my head hurts".
        val SYMPTOM_SENTENCE = Regex(
            "^(?:(\\w+)\\s+(?:is|has|got|feels|have)\\s+.+|(?:i am|i'm|im|feeling|i feel|i have|i've got|i got|got|having|suffering from|my)\\b.+|(?:burnt|burned|cut|twisted|sprained|scalded)\\b.+|.+\\b(?:hurts|is paining|aches)\\b.*)$",
            RegexOption.IGNORE_CASE,
        )
        val ACTIVITY_WORDS = linkedMapOf(
            "walk" to "Walk", "walked" to "Walk", "stroll" to "Walk", "ran" to "Run", "run" to "Run", "jog" to "Run", "jogged" to "Run",
            "cycl" to "Cycling", "biked" to "Cycling", "yoga" to "Yoga", "gym" to "Gym", "workout" to "Gym", "worked out" to "Gym",
            "exercise" to "Gym", "badminton" to "Sports", "cricket" to "Sports", "football" to "Sports", "tennis" to "Sports",
            "swim" to "Swim", "swam" to "Swim", "danc" to "Dance", "garden" to "Gardening", "housework" to "Housework",
            "cleaned the house" to "Housework", "chores" to "Housework",
        )
        val DURATION = Regex("\\b(\\d+(?:\\.\\d+)?|an|a)\\s?(minutes|minute|mins|min|m|hours|hour|hrs|hr|h)\\b")
        val STEPS = Regex("\\b(\\d[\\d,]*)\\s?steps\\b")
        val TRIP_START = Regex(
            "^(?:start(?:ing)?|plan(?:ning)?|new|begin)\\s+(?:a\\s+|an\\s+|the\\s+)?(?:trip|outing|holiday|vacation)(?:\\s+to|\\s*:)?\\s+(.+)$|^(?:trip|outing)\\s*:\\s*(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val SUBCATEGORY_BY_WORD: Map<String, Pair<String, String>> = mapOf(
            "breakfast" to ("Dining" to "Breakfast"), "lunch" to ("Dining" to "Lunch"), "dinner" to ("Dining" to "Dinner"),
            "snacks" to ("Dining" to "Snacks"), "coffee" to ("Dining" to "Coffee"), "drinks" to ("Dining" to "Drinks"),
            "uber" to ("Transport" to "Cab"), "ola" to ("Transport" to "Cab"), "cab" to ("Transport" to "Cab"), "taxi" to ("Transport" to "Cab"),
            "auto" to ("Transport" to "Auto"), "rapido" to ("Transport" to "Auto"), "petrol" to ("Transport" to "Fuel"), "fuel" to ("Transport" to "Fuel"),
            "diesel" to ("Transport" to "Fuel"), "parking" to ("Transport" to "Parking"), "toll" to ("Transport" to "Toll"), "metro" to ("Transport" to "Metro"),
            "clothes" to ("Shopping" to "Clothes"), "shoes" to ("Shopping" to "Shoes"), "souvenirs" to ("Shopping" to "Souvenirs"),
            "hotel" to ("Travel" to "Hotel"), "flight" to ("Travel" to "Flights"), "tickets" to ("Entertainment" to "Events"), "movie" to ("Entertainment" to "Movies"),
            "electricity" to ("Bills" to "Electricity"), "wifi" to ("Bills" to "Internet"), "internet" to ("Bills" to "Internet"),
            "vegetables" to ("Groceries" to "Vegetables"), "fruits" to ("Groceries" to "Fruits"), "milk" to ("Groceries" to "Dairy"),
            "doctor" to ("Health" to "Doctor"), "pharmacy" to ("Health" to "Medicines"), "gym" to ("Health" to "Gym"),
        )
        val GOING_TO = Regex(
            "^(?:(?:i am|i'm|im|we are|we're|am|we|i)\\s+)?(?:going|heading|off|driving|walking|on (?:my|our) way)\\s+(?:to|towards|near|around)\\s+(?:the\\s+)?(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val NEAR = Regex(
            "^(?:what(?:'s| is| all is)?|anything|places|show(?: me)?(?: places)?|suggest(?: places)?)\\s+(?:good\\s+)?(?:to (?:do|try|visit|eat)\\s+)?(?:near|around|nearby)\\s+(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val NOT_A_PLACE = setOf("pay", "call", "buy", "get", "cook", "make", "do", "book", "clean", "take", "meet", "send", "watch", "be", "sleep", "fix", "start", "finish")
        val ASK_WHERE = Regex("^where(?:'s| is| are| did (?:i|we) (?:keep|put|leave)| do (?:we|i) keep)(?: the| my| our)? (.+?)(?: kept)?$", RegexOption.IGNORE_CASE)
        val KEPT_VERB = Regex(
            "^(?:i |we )?(?:kept|keep|stored|store|placed|hid|left|put)\\s+(?:the |my |our )?(.+?)\\s+(?:in|inside|at|on|under|behind|with)\\s+(?:the |my |our )?(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val KEPT_IS = Regex(
            "^(?:the |my |our )?(.+?)\\s+(?:is|are)\\s+(?:kept |stored |placed )?(?:in|inside|at|on|under|behind|with)\\s+(?:the |my |our )?(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val STORAGE_WORDS = listOf(
            "locker", "drawer", "wardrobe", "cupboard", "almirah", "box", "shelf", "bag", "safe", "cabinet", "room",
            "suitcase", "bank", "attic", "loft", "store", "trunk", "pouch", "bowl", "stand", "rack", "car", "office",
            "fridge", "freezer", "jar", "container", "tin", "dabba", "basket", "file", "folder",
        )
        val SUPPLY_WORDS = linkedMapOf(
            "sunscreen" to "Cosmetics & skincare", "sunblock" to "Cosmetics & skincare", "moisturiser" to "Cosmetics & skincare",
            "moisturizer" to "Cosmetics & skincare", "face cream" to "Cosmetics & skincare", "night cream" to "Cosmetics & skincare",
            "serum" to "Cosmetics & skincare", "lipstick" to "Cosmetics & skincare", "lip balm" to "Cosmetics & skincare",
            "kajal" to "Cosmetics & skincare", "eyeliner" to "Cosmetics & skincare", "mascara" to "Cosmetics & skincare",
            "foundation" to "Cosmetics & skincare", "compact" to "Cosmetics & skincare", "primer" to "Cosmetics & skincare",
            "perfume" to "Cosmetics & skincare", "nail polish" to "Cosmetics & skincare", "face pack" to "Cosmetics & skincare",
            "body lotion" to "Cosmetics & skincare", "makeup" to "Cosmetics & skincare", "concealer" to "Cosmetics & skincare",
            "batteries" to "Batteries", "battery" to "Batteries", "aaa" to "Batteries", "aa" to "Batteries",
            "bulb" to "Bulbs", "bulbs" to "Bulbs", "tubelight" to "Bulbs",
            "detergent" to "Laundry", "fabric softener" to "Laundry",
            "dishwash" to "Cleaning", "phenyl" to "Cleaning", "harpic" to "Cleaning", "floor cleaner" to "Cleaning", "garbage bags" to "Cleaning", "trash bags" to "Cleaning",
            "soap" to "Toiletries", "shampoo" to "Toiletries", "toothpaste" to "Toiletries", "toothbrush" to "Toiletries", "tissues" to "Toiletries", "tissue" to "Toiletries", "toilet paper" to "Toiletries", "razor" to "Toiletries",
            "foil" to "Kitchen", "cling film" to "Kitchen", "matchbox" to "Kitchen",
            "candles" to "Other", "pens" to "Stationery", "tape" to "Stationery",
            "band-aid" to "First aid", "bandage" to "First aid", "cotton" to "First aid", "dettol" to "First aid",
        )
        val GROWTH_ADD = Regex("^(?:add|got|received|credit|credited)\\s+(?:interest|returns?|gains?|dividend)(?: of)?\\s+(.+?)\\s+(?:to|on|for|in)\\s+(?:the |my |our )?(.+)$", RegexOption.IGNORE_CASE)
        val GROWTH_GOT = Regex("^(?:got|received|earned)\\s+(.+?)\\s+(?:interest|returns?|gains?|dividend)\\s+(?:on|from|in)\\s+(?:the |my |our )?(.+)$", RegexOption.IGNORE_CASE)
        val REVALUE = Regex("^(?:the |my |our )?(.+?)\\s+(?:is now worth|is worth|now worth|is now|current value is|value is|valued at)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val REVALUE_UPDATE = Regex("^(?:update|set)\\s+(?:the |my |our )?(.+?)(?: value)?\\s+to\\s+(.+)$", RegexOption.IGNORE_CASE)
        val ASK_AFFORD = Regex("^can (?:we|i) afford(?: the| a| an| our| my)? (.+)$", RegexOption.IGNORE_CASE)
        val MOVE = Regex("^(?:move|shift|transfer|use)\\s+(.+?)\\s+from\\s+(?:the\\s+|my\\s+|our\\s+)?(.+?)\\s+(?:to|into|for)\\s+(?:the\\s+|my\\s+|our\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        val FUND = Regex("^(?:save|put|add|keep|set aside|park|invest)\\s+(.+?)\\s+(?:to|for|into|towards|in)\\s+(?:the\\s+|my\\s+|our\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        val FOR_TAIL = Regex("\\s(?:for|towards)\\s+(?:the\\s+|my\\s+|our\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        val DREAM_PREFIX = Regex(
            "^(?:(?:start|create|make|open|new)\\s+(?:a\\s+)?gullak(?:\\s+for)?|gullak\\s+for|dream|goal|vision|bucket list|we want to|i want to|want to|plan to|planning to|save up for|save for|saving for)\\s*[:\\-]?\\s+(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val CREDIT_WORDS = linkedMapOf(
            "gift card" to "Gift card", "giftcard" to "Gift card", "gift voucher" to "Voucher", "voucher" to "Voucher",
            "coupon" to "Coupon", "cashback" to "Cashback", "reward points" to "Card points", "points" to "Card points", "credit note" to "Voucher",
        )
        val POCKET_SYNONYMS = mapOf(
            "gifting" to "Gifts", "gift" to "Gifts", "gifts" to "Gifts", "presents" to "Gifts",
            "travel" to "Travel", "trip" to "Travel", "trips" to "Travel", "holiday" to "Travel", "vacation" to "Travel",
            "dining" to "Dining", "eating out" to "Dining", "food" to "Dining", "restaurants" to "Dining",
            "groceries" to "Groceries", "grocery" to "Groceries", "transport" to "Transport", "travelling" to "Travel",
            "shopping" to "Shopping", "entertainment" to "Entertainment", "movies" to "Entertainment",
            "health" to "Health", "medicines" to "Health", "bills" to "Bills", "home" to "Home", "rent" to "Rent",
            "savings" to "Gullak", "pot" to "Gullak", "savings pot" to "Gullak", "gullak" to "Gullak",
            "fd" to "FD", "mf" to "Mutual fund", "sip" to "Mutual fund", "mutual fund" to "Mutual fund",
        )
        val DREAM_KIND_WORDS = linkedMapOf(
            "car" to "Car", "bike" to "Car", "trip" to "Travel", "travel" to "Travel", "visit" to "Travel", "go to" to "Travel",
            "holiday" to "Travel", "house" to "Home", "home" to "Home", "flat" to "Home", "phone" to "Gadget", "laptop" to "Gadget",
            "tv" to "Gadget", "wedding" to "Wedding", "course" to "Education", "mba" to "Education", "college" to "Education",
            "concert" to "Experience",
            "emergency" to "Emergency", "rainy day" to "Emergency", "medical" to "Emergency",
            "diwali" to "Festival", "festival" to "Festival", "holi" to "Festival", "eid" to "Festival", "christmas" to "Festival", "rakhi" to "Festival",
            "savings" to "Savings", "fund" to "Savings",
        )
        val COOKED = Regex("^(?:i |we |just )?(?:made|cooked|prepared|cook)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val COOKED_TAIL = Regex(
            "\\s+(?:today|tonight|now|for (?:breakfast|lunch|dinner|everyone)|for (\\d+)(?: people)?)$",
            RegexOption.IGNORE_CASE,
        )
        val TASK_PRIORITY = Regex("\\b(?:urgent(?:ly)?|asap|important|high priority|top priority|medium priority)\\b", RegexOption.IGNORE_CASE)
        val TASK_REPEAT = Regex(
            "\\b(?:every\\s+(day|week|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)|(daily|weekly|monthly|everyday))\\b",
            RegexOption.IGNORE_CASE,
        )
        /** First words that make a line a task for sure. */
        val TASK_VERBS = setOf(
            "call", "pay", "book", "fix", "clean", "renew", "submit", "send", "email", "mail", "pick", "drop", "take",
            "return", "file", "recharge", "iron", "wash", "repair", "service", "schedule", "cancel", "check", "update",
            "apply", "finish", "complete", "meet", "remind", "collect", "deposit", "register", "print", "reply", "text",
            "message", "ask", "tell", "do", "make", "change", "replace", "install", "sort", "organise", "organize", "plan",
        )
        val TASK_PREFIX = Regex("^(?:remind me to|remind me|todo:?|to-do:?|task:?)\\s+", RegexOption.IGNORE_CASE)

        val SHOPPING_ALIASES = setOf("shopping", "grocery", "groceries", "cart")
        val CARD_BILL = Regex("\\b(?:credit\\s+)?card\\s+bill\\b|\\bcc\\s+bill\\b|\\bcard\\s+statement\\b", RegexOption.IGNORE_CASE)
        val UPI_WORDS = setOf("upi", "gpay", "phonepe", "paytm", "bhim")
        val LIST_SPLIT = Regex("\\s*,(?!\\d)\\s*|\\s+and\\s+|\\s*&\\s*", RegexOption.IGNORE_CASE)
        val BATCH_SPLIT = Regex("\\s*(?:,(?!\\d)|;)\\s*(?:and\\s+|also\\s+|then\\s+)?|\\s+(?:and also|and then|also|and|plus)\\s+", RegexOption.IGNORE_CASE)
        val CHANGE_DELETE = Regex(
            "^(?:please\\s+)?(?:delete|remove|cancel|scrap|erase|discard|undo|get rid of|stop|trash|bin|drop|scratch|wipe out|wipe|strike off|strike out|take off|cross out|hatao|hata do|mita do|nikal do)\\s+(.+)$",
            RegexOption.IGNORE_CASE,
        )
        /** "Uber expense delete karo", "milk ko hata do": the thing first, then the Hindi verb. */
        val DELETE_HINGLISH = Regex(
            "^(.+?)\\s+(?:ko\\s+)?(?:delete|remove|cancel|hatao|hata do|hata de|mita do|nikal do|nikalo)(?:\\s+(?:karo|kar do|kardo|kar de|karna|please|na))?$",
            RegexOption.IGNORE_CASE,
        )
        val RISKY_DELETE_VERBS = listOf("drop", "scratch", "trash", "bin", "wipe", "strike", "take off", "cross out")
        val CHANGE_SET = Regex(
            "^(?:please\\s+)?(?:fix|correct|set|make|adjust|change|update|edit)\\s+(.+?)\\s+(?:to\\s+|as\\s+|=\\s*)?((?:₹|rs\\.?\\s?|inr\\s?)?\\d[\\d,]*(?:\\.\\d+)?k?|today|tomorrow|yesterday|next week|monday|tuesday|wednesday|thursday|friday|saturday|sunday)$",
            RegexOption.IGNORE_CASE,
        )
        val VALUE_WORDS = setOf("amount", "price", "cost", "value", "date", "total", "bill")
        val ADD_FOR = Regex("^(?:add|put|log|note|record)\\s+(?:an?\\s+)?(?:expense\\s+(?:of\\s+)?)?(?:₹|rs\\.?\\s?)?\\d[\\d,]*(?:\\.\\d+)?k?\\s+(?:rupees\\s+|rs\\s+)?(?:for|on)\\s+", RegexOption.IGNORE_CASE)
        val MONEY_WORDS = setOf("expense", "rupees", "rupee", "rs", "inr", "spent", "paid", "shelled", "forked")
        val POLITE = Regex("^(?:(?:hey|ok|okay)\\s+)?(?:(?:can|could|will|would)\\s+you\\s+)?(?:(?:please|pls|plz|kindly)\\s+)?", RegexOption.IGNORE_CASE)
        val DELETE_IT_TAIL = Regex(
            "^(.+?)[,\\s-]+(?:(?:can|could|will|would)\\s+you\\s+)?(?:please\\s+)?(?:delete|remove|cancel|get rid of)\\s+(?:it|this|that|this one|that one)(?:\\s+please)?$",
            RegexOption.IGNORE_CASE,
        )
        val CHANGE_DONE = Regex("^(?:please\\s+)?mark\\s+(.+?)\\s+(?:as\\s+)?(done|complete|completed|finished|bought|purchased|watched|read|paid|achieved)$", RegexOption.IGNORE_CASE)
        val CHANGE_END_TRIP = Regex("^(?:end|finish|close|wrap up)\\s+(?:the\\s+|our\\s+)?(.+?\\b(?:trip|outing))$|^(?:the\\s+|our\\s+)?(.+?\\b(?:trip|outing))\\s+(?:is\\s+)?(?:over|ended|finished)$", RegexOption.IGNORE_CASE)
        val CHANGE_DONE_TAIL = Regex("^(.+?)\\s+(?:is\\s+|are\\s+|has been\\s+|was\\s+)?(done|completed)$", RegexOption.IGNORE_CASE)
        val CHANGE_EDIT = Regex("^(?:please\\s+)?(?:edit|change|update|modify|reschedule|rename)\\s+(.+?)(?:\\s+(?:to|as|into)\\s+(.+))?$", RegexOption.IGNORE_CASE)
        val CHANGE_LINK = Regex(
            "^(?:add|put|move|link|tag|club)\\s+((?:the |this |that |my |last |latest |recent )*.*?\\b(?:expense|expenses|spend|spends|payment|bill))\\s+(?:to|under|in|into|with)\\s+(?:the\\s+|my\\s+|our\\s+)?(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val FROM_LIST = Regex("^(.+?)\\s+(?:from|off|in|on)\\s+(?:the\\s+|my\\s+|our\\s+)?(.+?)$", RegexOption.IGNORE_CASE)
        val CHANGE_KINDS = mapOf(
            "expense" to "expense", "spend" to "expense", "payment" to "expense", "bill" to "expense", "transaction" to "expense",
            "task" to "task", "todo" to "task", "to-do" to "task", "chore" to "task",
            "event" to "event", "birthday" to "event", "anniversary" to "event", "appointment" to "event",
            "subscription" to "subscription", "recipe" to "recipe", "dish" to "recipe", "medicine" to "medicine",
            "dream" to "dream", "goal" to "dream", "trip" to "trip", "outing" to "trip", "place" to "place",
            "item" to null,
        ).filterValues { it != null }.mapValues { it.value!! }
        val CHANGE_FILLERS = setOf(
            "the", "my", "our", "this", "that", "last", "latest", "recent", "a", "an", "item", "entry", "one",
            "amount", "price", "cost", "value", "date", "total", "of", "ko",
        )
        val INVESTMENT_WORDS = setOf("fd", "rd", "sip", "ppf", "deposit", "fund", "mf", "stocks", "gold")
        val SPEND_VERBS = setOf("spent", "paid", "spend", "pay", "gave")
        val ACQUIRE_VERBS = setOf("bought", "got", "new", "add", "added")
        val EXPIRY_WORDS = setOf("expires", "expiry", "exp", "expiring", "expire", "expired")
        val STOP_WORDS = setOf(
            "spent", "paid", "bought", "gave", "on", "for", "at", "to", "the", "a", "an", "via", "using", "with", "rs",
            // Said around the amount, never part of a name: "can you add an expense of 500 rupees for uber".
            "can", "could", "would", "will", "you", "please", "pls", "plz", "kindly", "i", "we", "me", "my", "our", "just",
            "add", "added", "log", "logged", "put", "note", "noted", "record", "recorded", "expense", "expenses", "of",
            "rupees", "rupee", "inr", "shelled", "forked", "out", "over", "spend", "pay", "today", "yesterday", "tonight", "by",
            "per", "every", "each", "monthly", "yearly", "annually", "weekly", "quarterly", "month", "year", "week", "quarter",
        )
        val DATE_PREPOSITIONS = setOf("on", "by", "this", "next", "till", "until", "before")
        val FREQUENCY_WORDS = mapOf("monthly" to "Monthly", "yearly" to "Yearly", "annually" to "Yearly", "annual" to "Yearly", "weekly" to "Weekly", "quarterly" to "Quarterly")
        val FREQUENCY_UNITS = mapOf("month" to "Monthly", "year" to "Yearly", "week" to "Weekly", "quarter" to "Quarterly")
        val EVENT_KINDS = mapOf(
            "birthday" to "Birthday", "bday" to "Birthday", "anniversary" to "Anniversary",
            "renewal" to "Renewal", "renew" to "Renewal", "appointment" to "Appointment",
        )
        val MEDICINE_WORDS = setOf(
            "medicine", "medicines", "tablet", "tablets", "tab", "tabs", "syrup", "capsule", "capsules", "ointment", "drops",
            "crocin", "dolo", "paracetamol", "combiflam", "cetirizine", "ibuprofen", "vicks", "digene", "calpol", "benadryl",
            "antacid", "ors", "vitamin", "vitamins", "inhaler",
        )
        val WEEKDAYS = mapOf(
            "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY,
        )
        val MONTHS: Map<String, Month> = Month.entries.flatMap { month ->
            val full = month.name.lowercase()
            listOf(full to month, full.take(3) to month) + if (full.length > 4) listOf(full.take(4) to month) else emptyList()
        }.toMap()

        // Brand names map to a category but are kept as the merchant; generic words are not.
        val BRAND_KEYWORDS = mapOf(
            "Groceries" to listOf("dmart", "bigbasket", "blinkit", "zepto", "instamart", "walmart", "tesco", "costco"),
            "Dining" to listOf("swiggy", "zomato", "starbucks", "mcdonalds", "dominos"),
            "Transport" to listOf("uber", "ola", "rapido", "lyft"),
            "Shopping" to listOf("amazon", "flipkart", "myntra", "ikea"),
            "Entertainment" to listOf("netflix", "spotify", "prime", "hotstar", "bookmyshow", "youtube"),
            "Travel" to listOf("airbnb", "makemytrip", "booking"),
        )
        val GENERIC_KEYWORDS = mapOf(
            "Groceries" to listOf("grocery", "groceries", "vegetables", "veggies", "fruits", "milk", "supermarket"),
            "Dining" to listOf("dinner", "lunch", "breakfast", "restaurant", "cafe", "coffee", "food", "snacks"),
            "Transport" to listOf("taxi", "cab", "auto", "fuel", "petrol", "diesel", "metro", "bus", "train", "parking", "toll"),
            "Bills" to listOf("electricity", "water", "gas", "internet", "wifi", "broadband", "mobile", "recharge", "bill"),
            "Rent" to listOf("rent", "emi", "maintenance"),
            "Health" to listOf("medicine", "medicines", "pharmacy", "doctor", "hospital", "clinic", "lab", "dentist", "gym"),
            "Shopping" to listOf("clothes", "shoes", "shopping"),
            "Entertainment" to listOf("movie", "movies", "concert", "tickets"),
            "Home" to listOf("furniture", "repair", "plumber", "electrician", "maid", "cleaning"),
            "Travel" to listOf("flight", "hotel", "trip", "travel", "visa"),
            "Gifts" to listOf("gift", "gifts", "present"),
        )
        val GENERIC_WORDS = GENERIC_KEYWORDS.values.flatten().toSet()
        val CATEGORY_BY_KEYWORD = (BRAND_KEYWORDS.entries + GENERIC_KEYWORDS.entries)
            .flatMap { (category, words) -> words.map { it to category } }
            .toMap()
    }
}
