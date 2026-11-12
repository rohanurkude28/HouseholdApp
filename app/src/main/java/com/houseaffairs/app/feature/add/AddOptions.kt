package com.houseaffairs.app.feature.add

import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.feature.quickadd.ListTemplate

/**
 * One thing the add sheet offers, in the words people use. Most open a form ([type], with
 * [preset] such as the list a wish goes on); questions and money moves fill the box with
 * [text] to edit, since the parser handles those. [key] is how it's pinned and counted.
 */
data class AddOption(
    val key: String,
    val emoji: String,
    val label: String,
    val type: EntryType? = null,
    val preset: FormValues = emptyMap(),
    val text: String? = null,
    /** For a typed option: the kind whose access it needs (moving money needs Money). */
    val needs: EntryType? = null,
) {
    fun allowedFor(me: Member): Boolean =
        type?.let { it.available && me.canAdd(it) } ?: needs?.let { me.canAdd(it) } ?: true
}

private fun kind(type: EntryType, emoji: String, label: String) = AddOption(type.name, emoji, label, type)

private fun list(t: ListTemplate, label: String) = AddOption("WISH:${t.name}", t.emoji, label, EntryType.WISH, mapOf("template" to t.label))

private fun typed(key: String, emoji: String, label: String, text: String, needs: EntryType? = null) = AddOption(key, emoji, label, text = text, needs = needs)

/** Every way to add something, by area. Each area is one row in the sheet, so nothing is hidden and nothing is cluttered. */
val ADD_GROUPS: List<Pair<String, List<AddOption>>> = listOf(
    "Money" to listOf(
        kind(EntryType.EXPENSE, "💸", "I spent"),
        kind(EntryType.SUBSCRIPTION, "🔁", "Bills & subs"),
        kind(EntryType.TRIP, "🧳", "Trip"),
        kind(EntryType.CREDIT, "🎟️", "Gift card or coupon"),
        kind(EntryType.INVESTMENT, "📈", "Investment"),
        typed("MOVE", "↔️", "Move money", "move 1000 from Dining to Travel", needs = EntryType.SUBSCRIPTION),
    ),
    "Kitchen" to listOf(
        kind(EntryType.SHOPPING, "🛒", "Need to buy"),
        kind(EntryType.GROCERY, "🥫", "Pantry"),
        kind(EntryType.HOUSEHOLD, "🧴", "Supplies"),
        kind(EntryType.RECIPE, "🍳", "Recipe"),
    ),
    "Plans" to listOf(
        kind(EntryType.TASK, "✅", "To do"),
        kind(EntryType.EVENT, "🎂", "Birthday or event"),
        kind(EntryType.GOAL, "🏆", "Weekly goal"),
        kind(EntryType.ACTIVITY, "🚶", "Walk or workout"),
        kind(EntryType.DREAM, "✨", "Dream or gullak"),
    ),
    "Lists" to listOf(
        list(ListTemplate.WATCH, "Movie or show"),
        list(ListTemplate.BOOKS, "Book"),
        list(ListTemplate.MUSIC, "Song or album"),
        list(ListTemplate.TRAVEL, "Place to travel"),
        list(ListTemplate.EAT_OUT, "Place to eat"),
        list(ListTemplate.GIFTS, "Gift idea"),
        list(ListTemplate.BUY, "Buy someday"),
        list(ListTemplate.STUDY, "Learn something"),
    ),
    "Home & health" to listOf(
        kind(EntryType.MEDICINE, "💊", "Medicine"),
        kind(EntryType.KEPT, "📦", "Where it's kept"),
        kind(EntryType.PLACE, "📍", "Saved place"),
        kind(EntryType.DOCUMENT, "📄", "Document"),
    ),
    "Ask" to listOf(
        typed("ASK_WHERE", "🔎", "Where is…", "where are the car keys?"),
        typed("ASK_COOK", "🍲", "What can I cook?", "what can I cook?", needs = EntryType.RECIPE),
        typed("ASK_SPENT", "📊", "How much did we spend?", "how much did we spend on dining this month?", needs = EntryType.SUBSCRIPTION),
    ),
)

val ADD_OPTIONS: Map<String, AddOption> = ADD_GROUPS.flatMap { it.second }.associateBy { it.key }

/** The big tiles on a new phone, before this person's own habits show. */
val USUAL_DEFAULTS = listOf(EntryType.EXPENSE, EntryType.SHOPPING, EntryType.TASK, EntryType.MEDICINE).map { it.name }

/** What a save counts toward for the usual tiles: a wish counts toward its own list. */
fun countKey(type: EntryType, values: FormValues): String =
    if (type == EntryType.WISH) ListTemplate.entries.firstOrNull { it.label == values["template"] }?.let { "WISH:${it.name}" } ?: type.name
    else type.name
