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
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.houseaffairs.app.core.util.HIDDEN_AMOUNT
import com.houseaffairs.app.core.util.formatCompact
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
import com.houseaffairs.app.core.data.buildToday
import com.houseaffairs.app.core.data.COMING_UP_COLLECTIONS
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
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
import com.houseaffairs.app.core.util.formatMinor
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
    viewModel: HomeViewModel = hiltViewModel(),
) {
    CompositionLocalProvider(LocalHomeNav provides HomeNav(onOpenTab, onEdit, onSaved)) {
        HomeContent(onQuickAdd, onOpenSettings, onOpenReview, onOpenTab, onAddRecipe, viewModel)
    }
}

@Composable
private fun HomeContent(
    onQuickAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenTab: (String) -> Unit,
    onAddRecipe: (Recipe) -> Unit,
    viewModel: HomeViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshNudge() }
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

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Hero(state, dayPart, onQuickAdd, onOpenReview, onOpenTab, viewModel::toggleHideAmounts) }
        if (state.filters.size > 2) item { FilterRow(state, viewModel::setFilter) }
        setupSteps(state, onQuickAdd, onOpenSettings) { onOpenTab("money") }.takeIf { steps -> steps.any { !it.done } }?.let { steps ->
            item { SetupCard(steps, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) }
        }
        nudge?.let { n ->
            item {
                NudgeCard(n, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), onClick = onOpenReview, onDismiss = viewModel::dismissNudge)
            }
        }
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
                HomeSection.TODAY -> item { val nav = LocalHomeNav.current; TodaySection(state, onQuickAdd, onBillPaid = { key -> viewModel.markBillPaid(key, nav.saved) }, onLogRenewal = { id -> viewModel.logRenewal(id, nav.saved) }, onRestock = { title -> viewModel.addToShopping(title, nav.saved) }) { id -> viewModel.completeTask(id, nav.saved) } }
                HomeSection.COMING_UP -> item { ComingUpSection(state) }
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
        CustomiseSheet(state.layout, viewModel::toggleSection, viewModel::moveSection) { customising = false }
    }
}

// ---------------------------------------------------------------------------------------------
// Filters, attention, coming up

