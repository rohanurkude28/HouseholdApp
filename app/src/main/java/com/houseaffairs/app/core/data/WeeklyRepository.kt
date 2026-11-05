package com.houseaffairs.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import javax.inject.Inject
import javax.inject.Singleton

fun mondayOf(date: LocalDate): LocalDate = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

enum class DiaryKind { ACTIVITY, MEAL, TASK, EVENT, TRIP }

private val ACTIVITY_EMOJI = mapOf(
    "Walk" to "🚶", "Run" to "🏃", "Cycling" to "🚴", "Yoga" to "🧘", "Gym" to "🏋️", "Sports" to "⚽",
    "Swim" to "🏊", "Dance" to "💃", "Gardening" to "🌱", "Housework" to "🧹", "Study" to "📖",
)

/** Something that happened that day, picked up from what was logged. */
data class DiaryEntry(val emoji: String, val text: String, val kind: DiaryKind, val id: String)

data class DiaryDay(val date: LocalDate, val note: String?, val mood: String?, val entries: List<DiaryEntry>)

/** "2026-W45": the check-in and the story ring are per ISO week. */
fun isoWeek(date: LocalDate): String = "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

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
    val weekStart: LocalDate = mondayOf(LocalDate.now()),
    /** Newest day first. */
    val diary: List<DiaryDay> = emptyList(),
    /** Whether the pantry is tracked for this person, so "nothing went to waste" means something. */
    val tracksPantry: Boolean = false,
    /** What today's meals added up to, against the plan's targets; null when nothing was logged. */
    val today: Macros? = null,
) {
    val isThisWeek: Boolean get() = weekStart == mondayOf(LocalDate.now())
    /** Only for the week you're in: there's nothing to nudge about last week. */
    val nudge: Nudge? get() = if (isThisWeek) nudgeFor(wheel, plan, LocalDate.now()) else null
}

