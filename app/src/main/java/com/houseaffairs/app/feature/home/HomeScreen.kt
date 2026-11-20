package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.HomeSection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.houseaffairs.app.core.util.HIDDEN_AMOUNT
import com.houseaffairs.app.core.ui.shownCompact
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.houseaffairs.app.core.data.Area
import com.houseaffairs.app.core.data.Attention
import com.houseaffairs.app.core.data.Priority
import com.houseaffairs.app.core.data.TodayRow
import com.houseaffairs.app.core.data.BILL_PREFIX
import com.houseaffairs.app.core.data.RENEWAL_PREFIX
import com.houseaffairs.app.core.data.ActivityKind
import com.houseaffairs.app.core.data.CardKind
import com.houseaffairs.app.core.data.Idea
import com.houseaffairs.app.core.data.Moment
import com.houseaffairs.app.core.data.MomentCard
import com.houseaffairs.app.core.data.MomentInputs
import com.houseaffairs.app.core.data.momentCards
import com.houseaffairs.app.core.data.momentLine
import com.houseaffairs.app.core.data.nextUp
import com.houseaffairs.app.core.data.buildToday
import com.houseaffairs.app.core.data.COMING_UP_COLLECTIONS
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.houseaffairs.app.core.data.RightNow
import com.houseaffairs.app.core.data.SuggestionKind
import com.houseaffairs.app.core.data.WatchItem
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.houseaffairs.app.feature.add.EntryType
import com.houseaffairs.app.feature.review.StoryAvatar
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.houseaffairs.app.core.data.TaskItem
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.designsystem.LightStatusBarIcons
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.data.Recipe
import com.houseaffairs.app.core.ui.Avatar
import com.houseaffairs.app.core.ui.DishCard
import com.houseaffairs.app.core.ui.AvatarStack
import com.houseaffairs.app.core.ui.DayPart
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.HeroBackground
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.SectionTitle
import com.houseaffairs.app.core.ui.TickCircle
import com.houseaffairs.app.core.ui.opensEdit
import com.houseaffairs.app.core.ui.shownMinor
import com.houseaffairs.app.feature.review.NudgeCard
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Where Home's taps go: a tab ("plans/TASKS") or an entry's edit form. */
private class HomeNav(val open: (String) -> Unit, val edit: (EntryType, String) -> Unit, val saved: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit = {})

private val LocalHomeNav = staticCompositionLocalOf { HomeNav({}, { _, _ -> }) }

private fun collectionType(collection: String?): EntryType? = when (collection) {
    "tasks" -> EntryType.TASK
    "stock" -> EntryType.GROCERY
    "medicines" -> EntryType.MEDICINE
    "recurring" -> EntryType.SUBSCRIPTION
    "events" -> EntryType.EVENT
    else -> null
}

@Composable
fun HomeScreen(
    onQuickAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTab: (String) -> Unit,
    onAddRecipe: (Recipe) -> Unit,
    onEdit: (EntryType, String) -> Unit,
    onOpenReview: () -> Unit,
    onSaved: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit = {},
    onAddType: (EntryType) -> Unit = { onQuickAdd() },
    viewModel: HomeViewModel = hiltViewModel(),
) {
    CompositionLocalProvider(LocalHomeNav provides HomeNav(onOpenTab, onEdit, onSaved)) {
        HomeContent(onQuickAdd, onAddType, onOpenSettings, onOpenReview, onOpenTab, onAddRecipe, viewModel)
    }
}