@Composable
private fun FilterRow(state: HomeUiState, onSelect: (HomeFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 16.dp),
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

/** Tasks and alerts in one ranked list, so Home has a single place to look first. */
@Composable
private fun TodaySection(state: HomeUiState, onQuickAdd: () -> Unit, onBillPaid: (String) -> Unit, onLogRenewal: (String) -> Unit, onRestock: (String) -> Unit, onComplete: (String) -> Unit) {
    val nav = LocalHomeNav.current
    val tasks = if (state.canPlans && state.shows(Area.PLANS)) state.data.tasks else emptyList()
    val rows = buildToday(state.attention.filter { state.shows(it.area) }, tasks, LocalDate.now())
    var expanded by remember { mutableStateOf(false) }
    SectionTitle(
        "Today",
        action = if (rows.size > 5) (if (expanded) "Show less" else "Show all ${rows.size}") else null,
        onAction = { expanded = !expanded },
    )
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp).animateContentSize()) {
        if (rows.isEmpty()) {
            Row(Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onQuickAdd), verticalAlignment = Alignment.CenterVertically) {
                IconBlob(Icons.Outlined.AutoAwesome, LocalHouseColors.current.money, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("You're all caught up", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Tasks, expiring items and bills show up here. Try “pay rent tomorrow”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            (if (expanded) rows else rows.take(5)).forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                when (row) {
                    is TodayRow.Task -> {
                        val task = row.task
                        AttentionRow(
                            priority = row.priority,
                            area = Area.PLANS,
                            title = task.title,
                            detail = listOfNotNull(
                                task.priority.takeIf { it != "Low" }?.let { "$it priority" },
                                task.dueDate?.let { dueLabel(it).first },
                                task.assignee?.takeIf { it != state.myUid }?.let { "for ${state.nameOf(it)}" },
                            ).joinToString(" · ").ifEmpty { "No date" },
                            onClick = { nav.edit(EntryType.TASK, task.id) },
                        ) {
                            task.reach?.let { com.houseaffairs.app.core.ui.ReachButton(it, task.phone, task.title) }
                            TickCircle(LocalHouseColors.current.plans.color, onTick = { onComplete(task.id) }, contentDescription = "Done: ${task.title}")
                        }
                    }
                    is TodayRow.Alert -> {
                        val item = row.item
                        AttentionRow(
                            priority = item.priority,
                            area = item.area,
                            title = item.title,
                            detail = item.detail,
                            onClick = {
                                item.open?.let { return@AttentionRow nav.open(it) }
                                val type = collectionType(item.collection)
                                if (type != null) nav.edit(type, item.id.removePrefix(RENEWAL_PREFIX)) else nav.open("money")
                            },
                        ) {
                            // Card bills are marked paid right here; Money no longer has a card box.
                            val bill = item.id.removePrefix(BILL_PREFIX).takeIf { item.id.startsWith(BILL_PREFIX) }
                            val restock = item.restock
                            val renewal = item.id.removePrefix(RENEWAL_PREFIX).takeIf { item.id.startsWith(RENEWAL_PREFIX) }
                            if (bill != null) {
                                TextButton(onClick = { onBillPaid(bill) }) { Text("Paid") }
                            } else if (renewal != null) {
                                TextButton(onClick = { onLogRenewal(renewal) }) { Text("Log it") }
                            } else if (restock != null && state.data.shopping.none { com.houseaffairs.app.core.data.itemKey(it.name) == com.houseaffairs.app.core.data.itemKey(restock) }) {
                                TextButton(onClick = { onRestock(restock) }) { Text("+ List") }
                            } else {
                                Text(item.priority.label, style = MaterialTheme.typography.labelMedium, color = priorityColor(item.priority))
                            }
                        }
                    }
                }
            }
            if (tasks.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { nav.open("plans/TASKS") }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("All tasks (${tasks.size})")
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
    layout: List<SectionPref>,
    onToggle: (HomeSection) -> Unit,
    onMove: (HomeSection, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
            Text("Customise Home", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose what you see and in which order. Only on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    onQuickAdd: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenTab: (String) -> Unit,
    onToggleHide: () -> Unit,
) {
    HeroBackground(
        dayPart = dayPart,
        groundColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth().height(if (state.showStats) 340.dp else 300.dp),
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
            Spacer(Modifier.height(14.dp))
            if (state.showStats) {
                StatsStrip(state, onOpenTab, onToggleHide)
            } else {
                Text(summaryLine(state), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
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
    val today = LocalDate.now()
    val data = state.data
    val stats = buildList {
        if (state.canMoney) {
            val b = state.budget
            add(
                when {
                    b == null -> Stat("Set", "a budget", Icons.Outlined.AccountBalanceWallet, "money")
                    state.hideAmounts -> Stat(HIDDEN_AMOUNT, "budget left", Icons.Outlined.AccountBalanceWallet, "money")
                    b.leftMinor < 0 -> Stat(formatCompact(-b.leftMinor, b.currency), "over budget", Icons.Outlined.AccountBalanceWallet, "money")
                    else -> Stat(formatCompact(b.leftMinor, b.currency), "budget left", Icons.Outlined.AccountBalanceWallet, "money")
                },
            )
        }
        data.events.firstOrNull { !it.next.isAfter(today.plusDays(30)) }?.let { e ->
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, e.next)
            add(Stat(if (days == 0L) "Today" else "${days}d", e.title, Icons.Outlined.Cake, "plans/EVENTS"))
        }
        val expiring = (data.pantry + data.medicines).count { s -> s.expiry?.let { !it.isAfter(today.plusDays(3)) } == true }
        if ((state.canStock || state.canMedicine) && expiring > 0) add(Stat(expiring.toString(), "expiring soon", Icons.Outlined.Timer, "stock/PANTRY"))
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
        if (state.canMoney && data.monthSpentMinor > 0 && !state.hideAmounts) add("${formatMinor(data.monthSpentMinor, state.baseCurrency)} spent this month")
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
                "Add anything… “450 groceries”",
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
                GlanceCard(Icons.Outlined.AccountBalanceWallet, colors.money, "This month", formatMinor(data.monthSpentMinor, state.baseCurrency), onClick = { nav.open("money") }) {
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
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp).animateContentSize()) {
        if (upcoming.isEmpty()) {
            EmptyHint("Nothing in the next 30 days", "Add birthdays and renewals: “Riya birthday 12 Dec”", onClick = null)
        } else {
            upcoming.forEachIndexed { i, row ->
                if (i > 0) TimelineDivider()
                val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), row.date)
                TimelineRow(
                    date = row.date,
                    accent = areaAccent(row.area),
                    title = row.title,
                    detail = row.detail,
                    urgent = false,
                    onClick = { nav.edit(if (row.area == Area.MONEY) EntryType.SUBSCRIPTION else EntryType.EVENT, row.id) },
                ) {
                    Text(
                        when (days) {
                            0L -> "Today"
                            1L -> "Tomorrow"
                            else -> "in ${days}d"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = areaAccent(row.area).color,
                    )
                }
                // Its own tap target: the row edits the event, this opens its gift ideas.
                if (row.gifts > 0) {
                    Text(
                        "🎁 ${row.gifts} gift idea${if (row.gifts > 1) "s" else ""} ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = areaAccent(row.area).color,
                        modifier = Modifier.padding(start = 62.dp, top = 2.dp).clip(CircleShape).clickable { nav.open("plans/GIFTS") }.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
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
