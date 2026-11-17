package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.time.*
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.first

enum class DiaryKind { ACTIVITY, MEAL, TASK, EVENT, TRIP }

private val ACTIVITY_EMOJI = mapOf(
    "Walk" to "🚶", "Run" to "🏃", "Cycling" to "🚴", "Yoga" to "🧘", "Gym" to "🏋️", "Sports" to "⚽",
    "Swim" to "🏊", "Dance" to "💃", "Gardening" to "🌱", "Housework" to "🧹", "Study" to "📖",
)

/** Something that happened that day, picked up from what was logged. */
data class DiaryEntry(val emoji: String, val text: String, val kind: DiaryKind, val id: String)

data class DiaryDay(val date: LocalDate, val note: String?, val mood: String?, val entries: List<DiaryEntry>)

/** Steps, sleep and workouts from the phone's health app (Health Connect on Android). */
data class HealthWeek(val steps: Long, val sleepHoursPerNight: Double?, val exerciseMinutes: Int)

data class WeekReview(
    val wheel: Wheel,
    val week: WeekData,
    val spentMinor: Long?,
    val currency: String?,
    val activityMinutes: Int,
    val plan: WellPlan = WellPlan(),
    /** How many times each custom target was done this week. */
    val targetCounts: Map<String, Int> = emptyMap(),
    /** The Monday this week starts on. */
    val weekStart: LocalDate = mondayOf(systemToday()),
    /** Newest day first. */
    val diary: List<DiaryDay> = emptyList(),
    /** Whether the pantry is tracked for this person, so "nothing went to waste" means something. */
    val tracksPantry: Boolean = false,
    /** What today's meals added up to, against the plan's targets; null when nothing was logged. */
    val today: Macros? = null,
) {
    val isThisWeek: Boolean get() = weekStart == mondayOf(systemToday())
    /** Only for the week you're in: there's nothing to nudge about last week. */
    val nudge: Nudge? get() = if (isThisWeek) nudgeFor(wheel, plan, systemToday()) else null
}

/**
 * Live to 100, read for both phones: one person's week (the wheel, the diary), their month or
 * year story, their plan, the daily check-in and the weekly one. Each part respects what the
 * member may see; the diary, plan and check-ins are private to the person. [health] reads the
 * phone's steps and sleep where it can (Android's Health Connect); null elsewhere.
 */