@Composable
private fun HomeContent(
    onQuickAdd: () -> Unit,
    onAddType: (EntryType) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenTab: (String) -> Unit,
    onAddRecipe: (Recipe) -> Unit,
    viewModel: HomeViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val handled by viewModel.handled.collectAsStateWithLifecycle()
    val rows = remember(state.attention, state.data.tasks, state.filter, state.canPlans) { todayRows(state) }
    // Home talks about this part of the day: what it suggests at breakfast isn't what it asks at night.
    val moment = Moment.at(LocalTime.now().hour)
    val cards = remember(rows, handled, state, moment) { momentCards(moment, momentInputs(state, rows, moment), handled) }
    LaunchedEffect(Unit) {
        viewModel.refreshNudge()
        viewModel.markOpened()
    }
    val dayPart = DayPart.at(java.time.LocalTime.now().hour)
    val listState = rememberLazyListState()
    // The gradient is behind the status bar until roughly its first 300dp have scrolled away.
    val gradientPx = with(LocalDensity.current) { 300.dp.toPx() }
    val heroGone by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > gradientPx }
    }
    val heroMoved by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    LightStatusBarIcons(enabled = !heroGone || MaterialTheme.colorScheme.background.luminance() < 0.5f)

    var customising by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val homeNav = LocalHomeNav.current
    if (searching) SearchSheet(onDismiss = { searching = false }, onEdit = homeNav.edit, onOpenTab = onOpenTab)

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Hero(state, dayPart, if (state.data.loaded) momentLine(moment, cards) else "Getting your day ready…", onQuickAdd, onOpenReview, onOpenTab, viewModel::toggleHideAmounts, onSearch = { searching = true }) }
        // Still loading: no "All clear" or "Did you spend anything?" before the day is known.
        if (!state.data.loaded) {
            item { com.houseaffairs.app.core.ui.LoadingRow() }
            return@LazyColumn
        }
        // One way to add from Home: the Ask bar. Its sheet leads with the big tiles for what this person adds most.
        // Narrowing Home lives in Customise now; shown here only while a narrowing is on, to undo it.
        if (state.filters.size > 2 && state.filter != HomeFilter.ALL) item { FilterRow(state, viewModel::setFilter) }
        // Setting up, notifications and the daily nudge come after the deck, never before.
        // Setup, the live-well nudge and tidying up share one row you swipe through, instead of
        // three full-width cards stacked down the page.
        val tidyOn = state.layout.any { it.visible && it.section == HomeSection.TIDY }
        val extras: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            item { NotificationsOffCard() }
            item { WorthALook(state, nudge, tidyOn, viewModel, onQuickAdd, onOpenSettings, onOpenTab, onOpenReview) }
        }
        if (state.layout.none { it.visible && it.section == HomeSection.TODAY }) extras()
        // Sections in the order (and visibility) each person chose, narrowed by the filter.
        // Suggestions, cooking, watching and shopping share one tabbed card to keep Home short.
        val forYou = forYouTabs(state.layout, state.rightNow, kitchen = state.canStock && state.shows(Area.KITCHEN), plans = state.canPlans && state.shows(Area.PLANS))
        var forYouShown = false
        state.layout.filter { it.visible }.forEach { pref ->
            when (pref.section) {
                HomeSection.STATS -> Unit // drawn inside the hero
                HomeSection.TIDY -> Unit // in the "worth a look" row
                HomeSection.TODAY -> {
                    item { TodaySection(state, rows, moment, cards, handled, streak, viewModel, onQuickAdd, onAddType) }
                    extras()
                }
                HomeSection.COMING_UP -> item { ComingUpSection(state) }
                HomeSection.FAMILY -> if (state.members.size > 1) item { FamilyFeed(state) }
                HomeSection.GLANCE -> item { GlanceRow(state, onOpenSettings) }
                HomeSection.RIGHT_NOW, HomeSection.USE_IT_UP, HomeSection.WATCH, HomeSection.SHOPPING ->
                    if (!forYouShown && forYou.isNotEmpty()) {
                        forYouShown = true
                        item { ForYou(forYou, state, viewModel, onOpenTab, onAddRecipe) }
                    }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = { customising = true }) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Customise Home")
                }
            }
        }
    }
    // Keep the clock readable: sky colour while the hero scrolls under it, page colour once it's gone.
    val scrim by animateColorAsState(
        when {
            heroGone -> MaterialTheme.colorScheme.background
            heroMoved -> dayPart.colors.first()
            else -> Color.Transparent
        },
        label = "status bar scrim",
    )
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(scrim))
    }

    if (customising) {
        CustomiseSheet(state.layout, viewModel::toggleSection, viewModel::moveSection, onDismiss = { customising = false }, showOnly = if (state.filters.size > 2) ({ FilterRow(state, viewModel::setFilter, padded = false) }) else null)
    }
}

// ---------------------------------------------------------------------------------------------
// Filters, attention, coming up

