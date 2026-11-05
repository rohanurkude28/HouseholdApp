package com.houseaffairs.app.core.data

import com.houseaffairs.app.feature.quickadd.ListTemplate
import java.time.LocalDate
import java.time.YearMonth

/** A list item ticked off: a book read, a film watched, a place visited. */
data class FinishedThing(val title: String, val template: String?, val date: LocalDate)

/** What one person's month or year is made of; each part is already limited to what they may see. */
data class PeriodInputs(
    /** Paid spending in the home currency; null when the person can't see money. */
    val spends: List<Txn>? = null,
    val savedToDreams: List<Pair<LocalDate, Long>> = emptyList(),
    /** Food month by month (from [kitchenMonths]). */
    val kitchen: List<KitchenMonth> = emptyList(),
    val finished: List<FinishedThing> = emptyList(),
    val tasksDone: List<LocalDate> = emptyList(),
    val goalsReached: List<Pair<String, LocalDate>> = emptyList(),
    /** Walks, workouts and chores: when, and for how long. */
    val activities: List<Pair<LocalDate, Int>> = emptyList(),
    val meals: List<LocalDate> = emptyList(),
    /** Days with a diary line, and the mood picked that day (if any). */
    val diary: List<Pair<LocalDate, String?>> = emptyList(),
)

/** Finished things of one kind, worded as done: "📚 Read · Dune, Sapiens". */
data class FinishedGroup(val emoji: String, val verb: String, val titles: List<String>)

data class PeriodReview(
    val from: LocalDate,
    val to: LocalDate,
    val spentMinor: Long?,
    /** Biggest categories first, top 4. */
    val topSpends: List<Pair<String, Long>>,
    /** For a year: spend in each month, oldest first. */
    val spendByMonth: List<Pair<YearMonth, Long>>,
    val savedMinor: Long,
    val groceriesMinor: Long?,
    val bought: Int,
    val wasted: Int,
    val wastedMinor: Long,
    val finished: List<FinishedGroup>,
    val tasksDone: Int,
    val goalsReached: List<String>,
    val activeMinutes: Int,
    val activeDays: Int,
    val mealsCooked: Int,
    val diaryDays: Int,
    /** Moods picked, most often first: "🙂" to 12. */
    val moods: List<Pair<String, Int>>,
    val days: Int,
) {
    val isEmpty: Boolean
        get() = (spentMinor ?: 0) == 0L && bought == 0 && finished.isEmpty() && tasksDone == 0 && goalsReached.isEmpty() &&
            activeMinutes == 0 && mealsCooked == 0 && diaryDays == 0
}

/** How each list's finished items read in the story. */
private fun verbOf(template: String?): Pair<String, String> = when (ListTemplate.of(template)) {
    ListTemplate.WATCH -> "🎬" to "Watched"
    ListTemplate.BOOKS -> "📚" to "Read"
    ListTemplate.MUSIC -> "🎵" to "Listened to"
    ListTemplate.TRAVEL -> "🧳" to "Visited"
    ListTemplate.EAT_OUT -> "🍽️" to "Ate out at"
    ListTemplate.BUY -> "🛍️" to "Bought"
    ListTemplate.GIFTS -> "🎁" to "Gave"
    ListTemplate.STUDY -> "🎓" to "Learnt"
    null -> "✅" to "Done"
}

/**
 * A month or a year ([from]..[to], cut at [today] for the one you're in) in one person's story:
 * money, the kitchen, what they finished, and how they lived. Pure, so it's tested.
 */
