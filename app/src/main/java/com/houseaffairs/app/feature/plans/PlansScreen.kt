package com.houseaffairs.app.feature.plans

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import com.houseaffairs.app.core.data.TaskItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.houseaffairs.app.core.data.PlaceItem
import com.houseaffairs.app.feature.add.PLACE_KINDS
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.houseaffairs.app.core.data.Dream
import com.houseaffairs.app.core.data.DreamProgress
import com.houseaffairs.app.core.data.Pocket
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.SectionTitle
import com.houseaffairs.app.core.ui.TickCircle
import com.houseaffairs.app.core.ui.opensEdit
import com.houseaffairs.app.core.ui.shownCompact
import com.houseaffairs.app.core.ui.shownMinor
import com.houseaffairs.app.core.util.toMinorUnits
import com.houseaffairs.app.feature.add.SavedEntry
import com.houseaffairs.app.feature.add.EntryType
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.LaunchedEffect
import com.houseaffairs.app.feature.add.formatDate
import com.houseaffairs.app.core.data.Horizon
import com.houseaffairs.app.core.data.TripSummary
import com.houseaffairs.app.core.data.isCurrent
import java.time.LocalDate
import com.houseaffairs.app.feature.quickadd.ListTemplate


/** Plans: the family's vision board, plus tasks and upcoming events. */
@Composable
fun PlansScreen(
    onAddDream: (String?) -> Unit,
    onAddTrip: (String?) -> Unit,
    /** A trip expense, already filed under a kind of trip spending ("Stay", "Cafés") when one was tapped. */
    onAddTripExpense: (com.houseaffairs.app.core.data.Trip, com.houseaffairs.app.core.data.TripSpend?) -> Unit,
    onAddTask: () -> Unit,
    onAddEvent: () -> Unit,
    onAddGoal: () -> Unit,
    onAddPlace: () -> Unit,
    onAddWish: (ListTemplate?) -> Unit,
    onSaved: (SavedEntry) -> Unit,
    onEdit: (EntryType, String) -> Unit,
    requestedTab: String? = null,
    onTabShown: () -> Unit = {},
    viewModel: PlansViewModel = hiltViewModel(),
) {
    // For the family's own photos on list rows (files on this phone).
    val photoContext = LocalContext.current
    var pendingDream by remember { mutableStateOf<String?>(null) }
    // The vision board as a pinboard by default; "By when" groups it into rows.
    var boardView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var showGallery by remember { mutableStateOf(false) }
    var allTasks by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var openBuckets by remember { mutableStateOf(setOf<String>()) }
    var taskText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var showChores by remember { mutableStateOf(false) }
    if (showGallery) com.houseaffairs.app.core.photo.GallerySheet(onDismiss = { showGallery = false }, onOpenEntry = { type, id -> onEdit(EntryType.valueOf(type), id) })
    LaunchedEffect(requestedTab) {
        // "GIFTS" opens Lists filtered to gift ideas (from an event's "🎁 gift ideas").
        if (requestedTab == "GIFTS") {
            viewModel.selectTab(PlansTab.LISTS)
            viewModel.showList(ListTemplate.GIFTS)
            onTabShown()
        }
        PlansTab.entries.firstOrNull { it.name == requestedTab }?.let { viewModel.selectTab(it); onTabShown() }
        // "DREAM:<id>" (from Money's Coming up) opens that dream on the vision board.
        requestedTab?.takeIf { it.startsWith("DREAM:") }?.let { viewModel.selectTab(PlansTab.DREAMS); pendingDream = it.removePrefix("DREAM:"); onTabShown() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (showChores) {
        ChoresSheet(
            chores = state.tasks.filter { it.chore }.sortedBy { it.dueDate?.toEpochDay() ?: Long.MAX_VALUE },
            today = LocalDate.now(),
            onAdd = { title, every -> viewModel.addChore(title, every)?.let(onSaved) },
            onRemove = { viewModel.removeChore(it)?.let(onSaved) },
            onRepeat = { t, every -> viewModel.setChoreRepeat(t, every)?.let(onSaved) },
            onDismiss = { showChores = false },
        )
    }
    var open by remember { mutableStateOf<Dream?>(null) }
    // Dreams may still be loading when asked for, so it opens once it's there.
    LaunchedEffect(pendingDream, state.dreams) {
        state.dreams.firstOrNull { it.id == pendingDream }?.let { open = it; pendingDream = null }
    }
    var showPast by remember { mutableStateOf(false) }
    var showFinished by remember { mutableStateOf(false) }
    var showEarlierGoals by remember { mutableStateOf(false) }
    var showYear by remember { mutableStateOf(false) }
    if (showYear) PlansYearSheet(state.finished, state.goals, onDismiss = { showYear = false })
    val money = state.money

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), state = com.houseaffairs.app.core.ui.rememberTopListState(state.tab), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text("Plans", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (money != null) {
                        state.dreams.count { !it.achieved }.let { n -> "$n ${if (n == 1) "dream" else "dreams"}" } + if (com.houseaffairs.app.core.ui.CostPrivacy.hidden) "" else " · ${shownMinor(money.envelopes.potMinor, state.currency)} in the gullak"
                    } else {
                        "${state.dreams.count { !it.achieved }} dreams · ${state.tasks.size} tasks"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            com.houseaffairs.app.core.ui.SubTabs(PlansTab.entries, state.tab, { it.label }, viewModel::selectTab)
        }
        // This month's tasks, finished things and goals, where they're ticked off; tap for the year.
        // This month's tasks, finished things and goals, at the top where it's seen first; tap for the year.
        if (state.tab == PlansTab.TASKS || state.tab == PlansTab.GOALS || state.tab == PlansTab.LISTS) item(key = "plans-month") {
            PlansMonthCard(state.finished, state.goals, onOpen = { showYear = true }, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp))
        }
        when (state.tab) {
            PlansTab.DREAMS -> {
                // Trips and outings live here, next to the dream they make come true.
                val trips = money?.trips.orEmpty().filter { it.trip.isCurrent(LocalDate.now()) }
                val past = money?.trips.orEmpty().filter { !it.trip.isCurrent(LocalDate.now()) }
                if (money != null) {
                    item { SectionTitle("Trips & outings", action = "New", onAction = { onAddTrip(null) }) }
                    item {
                        TripsCard(
                            trips, { shownMinor(it, state.currency) }, { onAddTrip(null) },
                            onDone = { viewModel.markTripDone(it.trip)?.let(onSaved) },
                            onAddExpense = { t, kind -> onAddTripExpense(t.trip, kind) },
                            onStops = viewModel::setStops,
                            places = state.places.map { it.title },
                            lookUp = { name, near -> com.houseaffairs.app.core.data.lookUpPlace(name, near) },
                            onOpenExpense = { onEdit(EntryType.EXPENSE, it) },
                        ) { onEdit(EntryType.TRIP, it) }
                    }
                    if (past.isNotEmpty()) {
                        item {
                            TextButton(onClick = { showPast = !showPast }, modifier = Modifier.padding(start = 8.dp)) {
                                Text(if (showPast) "Hide past trips" else "Past trips · ${past.size}")
                            }
                        }
                        if (showPast) item { TripsCard(past, { shownMinor(it, state.currency) }, { onAddTrip(null) }, onAddExpense = { t, kind -> onAddTripExpense(t.trip, kind) }, onReopen = { viewModel.reopenTrip(it.trip) }, onOpenExpense = { onEdit(EntryType.EXPENSE, it) }) { onEdit(EntryType.TRIP, it) } }
                    }
                }
                // One row per horizon, same-size cards, each row ending in its own "add" tile.
                val today = LocalDate.now()
                val active = state.dreams.filter { !it.achieved }
                val byHorizon = active.groupBy { it.horizon(today) }
                // The board (drag to arrange, tap to flip) or rows by when.
                item(key = "board-switch") {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Vision board", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { showGallery = true }) { Text("📷") }
                        FilterChip(selected = boardView, onClick = { boardView = true }, label = { Text("Board") }, shape = CircleShape)
                        FilterChip(selected = !boardView, onClick = { boardView = false }, label = { Text("By when") }, shape = CircleShape)
                    }
                }
                if (boardView) {
                    item(key = "board-hint") {
                        Text("Tap a card to see how it's going · hold to move it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
                    }
                    item(key = "board") {
                        VisionBoard(
                            active, { d -> state.progress?.firstOrNull { it.dream.id == d.id } }, { shownCompact(it, state.currency) },
                            onOpen = { open = it }, onAdd = { onAddDream(null) }, onReorder = viewModel::reorderDreams,
                        )
                    }
                }
                if (!boardView) dreamsByWhen(byHorizon, { d -> state.progress?.firstOrNull { it.dream.id == d.id } }, { shownCompact(it, state.currency) }, { d -> trips.firstOrNull { it.trip.dream == d.title } }, onOpen = { open = it }, onAdd = onAddDream)
                val achieved = state.dreams.filter { it.achieved }
                if (achieved.isNotEmpty()) {
                    item { HorizonHeader("Achieved", "🎉", achieved.size) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(achieved, key = { it.id }) { dream ->
                                Box(Modifier.width(DREAM_TILE)) { DreamCard(dream, null, { shownCompact(it, state.currency) }, null) { open = dream } }
                            }
                        }
                    }
                }
                if (money != null && state.dreams.isNotEmpty()) {
                    item {
                        Text(
                            "Each dream saves up on its own. Say “save 5000 for Bali trip”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            // This week's goals with a progress slider; last weeks' stay below for the record.
            PlansTab.GOALS -> {
                val today = LocalDate.now()
                val week = com.houseaffairs.app.core.data.thisWeeksGoals(state.goals, today)
                val earlier = state.goals - week.toSet()
                // This week at a glance, the same way Tasks shows today.
                item(key = "goal-head") {
                    PlansHeader(
                        "This week",
                        if (week.isEmpty()) "No goals yet this week" else "${week.count { it.done }} of ${week.size} reached",
                        done = week.count { it.done }.takeIf { week.isNotEmpty() }, total = week.size.takeIf { week.isNotEmpty() }, emoji = "🎯",
                    )
                }
                item { AddRow("Add a goal for this week", onAddGoal) }
                if (week.isEmpty()) item { Hint("Something to learn or get done this week: “finish a book”, “3 workouts”, “file taxes”.") }
                items(week.size, key = { "goal-" + week[it].id }) { i ->
                    val goal = week[i]
                    CardRow(i == 0, i == week.size - 1) { GoalRow(goal, onEdit = { onEdit(EntryType.GOAL, goal.id) }, onProgress = { p -> viewModel.setGoalProgress(goal, p)?.let(onSaved) }) }
                }
                if (earlier.isNotEmpty()) {
                    // Folded by default: this week's goals are what you act on.
                    item(key = "goals-earlier") {
                        TextButton(onClick = { showEarlierGoals = !showEarlierGoals }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(if (showEarlierGoals) "Hide earlier weeks" else "Earlier weeks · ${earlier.size} · ${earlier.count { it.done }} reached")
                        }
                    }
                    if (showEarlierGoals) items(earlier.take(20), key = { "goal-old-" + it.id }) { goal -> GoalRow(goal, onEdit = { onEdit(EntryType.GOAL, goal.id) }, onProgress = null) }
                }
            }
            PlansTab.TASKS -> {
                val today = LocalDate.now()
                val doneToday = state.finished.count { f -> f.collection == "tasks" && f.at?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } == today }
                // The shared To do (in :ui, also the iPhone's); the call/message button is Android's.
                tasksPart(
                    tasks = state.tasks, finished = state.finished, doneToday = doneToday,
                    me = viewModel.myUid, nameOf = viewModel::nameOf, today = today,
                    view = TasksView(allTasks, { allTasks = it }, taskText, { taskText = it }, openBuckets, { openBuckets = it }, showFinished, { showFinished = it }),
                    actions = TaskActions(
                        complete = { viewModel.completeTask(it)?.let(onSaved) },
                        reschedule = { t, date, label -> viewModel.reschedule(t, date, label)?.let(onSaved) },
                        open = { onEdit(EntryType.TASK, it.id) },
                        quickAdd = { text -> viewModel.quickAddTask(text, null)?.let(onSaved) != null },
                        more = onAddTask,
                        openChores = { showChores = true },
                        reopen = viewModel::reopen,
                        answers = com.houseaffairs.app.core.ui.TaskAnswers(
                            accept = { viewModel.acceptTask(it)?.let(onSaved) },
                            decline = { t, note -> viewModel.declineTask(t, note)?.let(onSaved) },
                            keep = viewModel::keepTask,
                            reassign = { onEdit(EntryType.TASK, it.id) },
                        ),
                    ),
                    extra = { task -> task.reach?.let { com.houseaffairs.app.core.ui.ReachButton(it, task.phone, task.title) } },
                )
            }
            PlansTab.PLACES -> {
                item { NearMeRow(state, viewModel) }
                item {
                    LazyRow(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PLACE_KINDS) { kind ->
                            FilterChip(
                                selected = state.placeKind == kind,
                                onClick = { viewModel.selectPlaceKind(kind) },
                                label = { Text(kind) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
                item { AddRow("Save a restaurant, store or doctor", onAddPlace) }
                if (state.places.isEmpty()) item { Hint("Keep your go-to places: restaurants to try, the shop with fresh paneer, the kids' doctor.") }
                val near = state.nearby
                if (near != null) {
                    if (near.isEmpty()) item { Hint("Nothing saved within ${state.radiusKm.toInt()} km of you. Try a bigger radius.") }
                    items(near.size, key = { near[it].place.id }) { i -> CardRow(i == 0, i == near.size - 1) { PlaceRow(near[i].place, near[i].km) { onEdit(EntryType.PLACE, near[i].place.id) } } }
                } else {
                    items(state.places.size, key = { state.places[it].id }) { i -> val place = state.places[i]; CardRow(i == 0, i == state.places.size - 1) { PlaceRow(place) { onEdit(EntryType.PLACE, place.id) } } }
                }
            }
            PlansTab.LISTS -> {
                // One chip per list; media lists come with covers, ratings and where to find them.
                val media = state.watch.groupBy { it.template }
                val others = state.wishes.groupBy { ListTemplate.of(it.template) }
                // Travel, buy and study dreams show here too: the same dream as on the vision board.
                val dreamsIn = com.houseaffairs.app.core.data.dreamsByList(state.dreams)
                val counts = ListTemplate.entries.associateWith { (media[it]?.size ?: 0) + (others[it]?.size ?: 0) + (dreamsIn[it]?.size ?: 0) }
                item {
                    LazyRow(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ListTemplate.entries) { t ->
                            FilterChip(
                                selected = state.listFilter == t,
                                onClick = { viewModel.selectList(t) },
                                label = { Text("${t.emoji} ${t.title}" + (counts.getValue(t).takeIf { it > 0 }?.let { " · $it" } ?: "")) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
                val shown = ListTemplate.entries.filter { state.listFilter == null || state.listFilter == it }
                item { AddRow(state.listFilter?.let { "Add to ${it.title.lowercase()}" } ?: "Add to a list") { onAddWish(state.listFilter) } }
                if (shown.all { counts.getValue(it) == 0 }) {
                    item {
                        Hint(
                            when (state.listFilter) {
                                ListTemplate.BOOKS -> "Books you want to read. Say “add Atomic Habits to reading list”."
                                ListTemplate.MUSIC -> "Songs and albums to listen to. Say “add Kesariya to playlist”."
                                ListTemplate.WATCH -> "Movies and shows to watch together. Say “add Dune 2 to watch list”."
                                else -> "Movies, books, gifts, things to buy… Say “add Dune 2 to watch list”."
                            },
                        )
                    }
                }
                shown.forEach { template ->
                    // Planned first, then the most ❤️'d.
                    val allMedia = media[template].orEmpty().sortedWith(compareBy({ it.status != "PLANNED" }, { -it.votes.size }))
                    val allWishes = others[template].orEmpty().sortedWith(compareBy({ it.status != "PLANNED" }, { -it.votes.size }))
                    val allDreams = dreamsIn[template].orEmpty()
                    val total = allMedia.size + allWishes.size + allDreams.size
                    if (total == 0) return@forEach
                    // With every list showing, each keeps only its top few so the page stays short; its chip shows the rest.
                    val cap = if (state.listFilter == null) LIST_PEEK else Int.MAX_VALUE
                    val mediaItems = allMedia.take(cap)
                    val dreamItems = allDreams.take((cap - mediaItems.size).coerceAtLeast(0))
                    val wishItems = allWishes.take((cap - mediaItems.size - dreamItems.size).coerceAtLeast(0))
                    if (state.listFilter == null) item(key = "h-" + template.name) { ListHeader("${template.emoji} ${template.title}") }
                    // One rounded card per list, like Tasks: the first row rounds the top, the last the bottom.
                    val shownIds = mediaItems.map { it.id } + dreamItems.map { it.id } + wishItems.map { it.id }
                    fun first(id: String) = shownIds.firstOrNull() == id
                    fun last(id: String) = shownIds.lastOrNull() == id
                    items(mediaItems, key = { "media-" + it.id }) { w -> CardRow(first(w.id), last(w.id)) {
                        WishRow(
                            title = w.title,
                            detail = listOfNotNull(
                                w.creator,
                                w.imdb?.takeIf { it.isNotEmpty() }?.let { "IMDb $it" } ?: w.rating?.let { "★ %.1f".format(it) },
                                w.rottenTomatoes?.takeIf { it.isNotEmpty() }?.let { "🍅 $it" },
                                w.whereToWatch, w.year,
                            ).joinToString(" · ").ifEmpty { template.title },
                            image = cover(w.poster ?: w.photos.firstOrNull()?.let { com.houseaffairs.app.core.photo.PhotoStore.file(photoContext, it) }?.takeIf { it.exists() }),
                            doneLabel = template.markLabel,
                            onClick = { onEdit(EntryType.WISH, w.id) },
                            onDone = { viewModel.markDone(w.id, w.title)?.let(onSaved) },
                            votes = w.votes, myUid = viewModel.myUid, status = w.status,
                            onVote = { on -> viewModel.vote(w.id, on) }, onPlan = { viewModel.togglePlanned(w.id, w.status) },
                            links = com.houseaffairs.app.core.data.mediaLinks(template, w.title, w.year, w.creator, w.imdbId, w.link),
                        )
                    } }
                    items(dreamItems, key = { "dream-" + it.id }) { dream -> CardRow(first(dream.id), last(dream.id)) {
                        DreamListRow(dream, state.progress?.firstOrNull { it.dream.id == dream.id }, { shownMinor(it, state.currency) }) { open = dream }
                    } }
                    items(wishItems, key = { "wish-" + it.id }) { wish -> CardRow(first(wish.id), last(wish.id)) {
                        WishRow(
                            title = wish.title,
                            detail = listOfNotNull(wish.event?.let { "🎁 $it" }, wish.place, wish.budgetText).joinToString(" · ").ifEmpty { template.title },
                            image = cover(wish.photos.firstOrNull()?.let { com.houseaffairs.app.core.photo.PhotoStore.file(photoContext, it) }?.takeIf { it.exists() }),
                            doneLabel = template.markLabel,
                            onClick = { onEdit(EntryType.WISH, wish.id) },
                            onDone = { viewModel.markDone(wish.id, wish.title)?.let(onSaved) },
                            votes = wish.votes, myUid = viewModel.myUid, status = wish.status,
                            onVote = { on -> viewModel.vote(wish.id, on) }, onPlan = { viewModel.togglePlanned(wish.id, wish.status) },
                            links = com.houseaffairs.app.core.data.wishLinks(wish.link),
                        )
                    } }
                    if (total > mediaItems.size + dreamItems.size + wishItems.size) {
                        item(key = "more-" + template.name) {
                            TextButton(onClick = { viewModel.selectList(template) }, modifier = Modifier.padding(start = 8.dp)) {
                                Text("See all $total in ${template.title.lowercase()} ›")
                            }
                        }
                    }
                }
                val doneItems = state.finished.filter { f -> f.collection == "listItems" && (state.listFilter == null || ListTemplate.of(f.template) == state.listFilter) }
                if (doneItems.isNotEmpty()) {
                    item(key = "done-items") { FinishedSection("Finished", doneItems, showFinished, { showFinished = !showFinished }, "Put back", viewModel::reopen) }
                }
                // Lists saved before categories were tidied up ("Big goals"…) still show.
                state.wishes.filter { ListTemplate.of(it.template) == null }.takeIf { state.listFilter == null && it.isNotEmpty() }?.let { rest ->
                    item { ListHeader("Other") }
                    items(rest, key = { "other-" + it.id }) { wish ->
                        WishRow(wish.title, wish.template, null, "Mark done", onClick = { onEdit(EntryType.WISH, wish.id) }, onDone = { viewModel.markDone(wish.id, wish.title)?.let(onSaved) })
                    }
                }
            }
            PlansTab.EVENTS -> {
                // What's next, the same way Tasks shows today.
                state.events.minByOrNull { it.next.toEpochDay() }?.let { next ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), next.next)
                    item(key = "event-head") {
                        PlansHeader(next.title, when (days) { 0L -> "Today"; 1L -> "Tomorrow"; else -> "In $days days · ${formatDate(next.next)}" } + " · ${state.events.size} saved", emoji = if (next.kind.contains("Birthday", true)) "🎂" else "🎉")
                    }
                }
                item { AddRow("Add a birthday or event", onAddEvent) }
                if (state.events.isEmpty()) item { Hint("Add birthdays, anniversaries and renewals. Say “Riya birthday 12 Dec”.") }
                items(state.events.size, key = { state.events[it].id }) { i -> val event = state.events[i]; CardRow(i == 0, i == state.events.size - 1) {
                    EventRow(
                        event, gifts = state.wishes.count { it.event.equals(event.title, ignoreCase = true) },
                        onClick = { onEdit(EntryType.EVENT, event.id) },
                        onGifts = { viewModel.selectTab(PlansTab.LISTS); viewModel.showList(ListTemplate.GIFTS) },
                    )
                } }
            }
        }
    }

    open?.let { dream ->
        DreamSheet(
            dream = dream,
            progress = state.progress?.firstOrNull { it.dream.id == dream.id },
            money = state.money,
            currency = state.currency,
            full = { shownMinor(it, state.currency) },
            short = { shownCompact(it, state.currency) },
            onFund = { from, amount, label -> viewModel.fund(dream, from, amount, label)?.let(onSaved); open = null },
            onAchieved = { viewModel.setAchieved(dream, !dream.achieved); open = null },
            onEdit = { open = null; onEdit(EntryType.DREAM, dream.id) },
            onPlanTrip = { open = null; onAddTrip(dream.title) },
            onDismiss = { open = null },
        )
    }
}

@Composable
private fun NearMeRow(state: PlansUiState, viewModel: PlansViewModel) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.toggleNearMe()
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.nearby != null,
                onClick = {
                    if (viewModel.hasLocationPermission || state.nearby != null) {
                        viewModel.toggleNearMe()
                    } else {
                        permission.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                },
                label = { Text(if (state.locating) "Finding you…" else "📍 Near me") },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
            )
            if (state.nearby != null) {
                listOf(1.0, 2.0, 5.0).forEach { km ->
                    FilterChip(selected = state.radiusKm == km, onClick = { viewModel.setRadius(km) }, label = { Text("${km.toInt()} km") }, shape = CircleShape)
                }
            }
        }
        if (state.locationMissing) {
            Text("Couldn't get your location. Check that location is on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}


/** How many of each list show when every list is on screen at once. */
private const val LIST_PEEK = 4

/** A cover's URL or a family photo's file, loaded by Coil for the shared list row. */
private fun cover(model: Any?): (@Composable () -> Unit)? = model?.let { m ->
    {
        coil.compose.AsyncImage(
            model = m, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
