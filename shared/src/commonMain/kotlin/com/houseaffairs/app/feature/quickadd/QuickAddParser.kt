package com.houseaffairs.app.feature.quickadd

import com.houseaffairs.app.core.data.formatClock
import com.houseaffairs.app.core.data.minuteOfDay
import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.Decimal
import com.houseaffairs.app.core.util.decimalOf
import com.houseaffairs.app.core.util.plainString
import com.houseaffairs.app.core.util.times
import kotlinx.datetime.DayOfWeek

enum class ListTemplate(
    /** Stored on each item; kept stable so older entries still match. */
    val label: String,
    val aliases: Set<String>,
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
    // Courses, skills and exams; finishing one counts toward "Keep learning" on the live-well wheel.
    STUDY("Study", setOf("study", "learn", "learning", "course", "courses", "skill", "skills", "exam", "exams"), "Learn & study", "🎓", "Learnt it"),
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
            STUDY -> "Mark learnt"
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
        val amount: Decimal,
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
        /** The day when said ("yesterday", "last night", "on 3 Nov"); null means today. */
        val date: LocalDate? = null,
    ) : QuickAddDraft

    /** "Walked 30 min", "45 minutes yoga", "gardening for an hour", "did 8000 steps". */
    data class Activity(val kind: String, val minutes: Int?, val steps: Int?) : QuickAddDraft

    /** "Start trip Goa" / "outing: Sunday picnic this sunday". */
    /** [places] is the day's plan in order, when said ("…to Cubbon Park, Lalbagh and MTR"). */
    data class Trip(val title: String, val start: LocalDate?, val places: String? = null) : QuickAddDraft

    data class Subscription(val title: String, val amount: Decimal, val currency: String?, val frequency: String) : QuickAddDraft
    /** [quantity] is "500 g", "2 pcs"…, or null when none was said. */
    data class Shopping(val item: String, val quantity: String? = null) : QuickAddDraft
    /** [repeat] is "Daily", "Weekly" or "Monthly" for "water plants every week", else null. */
    data class Task(
        val title: String,
        val due: LocalDate?,
        val repeat: String? = null,
        val priority: String? = null,
        val reach: String? = null,
        /** "5:30 pm" when a time was said; the task then reminds at that time. */
        val time: String? = null,
        /** Who it's for when said: "Rohan, please pay the bill", "remind Mom to…". */
        val forName: String? = null,
    ) : QuickAddDraft
    data class Event(val title: String, val kind: String, val date: LocalDate?) : QuickAddDraft
    /** [frequency] is one of `DOSE_FREQUENCIES` when said ("twice a day"); [until] ends the course ("for 5 days"). */
    data class Medicine(val title: String, val forName: String?, val expiry: LocalDate?, val frequency: String? = null, val until: LocalDate? = null) : QuickAddDraft
    data class Grocery(val title: String, val quantity: String?, val expiry: LocalDate?) : QuickAddDraft
    /** [forName] is who a gift idea is for, when said ("gift for Riya: a smart watch"). */
    data class ListItem(val template: ListTemplate, val title: String, val forName: String? = null) : QuickAddDraft

    /** "Goal: run 5k this week": a goal for this week. */
    data class Goal(val title: String) : QuickAddDraft

    /** "Good dosa at Vidyarthi Bhavan", "try Toit for beer": a place worth going back to, and what for. */
    data class Place(val title: String, val kind: String, val goodFor: String?) : QuickAddDraft

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
    data class Move(val amount: Decimal, val from: String, val to: String) : QuickAddDraft

    /** "Save 5000 for Bali": gullak → a dream (or an envelope). */
    data class Fund(val amount: Decimal, val target: String) : QuickAddDraft

    /** "Got Amazon gift card 2000 for gifting". */
    data class Credit(val title: String, val kind: String, val amount: Decimal, val forWhat: String?, val expiry: LocalDate?) : QuickAddDraft

    /** "Dream: Bali trip 150000 by March". */
    data class Dream(val title: String, val kind: String, val amount: Decimal?, val date: LocalDate?) : QuickAddDraft

    /** A question for the assistant rather than something to save. */
    data class Ask(
        val question: Question,
        val subject: String?,
        val person: String? = null,
        val count: Int? = null,
        /** For SPENT: "this month" (the default), "last month", "this week", "this year"… */
        val period: String? = null,
    ) : QuickAddDraft

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

        /** "Want to watch a movie" / "read something": picks from that list (subject = the list's name). */
        PICK,

        /** "Can I cook paneer tikka for 3?" (subject = the dish, count = people). */
        CAN_COOK,

        /** "By when can we go to Bali?" (subject = the dream). */
        WHEN_DREAM,

        /** "How long did the detergent last?" (subject = the item). */
        LASTED,

        /** "How much did we spend on groceries this month?" (subject = category or shop; null = everything). */
        SPENT,

        /** "Share my live location with Ravi" (subject = the person). */
        SHARE_LOCATION,

        /** "Do we have eggs?", "is there milk?", "any curd left?" (subject = the item). */
        HAVE,

        /** "When is Riya's birthday?" (subject = what to look up). */
        WHEN,

        /** "What's due this week?", "show my tasks" (subject = "today", "tomorrow" or "this week"). */
        AGENDA,

        /** "Anything to buy?", "what's on the shopping list?" */
        TO_BUY,

        /** "Got salary 85000", "received 2000 from mom": money coming in, which isn't spending. */
        INCOME,
    }

    /** "Won't take crocin today" / "skip crocin tonight": no doses (or reminders) that day, and the count stays. */
    data class SkipDose(val medicine: String, val day: LocalDate) : QuickAddDraft

    /** "Gold necklace is in the bank locker" / "kept spare keys in the blue drawer". */
    data class Kept(val item: String, val location: String) : QuickAddDraft

    /** Household supplies: "bought 5 AAA batteries expiry 2028". */
    data class Household(val title: String, val quantity: String?, val expiry: LocalDate?, val type: String?) : QuickAddDraft

    /** Interest or gains on an investment: "add interest 1200 to HDFC FD". */
    data class Growth(val amount: Decimal, val target: String) : QuickAddDraft

    /** "HDFC FD is now worth 52000": set an investment's current value. */
    data class Revalue(val target: String, val amount: Decimal) : QuickAddDraft
}

/**
 * Turns one line of typed or spoken text into a draft, e.g.
 * "450 groceries DMart", "Netflix 649 monthly", "milk over", "crocin for Riya expires March",
 * "Riya birthday 12 Dec", "add Dune 2 to watch list", "pay rent tomorrow".
 *
 * Rule-based and on-device for the beta. Drafts only pre-fill a form; nothing is saved unseen.
 */
/** Joins several conditions in one symptom question's subject. */
const val SYMPTOM_JOIN = "|"

