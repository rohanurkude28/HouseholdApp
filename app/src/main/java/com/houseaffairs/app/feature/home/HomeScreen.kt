package com.houseaffairs.app.feature.home

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
    val dayPart = DayPart.now()
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
        item { Hero(state, dayPart, momentLine(moment, cards), onQuickAdd, onOpenReview, onOpenTab, viewModel::toggleHideAmounts, onSearch = { searching = true }) }
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
        val forYou = state.layout.filter { it.visible }.mapNotNull { pref ->
            when (pref.section) {
                HomeSection.RIGHT_NOW -> state.rightNow?.let { ForYouTab.RIGHT_NOW }
                HomeSection.USE_IT_UP -> ForYouTab.COOK.takeIf { state.canStock && state.shows(Area.KITCHEN) }
                HomeSection.WATCH -> ForYouTab.WATCH.takeIf { state.canPlans && state.shows(Area.PLANS) }
                HomeSection.SHOPPING -> ForYouTab.SHOPPING.takeIf { state.canStock && state.shows(Area.KITCHEN) }
                else -> null
            }
        }
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
                        item { ForYouCard(forYou, state, viewModel, onOpenTab, onAddRecipe) }
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
        CustomiseSheet(state, viewModel::toggleSection, viewModel::moveSection, viewModel::setFilter) { customising = false }
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

@Composable
private fun areaAccent(area: Area): Accent {
    val colors = LocalHouseColors.current
    return when (area) {
        Area.MONEY -> colors.money
        Area.KITCHEN -> colors.stock
        Area.HEALTH -> colors.vault
        Area.PLANS -> colors.plans
    }
}

private fun areaIcon(area: Area): ImageVector = when (area) {
    Area.MONEY -> Icons.Outlined.AccountBalanceWallet
    Area.KITCHEN -> Icons.Outlined.Kitchen
    Area.HEALTH -> Icons.Outlined.LocalHospital
    Area.PLANS -> Icons.Outlined.Event
}

private fun TodayRow.key(): String = when (this) {
    is TodayRow.Task -> task.id
    is TodayRow.Alert -> item.id
}

private fun todayRows(state: HomeUiState): List<TodayRow> {
    val tasks = if (state.canPlans && state.shows(Area.PLANS)) state.data.tasks else emptyList()
    return buildToday(state.attention.filter { state.shows(it.area) }, tasks, LocalDate.now())
}