@Composable
private fun FilterRow(state: HomeUiState, onSelect: (HomeFilter) -> Unit, padded: Boolean = true) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = if (padded) 20.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = if (padded) 16.dp else 8.dp),
    ) {
        items(state.filters.size) { index ->
            val filter = state.filters[index]
            FilterChip(
                selected = state.filter == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

private fun TodayRow.key(): String = when (this) {
    is TodayRow.Task -> task.id
    is TodayRow.Alert -> item.id
}

private fun todayRows(state: HomeUiState): List<TodayRow> {
    val tasks = if (state.canPlans && state.shows(Area.PLANS)) state.data.tasks else emptyList()
    return buildToday(state.attention.filter { state.shows(it.area) }, tasks, LocalDate.now())
}

/** What Home knows for this person right now, as the pure deck rule wants it. */
private fun momentInputs(state: HomeUiState, rows: List<TodayRow>, moment: Moment): MomentInputs {
    val today = LocalDate.now()
    // Someone else's task isn't mine to finish, so it never gets a card.
    val mine = rows.filter { it !is TodayRow.Task || it.task.assignee == null || it.task.assignee == state.myUid }
    val food = state.rightNow?.items?.firstOrNull { it.kind == SuggestionKind.FOOD }?.let { Idea(it.title, it.detail) }
    val rescue = state.ideas.firstOrNull()?.let { Idea(it.recipe.title, it.rescues.firstOrNull()?.let { r -> "Uses $r before it goes off" } ?: "You have everything") }
    val startOfDay = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val tomorrow = today.plusDays(1)
    return MomentInputs(
        things = mine.map { row ->
            when (row) {
                is TodayRow.Task -> MomentCard(row.key(), CardKind.THING, row.priority?.label ?: "When you can", row.task.title, taskDetail(row.task, state.myUid, LocalDate.now(), state::nameOf))
                is TodayRow.Alert -> MomentCard(row.key(), CardKind.THING, row.item.priority.label, row.item.title, row.item.detail)
            }
        },
        // Dinner is when food about to expire gets rescued; other meals take what suits the hour.
        meal = (if (moment == Moment.EVENING) rescue ?: food else food ?: rescue).takeIf { state.canStock && moment.meal != null },
        // The eat-out list on weekends; any day, a place the family already loved.
        eatOut = state.rightNow?.items?.firstOrNull { it.kind == SuggestionKind.EAT_OUT }?.let { Idea(it.title, it.detail) }
            ?: com.houseaffairs.app.core.data.likedBefore(state.places, moment.meal, java.time.LocalDate.now()),
        toBuy = if (state.canStock) state.data.shopping.map { it.name } else emptyList(),
        spentToday = state.data.recent.any { it.kind == ActivityKind.EXPENSE && it.createdBy == state.myUid && (it.createdAtMillis ?: startOfDay) >= startOfDay },
        watch = state.watch.firstOrNull()?.takeIf { state.canPlans }?.let { Idea(it.title, it.providers.firstOrNull() ?: it.platform ?: "On your watch list") },
        tomorrow = if (state.canPlans) {
            state.data.tasks.filter { it.dueDate == tomorrow && (it.assignee == null || it.assignee == state.myUid) }.map { it.title } +
                state.data.events.filter { it.next == tomorrow }.map { it.title }
        } else emptyList(),
    )
}

/**
 * Home's main part: a deck of cards for this part of the day, one idea or question each, swiped
 * through and answered with one tap. What it shows changes from morning to night, and it runs
 * out, so there's a reason to look again later instead of a list that never ends.
 */
@Composable
private fun TodaySection(
    state: HomeUiState,
    rows: List<TodayRow>,
    moment: Moment,
    cards: List<MomentCard>,
    handled: Set<String>,
    streak: Int,
    viewModel: HomeViewModel,
    onQuickAdd: () -> Unit,
    onAddType: (EntryType) -> Unit,
) {
    val nav = LocalHomeNav.current
    // Doing something counts towards the day; undoing it takes that back.
    fun did(id: String): (com.houseaffairs.app.feature.add.SavedEntry) -> Unit = { saved ->
        viewModel.setHandled(id)
        nav.saved(saved.copy(undo = { saved.undo(); viewModel.setHandled(id, false) }))
    }
    val answers = com.houseaffairs.app.core.ui.TaskAnswers(
        accept = { viewModel.acceptTask(it)?.let(nav.saved) },
        decline = { t, note -> viewModel.declineTask(t, note)?.let(nav.saved) },
        keep = viewModel::keepTask,
        reassign = { nav.edit(EntryType.TASK, it.id) },
    )
    val inDeck = cards.map { it.id }.toSet()
    val later = rows.filter { it.key() !in inDeck && it.key() !in handled }
    var expanded by remember { mutableStateOf(false) }
    // No to-dos left: the cheer says it, so the deck has no "that's all" card of its own.
    val cheered = state.data.loaded && cards.none { it.kind == CardKind.THING } && later.isEmpty()

    val done = handled.count { !it.startsWith(com.houseaffairs.app.core.data.MOMENT_PREFIX) }
    val total = done + cards.count { it.kind == CardKind.THING } + later.size
    Column(Modifier.padding(top = 18.dp)) {
        TodayHeader(moment, done, total, streak)
        Spacer(Modifier.height(10.dp))
        // No to-dos left for this person: say so, loudly. Meal, shopping and other ideas aren't
        // to-dos, so they stay swipeable underneath instead of holding the cheer back.
        val cleared = handled.count { !it.startsWith(com.houseaffairs.app.core.data.MOMENT_PREFIX) }
        // Only once Home has loaded, so an empty list while loading never cheers.
        if (cheered) {
            CheerCard(moment, cleared, LocalDate.now(), onQuickAdd, compact = cards.isNotEmpty())
            if (cards.isEmpty()) return@Column
            Spacer(Modifier.height(14.dp))
        } else {
            // Reaching 3, 5, 10… cleared gets a cheer on the way, not only at the very end.
            com.houseaffairs.app.core.data.milestoneReached(cleared, handled)?.takeIf { state.data.loaded }?.let { n ->
                MilestoneCard(n, LocalDate.now(), keepGoing = { viewModel.setHandled(com.houseaffairs.app.core.data.milestoneId(n)) })
                Spacer(Modifier.height(14.dp))
            }
        }
        // The next card peeks in from the right, so it's plain there's more to swipe to.
        DeckPager(cards.map { it.id } + listOfNotNull("end".takeIf { !cheered })) { page ->
            val card = cards.getOrNull(page)
            if (card == null) {
                EndCard(moment, answered = handled.isNotEmpty(), onQuickAdd)
            } else {
                AndroidDeckCard(card, "${page + 1} of ${cards.size}", rows.firstOrNull { it.key() == card.id }, state, viewModel, answers, ::did, onAddType)
            }
        }
        // Everything else that's open, always in view, each one a tap from cleared: Home is
        // mostly what's left to clear, counting down to the cheer.
        if (later.isNotEmpty()) {
            LeftToClearTitle(cleared, later.size)
            HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                val shown = if (expanded) later else later.take(com.houseaffairs.app.core.data.CLEAR_PEEK)
                shown.forEachIndexed { i, row ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    LaterRow(row, state, viewModel, answers, ::did)
                }
                if (later.size > com.houseaffairs.app.core.data.CLEAR_PEEK) {
                    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(if (expanded) "Show less" else "Show all ${later.size}")
                    }
                } else if (state.canPlans && state.data.tasks.isNotEmpty()) {
                    TextButton(onClick = { nav.open("plans/TASKS") }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("All tasks (${state.data.tasks.size})")
                    }
                }
            }
        }
    }
}


/** One card of the deck: a small heading, one big line, and a button that says what tapping it does. */
@Composable
private fun AndroidDeckCard(
    card: MomentCard,
    position: String,
    row: TodayRow?,
    state: HomeUiState,
    viewModel: HomeViewModel,
    answers: com.houseaffairs.app.core.ui.TaskAnswers,
    did: (String) -> (com.houseaffairs.app.feature.add.SavedEntry) -> Unit,
    onAddType: (EntryType) -> Unit,
) {
    val nav = LocalHomeNav.current
    val colors = LocalHouseColors.current
    val id = card.id
    val dismiss = { viewModel.setHandled(id) }
    fun go(route: String): () -> Unit = { dismiss(); nav.open(route) }
    val area = when (row) {
        is TodayRow.Task -> Area.PLANS
        is TodayRow.Alert -> row.item.area
        null -> null
    }
    val (emoji, accent) = when (card.kind) {
        CardKind.THING -> "" to areaAccent(area ?: Area.PLANS)
        CardKind.MEAL -> "🍲" to colors.stock
        CardKind.EAT_OUT -> "🍽️" to colors.plans
        CardKind.SHOPPING -> "🛒" to colors.stock
        CardKind.SPENDS -> "💸" to colors.money
        CardKind.WATCH -> "🎬" to colors.vault
        CardKind.TOMORROW -> "📅" to colors.plans
    }
    // What the main button says and does; null where a task has its own yes/no buttons.
    var later = "Not now"
    val main: Pair<String, () -> Unit>? = when (card.kind) {
        CardKind.MEAL -> "Let's cook" to go("stock/RECIPES")
        CardKind.EAT_OUT -> "See places" to go("plans/PLACES")
        CardKind.SHOPPING -> "Open the list" to go("stock/SHOPPING")
        // Not answered until something is added: the question goes away once a spend is noted.
        CardKind.SPENDS -> { later = "Nothing today"; "Yes, add it" to { onAddType(EntryType.EXPENSE) } }
        CardKind.WATCH -> "Open watch list" to go("plans/LISTS")
        CardKind.TOMORROW -> { later = ""; "Got it" to dismiss }
        CardKind.THING -> {
            later = "Not today"
            when (row) {
                is TodayRow.Task -> when (com.houseaffairs.app.core.data.handoffOf(row.task, state.myUid)) {
                    // Given to me, or back with me: answer that first.
                    com.houseaffairs.app.core.data.Handoff.ASKED_ME, com.houseaffairs.app.core.data.Handoff.CAME_BACK -> null
                    else -> "Done ✓" to { viewModel.completeTask(row.task.id, did(id)) }
                }
                is TodayRow.Alert -> {
                    val item = row.item
                    val bill = item.id.removePrefix(BILL_PREFIX).takeIf { item.id.startsWith(BILL_PREFIX) }
                    val renewal = item.id.removePrefix(RENEWAL_PREFIX).takeIf { item.id.startsWith(RENEWAL_PREFIX) }
                    val restock = item.restock?.takeIf { r -> state.data.shopping.none { com.houseaffairs.app.core.data.itemKey(it.name) == com.houseaffairs.app.core.data.itemKey(r) } }
                    when {
                        bill != null -> "Paid ✓" to { viewModel.markBillPaid(bill, did(id)) }
                        renewal != null -> "Log it" to { viewModel.logRenewal(renewal, did(id)) }
                        restock != null -> "Add to list" to { viewModel.addToShopping(restock, did(id)) }
                        item.habit != null -> "Add to my day" to { viewModel.addHabitTask(item.habit!!, did(id)) }
                        // Nothing to do but know about it: saying so is dealing with it.
                        else -> { later = ""; "Got it" to dismiss }
                    }
                }
                null -> { later = ""; "Got it" to dismiss }
            }
        }
    }
    val open: (() -> Unit)? = when (row) {
        is TodayRow.Task -> ({ nav.edit(EntryType.TASK, row.task.id) })
        is TodayRow.Alert -> ({ openAlert(row.item, nav) })
        null -> null
    }
    DeckCard(card.eyebrow, position, card.title, card.detail, accent, emoji, if (emoji.isEmpty()) areaIcon(area ?: Area.PLANS) else null, open) {
        if (main == null && row is TodayRow.Task) {
            com.houseaffairs.app.core.ui.HandoffButtons(row.task, state.myUid, answers)
        } else if (main != null) {
            DeckButtons(main.first, main.second, accent, later, dismiss)
        }
    }
}

private fun openAlert(item: com.houseaffairs.app.core.data.Attention, nav: HomeNav) {
    item.open?.let { return nav.open(it) }
    val type = collectionType(item.collection)
    if (type != null) nav.edit(type, item.id.removePrefix(RENEWAL_PREFIX)) else nav.open("money")
}

/** A row under "more for later": the same things, one line each. */
@Composable
private fun LaterRow(
    row: TodayRow,
    state: HomeUiState,
    viewModel: HomeViewModel,
    answers: com.houseaffairs.app.core.ui.TaskAnswers,
    did: (String) -> (com.houseaffairs.app.feature.add.SavedEntry) -> Unit,
) {
    val nav = LocalHomeNav.current
    val id = row.key()
    when (row) {
        is TodayRow.Task -> {
            val task = row.task
            AttentionRow(row.priority, Area.PLANS, task.title, taskDetail(task, state.myUid, LocalDate.now(), state::nameOf), onClick = { nav.edit(EntryType.TASK, task.id) }) {
                task.reach?.let { com.houseaffairs.app.core.ui.ReachButton(it, task.phone, task.title) }
                TickCircle(LocalHouseColors.current.plans.color, onTick = { viewModel.completeTask(task.id, did(id)) }, contentDescription = "Done: ${task.title}")
            }
            com.houseaffairs.app.core.ui.HandoffButtons(task, state.myUid, answers, Modifier.padding(start = 56.dp, top = 6.dp))
        }
        is TodayRow.Alert -> {
            val item = row.item
            AttentionRow(item.priority, item.area, item.title, item.detail, onClick = { openAlert(item, nav) }) {
                // Card bills are marked paid right here; Money no longer has a card box.
                val bill = item.id.removePrefix(BILL_PREFIX).takeIf { item.id.startsWith(BILL_PREFIX) }
                val restock = item.restock
                val renewal = item.id.removePrefix(RENEWAL_PREFIX).takeIf { item.id.startsWith(RENEWAL_PREFIX) }
                if (bill != null) {
                    TextButton(onClick = { viewModel.markBillPaid(bill, did(id)) }) { Text("Paid") }
                } else if (renewal != null) {
                    TextButton(onClick = { viewModel.logRenewal(renewal, did(id)) }) { Text("Log it") }
                } else if (restock != null && state.data.shopping.none { com.houseaffairs.app.core.data.itemKey(it.name) == com.houseaffairs.app.core.data.itemKey(restock) }) {
                    TextButton(onClick = { viewModel.addToShopping(restock, did(id)) }) { Text("+ List") }
                } else if (item.habit != null) {
                    TextButton(onClick = { viewModel.addHabitTask(item.habit!!, did(id)) }) { Text("+ Task") }
                } else {
                    // Just something to know: saying so clears it.
                    TextButton(onClick = { viewModel.setHandled(id) }) { Text("Got it") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero

@Composable
private fun Hero(
    state: HomeUiState,
    dayPart: DayPart,
    line: String,
    onQuickAdd: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenTab: (String) -> Unit,
    onToggleHide: () -> Unit,
    onSearch: () -> Unit,
) {
    val today = LocalDate.now()
    val peeks = if (state.data.loaded) heroPeek(
        state.data, today, state.canMoney, state.canStock, state.canMedicine,
        budgetLeftMinor = state.budget?.leftMinor, hideAmounts = state.hideAmounts,
    ) { shownCompact(it, state.budget?.currency ?: state.baseCurrency) } else null
    HomeHero(
        dayPart, today, "${greetingFor(LocalTime.now().hour)}, ${state.firstName.ifEmpty { "there" }}", line,
        peeks, onOpenTab, onQuickAdd, onSearch,
        summary = if (state.showStats) null else summaryLine(state),
        hidden = state.hideAmounts.takeIf { state.canMoney }, onToggleHidden = onToggleHide,
    ) {
        // Your avatar opens the weekly review; the ring lights up when a new one is ready.
        Row(Modifier.clip(CircleShape).clickable(onClick = onOpenReview).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarStack(state.members.filter { it.uid != state.myUid }.map { it.displayName }, size = 28.dp, ring = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.width(4.dp))
            StoryAvatar(state.firstName, fresh = state.reviewFresh)
        }
    }
}

private fun summaryLine(state: HomeUiState): String {
    val data = state.data
    val parts = buildList {
        if (state.canMoney && data.monthSpentMinor > 0 && !state.hideAmounts) add("${shownMinor(data.monthSpentMinor, state.baseCurrency)} spent this month")
        if (state.canStock && data.shopping.isNotEmpty()) add("${data.shopping.size} to buy")
        val dueToday = data.tasks.count { task -> task.dueDate?.let { !it.isAfter(LocalDate.now()) } == true }
        if (dueToday > 0) {
            add("$dueToday due today")
        } else if (data.tasks.isNotEmpty()) {
            add(if (data.tasks.size == 1) "1 open task" else "${data.tasks.size} open tasks")
        }
    }
    return if (parts.isEmpty()) "A calm day at ${state.homeName}. Add anything in one line below." else parts.joinToString(" · ")
}

// ---------------------------------------------------------------------------------------------
// At a glance

@Composable
private fun GlanceRow(state: HomeUiState, onOpenSettings: () -> Unit) {
    val nav = LocalHomeNav.current
    val colors = LocalHouseColors.current
    val data = state.data
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 20.dp),
    ) {
        if (state.canMoney && state.shows(Area.MONEY)) {
            item {
                GlanceCard(Icons.Outlined.AccountBalanceWallet, colors.money, "This month", shownMinor(data.monthSpentMinor, state.baseCurrency), onClick = { nav.open("money") }) {
                    CategoryBar(data.spentByCategory)
                    Spacer(Modifier.height(6.dp))
                    Caption(data.spentByCategory.firstOrNull()?.let { "Most on ${it.first}" } ?: "No spending yet")
                }
            }
        }
        if (state.canStock && state.shows(Area.KITCHEN)) {
            item {
                GlanceCard(Icons.Outlined.ShoppingCart, colors.stock, "To buy", data.shopping.size.toString(), onClick = { nav.open("stock/SHOPPING") }) {
                    Caption(data.shopping.take(3).joinToString(", ") { it.name }.ifEmpty { "List is empty" })
                }
            }
        }
        if (state.canMedicine && state.shows(Area.HEALTH)) {
            item {
                GlanceCard(Icons.Outlined.LocalHospital, colors.vault, "Medicines", data.medicines.size.toString(), onClick = { nav.open("vault/MEDICINES") }) {
                    Caption(data.medicines.firstOrNull { it.expiry != null }?.let { "Next expiry: ${it.title}" } ?: "Track expiry & who it's for")
                }
            }
        }
        if (state.canPlans && state.shows(Area.PLANS)) {
            item {
                GlanceCard(Icons.Outlined.TaskAlt, colors.plans, "Open tasks", data.tasks.size.toString(), onClick = { nav.open("plans/TASKS") }) {
                    Caption(data.tasks.firstOrNull()?.let { "Next: ${it.title}" } ?: "All clear")
                }
            }
        }
        item {
            GlanceCard(
                Icons.Outlined.Groups,
                colors.vault,
                "Family",
                state.members.size.toString(),
                onClick = onOpenSettings,
            ) {
                Caption(if (state.members.size <= 1 && state.isOwner) "Tap to invite" else state.members.joinToString(", ") { it.displayName.substringBefore(' ') })
            }
        }
    }
}

@Composable
private fun GlanceCard(
    icon: ImageVector,
    accent: Accent,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    footer: @Composable () -> Unit,
) {
    HaCard(Modifier.width(164.dp).height(176.dp), onClick = onClick) {
        IconBlob(icon, accent, size = 40.dp)
        Spacer(Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Fixed footer height keeps labels aligned across cards.
        Column(Modifier.height(36.dp), verticalArrangement = Arrangement.Bottom) { footer() }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Thin stacked bar of spend per category. */
@Composable
private fun CategoryBar(byCategory: List<Pair<String, Long>>) {
    val colors = LocalHouseColors.current
    val palette = listOf(colors.money.color, colors.plans.color, colors.stock.color, colors.vault.color, colors.coral)
    Row(
        Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        byCategory.take(5).forEachIndexed { index, (_, amount) ->
            Box(Modifier.weight(amount.coerceAtLeast(1).toFloat()).fillMaxHeight().background(palette[index]))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Setup

// ---------------------------------------------------------------------------------------------
// Tasks

private fun dueLabel(day: LocalDate): Pair<String, Boolean> {
    val today = LocalDate.now()
    return when {
        day.isBefore(today) -> "Overdue" to true
        day == today -> "Today" to false
        day == today.plusDays(1) -> "Tomorrow" to false
        else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM")) to false
    }
}

@Composable
private fun Pill(text: String, accent: Accent) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = accent.color,
        modifier = Modifier.clip(CircleShape).background(accent.container).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Shopping


// ---------------------------------------------------------------------------------------------
// Timeline: one card, three tabs, rows with a date tile

@Composable
private fun ComingUpSection(state: HomeUiState) {
    val nav = LocalHomeNav.current
    val today = LocalDate.now()
    val rows = comingUp(state.data, state.attention, today, state.canPlans && state.shows(Area.PLANS), state.canMoney && state.shows(Area.MONEY), state.giftsFor)
    if (rows.isEmpty() && !state.canPlans) return
    ComingUpStrip(rows, today, onOpen = { row -> nav.edit(if (row.area == Area.MONEY) EntryType.SUBSCRIPTION else EntryType.EVENT, row.id) }, onGifts = { nav.open("plans/GIFTS") })
}

/** Setup, the live-well nudge and tidying up in one swipeable row (shared `WorthALookRow`). */
@Composable
private fun WorthALook(
    state: HomeUiState,
    nudge: com.houseaffairs.app.core.data.Nudge?,
    tidyOn: Boolean,
    viewModel: HomeViewModel,
    onQuickAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTab: (String) -> Unit,
    onOpenReview: () -> Unit,
) {
    val tidyHidden by viewModel.tidyHidden.collectAsStateWithLifecycle()
    val tidy = remember(state.data, state.budget) {
        homeNudges(state.data, LocalDate.now(), state.canMoney, state.canStock, state.canMedicine, state.canPlans, hasBudget = state.budget != null)
    }
    val steps = setupSteps(state.isOwner, state.members.size, state.quickAddTried || state.data.recent.isNotEmpty(), state.budget != null, onOpenSettings, onQuickAdd) { onOpenTab("money") }
        .takeIf { s -> s.any { !it.done } }
    WorthALookRow(
        buildList {
            steps?.let { add { m -> SetupCard(it, m) } }
            nudge?.let { n -> add { m -> NudgeCard(n, m, onClick = onOpenReview, onDismiss = viewModel::dismissNudge) } }
            if (tidyOn && !tidyHidden && state.data.loaded && tidy.isNotEmpty()) add { m -> TidyCard(tidy, onDismiss = viewModel::dismissTidy, modifier = m) }
        },
    )
}

@Composable
private fun TimelineDivider() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun TimelineRow(
    date: LocalDate?,
    accent: Accent,
    title: String,
    detail: String,
    urgent: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(Modifier.clip(MaterialTheme.shapes.small).opensEdit(onClick), verticalAlignment = Alignment.CenterVertically) {
        DateTile(date, LocalDate.now(), accent, urgent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

// ---------------------------------------------------------------------------------------------
// Right now and watch list

@Composable
private fun TidyCard(nudges: List<HomeNudge>, onDismiss: () -> Unit, modifier: Modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) =
    TidyCard(nudges, onDismiss, LocalHomeNav.current.open, modifier)

/**
 * Shown while this phone blocks the app's notifications (said no in the tour, or turned them off
 * later): reminders, doses and the digest can't reach anyone otherwise. Checked again on return.
 */
@Composable
private fun NotificationsOffCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var off by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val check = { off = !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() }
        check()
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) check() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    if (!off) return
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔕", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Notifications are off", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("Reminders and medicine doses won't ring.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        TextButton(onClick = {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }) { Text("Turn on") }
    }
}

@Composable
private fun FamilyFeed(state: HomeUiState) = FamilyFeedCard(state.data.recent, state.myUid, state::nameOf, LocalHomeNav.current.open)

/** For you (shared :ui `ForYouCard`), fed from Home's state. */
@Composable
private fun ForYou(tabs: List<ForYouTab>, state: HomeUiState, viewModel: HomeViewModel, onOpenTab: (String) -> Unit, onAddRecipe: (Recipe) -> Unit) {
    val nav = LocalHomeNav.current
    ForYouCard(
        tabs,
        ForYouData(state.rightNow, state.ideas, state.cookWhy, state.watch, state.watchWhy, state.data.shopping, state.data.pantry),
        LocalDate.now(),
        ForYouActions(
            openTab = onOpenTab, addRecipe = onAddRecipe,
            editWish = { nav.edit(EntryType.WISH, it) }, editShopping = { nav.edit(EntryType.SHOPPING, it) },
            watched = { viewModel.markWatched(it, nav.saved) }, bought = { viewModel.markBought(it, nav.saved) },
        ),
    )
}