class QuickAddParser(
    private val today: () -> LocalDate = ::systemToday,
    /** Minutes after midnight now, so "at 9 am" said at 10 means tomorrow; null when unknown. */
    private val nowMinute: () -> Int? = { null },
) {

    /** The spending category one word points at ("uber" → Transport), for guessing while a form is typed. */
    fun categoryOfWord(word: String): String? = CATEGORY_BY_KEYWORD[word] ?: SUBCATEGORY_BY_WORD[word]?.first

    fun parse(input: String): QuickAddDraft? {
        val text = withLakhs(input.trim().replace(WHITESPACE, " "))
        if (text.isEmpty()) return null
        return parseSkipDose(text)
            ?: parseWant(text)
            ?: parseAsk(text)
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
        return WANT_PREFIX.replace(text, "").split(" ").first().normalizedWord() !in TASK_VERBS
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
    fun parseAsNew(input: String): QuickAddDraft? = parseSingle(withLakhs(input.trim().replace(WHITESPACE, " ")))

    private fun parseSkipDose(text: String): QuickAddDraft.SkipDose? {
        val t = text.trim().trimEnd('.', '!').trim()
        val m = SKIP_DOSE.matchEntire(t) ?: SKIP_DOSE_SHORT.matchEntire(t) ?: return null
        val name = m.groupValues[1].trim().removePrefix("my ").removeSuffix(" dose").removeSuffix(" doses").trim()
        if (name.isEmpty()) return null
        val day = if (m.groupValues[2].equals("tomorrow", ignoreCase = true)) today().plusDays(1) else today()
        return QuickAddDraft.SkipDose(name.capitalized(), day)
    }

    /** "5 lakh" → "500000", "1.2 crore" → "12000000": said that way, but every amount rule reads digits. */
    private fun withLakhs(text: String): String = LAKH.replace(text) { m ->
        val n = decimalOf(m.groupValues[1]) ?: return@replace m.value
        val unit = if (m.groupValues[2].lowercase().startsWith("c")) 10_000_000L else 100_000L
        (n * decimalOf(unit)).plainString().replace(Regex("\\.0+$"), "")
    }

    /** Everything but questions, changes and batches; also used for each part of a batch. */
    private fun parseSingle(text: String): QuickAddDraft? {
        return parseGoal(text)
            ?: parseLent(text)
            ?: parsePersonTask(text)
            // "Remind me every day at 9 pm to take vitamins" is a reminder, whatever it mentions.
            ?: (if (REMINDER.containsMatchIn(text) || REMIND_START.containsMatchIn(text)) parseReminder(text) else null)
            ?: parseActivity(text)
            ?: parseTrip(text)
            ?: parseAddTo(text)
            ?: parseMove(text)
            ?: parseGrowth(text)
            ?: parseRevalue(text)
            ?: parseCredit(text)
            ?: parseFund(text)
            ?: parseKept(text)
            // "Running low on detergent" is something to buy, before detergent reads as a supply.
            ?: parseRunningOut(text)
            ?: parseHousehold(text)
            ?: parseDream(text)
            ?: parseCooked(text)
            ?: parseEvent(text)
            ?: parseMedicine(text)
            ?: parseMoney(text)
            ?: parseGrocery(text)
            ?: parseMeasured(text)
            // Plain lines that read as a list or a place, before anything falls through to a task.
            ?: parseListShortcut(text)
            ?: parsePlaceTip(text)
            ?: parseTask(text)
    }

    /** "Goal: run 5k this week", "weekly goal finish a book": this week's goal, not a savings dream. */
    private fun parseGoal(text: String): QuickAddDraft.Goal? {
        val m = GOAL_LINE.matchEntire(text.trim()) ?: return null
        val title = m.groupValues[1].replace(THIS_WEEK, "").trim().trim(*PUNCTUATION).capitalized()
        return title.takeIf { it.isNotEmpty() }?.let { QuickAddDraft.Goal(it) }
    }

    /** "Lent 5000 to Ravi" → get it back; "borrowed 2000 from Mom" → pay it back. A task, so it isn't forgotten. */
    private fun parseLent(text: String): QuickAddDraft.Task? {
        val m = LENT.matchEntire(text.trim()) ?: return null
        val amount = m.groupValues[2].trim()
        val who = m.groupValues[3].trim().trim(*PUNCTUATION).capitalized()
        val title = if (m.groupValues[1].lowercase().startsWith("borrow")) "Return $amount to $who" else "Get $amount back from $who"
        return QuickAddDraft.Task(title, null)
    }

    /** "Watch Dune 2", "read Atomic Habits", "listen to Coldplay", "gift for Riya: a smart watch". */
    private fun parseListShortcut(text: String): QuickAddDraft.ListItem? {
        val t = text.trim().trimEnd('.', '!')
        GIFT_IDEA.matchEntire(t)?.let { m ->
            val what = m.groupValues[2].trim().trim(*PUNCTUATION)
            if (what.isNotEmpty()) return QuickAddDraft.ListItem(ListTemplate.GIFTS, what.capitalized(), m.groupValues[1].trim().ifEmpty { null }?.capitalized())
        }
        val m = LIST_VERB.matchEntire(t) ?: return null
        val what = m.groupValues[2].trim().trim(*PUNCTUATION)
        if (what.isEmpty() || what.split(" ").size > 6 || what.lowercase().split(" ").first() in NOT_A_TITLE) return null
        val template = when (m.groupValues[1].lowercase()) {
            "watch", "see" -> ListTemplate.WATCH
            "read" -> ListTemplate.BOOKS
            else -> ListTemplate.MUSIC
        }
        return QuickAddDraft.ListItem(template, what.split(" ").joinToString(" ") { it.capitalized() })
    }

    /** "Good dosa at Vidyarthi Bhavan" → the place, good for dosa; "try Toit for beer" → Toit, for beer. */
    private fun parsePlaceTip(text: String): QuickAddDraft.Place? {
        val t = text.trim().trimEnd('.', '!')
        PLACE_GOOD_AT.matchEntire(t)?.let { m ->
            return QuickAddDraft.Place(m.groupValues[2].trim().split(" ").joinToString(" ") { it.capitalized() }, "Restaurant", m.groupValues[1].trim().capitalized())
        }
        PLACE_TRY.matchEntire(t)?.let { m ->
            return QuickAddDraft.Place(m.groupValues[1].trim().split(" ").joinToString(" ") { it.capitalized() }, "Restaurant", m.groupValues[2].trim().capitalized())
        }
        return null
    }

    private fun parseAddTo(text: String): QuickAddDraft? {
        val match = ADD_TO.matchEntire(text.trimEnd('.', '!', '?').trim()) ?: return null
        val what = match.groupValues[1].trim()
        val target = match.groupValues[2].trim().lowercase()
        val template = ListTemplate.entries.firstOrNull { target in it.aliases }
        if (target !in SHOPPING_ALIASES && template == null) return null
        // "milk, eggs and bread": several items. Titles like "Tom and Jerry" only split on commas.
        val parts = (if (template?.isMedia == true) what.split(",") else what.split(LIST_SPLIT).flatMap { if (template == null) groceryWords(it) else listOf(it) })
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
        counted(text)?.let { return it }
        // A reminder's own words ("remind me to call mom and pay rent", "…and remind everyday") are one reminder.
        if (REMINDER.containsMatchIn(text) || REMIND_START.containsMatchIn(text) || ANY_REMIND.containsMatchIn(text)) return null
        // "Add tomatoes, milk and paneer to groceries": one list, split by the add-to rule.
        if (ADD_TO.matchEntire(text.trimEnd('.', '!', '?').trim()) != null) return null
        val parts = text.split(BATCH_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val singles = parts.map { parseSingle(it) ?: return null }
        if (singles.any { it is QuickAddDraft.Batch || it is QuickAddDraft.Cooked || it is QuickAddDraft.Move }) return null
        // "Call mom, pay rent and buy milk": each part starts with something to do, so each is its own.
        // "Call Riya and Aman" isn't: "Aman" doesn't, so it stays one task.
        val doing = parts.map { p -> p.split(" ").first().normalizedWord().let { it in TASK_VERBS || it in BUY_WORDS } }
        val firstMoney = singles.firstOrNull { it is QuickAddDraft.Expense } as? QuickAddDraft.Expense
        val drafts = singles.mapIndexed { i, d ->
            val p = parts[i]
            when {
                d !is QuickAddDraft.Task -> d
                doing[i] -> d
                // "Bought milk 60, eggs 90": the price goes with the first one's kind of spend.
                firstMoney != null -> pricedLike(p, firstMoney) ?: return null
                // "Milk, eggs and bread": short, unknown words in a list are things to buy.
                singles.none { it is QuickAddDraft.Task && doing[singles.indexOf(it)] } && p.split(" ").size <= 3 && p.none(Char::isDigit) ->
                    shoppingOf(p) ?: QuickAddDraft.Shopping(p.capitalized())
                else -> return null
            }
        }
        return QuickAddDraft.Batch(drafts.flatMap { (it as? QuickAddDraft.Batch)?.drafts ?: listOf(it) })
    }

    /** "2 milk 1 bread 6 eggs": counts and items in a row, each one to buy. */
    private fun counted(text: String): QuickAddDraft.Batch? {
        if (CURRENCY_MARKER.containsMatchIn(text) || !COUNTED_LIST.matches(text.trim())) return null
        val items = COUNTED_ITEM.findAll(text.trim()).map { m -> QuickAddDraft.Shopping(m.groupValues[2].trim().capitalized(), "${m.groupValues[1]} pcs") }.toList()
        return items.takeIf { it.size >= 2 }?.let { QuickAddDraft.Batch(it) }
    }

    /** "eggs 90" after "bought milk 60": an item and its price, spent like the first. */
    private fun pricedLike(part: String, first: QuickAddDraft.Expense): QuickAddDraft.Expense? {
        val m = ITEM_PRICE.matchEntire(part.trim()) ?: return null
        val amount = justAmount(m.groupValues[2]) ?: return null
        val item = m.groupValues[1].trim()
        val sub = SUBCATEGORY_BY_WORD[item.lowercase()]
        return QuickAddDraft.Expense(amount, first.currency, CATEGORY_BY_KEYWORD[item.lowercase()] ?: sub?.first ?: first.category, item.capitalized(), part, sub?.second, first.paidWith, first.card)
    }

    /** "milk eggs bread" → three items, when every word is a grocery the aisles know; else one. */
    private fun groceryWords(item: String): List<String> {
        val words = item.trim().split(WHITESPACE).filter { it.isNotEmpty() && it.lowercase() !in setOf("and", "some", "a", "an") }
        if (words.size < 2 || words.any { it.any(Char::isDigit) }) return listOf(item)
        return if (words.all { com.houseaffairs.app.core.data.aisleOf(it) != com.houseaffairs.app.core.data.Aisle.OTHER }) words else listOf(item)
    }

    /**
     * "Set an alarm for 6 am", "wake me up at 6", "remind me in 30 minutes to check the oven",
     * "remind me at 6 to call Ravi", "reminder: dentist friday 4pm": a task with a time, which then
     * rings. A bare hour ("at 6") is the next 6 o'clock; "in 30 minutes" needs the clock.
     */
    private fun parseReminder(text: String): QuickAddDraft.Task {
        val alarm = ALARM_WORDS.containsMatchIn(text)
        var rest = REMIND_START.replace(text.trim().trimEnd('.', '!', '?'), "").trim()
        val now = nowMinute()
        var minute: Int? = null
        var day: LocalDate? = null
        // "in 30 minutes", "in an hour", "in 2 hours".
        IN_A_WHILE.find(rest)?.let { m ->
            val n = when (val c = m.groupValues[1].lowercase()) { "a", "an" -> 1.0; "half an" -> 0.5; else -> c.toDoubleOrNull() ?: 1.0 }
            val mins = (if (m.groupValues[2].lowercase().startsWith("h")) n * 60 else n).toInt()
            if (now != null) {
                val at = now + mins
                minute = at % (24 * 60)
                day = today().plusDays((at / (24 * 60)).toLong())
                rest = rest.removeRange(m.range).trim()
            }
        }
        // "at 6" with no am or pm: the next 6 o'clock from now.
        if (minute == null && TASK_TIME.find(rest) == null) BARE_HOUR.find(rest)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) {
                // An alarm or wake-up at "6" is the morning; a reminder is whichever 6 comes next.
                val choices = if (alarm) listOf(h % 12 * 60) else listOf(h % 12 * 60, h % 12 * 60 + 12 * 60)
                val n = now ?: -1
                minute = choices.firstOrNull { it > n } ?: choices.first()
                if (choices.none { it > n }) day = today().plusDays(1)
                rest = rest.removeRange(m.range).trim()
            }
        }
        // "on 15th", "on the 3rd": the next such date.
        ON_DAY.find(rest)?.let { m ->
            val d = m.groupValues[1].toInt()
            if (d in 1..31) {
                val t = today()
                val thisMonth = clampedDate(t.year, t.monthValue, d)
                day = if (thisMonth.isBefore(t)) clampedDate(addMonths(t.year, t.monthValue, 1).first, addMonths(t.year, t.monthValue, 1).second, d) else thisMonth
                rest = rest.removeRange(m.range).trim()
            }
        }
        // "tomorrow morning": a sensible hour when none is said.
        val partOfDay = PART_OF_DAY.find(rest)?.let { m -> rest = rest.removeRange(m.range).trim(); m.groupValues[1].lowercase() }
        val task = parseTask(rest.ifEmpty { "-" })
        val time = task.time ?: minute?.let(::formatClock) ?: if (TASK_TIME.find(rest) == null) partOfDay?.let { PART_OF_DAY_MINUTE[it] }?.let(::formatClock) else null
        var title = task.title.replace(DANGLING, "").trim()
        while (LEADING_JOIN.containsMatchIn(title)) title = title.replace(LEADING_JOIN, "").trim()
        // Only time words were said ("set an alarm for 6 am"): parseTask hands those back as the title.
        val leftover = TASK_TIME.replace(rest, " ").split(WHITESPACE).filter { it.isNotBlank() }.toMutableList()
        extractDate(leftover)
        val onlyWhen = rest.isBlank() || leftover.all { it.normalizedWord() in setOf("for", "at", "on", "to", "by", "tomorrow", "today", "tonight", "-") }
        title = (if (onlyWhen) "" else title).capitalized()
            .ifEmpty { if (WAKE.containsMatchIn(text)) "Wake up" else if (alarm) "Alarm" else "Reminder" }
        val timedDue = time?.let { minuteOfDay(it) }?.let { at -> if ((now ?: -1) >= at) today().plusDays(1) else today() }
        return task.copy(title = title, time = time, due = day ?: task.due ?: timedDue)
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
    fun amountIn(text: String): Decimal? = justAmount(text)

    private fun amountOf(text: String): Decimal? {
        val match = AMOUNT.find(text) ?: return null
        var amount = match.groupValues[2].replace(",", "").let(::decimalOf) ?: return null
        if (match.groups[3] != null) amount = amount * decimalOf(1000)
        return amount
    }

    /** True when [text] is just an amount ("2000", "₹2,000", "2k"). */
    private fun justAmount(text: String): Decimal? =
        AMOUNT.matchEntire(text.trim())?.let { amountOf(text) }

    private fun parseAsk(text: String): QuickAddDraft.Ask? {
        val t = text.trim().trimEnd('?', '.', '!').trim()
        // Paying a card bill moves money you already counted when you spent it, so it isn't an expense.
        if (CARD_BILL.containsMatchIn(t)) return QuickAddDraft.Ask(QuickAddDraft.Question.CARD_BILL, if (Regex("\\b(paid|pay|paying|cleared|settled)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) "paid" else null)
        CAN_COOK.matchEntire(t)?.let { m ->
            val dish = m.groupValues[1].ifEmpty { m.groupValues[3] }.trim().trim(*PUNCTUATION)
            if (dish.isNotEmpty() && dish.lowercase() !in setOf("something", "anything", "it", "dinner", "lunch", "breakfast")) {
                return QuickAddDraft.Ask(QuickAddDraft.Question.CAN_COOK, dish.capitalized(), count = m.groupValues[2].ifEmpty { m.groupValues[4] }.toIntOrNull())
            }
        }
        (WHEN_DREAM.matchEntire(t) ?: HOW_LONG_TO_DREAM.matchEntire(t))?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.WHEN_DREAM, m.groupValues[1].trim().trim(*PUNCTUATION).capitalized()) }
        (LASTED.matchEntire(t) ?: RUN_OUT.matchEntire(t))?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.LASTED, m.groupValues[1].trim().trim(*PUNCTUATION).capitalized()) }
        ASK_SPENT.matchEntire(t)?.let { m ->
            val subject = m.groupValues[1].trim().trim(*PUNCTUATION).removePrefix("the ").ifEmpty { null }
            val period = m.groupValues[2].ifEmpty { null }?.lowercase()?.let { if (it == "today") it else "$it ${m.groupValues[3].lowercase()}" }
            return QuickAddDraft.Ask(QuickAddDraft.Question.SPENT, subject?.capitalized(), period = period ?: "this month")
        }
        // "How much for groceries last month": spending, not what's left.
        ASK_SPENT_ON.matchEntire(t)?.let { m ->
            val period = m.groupValues[2].ifEmpty { null }?.lowercase()?.let { "$it ${m.groupValues[3].lowercase()}" }
            return QuickAddDraft.Ask(QuickAddDraft.Question.SPENT, m.groupValues[1].trim().trim(*PUNCTUATION).removePrefix("the ").capitalized(), period = period ?: "this month")
        }
        if (ASK_INCOME.matchEntire(t) != null) return QuickAddDraft.Ask(QuickAddDraft.Question.INCOME, t)
        SHARE_LOCATION.matchEntire(t)?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.SHARE_LOCATION, m.groupValues[1].trim().trim(*PUNCTUATION).capitalized()) }
        ASK_COOK.matchEntire(t)?.let { return QuickAddDraft.Ask(QuickAddDraft.Question.COOK, null) }
        if (ASK_TO_BUY.matchEntire(t) != null) return QuickAddDraft.Ask(QuickAddDraft.Question.TO_BUY, null)
        ASK_AGENDA.matchEntire(t)?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.AGENDA, m.groupValues[1].lowercase().ifEmpty { "today" }) }
        (ASK_HAVE.matchEntire(t))?.let { m ->
            val item = m.groups.drop(1).firstOrNull { it != null && it.value.isNotBlank() }?.value?.trim()?.trim(*PUNCTUATION)
            if (!item.isNullOrEmpty() && item.lowercase() !in setOf("time", "money", "plans", "anything", "something")) return QuickAddDraft.Ask(QuickAddDraft.Question.HAVE, item.capitalized())
        }
        ASK_WHEN.matchEntire(t)?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.WHEN, m.groupValues[1].trim().trim(*PUNCTUATION).capitalized()) }
        // Before symptoms: "I feel like eating something spicy" is hunger, not illness.
        (CRAVING.matchEntire(t) ?: CRAVING_TASTE.matchEntire(t))?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.CRAVING, m.groupValues[1].trim()) }
        ASK_EXPIRING.matchEntire(t)?.let { return QuickAddDraft.Ask(QuickAddDraft.Question.EXPIRING, null) }
        ASK_LEFT.matchEntire(t)?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.LEFT, m.groupValues[1].trim().ifEmpty { null }) }
        ASK_AFFORD.matchEntire(t)?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.AFFORD, m.groupValues[1].trim()) }
        ASK_WHERE.matchEntire(t)?.let { m -> return QuickAddDraft.Ask(QuickAddDraft.Question.WHERE, m.groupValues[1].trim()) }
        SYMPTOM_SENTENCE.matchEntire(t)?.let { m ->
            // Every condition said ("fever and cold"), joined with "|" so each gets its own answer.
            val cares = com.houseaffairs.app.core.data.caresFor(t).takeIf { it.isNotEmpty() } ?: return@let
            val who = m.groupValues[1].trim().takeIf { it.isNotEmpty() && it.lowercase() !in setOf("i", "we", "i'm", "im", "my", "me") }
            return QuickAddDraft.Ask(QuickAddDraft.Question.SYMPTOM, cares.joinToString(SYMPTOM_JOIN) { it.tag }, who?.replaceFirstChar { it.uppercaseChar() })
        }
        // "Headache since morning", "stomach upset", "kid fell and cut his knee": a symptom said plainly.
        com.houseaffairs.app.core.data.caresFor(t).takeIf { cares ->
            cares.isNotEmpty() && AMOUNT.find(t) == null && t.split(" ").none { it.normalizedWord() in MEDICINE_WORDS } &&
                // Short words that mean other things at home ("gas cylinder", "cold coffee") don't start a symptom on their own.
                (cares.any { c -> c.words.any { w -> w.length >= 5 && w !in AMBIGUOUS_SYMPTOMS && (t.lowercase() + " ").startsWith("$w ") } } || HURT_EVENT.containsMatchIn(t))
        }?.let { cares -> return QuickAddDraft.Ask(QuickAddDraft.Question.SYMPTOM, cares.joinToString(SYMPTOM_JOIN) { it.tag }) }
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
        // "Sunday outing to Cubbon Park, Lalbagh and MTR": the outing and its stops, in order.
        TRIP_STOPS.matchEntire(text.trimEnd('.'))?.let { m ->
            val places = m.groupValues[2].trim()
            if (places.split(Regex(",|\\band\\b|\\bthen\\b")).count { it.isNotBlank() } >= 2) {
                val title = m.groupValues[1].trim().capitalized()
                val start = extractDate(title.split(" ").toMutableList())
                return QuickAddDraft.Trip(title, start, places)
            }
        }
        TRIP_TO.matchEntire(text.trim().trimEnd('.', '!'))?.let { m ->
            val words = m.groupValues[2].split(" ").filter { it.isNotEmpty() }.toMutableList()
            // "A weekend at Coorg" with no day said: this coming weekend.
            val start = extractDate(words) ?: if (m.groupValues[1].lowercase() == "weekend") weekendFrom(today()) else null
            val title = words.joinToString(" ").trim().trim(*PUNCTUATION).split(" ").joinToString(" ") { it.capitalized() }
            if (title.isNotEmpty()) return QuickAddDraft.Trip(title, start)
        }
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
            // "Spare keys in the drawer": no "is", but somewhere things are kept.
            ?: KEPT_BARE.matchEntire(text)?.takeIf { match ->
                val where = match.groupValues[2].lowercase().split(" ")
                match.groupValues[1].split(" ").size <= 4 && where.any { w -> STORAGE_WORDS.any { w.startsWith(it) } }
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

    /**
     * "Want to watch Oppenheimer" goes on the watch list; "want to read a book" (nothing named)
     * asks for picks from it. Before dreams, so "want to …" with a list verb isn't a savings goal.
     * With an amount ("want to buy a car 5 lakh") it's left to dreams.
     */
    private fun parseWant(text: String): QuickAddDraft? {
        val t = text.trim().trimEnd('?', '.', '!')
        // "Reading list: Sapiens", "watchlist Dune".
        LIST_PREFIX.matchEntire(t)?.let { m ->
            val template = if (m.groupValues[1].lowercase().contains("watch")) ListTemplate.WATCH else ListTemplate.BOOKS
            return QuickAddDraft.ListItem(template, cleanWantTitle(m.groupValues[2]))
        }
        WANT.matchEntire(t)?.let { m -> wantDraft(m.groupValues[1].lowercase().replace(WHITESPACE, " "), m.groupValues[2].trim())?.let { return it } }
        // "Batman dark night to watch together", "Atomic Habits to read".
        TO_DO_LATER.matchEntire(t)?.let { m ->
            // "I need to read" is a wish without a title, not a book called "I need".
            if (m.groupValues[1].lowercase().split(" ").last() in WISH_VERBS) return null
            val template = when (m.groupValues[2].lowercase().replace(WHITESPACE, " ")) {
                "watch", "see" -> ListTemplate.WATCH
                "read" -> ListTemplate.BOOKS
                "learn", "study" -> ListTemplate.STUDY
                else -> ListTemplate.MUSIC
            }
            return QuickAddDraft.ListItem(template, m.groupValues[1].trim().capitalized())
        }
        return null
    }

    /** The list a "want to [verb] [what]" belongs on, or null when it's a plain wish (a task or a dream). */
    private fun wantDraft(verb: String, what: String): QuickAddDraft? {
        val lowerWhat = what.lowercase()
        val first = lowerWhat.substringBefore(' ')
        val template = when {
            "read" in verb -> ListTemplate.BOOKS
            "watch" in verb || "binge" in verb || verb == "see" -> ListTemplate.WATCH
            "listen" in verb || verb == "hear" -> ListTemplate.MUSIC
            verb in setOf("learn", "study") -> ListTemplate.STUDY
            verb == "gift" -> ListTemplate.GIFTS
            // "Finish this book", "start a new series": only about something to read or watch.
            verb in setOf("finish", "start", "continue", "begin") -> when {
                words(lowerWhat).any { it in setOf("book", "books", "novel", "novels") } -> ListTemplate.BOOKS
                words(lowerWhat).any { it in setOf("movie", "movies", "film", "series", "show", "shows", "season") } -> ListTemplate.WATCH
                else -> return null
            }
            verb in setOf("eat at", "try out", "go out to") -> ListTemplate.EAT_OUT
            // "Try a new restaurant" is eating out; "try yoga" isn't.
            verb == "try" -> if (words(lowerWhat).any { it in PLACE_NOUNS }) ListTemplate.EAT_OUT else return null
            // "Visit grandma" is a plan with family, not a travel wish.
            verb in setOf("visit", "go to", "travel to", "go on a trip to") -> if (first in PEOPLE_WORDS || words(lowerWhat).any { it in PEOPLE_WORDS }) return null else ListTemplate.TRAVEL
            // "Get fit", "get married": not shopping.
            verb == "buy" || verb == "get" -> if (verb == "get" && (first in GET_NOT_BUY || first.isEmpty())) return null else ListTemplate.BUY
            else -> return null
        }
        // "want to buy a new car 8 lakh" is saving up for something: a dream, not a wish.
        if (template == ListTemplate.BUY && WANT_AMOUNT.containsMatchIn(what)) return null
        // "a movie", "something", "some music", "a good book", or nothing at all: suggest from the list.
        val said = words(lowerWhat).filter { it.isNotEmpty() }
        val nounsOk = when {
            template.isMedia -> MEDIA_NOUNS
            template == ListTemplate.TRAVEL || template == ListTemplate.EAT_OUT -> PLACE_NOUNS
            else -> emptySet()
        }
        if (said.all { it in GENERIC_WANT_WORDS || it in nounsOk }) {
            return if (template.isMedia || nounsOk.isNotEmpty() || said.isNotEmpty()) QuickAddDraft.Ask(QuickAddDraft.Question.PICK, template.name) else null
        }
        val title = cleanWantTitle(what.removePrefix("the ").removePrefix("The "))
        return QuickAddDraft.ListItem(template, title)
    }

    /** "Ladakh next year" → "Ladakh": when is for the list, not the title. */
    private fun cleanWantTitle(raw: String): String =
        raw.replace(WANT_WHEN_TAIL, "").trim().trim(*PUNCTUATION).capitalized()

    private fun parseDream(text: String): QuickAddDraft.Dream? {
        val match = DREAM_PREFIX.matchEntire(text) ?: return null
        val body = match.groupValues[1]
        // "Want to call mom" or "want to start reading" is a to-do; only saving up for something is a dream.
        if (WANT_PREFIX.containsMatchIn(text) && !WANT_AMOUNT.containsMatchIn(body) && !SAVING_UP.containsMatchIn(body)) return null
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
        if (dish.isEmpty() || hasBareAmount(dish)) return null
        return QuickAddDraft.Cooked(dish.capitalized(), servings)
    }

    private fun parseRunningOut(text: String): QuickAddDraft? {
        // "…detergent. Add it to the shopping list." says the same thing twice.
        val said = ADD_IT_TO_LIST.replace(text, "").trim().trimEnd('.', '!').trim()
        val item = (OUT_OF.matchEntire(said) ?: ITEM_OVER.matchEntire(said))?.groupValues?.get(1)?.trim()?.trimEnd('.', '!') ?: return null
        // "need to call the plumber" is a task, not something to buy.
        if (item.startsWith("to ", ignoreCase = true)) return null
        // "need milk eggs and curd": each one, when they're all groceries.
        val items = item.split(LIST_SPLIT).flatMap { groceryWords(it) }.map { it.trim() }.filter { it.isNotEmpty() }
        if (items.size > 1) return QuickAddDraft.Batch(items.map { shoppingOf(it) ?: QuickAddDraft.Shopping(it.capitalized()) })
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

    private fun parseMedicine(input: String): QuickAddDraft.Medicine? {
        if (input.split(" ").none { it.normalizedWord() in MEDICINE_WORDS }) return null
        if (CURRENCY_MARKER.containsMatchIn(input)) return null // "₹120 crocin" is an expense
        // "Medicine 340 apollo": a pharmacy bill, not a medicine called that.
        val said = input.split(" ").map { it.normalizedWord() }
        if (said.any { it in PHARMACIES } || (said.any { it == "medicine" || it == "medicines" } && AMOUNT.find(input)?.groupValues?.get(2)?.replace(",", "")?.toIntOrNull()?.let { it >= 20 } == true && said.none { it in EXPIRY_WORDS })) return null
        var text = input
        // "twice a day", "3 times daily", "every night": a schedule, so doses can be reminded.
        val frequency = DOSE_FREQUENCY.find(text)?.let { m ->
            text = text.removeRange(m.range).trim()
            when (m.groupValues[1].lowercase()) {
                "twice", "2 times", "two times", "2x" -> "Twice a day"
                "thrice", "3 times", "three times", "3x" -> "Three times a day"
                else -> "Once a day"
            }
        }
        // "for 5 days", "for a week": when the course ends.
        val until = COURSE_LENGTH.find(text)?.let { m ->
            text = text.removeRange(m.range).trim()
            val n = m.groupValues[1].let { if (it.equals("a", true) || it.equals("one", true)) 1 else it.toIntOrNull() ?: 1 }
            today().plusDays((if (m.groupValues[2].lowercase().startsWith("week")) n * 7 else n).toLong() - 1)
        }
        val words = text.split(" ").filter { it.isNotEmpty() }.toMutableList()
        val expiry = extractExpiry(words)
        val forName = extractFor(words)
        val title = words.filterNot { it.normalizedWord() in ACQUIRE_VERBS }.joinToString(" ").trim().capitalized()
        return QuickAddDraft.Medicine(title.ifEmpty { "Medicine" }, forName, expiry, frequency, until)
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
        var amount = match.groupValues[2].replace(",", "").let(::decimalOf) ?: return null
        if (match.groups[3] != null) amount = amount * decimalOf(1000)

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
            frequency != null ||
            paidWith != null
        if (!looksLikeMoney) return null

        val currency = currencyToken?.let(::currencyCode)
        // "Uber 230 yesterday", "zomato 640 last night": that day, and kept out of the shop's name.
        val dated = words.toMutableList()
        val date = extractDate(dated)
        // A known brand said anywhere is the shop: "amazon order 1299 headphones" → Amazon.
        val merchant = dated.firstOrNull { it.normalizedWord() in BRANDS }?.trim(*PUNCTUATION)
            ?: merchantFrom(dated.filter { it.normalizedWord() !in MERCHANT_NOISE })
        if (frequency != null) {
            return QuickAddDraft.Subscription((merchant ?: category ?: "Subscription").capitalized(), amount, currency, frequency)
        }
        val sub = normalized.firstNotNullOfOrNull { SUBCATEGORY_BY_WORD[it] }?.takeIf { (cat, _) -> category == null || cat == category }
        return QuickAddDraft.Expense(amount, currency, category ?: sub?.first ?: "Other", merchant, text, sub?.second, paidWith, card, date)
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

    /**
     * A task for someone: "remind Mom to take her medicine at 9 pm", "ask Ravi to fix the tap",
     * "Rohan, please pay the electricity bill tomorrow". Talking to someone only counts when the
     * rest is a task ("Ravi, 500 for groceries" stays an expense).
     */
    private fun parsePersonTask(text: String): QuickAddDraft.Task? {
        val t = text.trim().trimEnd('.', '!', '?').trim()
        REMIND_PERSON.matchEntire(t)?.let { m ->
            val name = m.groupValues[1]
            if (name.lowercase() !in NOT_NAMES) return parseTask(m.groupValues[2]).copy(forName = name.capitalized())
        }
        val m = ADDRESSED.matchEntire(t) ?: return null
        val name = m.groupValues[1]
        if (name.lowercase() in NOT_NAMES) return null
        val rest = m.groupValues[2].trim()
        if (parseSingle(rest) !is QuickAddDraft.Task) return null
        return parseTask(rest).copy(forName = name.capitalized())
    }

    private fun parseTask(text: String): QuickAddDraft.Task {
        // "…at 6 PM IST": the phone's own time zone is the one that matters.
        var rest = WANT_PREFIX.replace(TASK_PREFIX.replace(TIME_ZONE.replace(text, ""), ""), "")
        // "…and remind everyday": the reminder is the task's time; the words only say it repeats.
        rest = REMIND_TAIL.replace(rest, " ").replace(WHITESPACE, " ").trim()
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
        // "at 5:30 pm", "17:30", "noon": a time to be reminded at.
        var minute = TASK_TIME.find(rest)?.let { m ->
            minuteOfDay(m.groupValues[1].replace(".", ":"))?.let { afternoonIfBare(m.groupValues[1], it) }?.also { rest = rest.removeRange(m.range).trim() }
        }
        val tonight = Regex("\\btonight\\b", RegexOption.IGNORE_CASE).containsMatchIn(rest)
        val words = rest.split(" ").filter { it.isNotEmpty() }.toMutableList()
        val due = extractDate(words)
        if (minute == null && tonight) minute = 20 * 60
        val title = words.joinToString(" ").trim().trimEnd(',', '-', '.').removePrefix("to ").removePrefix("To ").trim().capitalized()
        // "Call mom", "message Ravi": the task opens the dialer or WhatsApp, and is due today unless a day was said.
        val reach = reachOf(title)
        // A time with no day is the next one: today, or tomorrow once it has passed.
        val timedDue = minute?.let { at -> if ((nowMinute() ?: -1) >= at) today().plusDays(1) else today() }
        return QuickAddDraft.Task(title.ifEmpty { text }, due ?: timedDue ?: if (reach != null) today() else null, repeat, priority, reach, minute?.let(::formatClock))
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
            val next = words.getOrNull(i + 1)?.normalizedWord()
            // Phrases of a few words: "this weekend", "next week", "next month", "in 3 days", "last night".
            val phrase: Pair<LocalDate, Int>? = when {
                w == "day" && next == "after" && words.getOrNull(i + 2)?.normalizedWord() == "tomorrow" -> today.plusDays(2) to 3
                w == "last" && next == "night" -> today.minusDays(1) to 2
                (w == "this" || w == "coming") && next == "weekend" -> weekendFrom(today) to 2
                w == "next" && next == "weekend" -> weekendFrom(today).plusDays(7) to 2
                w == "weekend" && words.getOrNull(i - 1)?.normalizedWord() !in setOf("this", "next", "coming") -> weekendFrom(today) to 1
                w == "next" && next == "week" -> nextOrSame(today.plusDays(1), DayOfWeek.MONDAY) to 2
                w == "next" && next == "month" -> addMonths(today.year, today.monthValue, 1).let { (y, mo) -> clampedDate(y, mo, today.dayOfMonth) } to 2
                w == "in" && next?.toIntOrNull() != null && words.getOrNull(i + 2)?.normalizedWord()?.removeSuffix("s") in setOf("day", "week") -> {
                    val n = next.toInt().toLong()
                    (if (words[i + 2].normalizedWord().startsWith("week")) today.plusDays(n * 7) else today.plusDays(n)) to 3
                }
                else -> null
            }
            if (phrase != null) {
                repeat(phrase.second) { words.removeAt(i) }
                if (i > 0 && words[i - 1].lowercase() in DATE_PREPOSITIONS) words.removeAt(i - 1)
                return phrase.first
            }
            val simple = when (w) {
                "today", "tonight" -> today
                "tomorrow", "tmrw", "tmr" -> today.plusDays(1)
                "yesterday" -> today.minusDays(1)
                else -> WEEKDAYS[w]?.let { nextOrSame(today, it) }
            }
            if (simple != null) return simple.also { consume(words, i, 1) }

            // "expiry 2028" (batteries, documents): the end of that year.
            if (endOfMonth && w.length == 4 && w.all(Char::isDigit) && w.toInt() in 2000..2100 && words.getOrNull(i - 1)?.normalizedWord() !in MONTHS) {
                return localDate(w.toInt(), 12, 31).also { consume(words, i, 1) }
            }
            val month = MONTHS[w] ?: continue
            val before = words.getOrNull(i - 1)?.let(::dayOfMonth)
            val after = words.getOrNull(i + 1)?.let(::dayOfMonth)
            val year = words.getOrNull(i + 1)?.normalizedWord()?.let(::yearOf)
            when {
                before != null && year != null -> return safeDate(year, month, before).also { consume(words, i - 1, 3) }
                before != null -> return nextOccurrence(month, before).also { consume(words, i - 1, 2) }
                year != null -> return lastDayOfMonth(year, month).also { consume(words, i, 2) }
                after != null -> return nextOccurrence(month, after).also { consume(words, i, 2) }
                // A bare month ("may", "march") is too ambiguous except right after "expires".
                endOfMonth -> return nextOccurrence(month, 1).let { lastDayOfMonth(it.year, it.monthValue) }.also { consume(words, i, 1) }
            }
        }
        return null
    }

    /** "Pick up kids at 4:30": without am or pm, 1 to 6 o'clock means the afternoon. 7 stays as said, since alarms are often at 7. */
    private fun afternoonIfBare(said: String, minute: Int): Int =
        if (!Regex("am|pm|a\\.m|p\\.m|noon", RegexOption.IGNORE_CASE).containsMatchIn(said) && minute / 60 in 1..6) minute + 12 * 60 else minute

    /** The coming Saturday (today, on a Saturday). */
    private fun weekendFrom(today: LocalDate): LocalDate = nextOrSame(today, DayOfWeek.SATURDAY)

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
    private fun safeDate(year: Int, month: Int, day: Int): LocalDate = clampedDate(year, month, day)

    private fun nextOccurrence(month: Int, day: Int): LocalDate {
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
        /** "twice a day", "3 times daily", "once daily", "every night". */
        val DOSE_FREQUENCY = Regex("\\b(once|twice|thrice|three times|two times|[23] times|[23]x)(?: a| per)? ?(?:day|daily)\\b|\\b(daily|every day|every night|every morning)\\b", RegexOption.IGNORE_CASE)

        /** "for 5 days", "for a week", "for 2 weeks". */
        val COURSE_LENGTH = Regex("\\bfor (\\d+|a|one) (days?|weeks?)\\b", RegexOption.IGNORE_CASE)

        /** "remind Mom to…", "ask Ravi to…", "tell Riya to…" (not "remind me to"). */
        val REMIND_PERSON = Regex("^(?:please )?(?:remind|ask|tell) ([a-z]+) to (.+)$", RegexOption.IGNORE_CASE)

        /** "Rohan, please pay the bill" / "Rohan please pay the bill" (voice drops the comma). */
        val ADDRESSED = Regex("^([a-z]+)(?:\\s*,\\s*(?:please |pls |kindly |can you |could you )?| (?:please|pls|kindly|can you|could you) )(.+)$", RegexOption.IGNORE_CASE)

        /** Words that start a sentence but aren't someone's name. */
        val NOT_NAMES = setOf(
            "me", "us", "i", "we", "you", "it", "them", "him", "her", "ok", "okay", "so", "also", "then", "now", "today",
            "tomorrow", "and", "but", "please", "hey", "hi", "hello", "yes", "no", "well", "oh", "the", "a",
            "everyday", "daily", "every", "each", "weekly", "monthly", "always",
        )

        /** "add it to the shopping list" after naming what's running low. */
        val ADD_IT_TO_LIST = Regex("[.,!]?\\s*(?:please )?(?:add|put) (?:it|them|that|those) (?:to|on|in)(?: the| my| our)? (?:shopping|grocery|groceries)(?: list)?[.!]?$", RegexOption.IGNORE_CASE)

        /** "How much did we spend on groceries this month", "what have I spent at DMart last week". */
        val ASK_SPENT = Regex(
            "^(?:how much|what) (?:did|have|has) (?:we|i|the family|the home|our family) (?:spend|spent)(?: (?:on|for|at) (.+?))?(?: (this|last|today)(?: (week|month|year))?| so far)?$",
            RegexOption.IGNORE_CASE,
        )

        /** "Share my live location with Ravi", "send my location to Mom". */
        val SHARE_LOCATION = Regex("^(?:share|send) (?:my )?(?:live |current )?location (?:with|to) (.+)$", RegexOption.IGNORE_CASE)

        val OUT_OF = Regex(
            "^(?:we(?:['’]re| are) |i(?:['’]m| am) |we )?(?:running low on|running out of|low on|almost out of|nearly out of|ran out of|out of|no more|need to buy|need|buy) (.+)$",
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
            "^(?:what(?:'s| is) for (?:dinner|lunch|breakfast)|what (?:should|shall) (?:we|i) (?:eat|have)|what to eat|(?:(?:what|which)(?: dish(?:es)?)? (?:can|should|shall|could) (?:i|we) (?:cook|make))|what to (?:cook|make)|suggest (?:a |some )?(?:dish|dishes|recipe|recipes|something to cook)|(?:any )?(?:dinner|lunch|breakfast) ideas)\\b.*$",
            RegexOption.IGNORE_CASE,
        )
        val CAN_COOK = Regex(
            "^(?:can|could|shall) (?:i|we) (?:cook|make)\\s+(?:the |some |a )?(.+?)(?:\\s+for\\s+(\\d+)(?:\\s+(?:people|persons|person|of us|guests|plates))?)?$" +
                "|^(?:is there|do we have|have we got) enough (?:to (?:cook|make)\\s+|for\\s+)(?:the |some )?(.+?)(?:\\s+for\\s+(\\d+)(?:\\s+(?:people|persons|person))?)?$",
            RegexOption.IGNORE_CASE,
        )
        val WHEN_DREAM = Regex("^(?:by when|when) (?:can|could|will|would|shall) (?:we|i) (?:go to|go on|go for|visit|buy|get|afford|have|reach|do|take)\\s+(?:the |a |an |our |my )?(.+)$", RegexOption.IGNORE_CASE)
        val HOW_LONG_TO_DREAM = Regex("^how long (?:until|till|before|to save for|to afford)\\s+(?:we can (?:go to|buy|afford) )?(?:the |a |an |our |my )?(.+)$", RegexOption.IGNORE_CASE)
        val LASTED = Regex("^how long (?:did|does|do|will|would) (?:the |our |my |a |one )?(.+?) (?:last|go|run)(?: for| us)?$", RegexOption.IGNORE_CASE)
        val RUN_OUT = Regex("^when (?:will|would|does|do) (?:the |our |my )?(.+?) (?:run out|finish|get over|be over)$", RegexOption.IGNORE_CASE)
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
            "studied" to "Study", "studying" to "Study", "study" to "Study", "revised" to "Study", "practised" to "Study", "practiced" to "Study",
        )
        val DURATION = Regex("\\b(\\d+(?:\\.\\d+)?|an|a)\\s?(minutes|minute|mins|min|m|hours|hour|hrs|hr|h)\\b")
        val STEPS = Regex("\\b(\\d[\\d,]*)\\s?steps\\b")
        /** "Sunday outing to A, B and C", "plan a day out: A then B". */
        val TRIP_STOPS = Regex("^(?:plan(?:ning)?\\s+(?:a\\s+|an\\s+|the\\s+)?)?(.*?\\b(?:outing|trip|day out|road trip))\\s*(?:to|via|covering|:)\\s+(.+)$", RegexOption.IGNORE_CASE)

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
            "cook" to ("Home" to "Help"), "maid" to ("Home" to "Help"), "driver" to ("Home" to "Help"), "nanny" to ("Home" to "Help"),
            "gardener" to ("Home" to "Help"), "watchman" to ("Home" to "Help"), "dhobi" to ("Home" to "Laundry"), "ironing" to ("Home" to "Laundry"),
            "insurance" to ("Bills" to "Insurance"), "premium" to ("Bills" to "Insurance"), "lic" to ("Bills" to "Insurance"),
            "fees" to ("Bills" to "Education"), "tuition" to ("Bills" to "Education"), "school" to ("Bills" to "Education"),
            "books" to ("Shopping" to "Books"), "novel" to ("Shopping" to "Books"), "stationery" to ("Shopping" to "Stationery"),
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
        val ASK_SPENT_ON = Regex("^how much (?:did (?:we|i) spend |have (?:we|i) spent |we spent |spent )?(?:on|for) (.+?)(?: (this|last) (week|month|year))?$", RegexOption.IGNORE_CASE)
        val ASK_INCOME = Regex(
            "^(?:(?:got|received|receive|earned|credited)\\s+(?:my |the |a |our )?(?:salary|bonus|refund|cashback|stipend|pension)\\b.*|(?:got|received)\\s+(?:₹|rs\\.?\\s?)?\\d[\\d,]*k?\\s+(?:from|by)\\s+\\w+.*|(?:salary|bonus|refund|pension)\\s+(?:credited|received|came|is in|has come).*)$",
            RegexOption.IGNORE_CASE,
        )
        val ASK_TO_BUY = Regex(
            "^(?:anything|what(?:'s| is)?(?: all)?|what do (?:we|i) need|what should (?:we|i))\\s*(?:to buy|to get|on the (?:shopping |buy |grocery )?list|we need to buy)|^(?:show (?:me )?)?(?:the |our |my )?(?:shopping|grocery|buy) list$",
            RegexOption.IGNORE_CASE,
        )
        val ASK_AGENDA = Regex(
            "^(?:what(?:'s| is)\\s+(?:due|on|planned|pending|left to do)|what do (?:i|we) have(?: to do)?|show (?:me )?(?:my |our )?(?:tasks|to-?dos|agenda|plan)|(?:my|our) (?:day|tasks|agenda))(?:\\s+(today|tomorrow|this week|this weekend))?$",
            RegexOption.IGNORE_CASE,
        )
        val ASK_HAVE = Regex(
            "^(?:do|did) (?:we|i) (?:still )?(?:have|got) (?:any |some )?(.+?)(?: left| at home)?$|^is there (?:any |some )?(.+?)(?: left| at home)?$|^any (.+?) left$|^how many (.+?) (?:are |do we have )?left$",
            RegexOption.IGNORE_CASE,
        )
        val ASK_WHEN = Regex("^when(?:'s| is| was| are)\\s+(?:the |my |our )?(.+)$", RegexOption.IGNORE_CASE)
        /** "Kid fell and cut his knee", "burnt hand while cooking": something happened, and it hurts. */
        val AMBIGUOUS_SYMPTOMS = setOf("gas", "cold", "cut", "burn", "burns", "rash", "sprain", "bloating", "scratch", "graze", "wound", "temperature")
        val HURT_EVENT = Regex("\\b(?:fell|fallen|hurt|cut|burnt|burned|bleeding|sprained|twisted|bitten|stung)\\b", RegexOption.IGNORE_CASE)
        val GOAL_LINE = Regex("^(?:(?:my |this week'?s |weekly |new )?goal\\s*[:\\-]?\\s+|this week i (?:want to|will|wanna) )(.+)$", RegexOption.IGNORE_CASE)
        val THIS_WEEK = Regex("\\s*\\b(?:this|for the) week\\b", RegexOption.IGNORE_CASE)
        val LENT = Regex("^(lent|loaned|borrowed)\\s+((?:₹|rs\\.?\\s?)?\\d[\\d,]*k?)\\s+(?:to|from)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val GIFT_IDEA = Regex("^gift(?: idea)?(?: for (\\w+))?\\s*[:\\-]\\s*(.+)$|^gift idea (?:for (\\w+) )?(.+)$", RegexOption.IGNORE_CASE)
        val LIST_VERB = Regex("^(watch|see|read|listen to)\\s+(.+)$", RegexOption.IGNORE_CASE)
        /** "Watch out", "read the meter": everyday words, not titles. */
        val NOT_A_TITLE = setOf("out", "the", "kids", "baby", "over", "my", "our", "your", "his", "her", "it", "this", "that", "meter", "news", "messages", "mail", "emails")
        val PLACE_GOOD_AT = Regex("^(?:good|great|best|amazing|awesome|nice|lovely)\\s+(.+?)\\s+(?:at|@)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val PLACE_TRY = Regex("^try\\s+(.+?)\\s+for\\s+(?:the |their |its )?(.+)$", RegexOption.IGNORE_CASE)
        val TRIP_TO = Regex("^(?:(?:plan(?:ning)?|going on|go on|a|our|the)\\s+)?(?:a\\s+|an\\s+|our\\s+)?(trip|holiday|vacation|getaway|weekend)\\s+(?:to|in|at)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val KEPT_BARE = Regex("^(?:the |my |our )?(.+?)\\s+(?:in|inside|under|behind)\\s+(?:the |my |our )?(.+)$", RegexOption.IGNORE_CASE)
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
        val FUND = Regex("^(?:save|put|add|keep|set aside|park|invest|transferred|transfer|moved|deposited|deposit)\\s+(.+?)\\s+(?:to|for|into|towards|in)\\s+(?:the\\s+|my\\s+|our\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        val FOR_TAIL = Regex("\\s(?:for|towards)\\s+(?:the\\s+|my\\s+|our\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        val TO_DO_LATER = Regex(
            "^(?:add\\s+)?(.+?)\\s+to\\s+(watch|see|read|listen to|learn|study)(?:\\s+(?:together|later|sometime|someday|this weekend|with family))?$",
            RegexOption.IGNORE_CASE,
        )
        const val WISH = "(?:i\\s+|we\\s+|i(?=['’])|we(?=['’]))?(?:really\\s+|also\\s+|just\\s+)?" +
            "(?:want to|wanna|would like to|would love to|['’]d like to|['’]d love to|love to|wish to|hope to|feel like|in the mood to|plan to|planning to)"
        /** "I want to", "I'd like to": stripped from wishes that end up as tasks. */
        val WANT_PREFIX = Regex("^$WISH\\s+", RegexOption.IGNORE_CASE)
        // Longer verbs first so "start reading" isn't read as "start".
        val WANT = Regex(
            "^$WISH\\s+" +
                "(start reading|finish reading|continue reading|read more|re-?read|read|start watching|finish watching|binge watch|binge|re-?watch|watch|see|" +
                "listen to|hear|learn|study|visit|go to|travel to|go on a trip to|eat at|try out|go out to|try|buy|get|gift|finish|start|continue|begin)(?:\\s+(.+))?$",
            RegexOption.IGNORE_CASE,
        )
        val LAKH = Regex("(\\d+(?:\\.\\d+)?)\\s?(lakhs?|lacs?|crores?|cr)\\b", RegexOption.IGNORE_CASE)
        val LIST_PREFIX = Regex("^(reading list|to-?read list|book list|books list|watch ?list|watch later)\\s*[:\\-]?\\s+(.+)$", RegexOption.IGNORE_CASE)
        /** Last words before "to read" that mean a wish, not a title: "I need to read". */
        val WISH_VERBS = setOf("want", "wanna", "need", "have", "like", "love", "plan", "going", "got", "gotta", "trying", "try", "hope", "wish")
        val WANT_WHEN_TAIL = Regex("\\s+(?:next|this|coming)\\s+(?:year|month|week|weekend|summer|winter)$|\\s+(?:someday|sometime|one day|soon|later|together|with family|tonight)$", RegexOption.IGNORE_CASE)
        /** "Want to save for a house", "want to go on a holiday": saving up, so a dream. */
        val SAVING_UP = Regex("^(?:save|save up|buy|own|build|afford|purchase)\\b|\\b(?:trip|holiday|vacation|wedding)\\b", RegexOption.IGNORE_CASE)
        val MEDIA_NOUNS = setOf(
            "movie", "movies", "film", "films", "show", "shows", "series", "book", "books", "novel", "novels",
            "song", "songs", "music", "album", "albums", "podcast", "podcasts",
        )
        val PLACE_NOUNS = setOf("place", "places", "somewhere", "restaurant", "restaurants", "cafe", "cafes", "spot")
        val PEOPLE_WORDS = setOf(
            "mom", "mum", "mummy", "dad", "papa", "grandma", "grandpa", "nani", "dadi", "nana", "dada", "uncle", "aunt", "aunty",
            "parents", "in-laws", "inlaws", "friends", "friend", "doctor", "dentist", "relatives", "cousin", "cousins",
        )
        val GET_NOT_BUY = setOf("fit", "better", "healthy", "up", "ready", "married", "rid", "back", "started", "well", "promoted", "stronger", "organised", "organized", "in", "some", "sleep", "rest", "out")
        val WANT_AMOUNT = Regex("\\d+(?:\\.\\d+)?\\s?(?:lakh|lac|lakhs|crore|cr|k)\\b|[₹$]|\\brs\\.?\\s?\\d|\\d{3,}", RegexOption.IGNORE_CASE)
        /** Words that don't name anything: "a good movie", "something funny", "some music tonight". */
        val GENERIC_WANT_WORDS = setOf(
            "a", "an", "some", "something", "anything", "one", "good", "nice", "new", "fun", "light", "funny", "short", "long",
            "hindi", "english", "interesting", "romantic", "comedy", "action", "horror", "thriller", "relaxing", "chill",
            "tonight", "today", "now", "this", "that", "weekend", "together", "with", "more", "few", "couple", "of", "next",
            "the", "family", "kids", "to", "for", "my", "our", "again", "later", "sometime", "someday", "before", "bed", "bedtime",
        )
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
            "call", "pay", "book", "fix", "clean", "renew", "submit", "send", "email", "mail", "pick", "drop", "take", "lent", "loaned", "borrowed",
            "return", "file", "recharge", "iron", "wash", "repair", "service", "schedule", "cancel", "check", "update",
            "apply", "finish", "complete", "meet", "remind", "collect", "deposit", "register", "print", "reply", "text",
            "message", "ask", "tell", "do", "make", "change", "replace", "install", "sort", "organise", "organize", "plan",
        )
        val TASK_TIME = Regex("(?:\\s|^)(?:at|@|by|around)?\\s*(\\d{1,2}(?:[:.]\\d{2})?\\s?(?:am|pm|a\\.m\\.|p\\.m\\.)|\\d{1,2}:\\d{2}|noon)(?=\\s|$|[,.!?])", RegexOption.IGNORE_CASE)
        /** "Set an alarm", "set me a reminder", "alarm", "wake me up", "reminder:": a reminder, whatever follows. */
        val REMIND_START = Regex(
            "^(?:please\\s+)?(?:(?:can|could) you\\s+)?(?:(?:set|put|add|create|make)\\s+(?:me\\s+)?(?:an?\\s+|the\\s+)?(?:alarm|reminder|timer)s?(?:\\s+for\\s+me)?|reminder|alarm|wake me(?: up)?|remind me)\\b[:,]?\\s*",
            RegexOption.IGNORE_CASE,
        )
        val ANY_REMIND = Regex("\\bremind", RegexOption.IGNORE_CASE)
        val WAKE = Regex("\\bwake me\\b", RegexOption.IGNORE_CASE)
        val ALARM_WORDS = Regex("\\b(?:alarm|wake me)\\b", RegexOption.IGNORE_CASE)
        val IN_A_WHILE = Regex("\\bin\\s+(\\d+(?:\\.\\d+)?|an?|half an)\\s*(minutes?|mins?|hours?|hrs?)\\b", RegexOption.IGNORE_CASE)
        val BARE_HOUR = Regex("\\b(?:at|by|around)\\s+(\\d{1,2})(?![\\d:.]|\\s*(?:am|pm|a\\.m|p\\.m|th|st|nd|rd))\\b", RegexOption.IGNORE_CASE)
        val ON_DAY = Regex("\\bon\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)\\b(?!\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec))", RegexOption.IGNORE_CASE)
        val PART_OF_DAY = Regex("\\b(?:in the\\s+)?(morning|afternoon|evening|night)\\b", RegexOption.IGNORE_CASE)
        val PART_OF_DAY_MINUTE = mapOf("morning" to 9 * 60, "afternoon" to 14 * 60, "evening" to 18 * 60, "night" to 21 * 60)
        /** Words left hanging once the time is taken out: "for", "at" at the end. */
        val DANGLING = Regex("\\s+(?:for|at|on|by|to)$", RegexOption.IGNORE_CASE)
        val LEADING_JOIN = Regex("^(?:to|for|that|about|me to)\\s+", RegexOption.IGNORE_CASE)
        /** "2 milk 1 bread 6 eggs". */
        val COUNTED_LIST = Regex("^(?:\\d{1,2}\\s+[a-z][a-z]*(?:\\s+[a-z]+)?\\s*,?\\s*){2,}$", RegexOption.IGNORE_CASE)
        val COUNTED_ITEM = Regex("(\\d{1,2})\\s+([a-z][a-z]*(?:\\s+(?!\\d)[a-z]+)?)", RegexOption.IGNORE_CASE)
        /** "eggs 90", "bread ₹45". */
        val ITEM_PRICE = Regex("^([a-z][a-z ]*?)\\s+((?:₹|rs\\.?\\s?)?\\d[\\d,]*(?:\\.\\d+)?k?)$", RegexOption.IGNORE_CASE)
        val BUY_WORDS = setOf("buy", "get", "order", "bought", "pick")
        val TASK_PREFIX = Regex("^(?:please )?(?:remind me to|remind me|remind to|remind|todo:?|to-do:?|task:?)\\s+", RegexOption.IGNORE_CASE)

        /** Starts by asking to be reminded: "remind me…", "remind every day…" (not "remind Mom to…"). */
        val REMINDER = Regex("^(?:please )?remind(?: me)?(?:\\s+(?:to|every ?day|daily|every|at|on|tomorrow|today)\\b|$)", RegexOption.IGNORE_CASE)

        /** "and remind me", ", remind everyday": the repeat stays, these words go. */
        val REMIND_TAIL = Regex("(?:,|\\band\\b)?\\s*\\bremind(?: me)?\\b(?!\\s+to\\b)", RegexOption.IGNORE_CASE)

        /** "6 PM IST": time zones said after a time. */
        val TIME_ZONE = Regex("\\s*\\b(?:IST|GMT|UTC|BST|PST|PDT|EST|EDT|CET|CEST|SGT|GST)\\b", RegexOption.IGNORE_CASE)

        /** "won't take crocin today", "not having my vitamins tonight", "don't give Riya calpol today". */
        val SKIP_DOSE = Regex("^(?:i |we |she |he |riya )?(?:won['’]?t|will not|wont|not going to|am not going to|don['’]?t|do not|not|skipping|skip) (?:take|taking|eat|eating|have|having|give|giving)(?: my| the| her| his)? (.+?)(?: (today|tonight|tomorrow|this evening|this morning))?$", RegexOption.IGNORE_CASE)

        /** "skip crocin today", "no crocin tonight": a day word is needed so "skip" alone isn't misread. */
        val SKIP_DOSE_SHORT = Regex("^(?:skip|skipping|no)(?: my| the)? (.+?) (today|tonight|tomorrow)$", RegexOption.IGNORE_CASE)

        val SHOPPING_ALIASES = setOf("shopping", "grocery", "groceries", "cart", "list", "buy", "to buy", "buy list", "shopping list")
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
        val DATE_PREPOSITIONS = setOf("on", "by", "this", "next", "till", "until", "before", "from")
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
        /** Month names and short forms to 1..12. */
        val MONTHS: Map<String, Int> = listOf(
            "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
        ).withIndex().flatMap { (i, full) ->
            listOf(full to i + 1, full.take(3) to i + 1) + if (full.length > 4) listOf(full.take(4) to i + 1) else emptyList()
        }.toMap()

        // Brand names map to a category but are kept as the merchant; generic words are not.
        val BRAND_KEYWORDS = mapOf(
            "Groceries" to listOf("dmart", "bigbasket", "blinkit", "zepto", "instamart", "walmart", "tesco", "costco"),
            "Dining" to listOf("swiggy", "zomato", "starbucks", "mcdonalds", "dominos"),
            "Transport" to listOf("uber", "ola", "rapido", "lyft"),
            "Shopping" to listOf("amazon", "flipkart", "myntra", "ikea"),
            "Entertainment" to listOf("netflix", "spotify", "prime", "hotstar", "bookmyshow", "youtube"),
            "Travel" to listOf("airbnb", "makemytrip", "booking"),
            "Health" to listOf("apollo", "medplus", "pharmeasy", "netmeds", "1mg"),
        )
        val PHARMACIES = setOf("apollo", "medplus", "pharmeasy", "netmeds", "1mg", "pharmacy", "chemist")
        /** Brand names, kept as the shop wherever they're said. */
        val BRANDS: Set<String> by lazy { BRAND_KEYWORDS.values.flatten().toSet() }
        /** Said around a purchase but never part of a shop's name. */
        val MERCHANT_NOISE = setOf("renewed", "renew", "renewal", "order", "ordered", "delivery", "delivered", "bill", "paid", "night", "morning", "evening", "last")
        val GENERIC_KEYWORDS = mapOf(
            "Groceries" to listOf("grocery", "groceries", "vegetables", "veggies", "fruits", "milk", "supermarket"),
            "Dining" to listOf("dinner", "lunch", "breakfast", "restaurant", "cafe", "coffee", "food", "snacks"),
            "Transport" to listOf("taxi", "cab", "auto", "fuel", "petrol", "diesel", "metro", "bus", "train", "parking", "toll"),
            "Bills" to listOf("electricity", "water", "gas", "internet", "wifi", "broadband", "mobile", "recharge", "bill", "insurance", "premium", "lic", "fees", "tuition"),
            "Rent" to listOf("rent", "emi", "maintenance"),
            "Health" to listOf("medicine", "medicines", "pharmacy", "chemist", "doctor", "hospital", "clinic", "lab", "dentist", "gym"),
            "Shopping" to listOf("clothes", "shoes", "shopping", "books", "novel", "stationery"),
            "Entertainment" to listOf("movie", "movies", "concert", "tickets"),
            "Home" to listOf("furniture", "repair", "plumber", "electrician", "carpenter", "painter", "service", "servicing", "maid", "cleaning", "cook", "driver", "nanny", "gardener", "watchman", "dhobi"),
            "Travel" to listOf("flight", "hotel", "trip", "travel", "visa"),
            "Gifts" to listOf("gift", "gifts", "present"),
        )
        val GENERIC_WORDS = GENERIC_KEYWORDS.values.flatten().toSet()
        val CATEGORY_BY_KEYWORD = (BRAND_KEYWORDS.entries + GENERIC_KEYWORDS.entries)
            .flatMap { (category, words) -> words.map { it to category } }
            .toMap()
    }
}

private val REACH = Regex("^(call|phone|ring|dial|whatsapp|message|msg|reply to|reply|text|sms)\\b", RegexOption.IGNORE_CASE)

/** How a "call …" or "message …" task reaches the person: "Call", "WhatsApp" or "SMS"; null for other tasks. */
fun reachOf(title: String): String? = when (REACH.find(title.trim())?.groupValues?.get(1)?.lowercase()) {
    null -> null
    "call", "phone", "ring", "dial" -> "Call"
    "text", "sms" -> "SMS"
    else -> "WhatsApp"
}
