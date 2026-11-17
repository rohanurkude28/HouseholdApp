package com.houseaffairs.ios

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.EventItem
import com.houseaffairs.app.core.data.FinishedItem
import com.houseaffairs.app.core.data.Goal
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.data.WishItem
import com.houseaffairs.app.core.time.daysBetween
import com.houseaffairs.app.core.data.dayLabel
import com.houseaffairs.app.core.data.goalStep
import com.houseaffairs.app.core.data.isCurrent
import com.houseaffairs.app.core.data.thisWeeksGoals
import com.houseaffairs.app.core.data.wishLinks
import com.houseaffairs.app.feature.plans.AddRow
import com.houseaffairs.app.feature.plans.CardRow
import com.houseaffairs.app.feature.plans.EventRow
import com.houseaffairs.app.feature.plans.FinishedSection
import com.houseaffairs.app.feature.plans.GoalRow
import com.houseaffairs.app.feature.plans.Hint
import com.houseaffairs.app.feature.plans.ListHeader
import com.houseaffairs.app.feature.plans.PlansHeader
import com.houseaffairs.app.feature.plans.WishRow
import com.houseaffairs.app.feature.plans.dreamsByWhen
import com.houseaffairs.app.feature.quickadd.ListTemplate

private val things = ThingsRepository()

/** How many of each list show when every list is on screen at once (Android's LIST_PEEK). */
private const val LIST_PEEK = 4

// Plans → Dates, Goals and Lists on the iPhone, drawn by Android's rows (`:ui` PlansParts).

/** Android's Events: what's next at the top, the add box, then every date with its gift ideas. */
internal fun LazyListScope.datesOn(events: List<EventItem>, wishes: List<WishItem>, onAdd: () -> Unit, onEdit: (String, String) -> Unit, onGifts: () -> Unit) {
    val today = today()
    events.minByOrNull { it.next }?.let { next ->
        val days = daysBetween(today, next.next)
        item(key = "event-head") {
            PlansHeader(next.title, when (days) { 0L -> "Today"; 1L -> "Tomorrow"; else -> "In $days days · ${dayLabel(next.next, today)}" } + " · ${events.size} saved", emoji = if (next.kind.contains("Birthday", true)) "🎂" else "🎉")
        }
    }
    item { AddRow("Add a birthday or event", onAdd) }
    if (events.isEmpty()) item { Hint("Add birthdays, anniversaries and renewals. Say “Riya birthday 12 Dec”.") }
    items(events.size, key = { "ev" + events[it].id }) { i ->
        val e = events[i]
        CardRow(i == 0, i == events.size - 1) {
            EventRow(e, gifts = wishes.count { it.event.equals(e.title, ignoreCase = true) }, onClick = { onEdit("events", e.id) }, onGifts = onGifts)
        }
    }
}

/** Android's Goals: this week with a ring, the add box, sliders that save on release, earlier weeks folded. */
internal fun LazyListScope.goalsOn(
    s: SessionState.Ready, goals: List<Goal>, showEarlier: Boolean, onShowEarlier: () -> Unit,
    actions: Actions, onAdd: () -> Unit, onEdit: (String, String) -> Unit,
) {
    val week = thisWeeksGoals(goals, today())
    val earlier = goals - week.toSet()
    item(key = "goal-head") {
        PlansHeader(
            "This week",
            if (week.isEmpty()) "No goals yet this week" else "${week.count { it.done }} of ${week.size} reached",
            done = week.count { it.done }.takeIf { week.isNotEmpty() }, total = week.size.takeIf { week.isNotEmpty() }, emoji = "🎯",
        )
    }
    item { AddRow("Add a goal for this week", onAdd) }
    if (week.isEmpty()) item { Hint("Something to learn or get done this week: “finish a book”, “3 workouts”, “file taxes”.") }
    items(week.size, key = { "goal-" + week[it].id }) { i ->
        val g = week[i]
        CardRow(i == 0, i == week.size - 1) {
            GoalRow(g, onEdit = { onEdit("goals", g.id) }, onProgress = { p ->
                actions.run("${g.title} · ${goalStep(p)}") { val undo = things.setGoalProgress(s.household.id, g.id, p, g.progress); ({ undo() }) }
            })
        }
    }
    if (earlier.isNotEmpty()) {
        item(key = "goals-earlier") {
            TextButton(onClick = onShowEarlier, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(if (showEarlier) "Hide earlier weeks" else "Earlier weeks · ${earlier.size} · ${earlier.count { it.done }} reached")
            }
        }
        if (showEarlier) items(earlier.take(20), key = { "goal-old-" + it.id }) { g -> GoalRow(g, onEdit = { onEdit("goals", g.id) }, onProgress = null) }
    }
}

