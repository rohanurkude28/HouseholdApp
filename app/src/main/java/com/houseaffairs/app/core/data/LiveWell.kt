package com.houseaffairs.app.core.data

import kotlin.math.min
import kotlin.math.roundToInt

/** The four pillars of long, healthy lives, each scored from three everyday habits. */
enum class Pillar(val label: String, val short: String, val about: String) {
    MOVE("Move naturally", "Move", "Walking, chores and gardening, a little exercise"),
    EAT("Eat wisely", "Eat", "Home-cooked, mostly plants, not overdoing it"),
    CONNECT("Connect", "Connect", "Meals together, family time, your people"),
    OUTLOOK("Outlook", "Outlook", "Slowing down, purpose, gratitude"),
}

/**
 * What one person means to do for a long, good life: a few habits per pillar in their own words,
 * and weekly targets the wheel scores against. Private to them.
 */
data class WellPlan(
    val commitments: Map<Pillar, List<String>> = emptyMap(),
    val walkMinutes: Int = 150,
    val workoutMinutes: Int = 90,
    val homeMeals: Int = 7,
    val familyMeals: Int = 5,
    /** The person's own weekly targets ("sleep by 11 · 5 nights"), ticked off in the weekly story. */
    val custom: List<CustomTarget> = emptyList(),
) {
    val isEmpty: Boolean get() = commitments.values.all { it.isEmpty() }
}

data class CustomTarget(val name: String, val pillar: Pillar, val perWeek: Int)

/** Starting points for a plan; people can write their own too. */
val PLAN_IDEAS: Map<Pillar, List<String>> = linkedMapOf(
    Pillar.MOVE to listOf("Walk after dinner", "Take the stairs", "Water the plants myself", "Yoga on weekends", "Walk to the market", "Play with the kids outside"),
    Pillar.EAT to listOf("Dal or sabzi at every meal", "Stop when 80% full", "Cook dinner at home", "Fruit instead of sweets", "Fewer takeaways", "Early, light dinner"),
    Pillar.CONNECT to listOf("Family dinner, phones away", "Call parents on Sunday", "A weekend outing", "Meet a friend each week", "Help a neighbour"),
    Pillar.OUTLOOK to listOf("10 quiet minutes a day", "Share one good thing at night", "Pray or meditate", "Read before bed", "Sleep by 11", "Learn something new"),
)

/** The one gentle suggestion shown when the app opens and on the weekly story. */
data class Nudge(val pillar: Pillar, val gap: String, val suggestion: String, val byAi: Boolean = false)

/**
 * Picks something the week is light on and suggests one small step, preferring the person's
 * own plan. It rotates by day among the weakest few so it doesn't nag about the same thing.
 * Null when everything's going well.
 */
fun nudgeFor(wheel: Wheel, plan: WellPlan?, today: java.time.LocalDate): Nudge? {
    val weak = wheel.habits.filter { it.score != null && it.score < 0.6f }.sortedBy { it.score }.take(3)
    if (weak.isEmpty()) return null
    val habit = weak[today.dayOfYear % weak.size]
    val mine = plan?.commitments?.get(habit.pillar).orEmpty()
    val suggestion = if (mine.isNotEmpty()) {
        val pick = mine[today.dayOfYear % mine.size]
        "You planned to “${pick.replaceFirstChar { it.lowercase() }}”. ${if (today.dayOfWeek.value >= 6) "A weekend is a good time for it." else "Maybe today?"}"
    } else {
        habit.tip
    }
    return Nudge(habit.pillar, habit.detail.replaceFirstChar { it.uppercase() }, suggestion)
}

data class CookedMeal(val title: String, val people: Int, val plantBased: Boolean)

data class ActivityLog(val kind: String, val minutes: Int, val people: Int, val id: String = "", val date: java.time.LocalDate? = null)

/** A person's weekly self check-in: 0 = not really, 1 = a little, 2 = yes. */
data class CheckIn(val unwind: Int, val social: Int, val gratitude: Int)

/** Everything from the past 7 days the wheel is scored on. */
data class WeekData(
    val meals: List<CookedMeal> = emptyList(),
    val activities: List<ActivityLog> = emptyList(),
    val diningSpentMinor: Long? = null,
    /** Dining budget for a week (monthly limit × 7/30); null without a budget. */
    val diningAllowanceMinor: Long? = null,
    val tasksDone: Int = 0,
    val tasksOverdue: Int = 0,
    /** Trips, outings and family events that happened this week. */
    val togetherEvents: Int = 0,
    val savedToDreamsMinor: Long = 0,
    val wasted: Int = 0,
    val checkIn: CheckIn? = null,
    /** Days of the week covered so far (1 on Monday, 7 once it's over); weekly aims scale to it. */
    val days: Int = 7,
    /** Days with a diary line written: counts toward gratitude. */
    val diaryDays: Int = 0,
)

data class Habit(
    val pillar: Pillar,
    val name: String,
    /** 0..1, or null when there's nothing to go on yet. */
    val score: Float?,
    val detail: String,
    val tip: String,
)

data class Wheel(val habits: List<Habit>) {
    fun pillarScore(p: Pillar): Float? = habits.filter { it.pillar == p }.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    /** 0..100 over the habits that could be scored. */
    val overall: Int get() = (habits.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.average() ?: 0.0).times(100).roundToInt()
    /** The habit with most room to grow, for the week's one suggestion. */
    val focus: Habit? get() = habits.filter { it.score != null }.minByOrNull { it.score!! }
}

private fun ratio(value: Float, target: Float) = min(value / target, 1f)

private val WALKING = setOf("Walk", "Run", "Cycling")
private val CHORES = setOf("Gardening", "Housework")

