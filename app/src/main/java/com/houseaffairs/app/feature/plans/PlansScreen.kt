package com.houseaffairs.app.feature.plans

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
import com.houseaffairs.app.core.util.formatCompact
import com.houseaffairs.app.core.util.formatMinor
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

private val DREAM_GRADIENTS = mapOf(
    "Travel" to listOf(Color(0xFF36D1DC), Color(0xFF5B86E5)),
    "Car" to listOf(Color(0xFFFF8A65), Color(0xFFE0507D)),
    "Home" to listOf(Color(0xFF2BC9A0), Color(0xFF1A7FA0)),
    "Gadget" to listOf(Color(0xFF8E6BFF), Color(0xFF5B3FD9)),
    "Education" to listOf(Color(0xFFFFB547), Color(0xFFF0723C)),
    "Wedding" to listOf(Color(0xFFFF9AC2), Color(0xFFD9578A)),
    "Experience" to listOf(Color(0xFFB06CFF), Color(0xFF7446E8)),
    "Savings" to listOf(Color(0xFFFF9AC2), Color(0xFFB06CFF)),
    "Emergency" to listOf(Color(0xFF5AC8A8), Color(0xFF2E7D6B)),
    "Festival" to listOf(Color(0xFFFFC94D), Color(0xFFFF7A45)),
)
private fun gradientFor(kind: String) = DREAM_GRADIENTS[kind] ?: listOf(Color(0xFF6C7BFF), Color(0xFF5B3FD9))

