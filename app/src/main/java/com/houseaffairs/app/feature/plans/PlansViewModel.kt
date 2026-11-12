package com.houseaffairs.app.feature.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.Dream
import com.houseaffairs.app.core.data.DreamProgress
import com.houseaffairs.app.core.data.EventItem
import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.PlaceItem
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.data.WatchItem
import com.houseaffairs.app.core.data.WatchRepository
import com.houseaffairs.app.core.data.WishItem
import com.houseaffairs.app.core.data.GeoRepository
import com.houseaffairs.app.core.data.LatLng
import com.houseaffairs.app.core.data.NearbyPlace
import com.houseaffairs.app.core.data.nearbyPlaces
import kotlinx.coroutines.launch
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.Pocket
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.TaskItem
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.util.formatMinor
import com.houseaffairs.app.feature.add.SavedEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.houseaffairs.app.feature.quickadd.ListTemplate
import kotlinx.coroutines.flow.onEach

enum class PlansTab(val label: String) { DREAMS("Vision board"), TASKS("Tasks"), GOALS("Goals"), EVENTS("Events"), LISTS("Lists"), PLACES("Places") }

data class PlansUiState(
    val tab: PlansTab = PlansTab.DREAMS,
    /** With Money access: progress, pot and envelopes. Without: just the dreams. */
    val money: MoneyState? = null,
    val dreams: List<Dream> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val events: List<EventItem> = emptyList(),
    val goals: List<com.houseaffairs.app.core.data.Goal> = emptyList(),
    val places: List<PlaceItem> = emptyList(),
    val placeKind: String? = null,
    /** The list shown in Lists; null shows every list. */
    val listFilter: ListTemplate? = null,
    val wishes: List<WishItem> = emptyList(),
    val watch: List<WatchItem> = emptyList(),
    val finished: List<com.houseaffairs.app.core.data.FinishedItem> = emptyList(),
    /** "Near me" mode: places within [radiusKm] of the phone, with distances. Null when off. */
    val nearby: List<NearbyPlace>? = null,
    val radiusKm: Double = 2.0,
    val locating: Boolean = false,
    val locationMissing: Boolean = false,
    val currency: String = "USD",
) {
    val progress: List<DreamProgress>? get() = money?.dreams
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlansViewModel @Inject constructor(
    private val session: SessionManager,
    private val moneyRepo: MoneyRepository,
    private val home: HomeRepository,
    private val things: ThingsRepository,
    private val geo: GeoRepository,
    private val watchRepo: WatchRepository,
    households: com.houseaffairs.app.core.data.HouseholdRepository,
) : ViewModel() {
    // Names for "Prachi's turn" when a chore rota moves on.
    private val members: StateFlow<List<com.houseaffairs.app.core.model.Member>> = session.state
        .filterIsInstance<SessionState.Ready>()
        .map { it.household.id }
        .distinctUntilChanged()
        .flatMapLatest { households.observeMembers(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private data class Near(val on: Boolean = false, val center: LatLng? = null, val radiusKm: Double = 2.0, val locating: Boolean = false, val missing: Boolean = false)

    private val near = MutableStateFlow(Near())
    private val tab = MutableStateFlow(PlansTab.DREAMS)
    private val placeKind = MutableStateFlow<String?>(null)
    private val listFilter = MutableStateFlow<ListTemplate?>(null)
    private val ready = session.state.filterIsInstance<SessionState.Ready>()
    private data class Scope(val householdId: String, val uid: String, val canMoney: Boolean)

    private val data = ready
        .map { Scope(it.household.id, it.user.uid, it.me.canAccess(AppModule.MONEY)) to it }
        .distinctUntilChanged { a, b -> a.first == b.first }
        .flatMapLatest { (s, r) ->
            combine(
                if (s.canMoney) moneyRepo.observeMoney(s.householdId, s.uid) else flowOf(null),
                moneyRepo.observeDreams(s.householdId, s.uid),
                home.observe(s.householdId, r.me, r.household.baseCurrency),
                things.observePlaces(s.householdId, s.uid),
                combine(
                    things.observeWishes(s.householdId, s.uid),
                    watchRepo.observe(s.householdId, s.uid).onEach { list -> viewModelScope.launch { runCatching { watchRepo.enrich(s.householdId, list) } } },
                ) { w, v -> w to v },
            ) { money, dreams, homeData, places, (wishes, watch) -> Data(money, dreams, homeData, places, r.household.baseCurrency, wishes, watch) }
                .combine(things.observeFinished(s.householdId, s.uid)) { d, f -> d.copy(finished = f) }
                .combine(things.observeGoals(s.householdId, s.uid)) { d, g -> d.copy(goals = g) }
        }

    private data class Data(
        val money: MoneyState?,
        val dreams: List<Dream>,
        val home: HomeData,
        val places: List<PlaceItem>,
        val currency: String,
        val wishes: List<WishItem>,
        val watch: List<WatchItem>,
        val finished: List<com.houseaffairs.app.core.data.FinishedItem> = emptyList(),
        val goals: List<com.houseaffairs.app.core.data.Goal> = emptyList(),
    )

    val state: StateFlow<PlansUiState> = combine(data, tab, combine(placeKind, listFilter) { k, l -> k to l }, near) { d, t, (k, l), n ->
        val places = d.places.filter { k == null || it.kind == k }
        PlansUiState(
            tab = t, money = d.money, dreams = d.dreams, tasks = d.home.tasks, events = d.home.events,
            places = places, placeKind = k, listFilter = l,
            wishes = d.wishes, watch = d.watch, finished = d.finished, goals = d.goals,
            nearby = if (n.on && n.center != null) nearbyPlaces(places, n.center, null, n.radiusKm) else null,
            radiusKm = n.radiusKm,
            locating = n.locating,
            locationMissing = n.missing,
            currency = d.money?.budget?.currency ?: d.currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    val hasLocationPermission get() = geo.hasLocationPermission

    /** Turns "Near me" on (finding the phone and giving saved places coordinates) or off. */
    fun toggleNearMe() {
        if (near.value.on) {
            near.value = Near(radiusKm = near.value.radiusKm)
            return
        }
        near.value = near.value.copy(on = true, locating = true, missing = false)
        viewModelScope.launch {
            val r = session.state.value as? SessionState.Ready
            if (r != null) geo.fillCoordinates(r.household.id, state.value.places)
            val here = geo.currentLocation()
            near.value = near.value.copy(center = here, locating = false, missing = here == null, on = here != null)
        }
    }

    fun setRadius(km: Double) {
        near.value = near.value.copy(radiusKm = km)
    }

    /** Watched, visited, bought: it leaves every list and suggestion. */
    fun markDone(id: String, title: String): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = watchRepo.markWatched(r.household.id, id)
        com.houseaffairs.app.core.ui.FollowUps.show(com.houseaffairs.app.core.data.ratedFollowUp(title) { liked -> watchRepo.rate(r.household.id, id, liked) })
        return SavedEntry("$title · done", celebrate = true) { undo() }
    }

    /** A goal moved to another step; Undo puts it back where it was. */
    fun setGoalProgress(goal: com.houseaffairs.app.core.data.Goal, progress: Int): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = things.setGoalProgress(r.household.id, goal.id, progress, before = goal.progress)
        val step = com.houseaffairs.app.core.data.goalStep(progress)
        return SavedEntry("${goal.title} · ${step.lowercase()}" + if (progress >= 100) " 🎉" else "", celebrate = progress >= 100) { undo() }
    }

    /** ❤️ or un-❤️ a list item. */
    fun vote(id: String, on: Boolean) {
        val r = session.state.value as? SessionState.Ready ?: return
        things.setVote(r.household.id, id, r.user.uid, on)
    }

    /** Idea ↔ Planned. */
    fun togglePlanned(id: String, status: String) {
        val r = session.state.value as? SessionState.Ready ?: return
        things.setStatus(r.household.id, id, if (status == "PLANNED") "IDEA" else "PLANNED")
    }

    val myUid: String get() = (session.state.value as? SessionState.Ready)?.user?.uid.orEmpty()

    /** A trip's new plan: its stops by day, in order. */
    fun setStops(trip: com.houseaffairs.app.core.data.Trip, stops: List<com.houseaffairs.app.core.data.Stop>) {
        val r = session.state.value as? SessionState.Ready ?: return
        moneyRepo.setStops(r.household.id, trip.id, stops)
    }

    fun nameOf(uid: String): String =
        if (uid == myUid) "You" else members.value.firstOrNull { it.uid == uid }?.displayName?.substringBefore(' ') ?: "Someone"

    fun selectPlaceKind(kind: String?) {
        placeKind.value = if (placeKind.value == kind) null else kind
    }

    /** Shows just one list (not a toggle), e.g. gift ideas from an event. */
    fun showList(template: ListTemplate) {
        listFilter.value = template
    }

    fun selectList(template: ListTemplate) {
        listFilter.value = if (listFilter.value == template) null else template
    }

    fun selectTab(value: PlansTab) {
        tab.value = value
    }

    fun fund(dream: Dream, from: Pocket, amountMinor: Long, fromLabel: String): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val ref = moneyRepo.transfer(r.household.id, r.user.uid, from, Pocket.Dream(dream.id), amountMinor)
        return SavedEntry("${formatMinor(amountMinor, state.value.currency)} moved from $fromLabel to ${dream.title}") { ref.delete() }
    }

    /** Trip over: it moves to past trips and stops collecting expenses. */
    fun markTripDone(trip: com.houseaffairs.app.core.data.Trip): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = moneyRepo.setTripDone(r.household.id, trip)
        return SavedEntry("${trip.title} · done", celebrate = true) { undo() }
    }

    fun setAchieved(dream: Dream, achieved: Boolean) {
        (session.state.value as? SessionState.Ready)?.let { moneyRepo.markDreamAchieved(it.household.id, dream.id, achieved) }
    }

    /** The vision board after a drag: [ids] in their new order. */
    fun reorderDreams(ids: List<String>) {
        val r = session.state.value as? SessionState.Ready ?: return
        val before = state.value.dreams.associate { it.id to it.boardOrder }
        moneyRepo.setBoardOrder(r.household.id, com.houseaffairs.app.core.data.boardOrders(ids, before))
    }

    fun reopen(item: com.houseaffairs.app.core.data.FinishedItem) {
        (session.state.value as? SessionState.Ready)?.let { things.reopen(it.household.id, item) }
    }

    /** A trip marked done by mistake goes back to current trips. */
    fun reopenTrip(trip: com.houseaffairs.app.core.data.Trip) {
        (session.state.value as? SessionState.Ready)?.let { moneyRepo.reopenTrip(it.household.id, trip.id) }
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

    private val parser = com.houseaffairs.app.feature.quickadd.QuickAddParser(nowMinute = { java.time.LocalTime.now().let { it.hour * 60 + it.minute } })

    /**
     * Typed straight into Tasks: "call mom at 5 tomorrow" keeps its date and time; without a
     * date it goes on [day] (the day picked in the week strip, else today).
     */
    fun quickAddTask(text: String, day: java.time.LocalDate?): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val line = text.trim().takeIf { it.isNotEmpty() } ?: return null
        val draft = parser.parse(line) as? com.houseaffairs.app.feature.quickadd.QuickAddDraft.Task
        val title = draft?.title?.takeIf { it.isNotBlank() } ?: line.replaceFirstChar { it.uppercase() }
        val due = draft?.due ?: day ?: java.time.LocalDate.now()
        val undo = home.addTask(r.household.id, r.user.uid, title, due = due, time = draft?.time, repeat = draft?.repeat, source = "tasks")
        return SavedEntry("$title · ${com.houseaffairs.app.feature.add.formatDate(due)}") { undo() }
    }

    /** A chore added to the family's list: due today, for anyone, repeating. */
    fun addChore(title: String, repeat: String): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = home.addTask(r.household.id, r.user.uid, title.trim(), repeat = repeat, source = "chore", assignee = null)
        return SavedEntry("${title.trim()} · a family chore, ${repeat.lowercase()}") { undo() }
    }

    fun removeChore(task: TaskItem): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = home.removeTask(r.household.id, task.id)
        return SavedEntry("${task.title} · off the chores list") { undo() }
    }

    fun setChoreRepeat(task: TaskItem, repeat: String): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = home.setRepeat(r.household.id, task.id, repeat, task.repeat)
        return SavedEntry("${task.title} · ${repeat.lowercase()}") { undo() }
    }

    /** A new day from the date chip ("Tomorrow", "This weekend"…), or no date, with Undo. */
    fun reschedule(task: TaskItem, to: java.time.LocalDate?, label: String): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = home.moveTask(r.household.id, task.id, to, task.dueDate)
        return SavedEntry("${task.title} · ${label.lowercase()}") { undo() }
    }

    fun completeTask(task: TaskItem): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val undo = home.completeTask(r.household.id, r.user.uid, task)
        return SavedEntry(com.houseaffairs.app.feature.home.doneMessage(task, members.value), celebrate = true) { undo() }
    }
}