/**
 * Android's Lists: one chip per list, the add box, each list as a rounded card (planned first,
 * then the most ❤️), ❤️ and Idea → Planned on every row, and Finished with "Put back".
 * The watch list's covers and ratings are Android's for now.
 */
internal fun LazyListScope.listsOn(
    s: SessionState.Ready, wishes: List<WishItem>, watch: List<com.houseaffairs.app.core.data.WatchItem>, finished: List<FinishedItem>,
    filter: ListTemplate?, onFilter: (ListTemplate?) -> Unit, showDone: Boolean, onShowDone: () -> Unit,
    actions: Actions, onAdd: () -> Unit, onEdit: (String, String) -> Unit,
    dreams: List<com.houseaffairs.app.core.data.Dream> = emptyList(), money: com.houseaffairs.app.core.data.MoneyState? = null,
    onDream: (com.houseaffairs.app.core.data.Dream) -> Unit = { onEdit("dreams", it.id) },
) {
    val hid = s.household.id
    // Travel, buy and study dreams show here too: the same dream as on the vision board.
    val dreamsIn = com.houseaffairs.app.core.data.dreamsByList(dreams)
    val byList = wishes.filter { it.id !in actions.hidden }.groupBy { ListTemplate.of(it.template) }
    val media = watch.filter { it.id !in actions.hidden }.groupBy { it.template }
    val counts = ListTemplate.entries.associateWith { (byList[it]?.size ?: 0) + (media[it]?.size ?: 0) + (dreamsIn[it]?.size ?: 0) }
    item {
        LazyRow(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            items(ListTemplate.entries) { t ->
                FilterChip(
                    selected = filter == t,
                    onClick = { onFilter(if (filter == t) null else t) },
                    label = { Text("${t.emoji} ${t.title}" + (counts.getValue(t).takeIf { it > 0 }?.let { " · $it" } ?: "")) },
                    shape = CircleShape,
                )
            }
        }
    }
    val shown = ListTemplate.entries.filter { filter == null || filter == it }
    item { AddRow(filter?.let { "Add to ${it.title.lowercase()}" } ?: "Add to a list", onAdd) }
    if (shown.all { counts.getValue(it) == 0 }) item { Hint("Movies, books, gifts, things to buy… Say “add Dune 2 to watch list”.") }
    val costsHidden = s.uid in s.household.hideCostsFrom
    shown.forEach { template ->
        // Planned first, then the most ❤️'d; covers and ratings for movies, books and music.
        val allMedia = media[template].orEmpty().sortedWith(compareBy({ it.status != "PLANNED" }, { -it.votes.size }))
        val all = byList[template].orEmpty().sortedWith(compareBy({ it.status != "PLANNED" }, { -it.votes.size }))
        val allDreams = dreamsIn[template].orEmpty()
        val total = allMedia.size + all.size + allDreams.size
        if (total == 0) return@forEach
        val cap = if (filter == null) LIST_PEEK else Int.MAX_VALUE
        val mediaShown = allMedia.take(cap)
        val dreamShown = allDreams.take((cap - mediaShown.size).coerceAtLeast(0))
        val list = all.take((cap - mediaShown.size - dreamShown.size).coerceAtLeast(0))
        if (filter == null) item(key = "h-" + template.name) { ListHeader("${template.emoji} ${template.title}") }
        items(mediaShown.size, key = { "media-" + mediaShown[it].id }) { i ->
            val w = mediaShown[i]
            CardRow(i == 0, i == mediaShown.size + dreamShown.size + list.size - 1) {
                val images = com.houseaffairs.app.core.ui.LocalHouseImages.current
                WishRow(
                    title = w.title,
                    detail = listOfNotNull(
                        w.creator,
                        w.imdb?.takeIf { it.isNotEmpty() }?.let { "IMDb $it" } ?: w.rating?.let { "★ ${com.houseaffairs.app.core.util.oneDecimal(it)}" },
                        w.rottenTomatoes?.takeIf { it.isNotEmpty() }?.let { "🍅 $it" },
                        w.whereToWatch, w.year,
                    ).joinToString(" · ").ifEmpty { template.title },
                    image = w.poster?.takeIf { images.web }?.let { url -> { images.Web(url, w.title, Modifier.fillMaxSize()) } },
                    doneLabel = template.markLabel,
                    onClick = { onEdit("listItems", w.id) },
                    onDone = { actions.run("${w.title} · done") { val undo = things.markDone(hid, w.id, w.status); ({ undo() }) } },
                    votes = w.votes, myUid = s.uid, status = w.status,
                    onVote = { on -> things.setVote(hid, w.id, s.uid, on) },
                    onPlan = { things.setStatus(hid, w.id, if (w.status == "PLANNED") "IDEA" else "PLANNED") },
                    links = com.houseaffairs.app.core.data.mediaLinks(template, w.title, w.year, w.creator, w.imdbId, w.link),
                )
            }
        }
        items(dreamShown.size, key = { "dream-" + dreamShown[it].id }) { i ->
            val d = dreamShown[i]
            CardRow(i == 0 && mediaShown.isEmpty(), i == dreamShown.size - 1 && list.isEmpty()) {
                val cur = money?.budget?.currency ?: s.household.baseCurrency
                com.houseaffairs.app.feature.plans.DreamListRow(d, money?.dreams?.firstOrNull { it.dream.id == d.id }, { if (s.uid in s.household.hideCostsFrom) "•••" else formatMinor(it, cur) }) { onDream(d) }
            }
        }
        items(list.size, key = { "wish-" + list[it].id }) { i ->
            val w = list[i]
            CardRow(i == 0 && mediaShown.isEmpty() && dreamShown.isEmpty(), i == list.size - 1) {
                WishRow(
                    title = w.title,
                    detail = listOfNotNull(w.event?.let { "🎁 $it" }, w.place, w.budgetText.takeIf { !costsHidden }).joinToString(" · ").ifEmpty { template.title },
                    image = null,
                    doneLabel = template.markLabel,
                    onClick = { onEdit("listItems", w.id) },
                    onDone = { actions.run("${w.title} · done") { val undo = things.markDone(hid, w.id, w.status); ({ undo() }) } },
                    votes = w.votes, myUid = s.uid, status = w.status,
                    onVote = { on -> things.setVote(hid, w.id, s.uid, on) },
                    onPlan = { things.setStatus(hid, w.id, if (w.status == "PLANNED") "IDEA" else "PLANNED") },
                    links = wishLinks(w.link),
                )
            }
        }
        if (total > mediaShown.size + dreamShown.size + list.size) {
            item(key = "more-" + template.name) {
                TextButton(onClick = { onFilter(template) }, modifier = Modifier.padding(start = 8.dp)) { Text("See all $total in ${template.title.lowercase()} ›") }
            }
        }
    }
    val done = finished.filter { f -> f.collection == "listItems" && (filter == null || ListTemplate.of(f.template) == filter) }
    if (done.isNotEmpty()) item(key = "done-items") { FinishedSection("Finished", done, showDone, onShowDone, "Put back") { things.reopen(hid, it) } }
}