fun periodReview(from: LocalDate, to: LocalDate, today: LocalDate, inputs: PeriodInputs): PeriodReview {
    val end = minOf(to, today)
    fun inside(d: LocalDate) = !d.isBefore(from) && !d.isAfter(end)
    val spends = inputs.spends?.filter { !it.planned && inside(it.date) }
    val kitchen = inputs.kitchen.filter { !it.month.atEndOfMonth().isBefore(from) && !it.month.atDay(1).isAfter(end) }
    val active = inputs.activities.filter { inside(it.first) }
    val diary = inputs.diary.filter { inside(it.first) }
    return PeriodReview(
        from = from,
        to = to,
        spentMinor = spends?.sumOf { it.amountMinor },
        topSpends = spends.orEmpty().groupBy { it.category }.map { (c, t) -> c to t.sumOf { it.amountMinor } }.sortedByDescending { it.second }.take(4),
        spendByMonth = spends.orEmpty().groupBy { YearMonth.from(it.date) }.map { (m, t) -> m to t.sumOf { it.amountMinor } }.sortedBy { it.first },
        savedMinor = inputs.savedToDreams.filter { inside(it.first) }.sumOf { it.second },
        groceriesMinor = kitchen.mapNotNull { it.spentMinor }.takeIf { it.isNotEmpty() }?.sum(),
        bought = kitchen.sumOf { m -> m.bought.sumOf { it.second } },
        wasted = kitchen.sumOf { it.wasted.size },
        wastedMinor = kitchen.sumOf { it.wastedMinor },
        finished = inputs.finished.filter { inside(it.date) }.sortedBy { it.date }
            .groupBy { verbOf(it.template) }
            .map { (v, items) -> FinishedGroup(v.first, v.second, items.map { it.title }) }
            .sortedByDescending { it.titles.size },
        tasksDone = inputs.tasksDone.count(::inside),
        goalsReached = inputs.goalsReached.filter { inside(it.second) }.sortedBy { it.second }.map { it.first },
        activeMinutes = active.sumOf { it.second },
        activeDays = active.map { it.first }.distinct().size,
        mealsCooked = inputs.meals.count(::inside),
        diaryDays = diary.size,
        moods = diary.mapNotNull { it.second }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second },
        days = (end.toEpochDay() - from.toEpochDay() + 1).toInt().coerceAtLeast(0),
    )
}

/** One month in Plans: tasks done, list items finished (by kind) and goals reached. */
data class PlanMonth(val month: YearMonth, val tasks: List<String>, val finished: List<FinishedGroup>, val goals: List<String>) {
    val finishedCount: Int get() = finished.sumOf { it.titles.size }
}

/**
 * Plans month by month, newest first, for the last [months] months: [tasks] and [goals] are
 * (title, day done). Empty months are left out, except this one. Pure, so it's tested.
 */
fun planMonths(tasks: List<Pair<String, LocalDate>>, finished: List<FinishedThing>, goals: List<Pair<String, LocalDate>>, today: LocalDate, months: Int = 24): List<PlanMonth> {
    val now = YearMonth.from(today)
    return (0 until months).map { now.minusMonths(it.toLong()) }.map { ym ->
        fun <T> inMonth(items: List<T>, day: (T) -> LocalDate) = items.filter { YearMonth.from(day(it)) == ym }.sortedBy(day)
        val review = periodReview(ym.atDay(1), ym.atEndOfMonth(), today, PeriodInputs(finished = finished))
        PlanMonth(ym, inMonth(tasks) { it.second }.map { it.first }, review.finished, inMonth(goals) { it.second }.map { it.first })
    }.filter { it.month == now || it.tasks.isNotEmpty() || it.finished.isNotEmpty() || it.goals.isNotEmpty() }
}

/** A year of [PlanMonth]s, added up for the year view. */
data class PlanYear(val year: Int, val months: List<PlanMonth>) {
    val tasks: Int get() = months.sumOf { it.tasks.size }
    val goals: Int get() = months.sumOf { it.goals.size }

    /** Finished things over the year, by kind, most first. */
    val finished: List<FinishedGroup>
        get() = months.reversed().flatMap { it.finished }.groupBy { it.emoji to it.verb }
            .map { (k, g) -> FinishedGroup(k.first, k.second, g.flatMap { it.titles }) }.sortedByDescending { it.titles.size }
}

fun planYears(months: List<PlanMonth>): List<PlanYear> =
    months.groupBy { it.month.year }.map { (y, ms) -> PlanYear(y, ms.sortedByDescending { it.month }) }.sortedByDescending { it.year }
