package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*


/** The household's monthly budget. Amounts are minor units in [currency]. */
data class Budget(
    val limitMinor: Long,
    val currency: String,
    /** Day the budget month starts: 1 for calendar months, or salary day (e.g. 25). */
    val cycleStartDay: Int = 1,
    /** What the family wants left over at the end of the cycle. */
    val savingsGoalMinor: Long = 0,
    /** Per-category limits; categories not listed are unbudgeted. */
    val categories: Map<String, Long> = emptyMap(),
    /** Credit cards with their own statement cycles. */
    val cards: List<Card> = emptyList(),
    /** Card bills marked paid, by [billKey]. */
    val paidBills: Set<String> = emptySet(),
    /** Monthly take-home income, if the family shared it; only used to suggest the budget. */
    val incomeMinor: Long = 0,
    /** The family's own categories ("Cook", "Maid", "Insurance"), with or without a limit. */
    val customCategories: List<String> = emptyList(),
)

/** Every category to pick from: the fixed ones, then the family's own, with "Other" last. */
fun expenseCategories(budget: Budget?): List<String> {
    val fixed = com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES - "Other"
    val own = (budget?.customCategories.orEmpty() + budget?.categories?.keys.orEmpty())
        .map { it.trim() }
        .filter { c -> c.isNotEmpty() && !c.equals("Other", ignoreCase = true) && fixed.none { it.equals(c, ignoreCase = true) } }
        .distinctBy { it.lowercase() }
    return fixed + own + "Other"
}

/** True when [name] isn't one of the fixed categories, so saving it makes it the family's own. */
fun isNewCategory(name: String, budget: Budget?): Boolean =
    name.isNotBlank() && expenseCategories(budget).none { it.equals(name.trim(), ignoreCase = true) }