/** Plans: the family's vision board, plus tasks and upcoming events. */
@Composable
fun PlansScreen(
    onAddDream: (String?) -> Unit,
    onAddTrip: (String?) -> Unit,
    onAddTripExpense: (com.houseaffairs.app.core.data.Trip) -> Unit,
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
    LaunchedEffect(requestedTab) {
        // "GIFTS" opens Lists filtered to gift ideas (from an event's "🎁 gift ideas").
        if (requestedTab == "GIFTS") {
            viewModel.selectTab(PlansTab.LISTS)
            viewModel.showList(ListTemplate.GIFTS)
            onTabShown()
        }
        PlansTab.entries.firstOrNull { it.name == requestedTab }?.let { viewModel.selectTab(it); onTabShown() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Dream?>(null) }
    var showPast by remember { mutableStateOf(false) }
    var showFinished by remember { mutableStateOf(false) }
    val money = state.money

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text("Plans", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (money != null) {
                        "${state.dreams.count { !it.achieved }} dreams · ${formatMinor(money.envelopes.potMinor, state.currency)} in the gullak"
                    } else {
                        "${state.dreams.count { !it.achieved }} dreams · ${state.tasks.size} tasks"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PlansTab.entries) { tab ->
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(tab.label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
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
                            trips, { formatMinor(it, state.currency) }, { onAddTrip(null) },
                            onDone = { viewModel.markTripDone(it.trip)?.let(onSaved) },
                            onAddExpense = { onAddTripExpense(it.trip) },
                        ) { onEdit(EntryType.TRIP, it) }
                    }
                    if (past.isNotEmpty()) {
                        item {
                            TextButton(onClick = { showPast = !showPast }, modifier = Modifier.padding(start = 8.dp)) {
                                Text(if (showPast) "Hide past trips" else "Past trips · ${past.size}")
                            }
                        }
                        if (showPast) item { TripsCard(past, { formatMinor(it, state.currency) }, { onAddTrip(null) }, onAddExpense = { onAddTripExpense(it.trip) }, onReopen = { viewModel.reopenTrip(it.trip) }) { onEdit(EntryType.TRIP, it) } }
                    }
                }
                // One row per horizon, same-size cards, each row ending in its own "add" tile.
                val today = LocalDate.now()
                val active = state.dreams.filter { !it.achieved }
                val byHorizon = active.groupBy { it.horizon(today) }
                Horizon.entries.forEach { horizon ->
                    val list = byHorizon[horizon].orEmpty()
                    // Someday only shows when something's in it; the three time frames always do.
                    if (horizon == Horizon.SOMEDAY && list.isEmpty()) return@forEach
                    item(key = "hz-" + horizon.name) {
                        HorizonHeader(horizon.label, horizon.detail, list.size)
                    }
                    item(key = "row-" + horizon.name) {
                        // Nothing here yet: a slim add row instead of an empty tile.
                        if (list.isEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(MaterialTheme.shapes.large)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                                    .clickable { onAddDream(horizon.label) }.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Add a ${horizon.label.lowercase()} dream", style = MaterialTheme.typography.titleSmall)
                            }
                            return@item
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(list, key = { it.id }) { dream ->
                                Box(Modifier.width(DREAM_TILE)) {
                                    DreamCard(dream, state.progress?.firstOrNull { it.dream.id == dream.id }, state.currency, trips.firstOrNull { it.trip.dream == dream.title }) { open = dream }
                                }
                            }
                            item {
                                Box(Modifier.width(DREAM_TILE)) {
                                    NewDreamTile("Add another") { onAddDream(horizon.takeIf { it != Horizon.SOMEDAY }?.label) }
                                }
                            }
                        }
                    }
                }
                val achieved = state.dreams.filter { it.achieved }
                if (achieved.isNotEmpty()) {
                    item { HorizonHeader("Achieved", "🎉", achieved.size) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(achieved, key = { it.id }) { dream ->
                                Box(Modifier.width(DREAM_TILE)) { DreamCard(dream, null, state.currency, null) { open = dream } }
                            }
                        }
                    }
                }
                if (money != null && state.dreams.isNotEmpty()) {
                    item {
                        Text(
                            "Every dream is its own gullak. Unspent budget drops into the main gullak each month; move it to a dream from its card, or say “save 5000 for Bali trip”.",
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
                item { AddRow("Add a goal for this week", onAddGoal) }
                com.houseaffairs.app.core.data.goalsRecap(state.goals, today)?.let { recap -> item { Hint(recap) } }
                if (week.isEmpty()) item { Hint("Something to learn or get done this week: “finish a book”, “3 workouts”, “file taxes”.") }
                items(week, key = { "goal-" + it.id }) { goal -> GoalRow(goal, onEdit = { onEdit(EntryType.GOAL, goal.id) }, onProgress = { viewModel.setGoalProgress(goal.id, it) }) }
                if (earlier.isNotEmpty()) {
                    item { ListHeader("Earlier weeks") }
                    items(earlier.take(20), key = { "goal-old-" + it.id }) { goal -> GoalRow(goal, onEdit = { onEdit(EntryType.GOAL, goal.id) }, onProgress = null) }
                }
            }
            PlansTab.TASKS -> {
                item { AddRow("Add a task", onAddTask) }
                if (state.tasks.isEmpty()) item { Hint("Nothing to do. Say “pay rent tomorrow”.") }
                val doneTasks = state.finished.filter { it.collection == "tasks" }
                if (doneTasks.isNotEmpty()) {
                    item(key = "done-tasks") { FinishedSection("Done", doneTasks, showFinished, { showFinished = !showFinished }, "Not done", viewModel::reopen) }
                }
                items(state.tasks, key = { it.id }) { task ->
                    val plans = LocalHouseColors.current.plans
                    // Circle = done (with Undo); the rest of the row, tapped or held, = edit.
                    Row(Modifier.fillMaxWidth().opensEdit { onEdit(EntryType.TASK, task.id) }.padding(start = 10.dp, end = 20.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        TickCircle(plans.color, onTick = { viewModel.completeTask(task)?.let(onSaved) }, contentDescription = "Done: ${task.title}")
                        Spacer(Modifier.width(4.dp))
                        Text(task.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        PriorityTag(task.priority)
                        task.reach?.let { com.houseaffairs.app.core.ui.ReachButton(it, task.phone, task.title) }
                        task.dueDate?.let { Text(formatDate(it), style = MaterialTheme.typography.labelLarge, color = plans.color) }
                    }
                }
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
                    items(near, key = { it.place.id }) { PlaceRow(it.place, it.km) { onEdit(EntryType.PLACE, it.place.id) } }
                } else {
                    items(state.places, key = { it.id }) { place -> PlaceRow(place) { onEdit(EntryType.PLACE, place.id) } }
                }
            }
            PlansTab.LISTS -> {
                // One chip per list; media lists come with covers, ratings and where to find them.
                val media = state.watch.groupBy { it.template }
                val others = state.wishes.groupBy { ListTemplate.of(it.template) }
                val counts = ListTemplate.entries.associateWith { (media[it]?.size ?: 0) + (others[it]?.size ?: 0) }
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
                                else -> "Movies, books, music, travel ideas, places to eat, things to buy and gifts. Say “add Dune 2 to watch list”."
                            },
                        )
                    }
                }
                shown.forEach { template ->
                    val mediaItems = media[template].orEmpty()
                    val wishItems = others[template].orEmpty()
                    if (mediaItems.isEmpty() && wishItems.isEmpty()) return@forEach
                    if (state.listFilter == null) item(key = "h-" + template.name) { ListHeader("${template.emoji} ${template.title}") }
                    // Planned first, then the most ❤️'d.
                    items(mediaItems.sortedWith(compareBy({ it.status != "PLANNED" }, { -it.votes.size })), key = { "media-" + it.id }) { w ->
                        WishRow(
                            title = w.title,
                            detail = listOfNotNull(
                                w.creator,
                                w.imdb?.takeIf { it.isNotEmpty() }?.let { "IMDb $it" } ?: w.rating?.let { "★ %.1f".format(it) },
                                w.rottenTomatoes?.takeIf { it.isNotEmpty() }?.let { "🍅 $it" },
                                w.whereToWatch, w.year,
                            ).joinToString(" · ").ifEmpty { template.title },
                            image = w.poster,
                            doneLabel = template.markLabel,
                            onClick = { onEdit(EntryType.WISH, w.id) },
                            onDone = { viewModel.markDone(w.id, w.title)?.let(onSaved) },
                            votes = w.votes, myUid = viewModel.myUid, status = w.status,
                            onVote = { on -> viewModel.vote(w.id, on) }, onPlan = { viewModel.togglePlanned(w.id, w.status) },
                            listenUrl = w.youtubeUrl,
                        )
                    }
                    items(wishItems.sortedWith(compareBy({ it.status != "PLANNED" }, { -it.votes.size })), key = { "wish-" + it.id }) { wish ->
                        WishRow(
                            title = wish.title,
                            detail = listOfNotNull(wish.event?.let { "🎁 $it" }, wish.place, wish.budgetText).joinToString(" · ").ifEmpty { template.title },
                            image = null,
                            doneLabel = template.markLabel,
                            onClick = { onEdit(EntryType.WISH, wish.id) },
                            onDone = { viewModel.markDone(wish.id, wish.title)?.let(onSaved) },
                            votes = wish.votes, myUid = viewModel.myUid, status = wish.status,
                            onVote = { on -> viewModel.vote(wish.id, on) }, onPlan = { viewModel.togglePlanned(wish.id, wish.status) },
                        )
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
                item { AddRow("Add a birthday or event", onAddEvent) }
                if (state.events.isEmpty()) item { Hint("Add birthdays, anniversaries and renewals. Say “Riya birthday 12 Dec”.") }
                items(state.events, key = { it.id }) { event ->
                    Row(Modifier.fillMaxWidth().opensEdit { onEdit(EntryType.EVENT, event.id) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBlob(Icons.Outlined.Cake, LocalHouseColors.current.plans, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleSmall)
                            val gifts = state.wishes.count { it.event.equals(event.title, ignoreCase = true) }
                            Text(event.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (gifts > 0) {
                                Text(
                                    "🎁 $gifts gift idea${if (gifts > 1) "s" else ""} ›",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LocalHouseColors.current.plans.color,
                                    modifier = Modifier.clip(CircleShape).clickable { viewModel.selectTab(PlansTab.LISTS); viewModel.showList(ListTemplate.GIFTS) }.padding(vertical = 2.dp),
                                )
                            }
                        }
                        Text(formatDate(event.next), style = MaterialTheme.typography.labelLarge, color = LocalHouseColors.current.plans.color)
                    }
                }
            }
        }
    }

    open?.let { dream ->
        DreamSheet(
            dream = dream,
            progress = state.progress?.firstOrNull { it.dream.id == dream.id },
            state = state,
            onFund = { from, amount, label -> viewModel.fund(dream, from, amount, label)?.let(onSaved); open = null },
            onAchieved = { viewModel.setAchieved(dream, !dream.achieved); open = null },
            onEdit = { open = null; onEdit(EntryType.DREAM, dream.id) },
            onPlanTrip = { open = null; onAddTrip(dream.title) },
            onDismiss = { open = null },
        )
    }
}