private val tripMoney = com.houseaffairs.app.core.data.MoneyRepository()

/**
 * Android's Trips & dreams: current trips with their spending, plan and buttons, past trips
 * folded, then the vision board (hold to drag, tap to flip). Place lookups and photos are
 * Android's for now; spend buttons and "New" open the + box.
 */
internal fun LazyListScope.tripsOn(
    s: SessionState.Ready, money: com.houseaffairs.app.core.data.MoneyState?, dreams: List<com.houseaffairs.app.core.data.Dream>,
    places: List<String>, showPast: Boolean, onShowPast: () -> Unit, boardView: Boolean, onBoardView: (Boolean) -> Unit,
    actions: Actions, onAdd: () -> Unit, onEdit: (String, String) -> Unit, onDream: (com.houseaffairs.app.core.data.Dream) -> Unit,
) {
    val hid = s.household.id
    val today = today()
    val cur = money?.budget?.currency ?: s.household.baseCurrency
    val hidden = s.uid in s.household.hideCostsFrom
    val full: (Long) -> String = { if (hidden) "•••" else formatMinor(it, cur) }
    val short: (Long) -> String = { if (hidden) "•••" else com.houseaffairs.app.core.util.compactMoneyText(it, cur) }
    if (money != null) {
        val current = money.trips.filter { it.trip.isCurrent(today) }
        val past = money.trips.filter { !it.trip.isCurrent(today) }
        item { com.houseaffairs.app.core.ui.SectionTitle("Trips & outings", action = "New", onAction = onAdd) }
        item {
            com.houseaffairs.app.feature.plans.TripsCard(
                current, full, onAdd,
                onDone = { t -> actions.run("${t.trip.title} · done") { val u = tripMoney.setTripDone(hid, t.trip); ({ u() }) } },
                onAddExpense = { _, _ -> onAdd() },
                onStops = { trip, stops -> tripMoney.setStops(hid, trip.id, stops) },
                places = places,
                lookUp = ::lookUpStop,
                onOpenExpense = { onEdit("transactions", it) },
            ) { onEdit("trips", it) }
        }
        if (past.isNotEmpty()) {
            item { TextButton(onClick = onShowPast, modifier = Modifier.padding(start = 8.dp)) { Text(if (showPast) "Hide past trips" else "Past trips · ${past.size}") } }
            if (showPast) item {
                com.houseaffairs.app.feature.plans.TripsCard(past, full, onAdd, onAddExpense = { _, _ -> onAdd() }, onReopen = { tripMoney.reopenTrip(hid, it.trip.id) }, onOpenExpense = { onEdit("transactions", it) }) { onEdit("trips", it) }
            }
        }
    }
    val active = dreams.filter { !it.achieved }
    val progressOf = { d: com.houseaffairs.app.core.data.Dream -> money?.dreams?.firstOrNull { it.dream.id == d.id } }
    item(key = "board-switch") {
        androidx.compose.foundation.layout.Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Vision board", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilterChip(selected = boardView, onClick = { onBoardView(true) }, label = { Text("Board") }, shape = CircleShape)
            FilterChip(selected = !boardView, onClick = { onBoardView(false) }, label = { Text("By when") }, shape = CircleShape)
        }
    }
    if (!boardView) {
        dreamsByWhen(active.groupBy { it.horizon(today) }, progressOf, short, { d -> money?.trips?.firstOrNull { it.trip.dream == d.title } }, onOpen = onDream, onAdd = { onAdd() })
        return
    }
    item(key = "board-hint") {
        Text("Tap a card to see how it's going · hold to move it", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
    }
    item(key = "board") {
        com.houseaffairs.app.feature.plans.VisionBoard(
            active, progressOf, short,
            onOpen = onDream, onAdd = onAdd,
            onReorder = { ids -> tripMoney.setBoardOrder(hid, com.houseaffairs.app.core.data.boardOrders(ids, dreams.associate { it.id to it.boardOrder })) },
        )
    }
}

/** Android's Places: a chip per kind, the add box, and each saved place with a Map button. "Near me" is Android's for now. */
internal fun LazyListScope.placesOn(places: List<com.houseaffairs.app.core.data.PlaceItem>, kind: String?, onKind: (String?) -> Unit, onAdd: () -> Unit, onEdit: (String, String) -> Unit) {
    item {
        LazyRow(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            items(com.houseaffairs.app.core.data.PLACE_KINDS) { k ->
                FilterChip(selected = kind == k, onClick = { onKind(if (kind == k) null else k) }, label = { Text(k) }, shape = CircleShape)
            }
        }
    }
    item { AddRow("Save a restaurant, store or doctor", onAdd) }
    val shown = places.filter { kind == null || it.kind == kind }
    if (places.isEmpty()) item { Hint("Keep your go-to places: restaurants to try, the shop with fresh paneer, the kids' doctor.") }
    items(shown.size, key = { "pl" + shown[it].id }) { i ->
        CardRow(i == 0, i == shown.size - 1) { com.houseaffairs.app.feature.plans.PlaceRow(shown[i]) { onEdit("places", shown[i].id) } }
    }
}