/** The family's own category named in a line: "cook 5000" → "Cook" once they've added Cook. */
fun customCategoryIn(text: String, budget: Budget?): String? {
    val own = expenseCategories(budget).drop(com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES.size - 1).dropLast(1)
    // Longest first, so "Car insurance" wins over "Insurance".
    return own.sortedByDescending { it.length }.firstOrNull { c ->
        Regex("\\b${Regex.escape(c)}s?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }
}

data class Txn(
    val id: String,
    val title: String,
    val category: String,
    val amountMinor: Long,
    val date: LocalDate,
    /** Planned spends reserve budget before they happen ("trip next week"). */
    val planned: Boolean,
    val subcategory: String? = null,
    /** Name of the trip or outing this was part of. */
    val trip: String? = null,
    /** "UPI", "Card", "Cash"… as entered. */
    val paidWith: String? = null,
    /** Which credit card, when there's more than one. */
    val card: String? = null,
)

/** A subscription or bill expected later in the cycle. */
data class DuePayment(val id: String, val title: String, val date: LocalDate, val amountMinor: Long)

data class Cycle(val start: LocalDate, val endExclusive: LocalDate) {
    val days: Int get() = daysBetween(start, endExclusive).toInt()
    operator fun contains(date: LocalDate) = !date.isBefore(start) && date.isBefore(endExclusive)
    /** 1-based day number of [date] within the cycle. */
    fun dayOf(date: LocalDate): Int = daysBetween(start, date).toInt() + 1
}

/** The budget month containing [today], starting on [startDay] (clamped to short months). */
fun cycleFor(today: LocalDate, startDay: Int): Cycle {
    fun startIn(year: Int, month: Int, plus: Int = 0) = addMonths(year, month, plus).let { (y, m) -> clampedDate(y, m, startDay) }
    val thisMonth = startIn(today.year, today.monthValue)
    val start = if (today.isBefore(thisMonth)) startIn(today.year, today.monthValue, -1) else thisMonth
    return Cycle(start, startIn(start.year, start.monthValue, 1))
}

enum class Pace { UNDER, ON_TRACK, AHEAD }

/** Paid in one go (usually early in the month), so they're never "spending fast". */
val FIXED_CATEGORIES = setOf("Rent", "Bills")

data class CategoryStatus(
    val name: String,
    val limitMinor: Long?,
    val spentMinor: Long,
    val plannedMinor: Long,
    val pace: Pace,
) {
    val leftMinor: Long? get() = limitMinor?.let { it - spentMinor - plannedMinor }
    val over: Boolean get() = limitMinor != null && spentMinor > limitMinor
}

data class UpcomingSpend(val id: String, val title: String, val date: LocalDate, val amountMinor: Long, val planned: Boolean, val category: String? = null)

data class BudgetSummary(
    val cycle: Cycle,
    val currency: String,
    val limitMinor: Long,
    val savingsGoalMinor: Long,
    val spentMinor: Long,
    val plannedMinor: Long,
    /** Subscriptions and bills still due this cycle. */
    val committedMinor: Long,
    val day: Int,
    val pace: Pace,
    /** How much spending "should" have happened by today to finish on target. */
    val expectedByTodayMinor: Long,
    val categories: List<CategoryStatus>,
    val upcoming: List<UpcomingSpend>,
    /** What's been paid this cycle, newest first. */
    val paid: List<Txn>,
) {
    /** Budget not yet spent, planned or committed. */
    val leftMinor: Long get() = limitMinor - spentMinor - plannedMinor - committedMinor
    /** What can still be spent while keeping the savings goal. */
    val safeToSpendMinor: Long get() = leftMinor - savingsGoalMinor
    val daysLeft: Int get() = cycle.days - day + 1
}

/** Days of spending needed before a pace is projected; earlier, one outing would read as a trend. */
internal const val PACE_SETTLE_DAYS = 7

private fun paceOf(spent: Long, cap: Long, day: Int, days: Int): Pace {
    if (cap <= 0) return Pace.ON_TRACK
    val expected = cap * day / days
    // Ahead means >10% over the expected line *and* a meaningful share used, so one early dinner isn't an alarm.
    // In the first week that share is half: a quarter on day 2 is a weekend, not a month's habit.
    val meaningful = if (day < PACE_SETTLE_DAYS) spent * 2 >= cap else spent * 4 >= cap
    return when {
        spent > expected * 11 / 10 + cap / 50 && meaningful -> Pace.AHEAD
        spent < expected * 7 / 10 -> Pace.UNDER
        else -> Pace.ON_TRACK
    }
}

/**
 * Where the budget stands today. [txns] and [due] may contain anything; only this cycle counts.
 * Pure, so the money rules are unit-tested.
 */
fun summarize(budget: Budget, txns: List<Txn>, due: List<DuePayment>, today: LocalDate): BudgetSummary {
    val cycle = cycleFor(today, budget.cycleStartDay)
    val inCycle = txns.filter { it.date in cycle || (it.planned && !it.date.isBefore(today) && it.date.isBefore(cycle.endExclusive)) }
    val paid = inCycle.filter { !it.planned }
    val planned = inCycle.filter { it.planned }
    val day = cycle.dayOf(today)
    val stillDue = due.filter { !it.date.isBefore(today) && it.date.isBefore(cycle.endExclusive) }

    val spendCap = (budget.limitMinor - budget.savingsGoalMinor).coerceAtLeast(0)
    val spent = paid.sumOf { it.amountMinor }
    val fixedSpent = paid.filter { it.category in FIXED_CATEGORIES }.sumOf { it.amountMinor }
    // What fixed costs take this month: their limits if set, otherwise what's been paid.
    val fixedShare = FIXED_CATEGORIES.sumOf { budget.categories[it] ?: 0L }.coerceAtLeast(fixedSpent)
    val names = (budget.categories.keys + inCycle.map { it.category }).distinct()
    val categories = names.map { name ->
        val catSpent = paid.filter { it.category == name }.sumOf { it.amountMinor }
        val limit = budget.categories[name]
        CategoryStatus(
            name = name,
            limitMinor = limit,
            spentMinor = catSpent,
            plannedMinor = planned.filter { it.category == name }.sumOf { it.amountMinor },
            pace = if (name in FIXED_CATEGORIES) Pace.ON_TRACK else limit?.let { paceOf(catSpent, it, day, cycle.days) } ?: Pace.ON_TRACK,
        )
    }.sortedWith(compareByDescending<CategoryStatus> { it.over }.thenByDescending { it.spentMinor.toDouble() / (it.limitMinor ?: Long.MAX_VALUE) }.thenByDescending { it.spentMinor })

    return BudgetSummary(
        cycle = cycle,
        currency = budget.currency,
        limitMinor = budget.limitMinor,
        savingsGoalMinor = budget.savingsGoalMinor,
        spentMinor = spent,
        plannedMinor = planned.sumOf { it.amountMinor },
        committedMinor = stillDue.sumOf { it.amountMinor },
        day = day,
        // Pace only the day-to-day part: fixed costs count in full from day one.
        pace = paceOf(spent - fixedSpent, (spendCap - fixedShare).coerceAtLeast(0), day, cycle.days),
        expectedByTodayMinor = fixedShare + (spendCap - fixedShare).coerceAtLeast(0) * day / cycle.days,
        categories = categories,
        upcoming = (
            planned.map { UpcomingSpend(it.id, it.title, it.date, it.amountMinor, planned = true, category = it.category) } +
                stillDue.map { UpcomingSpend(it.id, it.title, it.date, it.amountMinor, planned = false) }
            ).sortedBy { it.date.toEpochDay() },
        paid = paid.sortedByDescending { it.date.toEpochDay() },
    )
}

/** A starting split for a new budget, in percent of the monthly amount. Savings is the remainder. */
val SUGGESTED_SPLIT = linkedMapOf(
    "Rent" to 30, "Groceries" to 12, "Bills" to 6, "Transport" to 6, "Dining" to 5, "Health" to 4,
    "Shopping" to 5, "Entertainment" to 3, "Home" to 3, "Travel" to 4, "Gifts" to 2,
)