/** "Done · 4": tap to see finished items, each with a button that puts it back. */
@Composable
private fun FinishedSection(
    title: String,
    items: List<com.houseaffairs.app.core.data.FinishedItem>,
    open: Boolean,
    onToggle: () -> Unit,
    undoLabel: String,
    onReopen: (com.houseaffairs.app.core.data.FinishedItem) -> Unit,
) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = onToggle) { Text(if (open) "Hide ${title.lowercase()}" else "$title · ${items.size}") }
        if (open) {
            items.take(20).forEach { f ->
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (ListTemplate.of(f.template)?.emoji?.plus(" ") ?: "✓ ") + f.title,
                        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { onReopen(f) }) { Text(undoLabel) }
                }
            }
        }
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    val accent = LocalHouseColors.current.plans
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clip(MaterialTheme.shapes.large)
            .background(accent.container).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = accent.color)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = accent.color)
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

@Composable
private fun ListHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun WishRow(
    title: String,
    detail: String,
    image: String?,
    doneLabel: String,
    onClick: () -> Unit,
    onDone: () -> Unit,
    votes: List<String> = emptyList(),
    myUid: String = "",
    status: String = "IDEA",
    onVote: ((Boolean) -> Unit)? = null,
    onPlan: (() -> Unit)? = null,
    /** Music: opens YouTube Music on this song or album. */
    listenUrl: String? = null,
) {
    Row(Modifier.fillMaxWidth().opensEdit(onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (image != null) {
            coil.compose.AsyncImage(
                model = image, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(MaterialTheme.shapes.small),
            )
        } else {
            IconBlob(Icons.Outlined.Star, LocalHouseColors.current.vault, size = 40.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // ❤️ from each member, and Idea → Planned; ticking it off is the circle on the right.
            if (onVote != null && onPlan != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    val mine = myUid in votes
                    Text(
                        (if (mine) "❤️" else "🤍") + if (votes.isNotEmpty()) " ${votes.size}" else "",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(CircleShape).clickable(onClickLabel = if (mine) "Remove your heart" else "Heart it") { onVote(!mine) }.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    listenUrl?.let { url ->
                        val uri = androidx.compose.ui.platform.LocalUriHandler.current
                        Text(
                            "▶ YouTube",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clip(CircleShape).clickable(onClickLabel = "Play on YouTube") { runCatching { uri.openUri(url) } }.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    val planned = status == "PLANNED"
                    Text(
                        if (planned) "📅 Planned" else "💡 Idea",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (planned) LocalHouseColors.current.plans.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (planned) LocalHouseColors.current.plans.container else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClickLabel = if (planned) "Back to idea" else "Mark planned", onClick = onPlan)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
        // Same as everywhere: the circle ticks it off; the row (tap or hold) opens it.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TickCircle(LocalHouseColors.current.vault.color, onTick = onDone, size = 24.dp, contentDescription = "$doneLabel: $title")
            Text(doneLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceItem, km: Double? = null, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    val plans = LocalHouseColors.current.plans
    Row(Modifier.fillMaxWidth().opensEdit(onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        IconBlob(placeIcon(place.kind), plans, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(place.title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(km?.let { when {
                    it < 0.05 -> "right next door"
                    it < 1 -> "${(it * 1000).toInt() / 50 * 50} m"
                    else -> "%.1f km".format(it)
                } }, place.kind, place.area, place.rating).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            place.goodFor?.takeIf { it.isNotBlank() }?.let { Text("Try: $it", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        TextButton(onClick = {
            // Their saved Maps link, or a search for the name and area.
            val uri = place.mapLink?.let(Uri::parse) ?: Uri.parse("geo:0,0?q=" + Uri.encode(listOfNotNull(place.title, place.area).joinToString(" ")))
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }) { Text("Map") }
    }
}

private fun placeIcon(kind: String) = when (kind) {
    "Restaurant", "Café" -> Icons.Outlined.Restaurant
    "Grocery", "Market" -> Icons.Outlined.Storefront
    "Pharmacy", "Doctor", "Hospital" -> Icons.Outlined.LocalHospital
    else -> Icons.Outlined.Place
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
}

private val DREAM_TILE = 156.dp

/** "Short term · within a year · 2": same rhythm as the app's section titles. */
@Composable
private fun HorizonHeader(title: String, detail: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            if (count > 0) "$detail · $count" else detail,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NewDreamTile(label: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(0.82f).clip(MaterialTheme.shapes.large)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Trip, car, home…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A vision-board tile: emoji, name, and how close the family is. */
@Composable
private fun DreamCard(dream: Dream, progress: DreamProgress?, currency: String, trip: TripSummary?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(0.82f).clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(gradientFor(dream.kind))).clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(dream.emoji, fontSize = 40.sp)
        Spacer(Modifier.weight(1f))
        Text(dream.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val status = when {
            dream.achieved -> "Achieved 🎉"
            trip != null -> "🧳 ${trip.trip.title} · ${formatDate(trip.trip.start)}"
            progress == null -> dream.targetDate?.let { "By ${formatDate(it)}" } ?: "Someday"
            progress.remainingMinor == 0L -> "Fully funded"
            progress.affordableNow -> "You can afford this now"
            progress.monthlyNeededMinor != null -> "${formatCompact(progress.monthlyNeededMinor, currency)}/mo to make it"
            else -> "${formatCompact(progress.remainingMinor, currency)} to go"
        }
        Text(status, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
        if (progress != null && dream.targetMinor > 0) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                val f = if (dream.achieved) 1f else progress.progress
                if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(Color.White))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatCompact(progress.totalMinor, currency)} of ${formatCompact(dream.targetMinor, currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DreamSheet(
    dream: Dream,
    progress: DreamProgress?,
    state: PlansUiState,
    onFund: (Pocket, Long, String) -> Unit,
    onAchieved: () -> Unit,
    onEdit: () -> Unit,
    onPlanTrip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val money = state.money
    val currency = state.currency
    var amount by remember { mutableStateOf(progress?.remainingMinor?.takeIf { it > 0 }?.let { (it / 100).toString() } ?: "") }
    val sources: List<Triple<Pocket, String, Long>> = buildList {
        money?.let { add(Triple(Pocket.Pot, "Gullak", it.envelopes.potMinor)) }
        money?.envelopes?.envelopes?.filter { it.availableMinor > 0 }?.forEach { add(Triple(Pocket.Category(it.category), it.category, it.availableMinor)) }
    }
    var source by remember { mutableStateOf(sources.firstOrNull()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dream.emoji, fontSize = 36.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(dream.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(
                            dream.targetMinor.takeIf { it > 0 }?.let { formatMinor(it, currency) },
                            dream.targetDate?.let { "by ${formatDate(it)}" },
                        ).joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (progress != null) {
                HaCard(Modifier.fillMaxWidth()) {
                    Text("${formatMinor(progress.savedMinor, currency)} saved" + if (progress.creditsMinor > 0) " + ${formatMinor(progress.creditsMinor, currency)} in credits" else "", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            progress.remainingMinor == 0L -> "Fully funded. Time to book it!"
                            progress.affordableNow -> "The gullak (${formatMinor(money!!.envelopes.potMinor, currency)}) covers the remaining ${formatMinor(progress.remainingMinor, currency)}."
                            progress.monthlyNeededMinor != null -> "Set aside ${formatMinor(progress.monthlyNeededMinor, currency)} a month to make it on time."
                            else -> "${formatMinor(progress.remainingMinor, currency)} to go."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (progress.remainingMinor > 0 && sources.isNotEmpty()) {
                    Text("Add money from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sources.forEach { s ->
                            FilterChip(
                                selected = source == s,
                                onClick = { source = s },
                                label = { Text("${s.second} · ${formatCompact(s.third, currency)}") },
                                shape = CircleShape,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount") },
                        prefix = { Text("$currency  ") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val minor = amount.toBigDecimalOrNull()?.let { toMinorUnits(it, currency) }
                    val s = source
                    Button(
                        onClick = { if (s != null && minor != null) onFund(s.first, minor, s.second) },
                        enabled = s != null && minor != null && minor > 0 && minor <= s.third,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Move to ${dream.title}") }
                }
            } else {
                Text("Adults in your home track savings for this dream.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            money?.trips?.filter { it.trip.dream == dream.title }?.takeIf { it.isNotEmpty() }?.let { linked ->
                Text("Trips for this dream", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                linked.forEach { t ->
                    Text(
                        "🧳 ${t.trip.title} · ${formatDate(t.trip.start)} · ${formatMinor(t.totalMinor, currency)} spent" + (t.leftMinor?.let { if (it >= 0) " · ${formatMinor(it, currency)} left" else "" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (money != null && (dream.kind == "Travel" || dream.kind == "Experience")) {
                TextButton(onClick = onPlanTrip, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Plan the trip") }
            }
            TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Edit dream") }
            if (money != null) {
                TextButton(onClick = onAchieved, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (dream.achieved) "Mark as not achieved" else "We did it! Mark achieved 🎉")
                }
            }
        }
    }
}

/** A weekly goal: drag to say how far along it is; earlier weeks are read-only. */
@Composable
private fun GoalRow(goal: com.houseaffairs.app.core.data.Goal, onEdit: () -> Unit, onProgress: ((Int) -> Unit)?) {
    val accent = LocalHouseColors.current.plans
    var value by remember(goal.id, goal.progress) { mutableStateOf(goal.progress.toFloat()) }
    Column(Modifier.fillMaxWidth().opensEdit(onEdit).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (goal.done) "✅" else "🎯", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = MaterialTheme.typography.titleSmall)
                goal.kind?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("${value.toInt()}%", style = MaterialTheme.typography.labelLarge, color = accent.color)
        }
        if (onProgress != null) {
            androidx.compose.material3.Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { onProgress(value.toInt()) },
                valueRange = 0f..100f,
                steps = 9,
            )
        }
    }
}

/** "High" in red, "Medium" in amber; Low (the default) shows nothing, so the list stays calm. */
@Composable
internal fun PriorityTag(priority: String) {
    val color = when (priority) {
        "High" -> MaterialTheme.colorScheme.error
        "Medium" -> androidx.compose.ui.graphics.Color(0xFFD39B32)
        else -> return
    }
    Text(
        priority,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(end = 8.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