/** Reads the last 7 days once, for the weekly review. Each part respects what the member may see. */
@Singleton
class WeeklyRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val pantry: PantryRepository,
) {
    private fun col(householdId: String, name: String) = db.collection("households").document(householdId).collection(name)

    private suspend fun visible(householdId: String, name: String, uid: String): List<DocumentSnapshot> =
        runCatching { col(householdId, name).whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid)).get().await().documents }
            .getOrDefault(emptyList())

    private fun DocumentSnapshot.created(): LocalDate? =
        getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

    /**
     * One person's week, Monday to Sunday (to today, for this week): their own walks, meals and
     * tasks, the family's outings and events they were part of, their diary, and the wheel.
     */
    suspend fun load(householdId: String, me: Member, weekStart: LocalDate = mondayOf(LocalDate.now()), today: LocalDate = LocalDate.now()): WeekReview {
        val from = mondayOf(weekStart)
        val to = minOf(from.plusDays(6), today)
        fun inWeek(d: LocalDate?) = d != null && !d.isBefore(from) && !d.isAfter(to)
        val uid = me.uid
        // Is this mine? Listed among the people, or (when nobody's listed) added by me.
        fun DocumentSnapshot.involves(field: String): Boolean {
            val people = (get(field) as? List<*>)?.filterIsInstance<String>() ?: getString(field)?.let { listOf(it) }
            return if (people.isNullOrEmpty()) getString("createdBy") == uid else uid in people
        }

        // Meals I ate: the cooking log, checked against recipes for plant-based.
        val recipes = (visible(householdId, "recipes", uid).map { d ->
            (d.getString("title") ?: "").lowercase() to (d.get("ingredients") as? List<*>).orEmpty().mapNotNull { raw ->
                (raw as? Map<*, *>)?.let { Ingredient((it["name"] as? String).orEmpty(), (it["amount"] as? Number)?.toDouble(), (it["unit"] as? String) ?: "g") }
            }
        }).toMap()
        val mealDocs = if (me.canAccess(AppModule.STOCK)) visible(householdId, "cookLog", uid).filter { inWeek(it.created()) && it.involves("eaters") } else emptyList()
        // Today's plate: one serving of each dish this person ate today.
        val savedRecipes = visible(householdId, "recipes", uid).map { d -> Recipe(d.id, d.getString("title").orEmpty(), emptyList(), 2, emptyList(), null, macros = d.getString("macros")) }
        val todayMacros = dayMacros(mealDocs.filter { it.created() == today }.map { it.getString("title").orEmpty() }, savedRecipes)
        val meals = mealDocs.map { d ->
            val title = d.getString("title").orEmpty()
            CookedMeal(title, (d.getLong("people") ?: 1).toInt().coerceAtLeast(1), recipes[title.lowercase()]?.let(::isPlantBased) ?: true)
        }

        val activityDocs = visible(householdId, "activities", uid).filter { inWeek(it.localDate("date") ?: it.created()) && it.involves("who") }
        val activities = activityDocs.map { d ->
            ActivityLog(
                id = d.id,
                date = d.localDate("date") ?: d.created(),
                kind = d.getString("kind") ?: "Walk",
                minutes = d.getString("minutes")?.toIntOrNull() ?: 0,
                people = (d.get("who") as? List<*>)?.size ?: 1,
            )
        }

        // Money stays household-wide: dining against a week's share of the Dining budget, total spend.
        var dining: Long? = null
        var allowance: Long? = null
        var spent: Long? = null
        var currency: String? = null
        var saved = 0L
        if (me.canAccess(AppModule.MONEY)) {
            val budget = runCatching { col(householdId, "budgets").document("main").get().await() }.getOrNull()
            currency = budget?.getString("currency")
            allowance = ((budget?.get("categories") as? Map<*, *>)?.get("Dining") as? Number)?.toLong()?.let { it * 7 / 30 }
            val week = visible(householdId, "transactions", uid).filter { it.getString("status") != "Planned" && inWeek(it.localDate("date")) }
            spent = week.filter { currency == null || it.getString("currency") == currency }.sumOf { it.getLong("amountMinor") ?: 0 }
            dining = week.filter { it.getString("category") == "Dining" }.sumOf { it.getLong("amountMinor") ?: 0 }
            saved = visible(householdId, "transfers", uid)
                .filter { (it.getString("to") ?: "").startsWith("dream:") && inWeek(it.localDate("date")) }
                .sumOf { it.getLong("amountMinor") ?: 0 }
        }

        // My tasks: assigned to me (or added by me with nobody assigned).
        val tasks = if (me.canAccess(AppModule.PLANS)) visible(householdId, "tasks", uid).filter { it.involves("assignee") } else emptyList()
        fun DocumentSnapshot.completed() = getTimestamp("completedAt")?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
        val doneTasks = tasks.filter { it.getBoolean("done") == true && inWeek(it.completed()) }
        val overdue = tasks.count { it.getBoolean("done") != true && it.localDate("due")?.isBefore(to) == true }
        // Study items I added and finished this week; marking done stamps updatedAt.
        val learned = if (me.canAccess(AppModule.PLANS)) {
            visible(householdId, "listItems", uid).count { d ->
                d.getString("template") == com.houseaffairs.app.feature.quickadd.ListTemplate.STUDY.label && d.getString("status") == "DONE" &&
                    d.getString("createdBy") == uid && inWeek(d.getTimestamp("updatedAt")?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate())
            }
        } else {
            0
        }

        val tripDocs = if (me.canAccess(AppModule.MONEY)) {
            visible(householdId, "trips", uid).filter { d ->
                val start = d.localDate("start") ?: return@filter false
                val end = d.localDate("end") ?: start
                !start.isAfter(to) && !end.isBefore(from) && d.involves("who")
            }
        } else {
            emptyList()
        }
        val eventDocs = if (me.canAccess(AppModule.PLANS)) {
            visible(householdId, "events", uid).mapNotNull { d ->
                val on = d.localDate("date")?.let { nextOccurrence(it, d.getString("repeat"), from) } ?: return@mapNotNull null
                if (inWeek(on) && d.involves("people")) d to on else null
            }
        } else {
            emptyList()
        }
        val wasted = if (me.canAccess(AppModule.STOCK)) visible(householdId, "stock", uid).count { d -> d.localDate("expiry")?.let { inWeek(it) && it.isBefore(today) } == true && d.getString("status") !in GONE_STATUSES } else 0

        val week = isoWeek(from)
        val checkDoc = runCatching { col(householdId, "checkins").document("${uid}_$week").get().await() }.getOrNull()?.takeIf { it.exists() }
        val counts = (checkDoc?.get("targets") as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> (k as? String)?.let { it to ((v as? Number)?.toInt() ?: 0) } }.toMap()
        val check = checkDoc
            ?.takeIf { it.contains("unwind") }
            ?.let { CheckIn((it.getLong("unwind") ?: 0).toInt(), (it.getLong("social") ?: 0).toInt(), (it.getLong("gratitude") ?: 0).toInt()) }

        // The diary: one line and a mood per day, private to me.
        val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val notes = dates.associateWith { day ->
            runCatching { col(householdId, "diary").document("${uid}_$day").get().await() }.getOrNull()?.takeIf { it.exists() }
        }
        val diary = dates.reversed().map { day ->
            val entries = buildList {
                activityDocs.filter { (it.localDate("date") ?: it.created()) == day }.forEach { d ->
                    val kind = d.getString("kind") ?: "Walk"
                    add(DiaryEntry(ACTIVITY_EMOJI[kind] ?: "🏅", "$kind · ${d.getString("minutes") ?: "?"} min", DiaryKind.ACTIVITY, d.id))
                }
                mealDocs.filter { it.created() == day }.forEach { d ->
                    val n = (d.getLong("people") ?: 1).toInt()
                    add(DiaryEntry("🍲", "Ate ${d.getString("title").orEmpty()}" + if (n >= 2) " together ($n)" else "", DiaryKind.MEAL, d.id))
                }
                doneTasks.filter { it.completed() == day }.forEach { d -> add(DiaryEntry("✅", d.getString("title").orEmpty(), DiaryKind.TASK, d.id)) }
                eventDocs.filter { it.second == day }.forEach { (d, _) -> add(DiaryEntry("🎉", d.getString("title").orEmpty(), DiaryKind.EVENT, d.id)) }
                tripDocs.filter { d -> val st = d.localDate("start")!!; !day.isBefore(st) && !day.isAfter(d.localDate("end") ?: st) }
                    .forEach { d -> add(DiaryEntry("🧳", d.getString("title").orEmpty(), DiaryKind.TRIP, d.id)) }
            }
            val note = notes[day]
            DiaryDay(day, note?.getString("note")?.takeIf { it.isNotBlank() }, note?.getString("mood"), entries)
        }

        val data = WeekData(
            meals, activities, dining, allowance, doneTasks.size, overdue,
            togetherEvents = tripDocs.size + eventDocs.size,
            savedToDreamsMinor = saved, wasted = wasted, checkIn = check,
            days = dates.size, diaryDays = diary.count { it.note != null }, learned = learned,
        )
        val plan = loadPlan(householdId, uid)
        return WeekReview(liveWell(data, plan), data, spent, currency, activities.sumOf { it.minutes }, plan, counts, from, diary, me.canAccess(AppModule.STOCK), todayMacros)
    }

    /**
     * One person's month or year ([from]..[to]) for the story: the home's spending and kitchen,
     * what was finished, and their own tasks, goals, walks, meals and diary.
     */
    suspend fun loadPeriod(householdId: String, me: Member, from: LocalDate, to: LocalDate, today: LocalDate = LocalDate.now()): PeriodReview {
        val uid = me.uid
        fun inside(d: LocalDate?) = d != null && !d.isBefore(from) && !d.isAfter(to)
        fun DocumentSnapshot.involves(field: String): Boolean {
            val people = (get(field) as? List<*>)?.filterIsInstance<String>() ?: getString(field)?.let { listOf(it) }
            return if (people.isNullOrEmpty()) getString("createdBy") == uid else uid in people
        }
        fun DocumentSnapshot.at(field: String) = getTimestamp(field)?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

        var spends: List<Txn>? = null
        var saved = emptyList<Pair<LocalDate, Long>>()
        var groceryBudget: Long? = null
        if (me.canAccess(AppModule.MONEY)) {
            val budget = runCatching { col(householdId, "budgets").document("main").get().await() }.getOrNull()
            val currency = budget?.getString("currency")
            groceryBudget = ((budget?.get("categories") as? Map<*, *>)?.get("Groceries") as? Number)?.toLong()
            spends = visible(householdId, "transactions", uid)
                .filter { inside(it.localDate("date")) && (currency == null || it.getString("currency") == currency) }
                .map { d -> Txn(d.id, d.getString("title").orEmpty(), d.getString("category") ?: "Other", d.getLong("amountMinor") ?: 0, d.localDate("date")!!, d.getString("status") == "Planned") }
            saved = visible(householdId, "transfers", uid)
                .filter { (it.getString("to") ?: "").startsWith("dream:") && inside(it.localDate("date")) }
                .map { it.localDate("date")!! to (it.getLong("amountMinor") ?: 0) }
        }
        val kitchen = if (me.canAccess(AppModule.STOCK)) {
            val h = pantry.observeHistory(householdId, uid, StockKind.FOOD).first()
            // Enough months back to cover the whole period.
            val months = ((today.year - from.year) * 12 + today.monthValue - from.monthValue + 1).coerceIn(1, 36)
            kitchenMonths(h.bought, h.lives, h.wasted, spends?.filter { it.category == "Groceries" }, today, groceryBudget, months)
        } else {
            emptyList()
        }
        val plans = me.canAccess(AppModule.PLANS)
        val finished = if (plans) {
            visible(householdId, "listItems", uid).filter { it.getString("status") == "DONE" }.mapNotNull { d ->
                (d.at("updatedAt") ?: d.created())?.takeIf(::inside)?.let { FinishedThing(d.getString("title").orEmpty(), d.getString("template"), it) }
            }
        } else {
            emptyList()
        }
        val tasks = if (plans) visible(householdId, "tasks", uid).filter { it.getBoolean("done") == true && it.involves("assignee") }.mapNotNull { it.at("completedAt") } else emptyList()
        val goals = if (plans) {
            visible(householdId, "weeklyGoals", uid).filter { (it.getLong("progress") ?: 0) >= 100 && it.involves("who") }
                .mapNotNull { d -> (d.at("doneAt") ?: d.created())?.let { d.getString("title").orEmpty() to it } }
        } else {
            emptyList()
        }
        val activities = visible(householdId, "activities", uid).filter { it.involves("who") }
            .mapNotNull { d -> (d.localDate("date") ?: d.created())?.let { it to (d.getString("minutes")?.toIntOrNull() ?: 0) } }
        val meals = if (me.canAccess(AppModule.STOCK)) visible(householdId, "cookLog", uid).filter { it.involves("eaters") }.mapNotNull { it.created() } else emptyList()
        // The diary is private: only my own lines.
        val diary = visible(householdId, "diary", uid).filter { it.getString("createdBy") == uid && !it.getString("note").isNullOrBlank() }
            .mapNotNull { d -> d.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it to d.getString("mood") } }
        return periodReview(from, to, today, PeriodInputs(spends, saved, kitchen, finished, tasks, goals, activities, meals, diary))
    }

    /** Writes (or clears) the day's diary line and mood; private to the person. */
    fun saveDiary(householdId: String, uid: String, day: LocalDate, note: String, mood: String?) {
        val ref = col(householdId, "diary").document("${uid}_$day")
        if (note.isBlank() && mood == null) {
            ref.delete()
            return
        }
        ref.set(
            mapOf(
                "note" to note.trim(), "mood" to mood, "date" to day.toString(),
                "createdBy" to uid, "visibleTo" to listOf(uid), "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    /** The person's own live-well plan, or the defaults. */
    suspend fun loadPlan(householdId: String, uid: String): WellPlan {
        val d = runCatching { col(householdId, "wellPlans").document(uid).get().await() }.getOrNull()?.takeIf { it.exists() } ?: return WellPlan()
        val raw = d.get("commitments") as? Map<*, *>
        return WellPlan(
            commitments = Pillar.entries.associateWith { p -> (raw?.get(p.name) as? List<*>).orEmpty().filterIsInstance<String>() },
            walkMinutes = d.getLong("walkMinutes")?.toInt() ?: 150,
            workoutMinutes = d.getLong("workoutMinutes")?.toInt() ?: 90,
            studyMinutes = d.getLong("studyMinutes")?.toInt() ?: 0,
            homeMeals = d.getLong("homeMeals")?.toInt() ?: 7,
            familyMeals = d.getLong("familyMeals")?.toInt() ?: 5,
            macros = (d.get("macros") as? Map<*, *>)?.let { m ->
                MacroTargets(
                    (m["protein"] as? Number)?.toInt() ?: 50, (m["carbs"] as? Number)?.toInt() ?: 250,
                    (m["fat"] as? Number)?.toInt() ?: 65, (m["fibre"] as? Number)?.toInt() ?: 30,
                )
            } ?: MacroTargets(),
            custom = (d.get("custom") as? List<*>).orEmpty().mapNotNull { raw ->
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
        col(householdId, "wellPlans").document(uid).set(
            mapOf(
                "commitments" to plan.commitments.mapKeys { it.key.name },
                "walkMinutes" to plan.walkMinutes, "workoutMinutes" to plan.workoutMinutes, "studyMinutes" to plan.studyMinutes,
                "homeMeals" to plan.homeMeals, "familyMeals" to plan.familyMeals,
                "macros" to mapOf("protein" to plan.macros.protein, "carbs" to plan.macros.carbs, "fat" to plan.macros.fat, "fibre" to plan.macros.fibre),
                "custom" to plan.custom.map { mapOf("name" to it.name, "pillar" to it.pillar.name, "perWeek" to it.perWeek) },
                "createdBy" to uid, "visibleTo" to listOf(uid), "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    /** Counts one more (or one less) for a custom target this week; private to the person. */
    fun bumpTarget(householdId: String, uid: String, name: String, count: Int, today: LocalDate = LocalDate.now()) {
        // [today] is any day of the week being counted.
        col(householdId, "checkins").document("${uid}_${isoWeek(today)}").set(
            mapOf(
                "targets" to mapOf(name to count.coerceAtLeast(0)),
                "week" to isoWeek(today), "createdBy" to uid, "visibleTo" to listOf(uid), "createdAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        )
    }

    /** Saves this week's check-in; private to the person. */
    suspend fun saveCheckIn(householdId: String, uid: String, check: CheckIn, today: LocalDate = LocalDate.now()) {
        // [today] is any day of the week the check-in is for.
        col(householdId, "checkins").document("${uid}_${isoWeek(today)}").set(
            mapOf(
                "unwind" to check.unwind, "social" to check.social, "gratitude" to check.gratitude,
                "week" to isoWeek(today), "createdBy" to uid, "visibleTo" to listOf(uid), "createdAt" to FieldValue.serverTimestamp(),
            ),
            // Merged, so target ticks from earlier in the week stay.
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }
}