private fun taskDetail(task: com.houseaffairs.app.core.data.TaskItem, state: HomeUiState): String =
    com.houseaffairs.app.core.data.handoffLine(task, state.myUid, state::nameOf).let { asked ->
        listOfNotNull(
            asked,
            task.priority.takeIf { it != "Low" }?.let { "$it priority" },
            task.dueDate?.let { dueLabel(it).first },
            // "Waiting for Ravi" already says who it's for.
            task.assignee?.takeIf { it != state.myUid && asked == null }?.let { "for ${state.nameOf(it)}" },
        ).joinToString(" · ").ifEmpty { "No date" }
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
                is TodayRow.Task -> MomentCard(row.key(), CardKind.THING, row.priority?.label ?: "When you can", row.task.title, taskDetail(row.task, state))
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
    val pages = if (cheered) cards.size else cards.size + 1
    val pager = androidx.compose.foundation.pager.rememberPagerState { pages }

    Column(Modifier.padding(top = 18.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${moment.emoji}  ${moment.label.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            // Today as a ring that fills as things get cleared, like an activity ring.
            val done = handled.count { !it.startsWith(com.houseaffairs.app.core.data.MOMENT_PREFIX) }
            val total = done + cards.count { it.kind == CardKind.THING } + later.size
            if (total > 0) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(end = 10.dp)) {
                    CircularProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 4.dp,
                        color = LocalHouseColors.current.money.color,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text("$done/$total", style = MaterialTheme.typography.labelSmall)
                }
            }
            com.houseaffairs.app.core.data.streakLine(streak)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = LocalHouseColors.current.coral)
            }
        }
        Spacer(Modifier.height(10.dp))
        // No to-dos left for this person: say so, loudly. Meal, shopping and other ideas aren't
        // to-dos, so they stay swipeable underneath instead of holding the cheer back.
        val cleared = handled.count { !it.startsWith(com.houseaffairs.app.core.data.MOMENT_PREFIX) }
        // Only once Home has loaded, so an empty list while loading never cheers.
        if (cheered) {
            CheerCard(moment, cleared, onQuickAdd, compact = cards.isNotEmpty())
            if (cards.isEmpty()) return@Column
            Spacer(Modifier.height(14.dp))
        } else {
            // Reaching 3, 5, 10… cleared gets a cheer on the way, not only at the very end.
            com.houseaffairs.app.core.data.milestoneReached(cleared, handled)?.takeIf { state.data.loaded }?.let { n ->
                MilestoneCard(n, keepGoing = { viewModel.setHandled(com.houseaffairs.app.core.data.milestoneId(n)) })
                Spacer(Modifier.height(14.dp))
            }
        }
        // The next card peeks in from the right, so it's plain there's more to swipe to.
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(start = 20.dp, end = if (cards.isEmpty()) 20.dp else 48.dp),
            pageSpacing = 12.dp,
            key = { cards.getOrNull(it)?.id ?: "end" },
        ) { page ->
            val card = cards.getOrNull(page)
            if (card == null) {
                EndCard(moment, answered = handled.isNotEmpty(), onQuickAdd)
            } else {
                DeckCard(card, "${page + 1} of ${cards.size}", rows.firstOrNull { it.key() == card.id }, state, viewModel, answers, ::did, onAddType)
            }
        }
        if (cards.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages) { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(width = if (on) 18.dp else 6.dp, height = 6.dp).clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
        // Everything else that's open, always in view, each one a tap from cleared: Home is
        // mostly what's left to clear, counting down to the cheer.
        if (later.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Left to clear", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(com.houseaffairs.app.core.data.clearedLine(cleared, later.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

private val DECK_HEIGHT = 216.dp

/** One card of the deck: a small heading, one big line, and a button that says what tapping it does. */
@Composable
private fun DeckCard(
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
        CardKind.MEAL -> "🍳" to colors.stock
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
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = accent.container,
        modifier = Modifier.fillMaxWidth().height(DECK_HEIGHT).clip(RoundedCornerShape(28.dp)).then(if (open != null) Modifier.clickable(onClick = open) else Modifier),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent.color, modifier = Modifier.weight(1f))
                Text(position, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(card.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(card.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                if (emoji.isNotEmpty()) Text(emoji, style = MaterialTheme.typography.displaySmall) else IconBlob(areaIcon(area ?: Area.PLANS), accent, size = 44.dp)
            }
            Spacer(Modifier.weight(1f))
            if (main == null && row is TodayRow.Task) {
                com.houseaffairs.app.core.ui.HandoffButtons(row.task, state.myUid, answers)
            } else if (main != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Button(
                        onClick = main.second,
                        shape = CircleShape,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accent.color, contentColor = Color.White),
                    ) { Text(main.first) }
                    if (later.isNotEmpty()) TextButton(onClick = dismiss) { Text(later, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** A milestone on the way (3, 5, 10… cleared today): noticed, cheered, then out of the way. */
@Composable
private fun MilestoneCard(n: Int, keepGoing: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = LocalHouseColors.current.money.container,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎉", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(com.houseaffairs.app.core.data.milestoneTitle(n), style = MaterialTheme.typography.titleLarge)
                Text(com.houseaffairs.app.core.data.cheerLine(LocalDate.now()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = keepGoing) { Text("Keep going") }
        }
    }
}

/** Nothing left for this person today: a cheer, a different nudge each day, and how much got cleared. */
@Composable
private fun CheerCard(moment: Moment, cleared: Int, onQuickAdd: () -> Unit, compact: Boolean = false) {
    val colors = LocalHouseColors.current
    // With ideas still to swipe underneath, the cheer is one short row, not a page.
    if (compact) {
        Surface(shape = RoundedCornerShape(24.dp), color = colors.money.container, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎉", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Hurray, nothing for you!", style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (cleared > 0) "You cleared $cleared today. " else "") + com.houseaffairs.app.core.data.cheerLine(LocalDate.now()),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.money.container,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("🎉", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text("Hurray, nothing for you!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                (if (cleared > 0) "You cleared $cleared today. " else "") + com.houseaffairs.app.core.data.cheerLine(LocalDate.now()),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(nextUp(moment), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onQuickAdd, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Add something") }
        }
    }
}

/** The last card: this part of the day is done, and here's what the next one brings. */
@Composable
private fun EndCard(moment: Moment, answered: Boolean, onQuickAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().height(DECK_HEIGHT)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text(if (answered) "🎉" else moment.emoji, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(if (answered) "That's all for ${moment.label.removePrefix("this ")}" else "Nothing needs you ${moment.label}", style = MaterialTheme.typography.titleLarge)
            Text(nextUp(moment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onQuickAdd, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Add something") }
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
            AttentionRow(row.priority, Area.PLANS, task.title, taskDetail(task, state), onClick = { nav.edit(EntryType.TASK, task.id) }) {
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

@Composable
private fun priorityColor(priority: Priority?): Color = when (priority) {
    Priority.URGENT -> MaterialTheme.colorScheme.error
    Priority.TODAY -> LocalHouseColors.current.coral
    Priority.SOON -> LocalHouseColors.current.plans.color
    null -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun AttentionRow(
    priority: Priority?,
    area: Area,
    title: String,
    detail: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(Modifier.clip(MaterialTheme.shapes.small).opensEdit(onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 36.dp).clip(CircleShape).background(priorityColor(priority)))
        Spacer(Modifier.width(12.dp))
        IconBlob(areaIcon(area), areaAccent(area), size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

private data class ComingUpRow(val id: String, val area: Area, val title: String, val detail: String, val date: LocalDate, val gifts: Int = 0)

/** Birthdays, renewals and payments in the next 30 days, soonest first, minus what Today shows. */
private fun comingUp(state: HomeUiState): List<ComingUpRow> {
    val horizon = LocalDate.now().plusDays(30)
    val inToday = state.attention.filter { it.collection in COMING_UP_COLLECTIONS && it.priority != Priority.SOON }.map { it.id }.toSet()
    val events = if (state.canPlans && state.shows(Area.PLANS)) {
        state.data.events.filter { !it.next.isAfter(horizon) }.map { e ->
            val gifts = state.giftsFor[e.title.lowercase()]
            ComingUpRow(e.id, Area.PLANS, e.title, e.kind, e.next, gifts ?: 0)
        }
    } else {
        emptyList()
    }
    val renewals = if (state.canMoney && state.shows(Area.MONEY)) {
        // Renewals waiting to be logged live in Today, not here.
        state.data.renewals.filter { !it.next.isAfter(horizon) && !it.pending(LocalDate.now()) }.map { ComingUpRow(it.id, Area.MONEY, it.title, it.amountText ?: "Payment", it.next) }
    } else {
        emptyList()
    }
    return (events + renewals).filter { it.id !in inToday }.sortedBy { it.date }.take(6)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomiseSheet(
    state: HomeUiState,
    onToggle: (HomeSection) -> Unit,
    onMove: (HomeSection, Boolean) -> Unit,
    onFilter: (HomeFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = state.layout
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
            Text("Customise Home", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose what you see and in which order. Only on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.filters.size > 2) {
                Spacer(Modifier.height(12.dp))
                Text("Show only", style = MaterialTheme.typography.titleSmall)
                FilterRow(state, onFilter, padded = false)
            }
            Spacer(Modifier.height(12.dp))
            layout.forEachIndexed { index, pref ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pref.section.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onMove(pref.section, true) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { onMove(pref.section, false) }, enabled = index < layout.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    Switch(checked = pref.visible, onCheckedChange = { onToggle(pref.section) })
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
    HeroBackground(
        dayPart = dayPart,
        groundColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth().height((if (state.showStats) 366 else 300).dp),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                )
                // Search the whole home by name or topic ("travel").
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onSearch),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(8.dp))
                // Your avatar opens the weekly review; the ring lights up when a new one is ready.
                Row(Modifier.clip(CircleShape).clickable(onClick = onOpenReview).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarStack(state.members.filter { it.uid != state.myUid }.map { it.displayName }, size = 28.dp, ring = Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.width(4.dp))
                    StoryAvatar(state.firstName, fresh = state.reviewFresh)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "${greetingFor(LocalTime.now())}, ${state.firstName.ifEmpty { "there" }}",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The whole day in one plain sentence, before any numbers.
            if (state.showStats) {
                Spacer(Modifier.height(4.dp))
                Text(line, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            if (state.showStats) {
                StatsStrip(state, onOpenTab, onToggleHide)
            } else {
                Text(line, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.92f))
                Text(summaryLine(state), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }
        AskBar(
            onClick = onQuickAdd,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

private data class Stat(val value: String, val label: String, val icon: ImageVector, val route: String?)

/** The day at a glance: budget left, the next birthday or anniversary, what's expiring. Tasks live in Today. */
@Composable
private fun StatsStrip(state: HomeUiState, onOpenTab: (String) -> Unit, onToggleHide: () -> Unit) {
    val stats = heroPeek(
        state.data, LocalDate.now(), state.canMoney, state.canStock, state.canMedicine,
        budgetLeftMinor = state.budget?.leftMinor, hideAmounts = state.hideAmounts,
    ) { shownCompact(it, state.budget?.currency ?: state.baseCurrency) }.map { p ->
        val icon = when (p.kind) {
            PeekKind.BUDGET -> Icons.Outlined.AccountBalanceWallet
            PeekKind.SPENT -> Icons.Outlined.Receipt
            PeekKind.TASKS -> Icons.Outlined.TaskAlt
            PeekKind.EVENT -> Icons.Outlined.Cake
            PeekKind.EXPIRING -> Icons.Outlined.Timer
            PeekKind.SHOPPING -> Icons.Outlined.ShoppingCart
            PeekKind.CALM -> Icons.Outlined.CheckCircle
        }
        Stat(p.value, p.label, icon, p.route)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(stats.size) { index ->
                val stat = stats[index]
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .then(if (stat.route != null) Modifier.clickable { onOpenTab(stat.route) } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(stat.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stat.value, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Text(
                        stat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
            }
        }
        if (state.canMoney) {
            IconButton(onClick = onToggleHide) {
                Icon(
                    if (state.hideAmounts) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (state.hideAmounts) "Show amounts" else "Hide amounts",
                    tint = Color.White,
                )
            }
        }
    }
}

fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
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

/** Looks like a search box: one tap to add anything by typing or speaking. */
@Composable
private fun AskBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth().height(60.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(LocalHouseColors.current.coral, MaterialTheme.colorScheme.primary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Ask, add or find anything…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Mic, contentDescription = "Speak", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
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

private data class SetupStep(val title: String, val done: Boolean, val onClick: (() -> Unit)?)

private fun setupSteps(state: HomeUiState, onQuickAdd: () -> Unit, onOpenSettings: () -> Unit, onOpenMoney: () -> Unit): List<SetupStep> =
    if (!state.isOwner) {
        emptyList()
    } else {
        listOf(
            SetupStep("Set up your home", done = true, onClick = null),
            SetupStep("Invite your family", done = state.members.size > 1, onClick = onOpenSettings),
            SetupStep("Add something with quick add", done = state.quickAddTried || state.data.recent.isNotEmpty(), onClick = onQuickAdd),
            SetupStep("Set a monthly budget", done = state.budget != null, onClick = onOpenMoney),
        )
    }

@Composable
private fun SetupCard(steps: List<SetupStep>, modifier: Modifier = Modifier) {
    val done = steps.count { it.done }
    val next = steps.first { !it.done }
    HaCard(modifier.fillMaxWidth(), onClick = next.onClick, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { done / steps.size.toFloat() },
                    modifier = Modifier.size(52.dp),
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
                Text("$done/${steps.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Finish setting up", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Next: ${next.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

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

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ShoppingCard(state: HomeUiState, onBought: (String) -> Unit, modifier: Modifier = Modifier) {
    val nav = LocalHomeNav.current
    val items = state.data.shopping
    HaCard(modifier.fillMaxWidth().padding(horizontal = 20.dp).animateContentSize()) {
        if (items.isEmpty()) {
            EmptyHint("Your list is empty.", "Say “add milk, eggs and bread to shopping”", onClick = null)
        } else {
            // Grouped by aisle, each with its own colour, so the list reads like the shop.
            items.groupBy { it.aisle }.toSortedMap(compareBy { it.ordinal }).entries.forEachIndexed { gi, (aisle, list) ->
                val color = Color(aisle.argb)
                if (gi > 0) Spacer(Modifier.height(12.dp))
                Text("${aisle.emoji}  ${aisle.label}", style = MaterialTheme.typography.labelMedium, color = color)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { item ->
                        // Circle = bought; the name (tap or hold) = edit.
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.14f)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TickCircle(color, onTick = { onBought(item.id) }, size = 18.dp, contentDescription = "Bought ${item.name}")
                            Text(
                                item.name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clip(CircleShape).opensEdit { nav.edit(EntryType.SHOPPING, item.id) }.padding(start = 2.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Caption("Tap the circle once it's in the cart · tap the name to edit")
        }
    }
}

private enum class ForYouTab(val label: String) { RIGHT_NOW("Right now"), COOK("Cook"), WATCH("Lists"), SHOPPING("Shopping") }

/** One card, four tabs: what suits this moment, what to cook, what to watch, what to buy. */
@Composable
private fun ForYouCard(tabs: List<ForYouTab>, state: HomeUiState, viewModel: HomeViewModel, onOpenTab: (String) -> Unit, onAddRecipe: (Recipe) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(tabs.first()) }
    val shown = if (tab in tabs) tab else tabs.first()
    Column(Modifier.padding(top = 24.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("For you", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                onOpenTab(
                    when (shown) {
                        ForYouTab.RIGHT_NOW, ForYouTab.WATCH -> "plans/LISTS"
                        ForYouTab.COOK -> "stock/RECIPES"
                        ForYouTab.SHOPPING -> "stock/SHOPPING"
                    },
                )
            }) { Text("See all") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs.size) { i ->
                val t = tabs[i]
                val label = when (t) {
                    ForYouTab.RIGHT_NOW -> state.rightNow?.headline?.takeIf { it.length <= 22 } ?: t.label
                    ForYouTab.SHOPPING -> "${t.label} · ${state.data.shopping.size}"
                    ForYouTab.WATCH -> "🎬📚🎵 ${t.label} · ${state.watch.size}"
                    else -> t.label
                }
                androidx.compose.material3.FilterChip(
                    selected = t == shown,
                    onClick = { tab = t },
                    label = { Text(label) },
                    shape = CircleShape,
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when (shown) {
            ForYouTab.RIGHT_NOW -> state.rightNow?.let { RightNowRow(it, onOpenTab, onAddRecipe) }
            ForYouTab.COOK -> Column {
                // What's about to go off, so the dishes below read as "use these up".
                val today = LocalDate.now()
                val expiring = state.data.pantry
                    .filter { s -> s.expiry?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(3)) } == true }
                    .sortedBy { it.expiry }
                if (expiring.isNotEmpty()) {
                    Text(
                        "Use soon: " + expiring.take(4).joinToString { "${it.title} (${dueLabel(it.expiry!!).first.lowercase()})" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                    )
                }
                if (state.ideas.isEmpty()) {
                    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        EmptyHint("Nothing to suggest yet.", "Add what's in your kitchen: “bought 1kg paneer expires Friday”", onClick = null)
                    }
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.ideas.take(COOK_TAB_SIZE).size) { index ->
                            val idea = state.ideas[index]
                            DishCard(idea, onClick = { if (idea.saved) onOpenTab("stock/RECIPES") else onAddRecipe(idea.recipe) }, why = state.cookWhy[idea.recipe.title])
                        }
                    }
                }
            }
            ForYouTab.WATCH -> if (state.watch.isEmpty()) {
                HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    EmptyHint("No movies, books or songs yet.", "Say “add Dune 2 to watch list” or “add Atomic Habits to reading list”", onClick = null)
                }
            } else {
                val nav = LocalHomeNav.current
                WatchRow(state.watch, state.watchWhy) { id -> viewModel.markWatched(id, nav.saved) }
            }
            ForYouTab.SHOPPING -> { val nav = LocalHomeNav.current; ShoppingCard(state, { id -> viewModel.markBought(id, nav.saved) }) }
        }
    }
}

@Composable
private fun EmptyHint(title: String, hint: String, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------
// Timeline: one card, three tabs, rows with a date tile

@Composable
private fun ComingUpSection(state: HomeUiState) {
    val nav = LocalHomeNav.current
    val upcoming = comingUp(state)
    if (upcoming.isEmpty() && !state.canPlans) return
    SectionTitle("Coming up")
    if (upcoming.isEmpty()) {
        HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            EmptyHint("Nothing in the next 30 days", "Add birthdays and renewals: “Riya birthday 12 Dec”", onClick = null)
        }
        return
    }
    // A strip of small date cards, like a calendar widget: the next month at a glance, swiped, not scrolled.
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(upcoming.size) { i ->
            val row = upcoming[i]
            val accent = areaAccent(row.area)
            val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), row.date)
            Column(
                Modifier.width(148.dp).height(150.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { nav.edit(if (row.area == Area.MONEY) EntryType.SUBSCRIPTION else EntryType.EVENT, row.id) }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateTile(row.date, accent, urgent = false)
                    Spacer(Modifier.weight(1f))
                    Text(when (days) { 0L -> "Today"; 1L -> "Tmrw"; else -> "${days}d" }, style = MaterialTheme.typography.labelLarge, color = accent.color)
                }
                Spacer(Modifier.height(10.dp))
                Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                // Its own tap target: the card edits the event, this opens its gift ideas.
                if (row.gifts > 0) {
                    Text(
                        "🎁 ${row.gifts} idea${if (row.gifts > 1) "s" else ""} ›",
                        style = MaterialTheme.typography.labelMedium, color = accent.color,
                        modifier = Modifier.clip(CircleShape).clickable { nav.open("plans/GIFTS") }.padding(vertical = 2.dp),
                    )
                } else {
                    Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val WORTH_A_LOOK_HEIGHT = 150.dp

/**
 * Setting up, the day's live-well nudge and tidying up, as one row of cards to swipe through:
 * all still there, none pushing Today down the page. Shows nothing when there's nothing.
 */
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
    val steps = setupSteps(state, onQuickAdd, onOpenSettings) { onOpenTab("money") }.takeIf { s -> s.any { !it.done } }
    // Every card fills the row's height, so short ones don't leave a gap under them.
    val fill = Modifier.fillMaxHeight()
    val pages = buildList<@Composable () -> Unit> {
        steps?.let { add { SetupCard(it, fill) } }
        nudge?.let { n -> add { NudgeCard(n, fill, onClick = onOpenReview, onDismiss = viewModel::dismissNudge) } }
        if (tidyOn && !tidyHidden && state.data.loaded && tidy.isNotEmpty()) add { TidyCard(tidy, onDismiss = viewModel::dismissTidy, modifier = fill) }
    }
    if (pages.isEmpty()) return
    if (pages.size == 1) {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) { pages.single()() }
        return
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState { pages.size }
    Column(Modifier.padding(top = 16.dp)) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(start = 20.dp, end = 44.dp),
            pageSpacing = 10.dp,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.height(WORTH_A_LOOK_HEIGHT),
        ) { page -> pages[page]() }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).size(width = if (on) 14.dp else 6.dp, height = 6.dp).clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

@Composable
private fun TimelineDivider() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Spacer(Modifier.height(10.dp))
}

/** A calendar-style tile: weekday over day number, or "TODAY". */
@Composable
private fun DateTile(date: LocalDate?, accent: Accent, urgent: Boolean) {
    val today = LocalDate.now()
    val bg = if (urgent) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else accent.container
    val fg = if (urgent) MaterialTheme.colorScheme.error else accent.color
    Column(
        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            date == null -> Text("—", style = MaterialTheme.typography.titleMedium, color = fg)
            date == today -> {
                Text("TODAY", style = MaterialTheme.typography.labelSmall, color = fg)
                Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = fg)
            }
            else -> {
                Text(date.format(DateTimeFormatter.ofPattern("EEE")).uppercase(), style = MaterialTheme.typography.labelSmall, color = fg)
                Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = fg)
            }
        }
    }
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
        DateTile(date, accent, urgent)
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
private fun RightNowRow(now: RightNow, onOpenTab: (String) -> Unit, onAddRecipe: (Recipe) -> Unit) {
    val colors = LocalHouseColors.current
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(now.items.size) { i ->
            val s = now.items[i]
            val (icon, accent) = when (s.kind) {
                SuggestionKind.FOOD -> Icons.Outlined.RestaurantMenu to colors.stock
                SuggestionKind.WATCH -> Icons.Outlined.Tv to colors.vault
                SuggestionKind.EAT_OUT -> Icons.Outlined.Restaurant to colors.plans
                SuggestionKind.SHOP -> Icons.Outlined.ShoppingBag to colors.money
            }
            HaCard(
                Modifier.width(180.dp).height(150.dp),
                onClick = {
                    when {
                        s.dish != null && !s.dish.saved -> onAddRecipe(s.dish.recipe)
                        s.kind == SuggestionKind.FOOD -> onOpenTab("stock/RECIPES")
                        s.kind == SuggestionKind.SHOP -> onOpenTab("money")
                        s.kind == SuggestionKind.WATCH -> onOpenTab("plans/LISTS")
                        else -> onOpenTab("plans/PLACES")
                    }
                },
            ) {
                if (s.image != null) {
                    AsyncImage(
                        model = s.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    IconBlob(icon, accent, size = 40.dp)
                }
                Spacer(Modifier.weight(1f))
                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(s.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Posters with rating and where to watch in India. */
@Composable
private fun WatchRow(watch: List<WatchItem>, why: Map<String, String>, onWatched: (String) -> Unit) {
    val nav = LocalHomeNav.current
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(watch.size) { i ->
            val w = watch[i]
            Column(Modifier.width(112.dp).clip(RoundedCornerShape(18.dp)).clickable { nav.edit(EntryType.WISH, w.id) }) {
                Box(
                    Modifier
                        .size(width = 112.dp, height = 156.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8E6BFF), Color(0xFF5B3FD9)))),
                ) {
                    if (w.poster != null) {
                        AsyncImage(model = w.poster, contentDescription = w.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    } else {
                        Text(
                            w.title.take(1).uppercase(),
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    // Which list it's from, so movies, books and songs can sit in one row.
                    Text(
                        w.template.emoji,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    w.rating?.let { r ->
                        Text(
                            "★ %.1f".format(r),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    // A round ✓ on the poster: tap once it's watched (read, heard); Undo is in the snackbar.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(onClickLabel = w.template.markLabel) { onWatched(w.id) }
                            .semantics { contentDescription = "${w.template.markLabel}: ${w.title}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(w.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                why[w.title]?.let { Text("✨ $it", style = MaterialTheme.typography.bodySmall, color = LocalHouseColors.current.vault.color, maxLines = 3) }
                Text(
                    listOfNotNull(w.creator ?: w.kind, w.year).joinToString(" · ").ifEmpty { w.template.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Always one line, so the cards are the same height across posters.
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                Text(
                    if (w.youtubeUrl != null) "▶ Play on YouTube" else w.whereToWatch?.let { "▶ $it" } ?: " ",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalHouseColors.current.vault.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (w.youtubeUrl != null) Modifier.clickable { runCatching { uri.openUri(w.youtubeUrl!!) } } else Modifier,
                )
            }
        }
    }
}

/** A few gentle pushes: tidy what's gone stale, add what's missing. "Not today" puts it away till tomorrow. */
@Composable
private fun TidyCard(nudges: List<HomeNudge>, onDismiss: () -> Unit, modifier: Modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
    val nav = LocalHomeNav.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (nudges.any { it.tidy }) "Tidy up" else "Worth adding", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Not today") }
        }
        nudges.forEach { n ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.open(n.route) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(n.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Text(n.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.open(n.route) }) { Text(n.action) }
            }
        }
    }
}

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

/** What the rest of the family added lately, so there's something new each time Home opens. */
@Composable
private fun FamilyFeed(state: HomeUiState) {
    val nav = LocalHomeNav.current
    val now = System.currentTimeMillis()
    val feed = remember(state.data.recent, state.members) { com.houseaffairs.app.core.data.familyFeed(state.data.recent, state.myUid, now, state::nameOf) }
    if (feed.isEmpty()) return
    SectionTitle("What's new at home")
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        feed.forEachIndexed { i, line ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                    nav.open(
                        when (line.kind) {
                            ActivityKind.EXPENSE, ActivityKind.SUBSCRIPTION -> "money"
                            ActivityKind.SHOPPING -> "stock/SHOPPING"
                            ActivityKind.GROCERY -> "stock"
                            ActivityKind.TASK -> "plans/TASKS"
                            ActivityKind.LIST_ITEM -> "plans/LISTS"
                            ActivityKind.EVENT -> "plans/EVENTS"
                            ActivityKind.MEDICINE -> "vault/MEDICINES"
                        },
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Text(line.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(com.houseaffairs.app.core.data.agoText(line.whenMillis, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