private val MEAT_WORDS = listOf("chicken", "mutton", "fish", "egg", "prawn", "meat", "beef", "pork", "lamb", "keema")

/** True when none of a dish's ingredients is meat, fish or egg. */
fun isPlantBased(ingredients: List<Ingredient>): Boolean =
    ingredients.none { i -> MEAT_WORDS.any { i.name.lowercase().contains(it) } }

/** Scores the week. Pure, so the rules are tested and easy to tune. */
fun liveWell(w: WeekData, plan: WellPlan = WellPlan()): Wheel {
    fun minutes(kinds: Set<String>?) = w.activities.filter { kinds == null || it.kind in kinds }.sumOf { it.minutes }
    val walk = minutes(WALKING)
    val chores = minutes(CHORES)
    val workout = w.activities.filter { it.kind !in WALKING && it.kind !in CHORES }.sumOf { it.minutes }
    val home = w.meals.size
    val plant = w.meals.count { it.plantBased }
    val family = w.meals.count { it.people >= 2 }
    val together = w.activities.count { it.people >= 2 } + w.togetherEvents
    val c = w.checkIn

    // A weekly aim, scaled to the days so far: by Wednesday, 3/7 of it is on track.
    val share = w.days.coerceIn(1, 7) / 7f
    fun aim(weekly: Int) = weekly * share
    val sofar = w.days < 7
    fun line(done: Int, weekly: Int, what: String) = if (sofar) "$done $what so far · aim $weekly a week" else "$done of $weekly $what"

    return Wheel(
        listOf(
            Habit(Pillar.MOVE, "Walk", ratio(walk.toFloat(), aim(plan.walkMinutes)), line(walk, plan.walkMinutes, "min walking"), "A 20-minute walk after dinner, every day, gets you there."),
            Habit(Pillar.MOVE, "Garden & chores", ratio(chores.toFloat(), aim(60)), line(chores, 60, "min gardening or housework"), "Do one chore by hand instead of the shortcut, like sweeping or watering plants."),
            Habit(Pillar.MOVE, "Workout", ratio(workout.toFloat(), aim(plan.workoutMinutes)), line(workout, plan.workoutMinutes, "min yoga, gym or sport"), "Two 45-minute sessions a week is plenty. Try yoga together."),

            Habit(Pillar.EAT, "Home-cooked", ratio(home.toFloat(), aim(plan.homeMeals)), line(home, plan.homeMeals, "meals cooked at home"), "Cook one more meal at home. “What can I cook?” has ideas."),
            Habit(
                Pillar.EAT, "Plant-based",
                if (home == 0) null else plant.toFloat() / home,
                if (home == 0) "No meals logged yet" else "$plant of $home meals plant-based",
                "Make dal, beans or a vegetable dish the star of more meals.",
            ),
            Habit(
                Pillar.EAT, "Moderation",
                w.diningAllowanceMinor?.takeIf { it > 0 }?.let { allowance ->
                    val spent = w.diningSpentMinor ?: 0
                    if (spent <= allowance) 1f else (2f - spent.toFloat() / allowance).coerceIn(0f, 1f)
                },
                when {
                    w.diningAllowanceMinor == null -> "Set a Dining budget to track"
                    (w.diningSpentMinor ?: 0) <= w.diningAllowanceMinor -> "Eating out within this week's dining budget"
                    else -> "Eating out more than this week's share of the dining budget"
                },
                "Swap one meal out for a home-cooked one this week.",
            ),

            Habit(Pillar.CONNECT, "Family meals", ratio(family.toFloat(), aim(plan.familyMeals)), line(family, plan.familyMeals, "meals eaten together"), "Pick a few dinners a week where everyone eats together, phones away."),
            Habit(Pillar.CONNECT, "Together time", ratio(together.toFloat(), aim(2).coerceAtLeast(1f)), line(together, 2, "outings or activities together"), "Plan a Sunday outing or a family walk."),
            Habit(Pillar.CONNECT, "Right tribe", c?.let { it.social / 2f }, if (c == null) "Answer the weekly check-in" else "Time with friends and family", "Call or meet someone who lifts you up."),

            Habit(Pillar.OUTLOOK, "Unwind", c?.let { it.unwind / 2f }, if (c == null) "Answer the weekly check-in" else "Time to slow down", "Ten quiet minutes a day: a walk, prayer, music or tea on the balcony."),
            Habit(
                Pillar.OUTLOOK, "Purpose",
                if (w.tasksDone + w.tasksOverdue == 0 && w.savedToDreamsMinor == 0L) null
                else maxOf(if (w.tasksDone + w.tasksOverdue > 0) w.tasksDone.toFloat() / (w.tasksDone + w.tasksOverdue) else 0f, if (w.savedToDreamsMinor > 0) 1f else 0f),
                "${w.tasksDone} tasks done" + if (w.savedToDreamsMinor > 0) " · saved toward a dream" else "",
                "Move a little money toward a dream, or finish one task that matters.",
            ),
            Habit(
                Pillar.OUTLOOK, "Gratitude",
                // The check-in, or the diary: a line written on most days counts as much.
                listOfNotNull(c?.let { it.gratitude / 2f }, if (w.diaryDays > 0) (w.diaryDays.toFloat() / w.days.coerceIn(1, 7)).coerceAtMost(1f) else null).maxOrNull(),
                when {
                    w.diaryDays > 0 -> "Diary written on ${w.diaryDays} of ${w.days} days"
                    c == null -> "Write a line in your diary, or answer the check-in"
                    else -> "Moments of gratitude or faith"
                },
                "Each night, write one good thing from the day in your diary.",
            ),
        ),
    )
}
