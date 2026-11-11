package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.needsRefill
import com.houseaffairs.app.core.data.leftOn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.Area
import com.houseaffairs.app.core.data.Priority
import com.houseaffairs.app.core.data.Attention
import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.buildAttention
import com.houseaffairs.app.core.data.BudgetSummary
import com.houseaffairs.app.core.data.DishIdea
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.PlaceItem
import com.houseaffairs.app.core.data.Recipe
import com.houseaffairs.app.core.data.RightNow
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.data.WatchItem
import com.houseaffairs.app.core.data.WatchRepository
import com.houseaffairs.app.core.data.WishItem
import com.houseaffairs.app.core.data.rightNow
import com.houseaffairs.app.core.data.withHistory
import com.houseaffairs.app.core.data.isoWeek
import kotlinx.coroutines.flow.onEach
import java.time.LocalDateTime
import com.houseaffairs.app.core.data.cookIdeas
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.util.HIDDEN_AMOUNT
import com.houseaffairs.app.core.util.formatMinor
import kotlinx.coroutines.flow.flowOf
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.data.HouseholdRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import com.houseaffairs.app.feature.quickadd.Assistant
import com.houseaffairs.app.feature.quickadd.Pick
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import com.houseaffairs.app.core.data.Nudge
import com.houseaffairs.app.core.data.WeeklyRepository

enum class HomeSection(val label: String) {
    STATS("Stats at the top"),
    TODAY("Today: tasks and alerts"),
    FAMILY("What's new at home"),
    TIDY("Tidy up & add"),
    COMING_UP("Coming up"),
    RIGHT_NOW("For right now"),
    USE_IT_UP("What to cook"),
    WATCH("Movies, books & music"),
    SHOPPING("Shopping list"),
    GLANCE("At a glance"),
}

/** How many dishes Home's Cook tab shows. */
internal const val COOK_TAB_SIZE = 6

data class SectionPref(val section: HomeSection, val visible: Boolean)

enum class HomeFilter(val label: String, val area: Area?) {
    ALL("All", null),
    MONEY("Money", Area.MONEY),
    KITCHEN("Kitchen", Area.KITCHEN),
    HEALTH("Health", Area.HEALTH),
    PLANS("Plans", Area.PLANS),
}

/** Sections renamed when Home was simplified, so saved layouts keep their order and switches. */
private val RENAMED = mapOf("ATTENTION" to HomeSection.TODAY, "TIMELINE" to HomeSection.COMING_UP)

internal fun parseLayout(raw: String?): List<SectionPref> {
    val saved = raw.orEmpty().split(",").mapNotNull { part ->
        val (name, on) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        (HomeSection.entries.firstOrNull { it.name == name } ?: RENAMED[name])?.let { SectionPref(it, on == "1") }
    }.distinctBy { it.section }
    // Sections added in later versions slot in at their default position, switched on.
    val result = saved.toMutableList()
    HomeSection.entries.forEachIndexed { index, section ->
        // "At a glance" repeats the tabs below, so it starts switched off.
        if (result.none { it.section == section }) result.add(index.coerceAtMost(result.size), SectionPref(section, section != HomeSection.GLANCE))
    }
    return result
}

internal fun encodeLayout(layout: List<SectionPref>) = layout.joinToString(",") { "${it.section.name}:${if (it.visible) 1 else 0}" }

