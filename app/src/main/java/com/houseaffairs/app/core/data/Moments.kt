package com.houseaffairs.app.core.data

/** How many of today's things Home hands a person in a day: few enough to finish, so the day can end. */
const val DAY_GOAL = 3

/** Ids of cards that aren't Today rows (meal ideas, the spend question…), kept in the same handled set. */
const val MOMENT_PREFIX = "moment:"

/** Asked once a day, whichever part of the day it's answered in. */
const val SPEND_CARD = MOMENT_PREFIX + "SPENDS"

/** The part of the day Home is talking about. What it suggests changes with it. */
enum class Moment(val label: String, val emoji: String, val meal: String?) {
    MORNING("this morning", "🌅", "Breakfast"),
    MIDDAY("this afternoon", "☀️", "Lunch"),
    EVENING("this evening", "🌇", "Dinner"),
    NIGHT("tonight", "🌙", null);

    companion object {
        fun at(hour: Int): Moment = when (hour) {
            in 5..10 -> MORNING
            in 11..15 -> MIDDAY
            in 16..20 -> EVENING
            else -> NIGHT
        }
    }
}

enum class CardKind { THING, MEAL, EAT_OUT, SHOPPING, SPENDS, WATCH, TOMORROW }

/** One card in Home's deck: a small heading, one question or idea, and a line of detail. */
data class MomentCard(val id: String, val kind: CardKind, val eyebrow: String, val title: String, val detail: String)

/** A suggestion in two lines ("Poha", "You have everything"). */
data class Idea(val title: String, val detail: String)

/** What Home knows right now, already narrowed to this person. */
data class MomentInputs(
    /** Today's rows that are theirs, most pressing first, as ready-made THING cards. */
    val things: List<MomentCard> = emptyList(),
    /** A dish for the coming meal. */
    val meal: Idea? = null,
    /** A place on the eat-out list, when it's a day for going out. */
    val eatOut: Idea? = null,
    val toBuy: List<String> = emptyList(),
    val spentToday: Boolean = false,
    val watch: Idea? = null,
    /** Titles of what's due tomorrow. */
    val tomorrow: List<String> = emptyList(),
)

/**
 * The deck for this part of the day, in the order worth seeing. Mornings lead with what needs
 * doing, afternoons with lunch and errands, evenings with dinner, nights with looking back and
 * winding down. Answered cards ([handled]) leave; things are capped at [goal] a day so the deck
 * empties instead of refilling.
 */
fun momentCards(moment: Moment, inputs: MomentInputs, handled: Set<String>, goal: Int = DAY_GOAL): List<MomentCard> {
    val dealt = handled.count { !it.startsWith(MOMENT_PREFIX) }
    val things = inputs.things.filter { it.id !in handled }.take((goal - dealt).coerceAtLeast(0))
    fun card(kind: CardKind, eyebrow: String, title: String, detail: String) =
        MomentCard(if (kind == CardKind.SPENDS) SPEND_CARD else "$MOMENT_PREFIX${moment.name}:${kind.name}", kind, eyebrow, title, detail)

    val meal = inputs.meal?.let { card(CardKind.MEAL, "${moment.meal ?: "Snack"} idea", "${it.title}?", it.detail) }
    val eatOut = inputs.eatOut?.let { card(CardKind.EAT_OUT, "Or eat out", "${it.title}?", it.detail) }
    val shopping = inputs.toBuy.takeIf { it.isNotEmpty() }?.let {
        val names = it.take(3).joinToString(", ") + if (it.size > 3) " and ${it.size - 3} more" else ""
        card(CardKind.SHOPPING, "Going out?", if (it.size == 1) "1 thing to buy" else "${it.size} things to buy", names)
    }
    val spends = card(CardKind.SPENDS, "Before the day ends", "Did you spend anything today?", "One tap keeps this month's money right.").takeIf { !inputs.spentToday }
    val watch = inputs.watch?.let { card(CardKind.WATCH, "Wind down", "Watch ${it.title}?", it.detail) }
    val tomorrow = inputs.tomorrow.takeIf { it.isNotEmpty() }?.let {
        card(CardKind.TOMORROW, "Tomorrow", if (it.size == 1) it.first() else "${it.size} things tomorrow", if (it.size == 1) "So it isn't a surprise in the morning." else it.take(3).joinToString(", "))
    }

    val deck = when (moment) {
        Moment.MORNING -> things.take(2) + listOfNotNull(meal, shopping) + things.drop(2)
        Moment.MIDDAY -> listOfNotNull(meal, eatOut, shopping) + things
        Moment.EVENING -> listOfNotNull(meal, eatOut) + things + listOfNotNull(shopping, spends)
        Moment.NIGHT -> listOfNotNull(spends) + things.take(1) + listOfNotNull(watch, tomorrow)
    }
    return deck.filter { it.id !in handled }
}

/** The moment in one plain line, for under the greeting. */
fun momentLine(moment: Moment, cards: List<MomentCard>): String = when (cards.size) {
    0 -> "All sorted for ${moment.label.removePrefix("this ")} 🎉"
    1 -> "1 thing for you ${moment.label}"
    else -> "${cards.size} things for you ${moment.label}"
}

/** What the next part of the day brings: the reason to look again later. */
fun nextUp(moment: Moment): String = when (moment) {
    Moment.MORNING -> "Lunch ideas come around 11."
    Moment.MIDDAY -> "Dinner ideas come around 4."
    Moment.EVENING -> "Later tonight: a quick look back at the day."
    Moment.NIGHT -> "Breakfast ideas will be here in the morning."
}

/** Rows shown under the deck before "Show all". */
const val CLEAR_PEEK = 3

/** Said when nothing is left for this person: a different one each day, never a lecture. */
private val CHEERS = listOf(
    "Go for a walk. You've earned it.",
    "A clear list is a calm mind. Enjoy it.",
    "Call someone you love. There's time today.",
    "Make a cup of tea and sit down for ten minutes.",
    "Nothing waiting. That's a good day at home.",
    "Cook something slow tonight, just because.",
    "Share one good thing from today with the family.",
)

fun cheerLine(today: java.time.LocalDate): String = CHEERS[(today.toEpochDay() % CHEERS.size).toInt()]

/** "3 cleared today · 5 to go": the list as something that gets smaller. */
fun clearedLine(cleared: Int, left: Int): String = when {
    cleared == 0 -> if (left == 1) "1 to clear" else "$left to clear"
    else -> "$cleared cleared today · $left to go"
}

/** Cleared-in-a-day counts worth a cheer: early and often, so a small effort is noticed too. */
val CLEAR_MILESTONES = listOf(3, 5, 10, 15, 20, 30)

/** The cheer card's id for milestone [n], kept in the same handled set so it shows once. */
fun milestoneId(n: Int): String = "${MOMENT_PREFIX}CHEER:$n"

/**
 * The milestone to celebrate now: the highest one [cleared] has reached that hasn't been
 * cheered yet ([handled] holds the ones already shown). Null when there's nothing new.
 */
fun milestoneReached(cleared: Int, handled: Set<String>): Int? =
    CLEAR_MILESTONES.filter { it <= cleared }.maxOrNull()?.takeIf { milestoneId(it) !in handled }

fun milestoneTitle(n: Int): String = when (n) {
    3 -> "3 done already!"
    5 -> "5 cleared. Great going!"
    10 -> "10 today. You're on fire!"
    else -> "$n cleared today. Amazing!"
}