class WeeklyRepository(
    private val pantry: PantryRepository = PantryRepository(),
    private val health: (suspend (LocalDate, LocalDate) -> HealthWeek?)? = null,
) {
    private suspend fun visibleDocs(householdId: String, name: String, uid: String): List<DocumentSnapshot> =
        runCatching { visible(householdId, name, uid).get().documents }.getOrDefault(emptyList())

    private fun DocumentSnapshot.created(): LocalDate? = createdMillis()?.let(::dateOfMillis)
    private fun DocumentSnapshot.at(field: String): LocalDate? = millis(field)?.let(::dateOfMillis)

    /** Is this mine? Listed among the people, or (when nobody's listed) added by me. */
    private fun DocumentSnapshot.involves(field: String, uid: String): Boolean {
        val people = strings(field).ifEmpty { str(field)?.let { listOf(it) }.orEmpty() }
        return if (people.isEmpty()) str("createdBy") == uid else uid in people
    }

    /**
     * One person's week, Monday to Sunday (to today, for this week): their own walks, meals and
     * tasks, the family's outings and events they were part of, their diary, and the wheel.
     */
    suspend fun load(householdId: String, me: Member, weekStart: LocalDate = mondayOf(systemToday()), today: LocalDate = systemToday()): WeekReview {
        val from = mondayOf(weekStart)
        val to = earlier(from.plusDays(6), today)
        fun inWeek(d: LocalDate?) = d != null && !d.isBefore(from) && !d.isAfter(to)
        val uid = me.uid

        // Meals I ate: the cooking log, checked against recipes for plant-based.
        val recipeDocs = visibleDocs(householdId, "recipes", uid)
        val recipes = recipeDocs.associate { d ->
            d.str("title").orEmpty().lowercase() to (d.plainData()["ingredients"] as? List<*>).orEmpty().mapNotNull { raw ->
                (raw as? Map<*, *>)?.let { Ingredient((it["name"] as? String).orEmpty(), (it["amount"] as? Number)?.toDouble(), (it["unit"] as? String) ?: "g") }
            }
        }
        val mealDocs = if (me.canAccess(AppModule.STOCK)) visibleDocs(householdId, "cookLog", uid).filter { inWeek(it.created()) && it.involves("eaters", uid) } else emptyList()
        // Today's plate: one serving of each dish this person ate today.
        val savedRecipes = recipeDocs.map { d -> Recipe(d.id, d.str("title").orEmpty(), emptyList(), 2, emptyList(), null, macros = d.str("macros")) }
        val todayMacros = dayMacros(mealDocs.filter { it.created() == today }.map { it.str("title").orEmpty() }, savedRecipes)
        val meals = mealDocs.map { d ->
            val title = d.str("title").orEmpty()
            CookedMeal(title, (d.long("people") ?: 1).toInt().coerceAtLeast(1), recipes[title.lowercase()]?.let(::isPlantBased) ?: true)
        }

        val activityDocs = visibleDocs(householdId, "activities", uid).filter { inWeek(it.date("date") ?: it.created()) && it.involves("who", uid) }
        val activities = activityDocs.map { d ->
            ActivityLog(
                id = d.id, date = d.date("date") ?: d.created(), kind = d.str("kind") ?: "Walk",
                minutes = d.str("minutes")?.toIntOrNull() ?: 0, people = d.strings("who").size.coerceAtLeast(1),
            )
        }

        // Money stays household-wide: dining against a week's share of the Dining budget, total spend.
        var dining: Long? = null
        var allowance: Long? = null
        var spent: Long? = null
        var currency: String? = null
        var saved = 0L
        if (me.canAccess(AppModule.MONEY)) {
            val budget = runCatching { collection(householdId, "budgets").document("main").get() }.getOrNull()
            currency = budget?.str("currency")
            allowance = ((budget?.plainData()?.get("categories") as? Map<*, *>)?.get("Dining") as? Number)?.toLong()?.let { it * 7 / 30 }
            val week = visibleDocs(householdId, "transactions", uid).filter { it.str("status") != "Planned" && inWeek(it.date("date")) }
            spent = week.filter { currency == null || it.str("currency") == currency }.sumOf { it.long("amountMinor") ?: 0 }
            dining = week.filter { it.str("category") == "Dining" }.sumOf { it.long("amountMinor") ?: 0 }
            saved = visibleDocs(householdId, "transfers", uid)
                .filter { (it.str("to") ?: "").startsWith("dream:") && inWeek(it.date("date")) }
                .sumOf { it.long("amountMinor") ?: 0 }
        }

        // My tasks: assigned to me (or added by me with nobody assigned).
        val tasks = if (me.canAccess(AppModule.PLANS)) visibleDocs(householdId, "tasks", uid).filter { it.involves("assignee", uid) } else emptyList()
        val doneTasks = tasks.filter { it.bool("done") == true && inWeek(it.at("completedAt")) }
        val overdue = tasks.count { it.bool("done") != true && it.date("due")?.isBefore(to) == true }
        // Study items I added and finished this week; marking done stamps updatedAt.
        val learned = if (me.canAccess(AppModule.PLANS)) {
            visibleDocs(householdId, "listItems", uid).count { d ->
                d.str("template") == com.houseaffairs.app.feature.quickadd.ListTemplate.STUDY.label && d.str("status") == "DONE" &&
                    d.str("createdBy") == uid && inWeek(d.at("updatedAt"))
            }
        } else 0

        val tripDocs = if (me.canAccess(AppModule.MONEY)) {
            visibleDocs(householdId, "trips", uid).filter { d ->
                val start = d.date("start") ?: return@filter false
                val end = d.date("end") ?: start
                !start.isAfter(to) && !end.isBefore(from) && d.involves("who", uid)
            }
        } else emptyList()
        val eventDocs = if (me.canAccess(AppModule.PLANS)) {
            visibleDocs(householdId, "events", uid).mapNotNull { d ->
                val on = d.date("date")?.let { nextOccurrence(it, d.str("repeat"), from) } ?: return@mapNotNull null
                if (inWeek(on) && d.involves("people", uid)) d to on else null
            }
        } else emptyList()
        val wasted = if (me.canAccess(AppModule.STOCK)) visibleDocs(householdId, "stock", uid).count { d -> d.date("expiry")?.let { inWeek(it) && it.isBefore(today) } == true && d.str("status") !in GONE_STATUSES } else 0

        val checkDoc = runCatching { collection(householdId, "checkins").document("${uid}_${isoWeek(from)}").get() }.getOrNull()?.takeIf { it.exists }
        val checkData = checkDoc?.plainData().orEmpty()
        val counts = (checkData["targets"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> (k as? String)?.let { it to ((v as? Number)?.toInt() ?: 0) } }.toMap()
        val check = checkDoc?.takeIf { "unwind" in checkData }?.let { CheckIn((it.long("unwind") ?: 0).toInt(), (it.long("social") ?: 0).toInt(), (it.long("gratitude") ?: 0).toInt()) }

        // The diary: one line and a mood per day, private to me.
        val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val notes = dates.associateWith { day ->
            runCatching { collection(householdId, "diary").document("${uid}_$day").get() }.getOrNull()?.takeIf { it.exists }
        }
        val diary = dates.reversed().map { day ->
            val entries = buildList {
                activityDocs.filter { (it.date("date") ?: it.created()) == day }.forEach { d ->
                    val kind = d.str("kind") ?: "Walk"
                    add(DiaryEntry(ACTIVITY_EMOJI[kind] ?: "🏅", "$kind · ${d.str("minutes") ?: "?"} min", DiaryKind.ACTIVITY, d.id))
                }
                mealDocs.filter { it.created() == day }.forEach { d ->
                    val n = (d.long("people") ?: 1).toInt()
                    add(DiaryEntry("🍲", "Ate ${d.str("title").orEmpty()}" + if (n >= 2) " together ($n)" else "", DiaryKind.MEAL, d.id))
                }
                doneTasks.filter { it.at("completedAt") == day }.forEach { d -> add(DiaryEntry("✅", d.str("title").orEmpty(), DiaryKind.TASK, d.id)) }
                eventDocs.filter { it.second == day }.forEach { (d, _) -> add(DiaryEntry("🎉", d.str("title").orEmpty(), DiaryKind.EVENT, d.id)) }
                tripDocs.filter { d -> val st = d.date("start")!!; !day.isBefore(st) && !day.isAfter(d.date("end") ?: st) }
                    .forEach { d -> add(DiaryEntry("🧳", d.str("title").orEmpty(), DiaryKind.TRIP, d.id)) }
            }
            val note = notes[day]
            DiaryDay(day, note?.str("note")?.takeIf { it.isNotBlank() }, note?.str("mood"), entries)
        }

        val data = WeekData(
            meals, activities, dining, allowance, doneTasks.size, overdue,
            togetherEvents = tripDocs.size + eventDocs.size,
            savedToDreamsMinor = saved, wasted = wasted, checkIn = check,
            days = dates.size, diaryDays = diary.count { it.note != null }, learned = learned,
            dayChecks = notes.values.mapNotNull { d ->
                val well = d?.plainData()?.get("well") as? Map<*, *> ?: return@mapNotNull null
                fun of(p: Pillar) = (well[p.name] as? Number)?.toInt() ?: 0
                DayCheck(of(Pillar.MOVE), of(Pillar.EAT), of(Pillar.CONNECT), of(Pillar.OUTLOOK))
            },
        ).let { d ->
            // Steps, sleep and workouts, where this phone can read them.
            runCatching { health?.invoke(dates.first(), dates.last()) }.getOrNull()?.let { h -> d.copy(steps = h.steps, sleepHours = h.sleepHoursPerNight, exerciseMinutes = h.exerciseMinutes) } ?: d
        }
        val plan = loadPlan(householdId, uid)
        return WeekReview(liveWell(data, plan), data, spent, currency, activities.sumOf { it.minutes }, plan, counts, from, diary, me.canAccess(AppModule.STOCK), todayMacros)
    }

    /** A day's steps, for the check-in's "Did you move?"; null without a health app. */
    suspend fun stepsOn(day: LocalDate): Long? = runCatching { health?.invoke(day, day)?.steps?.takeIf { it > 0 } }.getOrNull()

    /**
     * One person's month or year ([from]..[to]) for the story: the home's spending and kitchen,
     * what was finished, and their own tasks, goals, walks, meals and diary.
     */
    suspend fun loadPeriod(householdId: String, me: Member, from: LocalDate, to: LocalDate, today: LocalDate = systemToday()): PeriodReview {
        val uid = me.uid
        val yearLong = from.dayOfYear == 1 && to == from.plusYears(1).minusDays(1)
        // Read from the period before too, so the story can compare.
        val readFrom = if (yearLong) from.minusYears(1) else from.minusMonths(1)
        fun inside(d: LocalDate?) = d != null && !d.isBefore(readFrom) && !d.isAfter(to)

        var spends: List<Txn>? = null
        var saved = emptyList<Pair<LocalDate, Long>>()
        var groceryBudget: Long? = null
        if (me.canAccess(AppModule.MONEY)) {
            val budget = runCatching { collection(householdId, "budgets").document("main").get() }.getOrNull()
            val currency = budget?.str("currency")
            groceryBudget = ((budget?.plainData()?.get("categories") as? Map<*, *>)?.get("Groceries") as? Number)?.toLong()
            spends = visibleDocs(householdId, "transactions", uid)
                .filter { inside(it.date("date")) && (currency == null || it.str("currency") == currency) }
                .map { d -> Txn(d.id, d.str("title").orEmpty(), d.str("category") ?: "Other", d.long("amountMinor") ?: 0, d.date("date")!!, d.str("status") == "Planned") }
            saved = visibleDocs(householdId, "transfers", uid)
                .filter { (it.str("to") ?: "").startsWith("dream:") && inside(it.date("date")) }
                .map { it.date("date")!! to (it.long("amountMinor") ?: 0) }
        }
        val kitchen = if (me.canAccess(AppModule.STOCK)) {
            val h = pantry.observeHistory(householdId, uid, StockKind.FOOD).first()
            // Enough months back to cover the whole period.
            val months = ((today.year - readFrom.year) * 12 + today.monthValue - readFrom.monthValue + 1).coerceIn(1, 36)
            kitchenMonths(h.bought, h.lives, h.wasted, spends?.filter { it.category == "Groceries" }, today, groceryBudget, months)
        } else emptyList()
        val plans = me.canAccess(AppModule.PLANS)
        val finished = if (plans) {
            visibleDocs(householdId, "listItems", uid).filter { it.str("status") == "DONE" }.mapNotNull { d ->
                (d.at("updatedAt") ?: d.created())?.takeIf(::inside)?.let { FinishedThing(d.str("title").orEmpty(), d.str("template"), it) }
            }
        } else emptyList()
        val tasks = if (plans) visibleDocs(householdId, "tasks", uid).filter { it.bool("done") == true && it.involves("assignee", uid) }.mapNotNull { it.at("completedAt") } else emptyList()
        val goals = if (plans) {
            visibleDocs(householdId, "weeklyGoals", uid).filter { (it.long("progress") ?: 0) >= 100 && it.involves("who", uid) }
                .mapNotNull { d -> (d.at("doneAt") ?: d.created())?.let { d.str("title").orEmpty() to it } }
        } else emptyList()
        val activities = visibleDocs(householdId, "activities", uid).filter { it.involves("who", uid) }
            .mapNotNull { d -> (d.date("date") ?: d.created())?.let { it to (d.str("minutes")?.toIntOrNull() ?: 0) } }
        val meals = if (me.canAccess(AppModule.STOCK)) visibleDocs(householdId, "cookLog", uid).filter { it.involves("eaters", uid) }.mapNotNull { it.created() } else emptyList()
        // The diary is private: only my own lines.
        val diary = visibleDocs(householdId, "diary", uid).filter { it.str("createdBy") == uid && !it.str("note").isNullOrBlank() }
            .mapNotNull { d -> d.str("date")?.let { runCatching { parseDate(it) }.getOrNull() }?.let { it to d.str("mood") } }
        val inputs = PeriodInputs(spends, saved, kitchen, finished, tasks, goals, activities, meals, diary)
        // The month or year before, the same length back, for "less than October".
        val compared = comparedDays(from, to, today, yearLong)
        val previous = periodReview(compared.start, compared.endInclusive, today, inputs)
        return periodReview(from, to, today, inputs).copy(previous = previous)
    }

    /** Which of [days] already have a diary line or mood from this person. */
    suspend fun writtenDays(householdId: String, uid: String, days: List<LocalDate>): Set<LocalDate> =
        days.filter { day -> runCatching { collection(householdId, "diary").document("${uid}_$day").get() }.getOrNull()?.exists == true }.toSet()

    /** Writes (or clears) the day's diary line, mood and check-in; private to the person. */
    fun saveDiary(householdId: String, uid: String, day: LocalDate, note: String, mood: String?, check: DayCheck? = null) {
        val ref = collection(householdId, "diary").document("${uid}_$day")
        if (note.isBlank() && mood == null && check == null) {
            Writes.later { ref.delete() }
            return
        }
        val doc = mapOf(
            "note" to note.trim(), "mood" to mood, "date" to day.toString(),
            "createdBy" to uid, "visibleTo" to listOf(uid), "createdAt" to FieldValue.serverTimestamp,
        ) + (check?.let { mapOf("well" to Pillar.entries.associate { p -> p.name to it.of(p) }) } ?: emptyMap())
        // Merged, so answering the check-in later keeps a line written earlier.
        Writes.later { ref.set(doc, merge = true) }
    }

    /** The person's own live-well plan, or the defaults. */
    suspend fun loadPlan(householdId: String, uid: String): WellPlan {
        val d = runCatching { collection(householdId, "wellPlans").document(uid).get() }.getOrNull()?.takeIf { it.exists } ?: return WellPlan()
        val data = d.plainData()
        val raw = data["commitments"] as? Map<*, *>
        val macros = data["macros"] as? Map<*, *>
        return WellPlan(
            commitments = Pillar.entries.associateWith { p -> (raw?.get(p.name) as? List<*>).orEmpty().filterIsInstance<String>() },
            walkMinutes = d.long("walkMinutes")?.toInt() ?: 150,
            workoutMinutes = d.long("workoutMinutes")?.toInt() ?: 90,
            studyMinutes = d.long("studyMinutes")?.toInt() ?: 0,
            homeMeals = d.long("homeMeals")?.toInt() ?: 7,
            familyMeals = d.long("familyMeals")?.toInt() ?: 5,
            macros = macros?.let { m ->
                MacroTargets((m["protein"] as? Number)?.toInt() ?: 50, (m["carbs"] as? Number)?.toInt() ?: 250, (m["fat"] as? Number)?.toInt() ?: 65, (m["fibre"] as? Number)?.toInt() ?: 30)
            } ?: MacroTargets(),
            custom = (data["custom"] as? List<*>).orEmpty().mapNotNull { raw ->
                (raw as? Map<*, *>)?.let { m ->
                    CustomTarget(
                        name = (m["name"] as? String) ?: return@mapNotNull null,
                        pillar = Pillar.entries.firstOrNull { it.name == m["pillar"] } ?: Pillar.OUTLOOK,
                        perWeek = (m["perWeek"] as? Number)?.toInt() ?: 3,
                    )
                }
            },
        )
    }

    /** Saves the plan; only the person can see it. */
    fun savePlan(householdId: String, uid: String, plan: WellPlan) {
        val doc = mapOf(
            "commitments" to plan.commitments.mapKeys { it.key.name },
            "walkMinutes" to plan.walkMinutes, "workoutMinutes" to plan.workoutMinutes, "studyMinutes" to plan.studyMinutes,
            "homeMeals" to plan.homeMeals, "familyMeals" to plan.familyMeals,
            "macros" to mapOf("protein" to plan.macros.protein, "carbs" to plan.macros.carbs, "fat" to plan.macros.fat, "fibre" to plan.macros.fibre),
            "custom" to plan.custom.map { mapOf("name" to it.name, "pillar" to it.pillar.name, "perWeek" to it.perWeek) },
            "createdBy" to uid, "visibleTo" to listOf(uid), "updatedAt" to FieldValue.serverTimestamp,
        )
        Writes.later { collection(householdId, "wellPlans").document(uid).set(doc) }
    }

    /** Counts one more (or one less) for a custom target this week; private to the person. [today] is any day of that week. */
    fun bumpTarget(householdId: String, uid: String, name: String, count: Int, today: LocalDate = systemToday()) {
        val doc = mapOf(
            "targets" to mapOf(name to count.coerceAtLeast(0)),
            "week" to isoWeek(today), "createdBy" to uid, "visibleTo" to listOf(uid), "createdAt" to FieldValue.serverTimestamp,
        )
        Writes.later { collection(householdId, "checkins").document("${uid}_${isoWeek(today)}").set(doc, merge = true) }
    }

    /** Saves this week's check-in; private to the person. Merged, so target ticks from earlier in the week stay. */
    suspend fun saveCheckIn(householdId: String, uid: String, check: CheckIn, today: LocalDate = systemToday()) {
        collection(householdId, "checkins").document("${uid}_${isoWeek(today)}").set(
            mapOf(
                "unwind" to check.unwind, "social" to check.social, "gratitude" to check.gratitude,
                "week" to isoWeek(today), "createdBy" to uid, "visibleTo" to listOf(uid), "createdAt" to FieldValue.serverTimestamp,
            ),
            merge = true,
        )
    }
}