data class HomeUiState(
    val firstName: String = "",
    val homeName: String = "",
    val baseCurrency: String = "USD",
    val myUid: String = "",
    val members: List<Member> = emptyList(),
    val isOwner: Boolean = false,
    val canMoney: Boolean = false,
    val canStock: Boolean = false,
    val canPlans: Boolean = false,
    val canMedicine: Boolean = false,
    val quickAddTried: Boolean = false,
    val data: HomeData = HomeData(),
    val attention: List<Attention> = emptyList(),
    val filter: HomeFilter = HomeFilter.ALL,
    val layout: List<SectionPref> = parseLayout(null),
    val budget: BudgetSummary? = null,
    val hideAmounts: Boolean = false,
    /** Dishes that use up food about to expire. */
    val ideas: List<DishIdea> = emptyList(),
    val watch: List<WatchItem> = emptyList(),
    val rightNow: RightNow? = null,
    /** A new weekly review is waiting (lights up the story ring on the avatar). */
    val reviewFresh: Boolean = false,
    /** Gemini's reason for a dish or a show, by title, when smart suggestions are on. */
    val cookWhy: Map<String, String> = emptyMap(),
    val watchWhy: Map<String, String> = emptyMap(),
    /** Gift ideas per event, by lower-case event title. */
    val giftsFor: Map<String, Int> = emptyMap(),
    /** Saved places, for "you loved it last time" when a meal is coming up. */
    val places: List<PlaceItem> = emptyList(),
) {
    val showStats: Boolean get() = layout.firstOrNull { it.section == HomeSection.STATS }?.visible != false

    /** Filters this member can use: only areas their role can see. */
    val filters: List<HomeFilter>
        get() = HomeFilter.entries.filter {
            when (it) {
                HomeFilter.ALL -> true
                HomeFilter.MONEY -> canMoney
                HomeFilter.KITCHEN -> canStock
                HomeFilter.HEALTH -> canMedicine
                HomeFilter.PLANS -> canPlans
            }
        }

    fun shows(area: Area) = filter.area == null || filter.area == area

    fun nameOf(uid: String): String =
        if (uid == myUid) "You" else members.firstOrNull { it.uid == uid }?.displayName?.substringBefore(' ') ?: "Someone"
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val session: SessionManager,
    households: HouseholdRepository,
    private val home: HomeRepository,
    private val money: MoneyRepository,
    private val pantry: PantryRepository,
    private val watchRepo: WatchRepository,
    private val things: ThingsRepository,
    private val settings: SettingsRepository,
    private val weekly: WeeklyRepository,
    private val assistant: Assistant,
) : ViewModel() {
    private val ready = session.state.filterIsInstance<SessionState.Ready>()

    // One gentle live-well suggestion a day, from this week's wheel and the person's own plan.
    private val nudgeOfWeek = MutableStateFlow<Nudge?>(null)
    val nudge: StateFlow<Nudge?> = combine(nudgeOfWeek, settings.nudgeDismissedDay) { n, day -> n.takeIf { day != LocalDate.now().toString() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Gemini's wording of the nudge, kept for the same week's numbers so Home doesn't ask again.
    private var coached: Pair<String, String>? = null

    fun refreshNudge() {
        val r = session.state.value as? SessionState.Ready ?: return
        viewModelScope.launch {
            val review = runCatching { weekly.load(r.household.id, r.me) }.getOrNull()
            val nudge = review?.nudge
            nudgeOfWeek.value = nudge
            if (nudge == null || !settings.smartSuggest.first()) return@launch
            val about = coachSummary(review.wheel, review.plan)
            val tip = coached?.takeIf { it.first == about }?.second ?: assistant.coach(about)?.also { coached = about to it }
            if (tip != null && nudgeOfWeek.value == nudge) nudgeOfWeek.value = nudge.copy(suggestion = tip, byAi = true)
        }
    }

    fun dismissNudge() {
        viewModelScope.launch { settings.dismissNudge(LocalDate.now().toString()) }
    }

    /** True once "Not today" was tapped on the Tidy up card today. */
    /** Days in a row this person has opened the app; today counts as soon as Home shows. */
    val streak: StateFlow<Int> = settings.openedDays.map { com.houseaffairs.app.core.data.streakOf(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markOpened() {
        viewModelScope.launch { settings.markOpened(LocalDate.now()) }
    }

    /** Today rows dealt with or put off today, so the day's few things can run out. */
    val handled: StateFlow<Set<String>> = settings.handledOn(LocalDate.now().toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setHandled(id: String, handled: Boolean = true) {
        viewModelScope.launch { settings.setHandled(LocalDate.now().toString(), id, handled) }
    }

    val tidyHidden: StateFlow<Boolean> = settings.tidyDismissedDay.map { it == LocalDate.now().toString() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissTidy() {
        viewModelScope.launch { settings.dismissTidy(LocalDate.now().toString()) }
    }

    // Re-query only when something that changes the queries changes, not on every profile edit.
    private data class Scope(val householdId: String, val me: Member, val currency: String)

    private val data = ready
        .map { Scope(it.household.id, it.me.copy(joinedAt = null), it.household.baseCurrency) }
        .distinctUntilChanged()
        .flatMapLatest { home.observe(it.householdId, it.me, it.currency) }

    private val members = ready.map { it.household.id }.distinctUntilChanged().flatMapLatest { households.observeMembers(it) }

    private val filter = MutableStateFlow(HomeFilter.ALL)
    private val layout = settings.homeLayout.map(::parseLayout)
    private val budget = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.MONEY)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, canMoney) -> if (canMoney) money.observeMoney(householdId, uid) else flowOf(null) }

    private data class Kitchen(
        val pantry: List<PantryStock> = emptyList(),
        val recipes: List<Recipe> = emptyList(),
        val lastCooked: Map<String, LocalDate> = emptyMap(),
        val history: Pair<List<com.houseaffairs.app.core.data.Purchase>, List<com.houseaffairs.app.core.data.StockMemory>> = emptyList<com.houseaffairs.app.core.data.Purchase>() to emptyList(),
    )

    private val kitchen = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.STOCK)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, canStock) ->
            if (!canStock) {
                flowOf(Kitchen())
            } else {
                combine(
                    pantry.observePantry(householdId, uid),
                    pantry.observeRecipes(householdId, uid),
                    pantry.observeLastCooked(householdId, uid),
                    pantry.observeHistory(householdId, uid),
                    ::Kitchen,
                )
            }
        }

    private data class Plans(
        val watch: List<WatchItem> = emptyList(),
        val wishes: List<WishItem> = emptyList(),
        val places: List<PlaceItem> = emptyList(),
        val goals: List<com.houseaffairs.app.core.data.Goal> = emptyList(),
        /** Tasks ticked off, and when: the family's rhythm for chores nobody set to repeat. */
        val done: List<com.houseaffairs.app.core.data.Purchase> = emptyList(),
    )

    private val plans = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.PLANS)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, can) ->
            if (!can) {
                flowOf(Plans())
            } else {
                combine(
                    // Ratings, posters and where-to-watch are looked up once in the background.
                    watchRepo.observe(householdId, uid).onEach { list -> viewModelScope.launch { runCatching { watchRepo.enrich(householdId, list) } } },
                    things.observeWishes(householdId, uid),
                    things.observePlaces(householdId, uid),
                    things.observeGoals(householdId, uid),
                    things.observeFinished(householdId, uid).map { all ->
                        all.filter { it.collection == "tasks" }.mapNotNull { f ->
                            f.at?.let { com.houseaffairs.app.core.data.Purchase(f.title, it.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()) }
                        }
                    },
                    ::Plans,
                )
            }
        }

    // Smart suggestions: Gemini ranks the Cook and Lists tabs from what's really there.
    private data class Picks(val cook: List<Pick> = emptyList(), val watch: List<Pick> = emptyList())

    private val picks = MutableStateFlow(Picks())

    init {
        viewModelScope.launch {
            combine(settings.smartSuggest, kitchen, plans) { on, k, p ->
                if (!on) return@combine null
                val today = LocalDate.now()
                val dishes = withHistory(cookIdeas(k.pantry, k.recipes, today), k.lastCooked, today).take(10).map { it.recipe.title }
                pickInputs(LocalDateTime.now(), dishes, k.pantry, k.lastCooked, p.watch.take(15).map { it.title })
            }
                .distinctUntilChanged()
                // Let the lists settle after opening Home, so it's one request, not one per snapshot.
                .debounce(1_500)
                .collectLatest { inputs ->
                    picks.value = if (inputs == null) {
                        Picks()
                    } else {
                        Picks(
                            cook = assistant.pick("what to cook next", inputs.cookAbout, inputs.dishes, 3),
                            watch = assistant.pick("what to watch, read or listen to together", inputs.watchAbout, inputs.watch, 3),
                        )
                    }
                }
        }
    }

    private data class View(
        val filter: HomeFilter,
        val layout: List<SectionPref>,
        val money: MoneyState?,
        val hide: Boolean,
        val kitchen: Kitchen,
        val plans: Plans,
        val fresh: Boolean,
        val picks: Picks = Picks(),
    ) {
        val budget: BudgetSummary? get() = money?.summary
        /** What to cook: dishes that use up expiring food first, then ones the pantry mostly covers. */
        val ideas: List<DishIdea> get() = withHistory(cookIdeas(kitchen.pantry, kitchen.recipes, LocalDate.now()), kitchen.lastCooked, LocalDate.now())
    }

    private data class Base(val filter: HomeFilter, val layout: List<SectionPref>, val money: MoneyState?, val hide: Boolean)

    private val base = combine(filter, layout, budget, settings.hideAmounts, ::Base)

    private val view = combine(base, kitchen, plans, settings.reviewSeenWeek, picks) { b, k, p, seen, ai ->
        View(b.filter, b.layout, b.money, b.hide, k, p, fresh = seen != isoWeek(LocalDate.now()), picks = ai)
    }

    val state: StateFlow<HomeUiState> = combine(ready, members, data, settings.quickAddTried, view) { r, members, data, tried, v ->
        HomeUiState(
            firstName = r.me.displayName.substringBefore(' '),
            homeName = r.household.name,
            baseCurrency = r.household.baseCurrency,
            myUid = r.user.uid,
            members = members,
            isOwner = r.me.role == Role.OWNER,
            canMoney = r.me.canAccess(AppModule.MONEY),
            canStock = r.me.canAccess(AppModule.STOCK),
            canPlans = r.me.canAccess(AppModule.PLANS),
            canMedicine = r.me.canAccess(AppModule.MEDICINE),
            quickAddTried = tried,
            data = data,
            attention = buildAttention(data, LocalDate.now(), v.budget, v.money?.cardBills.orEmpty()) { minor ->
                if (v.hide) HIDDEN_AMOUNT else formatMinor(minor, v.budget?.currency ?: r.household.baseCurrency)
            }.map { item ->
                // An expiring item gets a dish suggestion right in the list.
                val dish = v.ideas.firstOrNull { idea -> item.area == Area.KITCHEN && idea.rescues.any { it.equals(item.title, ignoreCase = true) } }
                if (dish != null && item.detail.startsWith("Expires")) item.copy(detail = "${item.detail} · try ${dish.recipe.title}") else item
            } + restockAlerts(v.kitchen, data.shopping.map { it.name }) + refillAlerts(data.courses, data.shopping.map { it.name }) + goalsRecapAlert(v.plans.goals) + habitAlerts(v.plans.done, data.tasks.map { it.title }),
            filter = v.filter,
            layout = v.layout,
            budget = v.budget,
            hideAmounts = v.hide,
            // Gemini's picks first (when on), then the app's own order.
            ideas = v.ideas.sortedBy { idea -> v.picks.cook.indexOfFirst { it.title == idea.recipe.title }.let { if (it < 0) Int.MAX_VALUE else it } },
            watch = v.plans.watch.sortedBy { w -> v.picks.watch.indexOfFirst { it.title == w.title }.let { if (it < 0) Int.MAX_VALUE else it } },
            giftsFor = v.plans.wishes.mapNotNull { it.event?.lowercase() }.groupingBy { it }.eachCount(),
            places = v.plans.places,
            cookWhy = v.picks.cook.associate { it.title to it.why },
            watchWhy = v.picks.watch.associate { it.title to it.why },
            rightNow = rightNow(
                now = LocalDateTime.now(),
                pantry = v.kitchen.pantry,
                recipes = v.kitchen.recipes,
                wishes = v.plans.wishes,
                places = v.plans.places,
                // Only nudge shopping when the Shopping envelope actually has money.
                shoppingLeft = v.money?.envelopes?.envelopes?.firstOrNull { it.category == "Shopping" }?.availableMinor
                    ?.takeIf { it > 0 && !v.hide }?.let { formatMinor(it, v.money.budget.currency) },
                lastCooked = v.kitchen.lastCooked,
            )?.let { now ->
                // Dishes already on the Cook tab aren't suggested twice.
                val onCookTab = v.ideas.take(COOK_TAB_SIZE).map { it.recipe.title }.toSet()
                now.copy(items = now.items.filterNot { it.dish != null && it.title in onCookTab }).takeIf { it.items.isNotEmpty() }
            },
            reviewFresh = v.fresh,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setFilter(value: HomeFilter) {
        filter.value = value
    }

    fun toggleSection(section: HomeSection) = editLayout { list ->
        list.map { if (it.section == section) it.copy(visible = !it.visible) else it }
    }

    fun moveSection(section: HomeSection, up: Boolean) = editLayout { list ->
        val from = list.indexOfFirst { it.section == section }
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in list.indices) list else list.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun editLayout(block: (List<SectionPref>) -> List<SectionPref>) {
        viewModelScope.launch { settings.setHomeLayout(encodeLayout(block(state.value.layout))) }
    }

    /** Yes to a task given to me; the undo puts the question back. */
    fun acceptTask(task: com.houseaffairs.app.core.data.TaskItem): com.houseaffairs.app.feature.add.SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = home.answerTask(r.household.id, r.user.uid, task, accept = true)
        return com.houseaffairs.app.feature.add.SavedEntry("You'll do “${task.title}”") { undo() }
    }

    /** "Can't do it": back to whoever gave it, with the reason. */
    fun declineTask(task: com.houseaffairs.app.core.data.TaskItem, note: String?): com.houseaffairs.app.feature.add.SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = home.answerTask(r.household.id, r.user.uid, task, accept = false, note = note)
        return com.houseaffairs.app.feature.add.SavedEntry("Sent “${task.title}” back") { undo() }
    }

    /** "I'll do it" on a task that came back. */
    fun keepTask(task: com.houseaffairs.app.core.data.TaskItem) {
        val r = session.state.value as? SessionState.Ready ?: return
        home.keepTask(r.household.id, task)
    }

    fun completeTask(id: String, onDone: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        val task = state.value.data.tasks.firstOrNull { it.id == id } ?: return
        val undo = home.completeTask(r.household.id, r.user.uid, task)
        onDone(com.houseaffairs.app.feature.add.SavedEntry(doneMessage(task, state.value.members), celebrate = true) { undo() })
    }

    /** "Do laundry?": a chore that's due by the family's own rhythm, with "Add to my day". */
    private fun habitAlerts(done: List<com.houseaffairs.app.core.data.Purchase>, open: List<String>): List<Attention> {
        val today = LocalDate.now()
        return com.houseaffairs.app.core.data.usualTasks(done, open, today).map { u ->
            Attention("habit-${com.houseaffairs.app.core.data.choreKey(u.title)}", Area.PLANS, Priority.TODAY, "${u.title}?", u.why, today, open = "plans/TASKS", habit = u.title)
        }
    }

    /** "Add to my day" on a chore Home noticed: a task for today, mine, with Undo. */
    fun addHabitTask(title: String, onDone: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        val undo = home.addTask(r.household.id, r.user.uid, title)
        onDone(com.houseaffairs.app.feature.add.SavedEntry("$title · on today's list") { undo() })
    }

    /** "Time to buy rice": due from the family's buying rhythm, as Today items with "+ List". */
    private fun restockAlerts(k: Kitchen, onList: List<String>): List<Attention> {
        val today = LocalDate.now()
        return com.houseaffairs.app.core.data.restockDue(k.history.first, k.history.second, onList, today).map { r ->
            Attention(
                "restock-${r.title}", Area.KITCHEN, if (r.due.isAfter(today)) Priority.SOON else Priority.TODAY,
                "Restock ${r.title.lowercase()}", "${r.why} · due ${com.houseaffairs.app.feature.add.formatDate(r.due).lowercase()}",
                r.due, restock = r.title,
            )
        }
    }

    /** "Crocin runs out around Sat": a medicine taken on a schedule, before it's gone, with "+ List". */
    private fun refillAlerts(courses: List<com.houseaffairs.app.core.data.MedicineCourse>, onList: List<String>): List<Attention> {
        val today = LocalDate.now()
        return courses.filter { c -> onList.none { com.houseaffairs.app.core.data.sameIngredient(it, c.title) } }.mapNotNull { c ->
            val out = c.needsRefill(today) ?: return@mapNotNull null
            val left = c.leftOn(today)?.let { l -> if (l % 1.0 == 0.0) l.toLong().toString() else "%.1f".format(l) }
            Attention(
                "refill-${c.id}", Area.HEALTH, if (out.isAfter(today.plusDays(1))) Priority.SOON else Priority.TODAY,
                "Refill ${c.title}", listOfNotNull(left?.let { "$it ${c.unit} left" }, "runs out ${com.houseaffairs.app.feature.add.formatDate(out).lowercase()}").joinToString(" · "),
                out, collection = "medicines", restock = c.title,
            )
        }
    }

    /** Sundays: how the week's goals went, with a tap through to plan next week's. */
    private fun goalsRecapAlert(goals: List<com.houseaffairs.app.core.data.Goal>): List<Attention> {
        val today = LocalDate.now()
        if (today.dayOfWeek != java.time.DayOfWeek.SUNDAY) return emptyList()
        val recap = com.houseaffairs.app.core.data.goalsRecap(goals, today) ?: return emptyList()
        return listOf(Attention("goals-recap", Area.PLANS, Priority.TODAY, "Your week's goals", "$recap · plan next week?", today, open = "plans/GOALS"))
    }

    /** "+ List" on a Today item: onto the shopping list, with Undo. */
    fun addToShopping(title: String, onDone: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        val undo = pantry.addToShopping(r.household.id, r.user.uid, title)
        onDone(com.houseaffairs.app.feature.add.SavedEntry("$title · added to the shopping list") { undo() })
    }

    /** A renewal from Today logged as spent; Undo takes the expense back out. */
    fun logRenewal(id: String, onDone: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        val renewal = state.value.data.renewals.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            money.logRenewal(r.household.id, r.user.uid, id, renewal.next)?.let { undo ->
                onDone(com.houseaffairs.app.feature.add.SavedEntry("${renewal.title} · logged as spent") { undo() })
            }
        }
    }

    // Renewals set to "Log it automatically" are logged once their day comes. A transaction stops
    // two phones logging the same one; each (id, date) is only tried once per screen.
    private val autoLogged = mutableSetOf<Pair<String, LocalDate>>()

    init {
        viewModelScope.launch {
            data.collect { d ->
                val r = session.state.value as? SessionState.Ready ?: return@collect
                if (!r.me.canAccess(AppModule.MONEY)) return@collect
                val today = LocalDate.now()
                d.renewals.filter { it.auto && it.pending(today) && autoLogged.add(it.id to it.next) }.forEach { renewal ->
                    launch { money.logRenewal(r.household.id, r.user.uid, renewal.id, renewal.next, exclusive = true) }
                }
            }
        }
    }

    /** A card bill reminder in Today, marked paid; Undo brings the reminder back. */
    fun markBillPaid(key: String, onDone: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        money.setBillPaid(r.household.id, key, true)
        onDone(com.houseaffairs.app.feature.add.SavedEntry("${key.substringBefore('|')} card bill · paid", celebrate = true) { money.setBillPaid(r.household.id, key, false) })
    }

    /** Ticking an item off moves it into the pantry; Undo takes it back out. */
    fun markBought(id: String, onDone: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        val title = state.value.data.shopping.firstOrNull { it.id == id }?.name ?: "Item"
        viewModelScope.launch {
            runCatching { pantry.markBought(r.household.id, r.user.uid, id) }.getOrNull()?.let { bought ->
                onDone(com.houseaffairs.app.feature.add.SavedEntry("$title · bought, now in the pantry") { bought() })
                com.houseaffairs.app.core.ui.FollowUps.show(
                    com.houseaffairs.app.core.data.boughtFollowUp(title, bought.unit, LocalDate.now()) { a, u, e -> pantry.refineBought(r.household.id, bought.stockId, a, u, e) },
                )
            }
        }
    }

    fun markWatched(id: String, onDone: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        val item = state.value.watch.firstOrNull { it.id == id }
        val undo = watchRepo.markWatched(r.household.id, id)
        // "Dune 2 · marked watched", so it's clear what the ✓ did.
        val what = item?.template?.markLabel?.replaceFirstChar { it.lowercase() }?.replace("mark ", "marked ") ?: "done"
        onDone(com.houseaffairs.app.feature.add.SavedEntry("${item?.title ?: "Item"} · $what", celebrate = true) { undo() })
        com.houseaffairs.app.core.ui.FollowUps.show(com.houseaffairs.app.core.data.ratedFollowUp(item?.title ?: "it") { liked -> watchRepo.rate(r.household.id, id, liked) })
    }

    fun toggleHideAmounts() {
        viewModelScope.launch { settings.setHideAmounts(!state.value.hideAmounts) }
    }

    private fun withHousehold(block: (String) -> Unit) {
        (session.state.value as? SessionState.Ready)?.household?.id?.let(block)
    }
}
