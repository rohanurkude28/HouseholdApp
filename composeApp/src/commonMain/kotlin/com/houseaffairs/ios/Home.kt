package com.houseaffairs.ios

import com.houseaffairs.app.core.data.toggled
import com.houseaffairs.app.core.data.moved
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.ActivityKind
import com.houseaffairs.app.core.data.Area
import com.houseaffairs.app.core.data.BILL_PREFIX
import com.houseaffairs.app.core.data.CLEAR_PEEK
import com.houseaffairs.app.core.data.CardKind
import com.houseaffairs.app.core.data.Handoff
import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.data.MOMENT_PREFIX
import com.houseaffairs.app.core.data.Moment
import com.houseaffairs.app.core.data.MomentCard
import com.houseaffairs.app.core.data.MomentInputs
import com.houseaffairs.app.core.data.RENEWAL_PREFIX
import com.houseaffairs.app.core.data.TodayRow
import com.houseaffairs.app.core.data.buildAttention
import com.houseaffairs.app.core.data.buildToday
import com.houseaffairs.app.core.data.handoffOf
import com.houseaffairs.app.core.data.milestoneId
import com.houseaffairs.app.core.data.milestoneReached
import com.houseaffairs.app.core.data.momentCards
import com.houseaffairs.app.core.data.momentLine
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.time.plusDays
import com.houseaffairs.app.core.ui.AvatarStack
import com.houseaffairs.app.core.ui.DayPart
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.HandoffButtons
import com.houseaffairs.app.core.ui.LoadingRow
import com.houseaffairs.app.core.ui.TaskAnswers
import com.houseaffairs.app.core.ui.TickCircle
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.util.compactMoneyText
import com.houseaffairs.app.feature.home.AttentionRow
import com.houseaffairs.app.feature.home.CheerCard
import com.houseaffairs.app.feature.home.ComingUpStrip
import com.houseaffairs.app.feature.home.DeckButtons
import com.houseaffairs.app.feature.home.DeckCard
import com.houseaffairs.app.feature.home.DeckPager
import com.houseaffairs.app.feature.home.EndCard
import com.houseaffairs.app.feature.home.HomeHero
import com.houseaffairs.app.feature.home.LeftToClearTitle
import com.houseaffairs.app.feature.home.MilestoneCard
import com.houseaffairs.app.feature.home.TodayHeader
import com.houseaffairs.app.feature.home.areaAccent
import com.houseaffairs.app.feature.home.areaIcon
import com.houseaffairs.app.feature.home.comingUp
import com.houseaffairs.app.feature.home.greetingFor
import com.houseaffairs.app.feature.home.heroPeek
import com.houseaffairs.app.feature.home.taskDetail

private val homeRepo = HomeRepository()
private val homePantry = com.houseaffairs.app.core.data.PantryRepository()
private val homeThings = com.houseaffairs.app.core.data.ThingsRepository()
private val moneyRepo = com.houseaffairs.app.core.data.MoneyRepository()

private fun TodayRow.key(): String = when (this) {
    is TodayRow.Task -> task.id
    is TodayRow.Alert -> item.id
}

/**
 * The iPhone's Home, drawn by Android's pieces (`:ui` HomeParts): the sky hero with the day's
 * numbers and the Ask bar, the deck for this part of the day, Left to clear and Coming up.
 * Cards answered are remembered while the app is open (Android keeps them per phone).
 */
@Composable
internal fun HomeTab(
    s: SessionState.Ready,
    data: HomeData,
    store: FamilyStore,
    members: List<MemberInfo>,
    actions: Actions,
    onMe: () -> Unit,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String, String) -> Unit,
) {
    val today = today()
    val hour = nowMinute() / 60
    val moment = Moment.at(hour)
    val canMoney = s.me.canAccess(AppModule.MONEY)
    val canStock = s.me.canAccess(AppModule.STOCK)
    val canPlans = s.me.canAccess(AppModule.PLANS)
    val costsHidden = s.uid in s.household.hideCostsFrom
    val currency = s.household.baseCurrency
    // The same money rules as Android's Home (planned spends, subscriptions, trips), so "budget left" agrees.
    val moneyState by remember(s.household.id, s.uid) { if (canMoney) moneyRepo.observeMoney(s.household.id, s.uid) else kotlinx.coroutines.flow.flowOf(null) }.collectAsState(null)
    val budget = moneyState?.summary
    // Answered cards are kept on this phone for the day, as Android keeps them.
    val dayKey = today.toString()
    val handled = remember(dayKey) { mutableStateListOf<String>().apply { addAll(handledOn(dayKey)) } }
    LaunchedEffect(handled.toList()) { saveHandled(dayKey, handled.toSet()) }
    fun handle(id: String) { if (id !in handled) handled += id }
    // Days this phone opened Home, for the streak (as Android keeps them).
    val streak = remember(dayKey) {
        val days = loadPref("openedDays").orEmpty().split(",").filter { it.isNotBlank() }.toSet() + dayKey
        savePref("openedDays", com.houseaffairs.app.core.data.keptDays(days, today).sorted().joinToString(","))
        com.houseaffairs.app.core.data.streakOf(days, today)
    }
    // Customise: each section on or off and in this person's order, kept on this phone (as Android's).
    var layout by remember { mutableStateOf(com.houseaffairs.app.core.data.parseLayout(loadPref("homeLayout"))) }
    fun editLayout(next: List<com.houseaffairs.app.core.data.SectionPref>) { layout = next; savePref("homeLayout", com.houseaffairs.app.core.data.encodeLayout(next)) }
    // "-openTab HOME/Customise" (test runs only) opens the sheet.
    var customising by remember { mutableStateOf(startTab == "HOME/Customise") }
    fun shows(section: com.houseaffairs.app.core.data.HomeSection) = layout.any { it.visible && it.section == section }
    // This week's live-well nudge (the weakest part of the wheel), as Android's Home shows it.
    var nudge by remember { mutableStateOf<com.houseaffairs.app.core.data.Nudge?>(null) }
    LaunchedEffect(s.household.id, s.uid) { nudge = runCatching { weekly.load(s.household.id, s.member()).nudge }.getOrNull() }
    var nudgeHidden by remember(dayKey) { mutableStateOf(loadPref("nudgeDismissed") == dayKey) }
    var tidyHidden by remember(dayKey) { mutableStateOf(loadPref("tidyDismissed") == dayKey) }
    val nameOf: (String) -> String = { uid -> members.firstOrNull { it.uid == uid }?.displayName?.substringBefore(' ') ?: "someone" }

    val attention = remember(data, today, moneyState) { buildAttention(data, today, budget, moneyState?.cardBills.orEmpty(), money = { if (costsHidden) "•••" else formatMinor(it, currency) }) }
    val rows = remember(attention, data.tasks, actions.hidden.toList()) {
        buildToday(attention, if (canPlans) data.tasks.filter { it.id !in actions.hidden } else emptyList(), today)
    }
    // What the meal, eat-out and watch cards come from, read as Android's Home reads them.
    val pantry by remember(s.household.id) { if (canStock) homePantry.observePantry(s.household.id, s.uid) else kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(emptyList())
    val recipes by remember(s.household.id) { if (canStock) homePantry.observeRecipes(s.household.id, s.uid) else kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(emptyList())
    val lastCooked by remember(s.household.id) { if (canStock) homePantry.observeLastCooked(s.household.id, s.uid) else kotlinx.coroutines.flow.flowOf(emptyMap()) }.collectAsState(emptyMap())
    val wishes by remember(s.household.id) { if (canPlans) homeThings.observeWishes(s.household.id, s.uid) else kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(emptyList())
    val places by remember(s.household.id) { if (canPlans) homeThings.observePlaces(s.household.id, s.uid) else kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(emptyList())
    val watch by remember(s.household.id) { if (canPlans) homeThings.observeWatch(s.household.id, s.uid) else kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(emptyList())
    val ideas = remember(pantry, recipes, lastCooked, today) { com.houseaffairs.app.core.data.withHistory(com.houseaffairs.app.core.data.cookIdeas(pantry, recipes, today), lastCooked, today) }
    val now = remember(pantry, recipes, wishes, places, lastCooked, hour) {
        com.houseaffairs.app.core.data.rightNow(today, hour, pantry, recipes, wishes, places, shoppingLeft = null, lastCooked = lastCooked)
    }
    val cards = remember(rows, handled.toList(), moment, ideas, now, watch, places) {
        val food = now?.items?.firstOrNull { it.kind == com.houseaffairs.app.core.data.SuggestionKind.FOOD }?.let { com.houseaffairs.app.core.data.Idea(it.title, it.detail) }
        val rescue = ideas.firstOrNull()?.let { com.houseaffairs.app.core.data.Idea(it.recipe.title, it.rescues.firstOrNull()?.let { r -> "Uses $r before it goes off" } ?: "You have everything") }
        val base = inputs(s, data, rows, moment, today, canStock, canPlans)
        momentCards(
            moment,
            base.copy(
                // Dinner is when food about to expire gets rescued; other meals take what suits the hour.
                meal = (if (moment == Moment.EVENING) rescue ?: food else food ?: rescue).takeIf { canStock && moment.meal != null },
                // The eat-out list on weekends; any day, a place the family already loved.
                eatOut = now?.items?.firstOrNull { it.kind == com.houseaffairs.app.core.data.SuggestionKind.EAT_OUT }?.let { com.houseaffairs.app.core.data.Idea(it.title, it.detail) }
                    ?: com.houseaffairs.app.core.data.likedBefore(places, moment.meal, today),
                watch = watch.firstOrNull()?.takeIf { canPlans }?.let { com.houseaffairs.app.core.data.Idea(it.title, it.providers.firstOrNull() ?: it.platform ?: "On your watch list") },
            ),
            handled.toSet(),
        )
    }

    val answers = TaskAnswers(
        accept = { t -> actions.run("You'll do “${t.title}”") { val u = homeRepo.answerTask(s.household.id, s.uid, t, accept = true); ({ u() }) } },
        decline = { t, note -> actions.run("Sent “${t.title}” back") { val u = homeRepo.answerTask(s.household.id, s.uid, t, accept = false, note = note); ({ u() }) } },
        keep = { t -> homeRepo.keepTask(s.household.id, t) },
        reassign = { t -> onEdit("tasks", t.id) },
    )
    fun complete(row: TodayRow.Task) {
        handle(row.key())
        actions.run("${row.task.title} · done") { val u = homeRepo.completeTask(s.household.id, s.uid, row.task); ({ u(); handled.remove(row.key()) }) }
    }
    fun openRow(row: TodayRow) = when (row) {
        is TodayRow.Task -> onEdit("tasks", row.task.id)
        is TodayRow.Alert -> row.item.open?.let(onOpen) ?: row.item.collection?.let { onEdit(it, row.item.id.removePrefix(RENEWAL_PREFIX)) } ?: onOpen("money")
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            val peeks = if (data.loaded && shows(com.houseaffairs.app.core.data.HomeSection.STATS)) heroPeek(data, today, canMoney && !costsHidden, canStock, s.me.canAccess(AppModule.MEDICINE), budget?.leftMinor, hideAmounts = false) { compactMoneyText(it, budget?.currency ?: currency) } else null
            HomeHero(
                DayPart.at(hour), today, "${greetingFor(hour)}, ${s.me.displayName.substringBefore(' ').ifEmpty { "there" }}",
                if (data.loaded) momentLine(moment, cards) else "Getting your day ready…",
                peeks, onOpen, onAdd, onSearch,
            ) {
                // The family, tapped for who's in the home.
                Row(Modifier.clip(CircleShape).clickable(onClick = onMe).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarStack(members.map { it.displayName }, size = 28.dp, ring = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        // Still loading: no "All clear" before the day is known.
        if (!data.loaded) {
            item { LoadingRow() }
            return@LazyColumn
        }
        val todayPart: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = { item {
            val inDeck = cards.map { it.id }.toSet()
            val later = rows.filter { it.key() !in inDeck && it.key() !in handled }
            val cleared = handled.count { !it.startsWith(MOMENT_PREFIX) }
            val total = cleared + cards.count { it.kind == CardKind.THING } + later.size
            val cheered = cards.none { it.kind == CardKind.THING } && later.isEmpty()
            var expanded by remember { mutableStateOf(false) }
            Column(Modifier.padding(top = 18.dp)) {
                TodayHeader(moment, cleared, total, streak)
                Spacer(Modifier.height(10.dp))
                if (cheered) {
                    CheerCard(moment, cleared, today, onAdd, compact = cards.isNotEmpty())
                    if (cards.isNotEmpty()) Spacer(Modifier.height(14.dp))
                } else {
                    milestoneReached(cleared, handled.toSet())?.let { n ->
                        MilestoneCard(n, today) { handle(milestoneId(n)) }
                        Spacer(Modifier.height(14.dp))
                    }
                }
                if (!cheered || cards.isNotEmpty()) {
                    DeckPager(cards.map { it.id } + listOfNotNull("end".takeIf { !cheered })) { page ->
                        val card = cards.getOrNull(page)
                        if (card == null) EndCard(moment, answered = handled.isNotEmpty(), onAdd)
                        else Deck(card, "${page + 1} of ${cards.size}", rows.firstOrNull { it.key() == card.id }, s, answers, onOpen, onAdd, { handle(card.id) }, ::complete, ::openRow)
                    }
                }
                // Everything else that's open, always in view, each one a tap from cleared.
                if (later.isNotEmpty()) {
                    LeftToClearTitle(cleared, later.size)
                    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        val shown = if (expanded) later else later.take(CLEAR_PEEK)
                        shown.forEachIndexed { i, row ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            when (row) {
                                is TodayRow.Task -> {
                                    AttentionRow(row.priority, Area.PLANS, row.task.title, taskDetail(row.task, s.uid, today, nameOf), onClick = { openRow(row) }) {
                                        TickCircle(LocalHouseColors.current.plans.color, onTick = { complete(row) }, contentDescription = "Done: ${row.task.title}")
                                    }
                                    HandoffButtons(row.task, s.uid, answers, Modifier.padding(start = 56.dp, top = 6.dp))
                                }
                                is TodayRow.Alert -> AttentionRow(row.item.priority, row.item.area, row.item.title, row.item.detail, onClick = { openRow(row) }) {
                                    TextButton(onClick = { handle(row.key()) }) { Text("Got it") }
                                }
                            }
                        }
                        if (later.size > CLEAR_PEEK) {
                            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(if (expanded) "Show less" else "Show all ${later.size}") }
                        }
                    }
                }
            }
        } }
        // Tidy up or worth adding; "Not today" puts it away till tomorrow.
        val nudges = com.houseaffairs.app.feature.home.homeNudges(data, today, canMoney, canStock, s.me.canAccess(AppModule.MEDICINE), canPlans, hasBudget = budget != null)
        // For you: what suits this moment, what to cook, what to watch, what to buy (as Android's).
        val forYou = com.houseaffairs.app.feature.home.forYouTabs(layout, now, kitchen = canStock, plans = canPlans)
        var forYouShown = false
        // Sections in the order (and on/off) this person chose in Customise.
        layout.filter { it.visible }.forEach { pref ->
            when (pref.section) {
                com.houseaffairs.app.core.data.HomeSection.TODAY -> todayPart()
                // Setup, the live-well nudge and tidying up share one swipeable row, as on Android.
                com.houseaffairs.app.core.data.HomeSection.TIDY -> item {
                    val steps = com.houseaffairs.app.feature.home.setupSteps(
                        s.me.role == com.houseaffairs.app.core.model.Role.OWNER, members.size, data.recent.isNotEmpty(), budget != null,
                        onInvite = { onOpen("settings") }, onAdd = onAdd, onBudget = { onOpen("money") },
                    ).takeIf { list -> list.any { !it.done } }
                    com.houseaffairs.app.feature.home.WorthALookRow(
                        buildList {
                            steps?.let { add { m -> com.houseaffairs.app.feature.home.SetupCard(it, m) } }
                            nudge?.takeIf { !nudgeHidden }?.let { n -> add { m -> com.houseaffairs.app.feature.review.NudgeCard(n, m, onClick = onMe, onDismiss = { nudgeHidden = true; savePref("nudgeDismissed", dayKey) }) } }
                            if (!tidyHidden && nudges.isNotEmpty()) add { m ->
                                com.houseaffairs.app.feature.home.TidyCard(nudges, onDismiss = { tidyHidden = true; savePref("tidyDismissed", dayKey) }, onOpen = onOpen, modifier = m)
                            }
                        },
                    )
                }
                com.houseaffairs.app.core.data.HomeSection.COMING_UP -> item {
                    Spacer(Modifier.height(8.dp))
                    ComingUpStrip(
                        comingUp(data, attention, today, canPlans, canMoney && !costsHidden), today,
                        onOpen = { row -> onEdit(if (row.area == Area.MONEY) "recurring" else "events", row.id) },
                        onGifts = { onOpen("plans") },
                    )
                }
                // What the rest of the family added lately, once there's more than one person.
                com.houseaffairs.app.core.data.HomeSection.FAMILY -> if (members.size > 1) item { Spacer(Modifier.height(8.dp)); com.houseaffairs.app.feature.home.FamilyFeedCard(data.recent, s.uid, nameOf, onOpen) }
                com.houseaffairs.app.core.data.HomeSection.RIGHT_NOW, com.houseaffairs.app.core.data.HomeSection.USE_IT_UP,
                com.houseaffairs.app.core.data.HomeSection.WATCH, com.houseaffairs.app.core.data.HomeSection.SHOPPING ->
                    if (!forYouShown && forYou.isNotEmpty()) {
                        forYouShown = true
                        item {
                            com.houseaffairs.app.feature.home.ForYouCard(
                                forYou,
                                com.houseaffairs.app.feature.home.ForYouData(now, ideas, emptyMap(), watch.filter { it.id !in actions.hidden }, emptyMap(), data.shopping.filter { it.id !in actions.hidden }, data.pantry),
                                today,
                                com.houseaffairs.app.feature.home.ForYouActions(
                                    openTab = onOpen,
                                    addRecipe = { onOpen("stock/RECIPES") },
                                    editWish = { onEdit("listItems", it) },
                                    editShopping = { onEdit("shopping", it) },
                                    watched = { id -> val w = watch.firstOrNull { it.id == id }; actions.run("${w?.title ?: "Item"} · done") { val undo = homeThings.markDone(s.household.id, id); ({ undo() }) } },
                                    bought = { id -> val item = data.shopping.firstOrNull { it.id == id }; actions.run("${item?.name ?: "Item"} · bought, now in the pantry") { val b = homePantry.markBought(s.household.id, s.uid, id); ({ b.undo() }) } },
                                ),
                            )
                        }
                    }
                // The numbers sit in the hero; "At a glance" is Android's.
                else -> Unit
            }
        }
        item {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = { customising = true }) { Text("⚙️  Customise Home") }
            }
        }
    }
    if (customising) {
        com.houseaffairs.app.feature.home.CustomiseSheet(
            // "At a glance" is Android's only, so it isn't offered here.
            layout.filter { it.section != com.houseaffairs.app.core.data.HomeSection.GLANCE },
            onToggle = { editLayout(layout.toggled(it)) },
            onMove = { section, up -> editLayout(layout.moved(section, up)) },
            onDismiss = { customising = false },
        )
    }
}

/** What Home knows right now, as the shared deck rule wants it (Android's momentInputs, without meals and watching yet). */
private fun inputs(s: SessionState.Ready, data: HomeData, rows: List<TodayRow>, moment: Moment, today: com.houseaffairs.app.core.time.LocalDate, canStock: Boolean, canPlans: Boolean): MomentInputs {
    // Someone else's task isn't mine to finish, so it never gets a card.
    val mine = rows.filter { it !is TodayRow.Task || it.task.assignee == null || it.task.assignee == s.uid }
    val startOfDay = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - nowMinute() * 60_000L
    val tomorrow = today.plusDays(1)
    return MomentInputs(
        things = mine.map { row ->
            when (row) {
                is TodayRow.Task -> MomentCard(row.key(), CardKind.THING, row.priority?.label ?: "When you can", row.task.title, taskDetail(row.task, s.uid, today) { "someone" })
                is TodayRow.Alert -> MomentCard(row.key(), CardKind.THING, row.item.priority.label, row.item.title, row.item.detail)
            }
        },
        toBuy = if (canStock) data.shopping.map { it.name } else emptyList(),
        spentToday = data.recent.any { it.kind == ActivityKind.EXPENSE && it.createdBy == s.uid && (it.createdAtMillis ?: startOfDay) >= startOfDay },
        tomorrow = if (canPlans) data.tasks.filter { it.dueDate == tomorrow && (it.assignee == null || it.assignee == s.uid) }.map { it.title } + data.events.filter { it.next == tomorrow }.map { it.title } else emptyList(),
    )
}

/** One deck card with the buttons Android gives it. */
@Composable
private fun Deck(
    card: MomentCard,
    position: String,
    row: TodayRow?,
    s: SessionState.Ready,
    answers: TaskAnswers,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    dismiss: () -> Unit,
    complete: (TodayRow.Task) -> Unit,
    openRow: (TodayRow) -> Unit,
) {
    val colors = LocalHouseColors.current
    val area = when (row) { is TodayRow.Task -> Area.PLANS; is TodayRow.Alert -> row.item.area; null -> null }
    val (emoji, accent) = when (card.kind) {
        CardKind.THING -> "" to areaAccent(area ?: Area.PLANS)
        CardKind.MEAL -> "🍳" to colors.stock
        CardKind.EAT_OUT -> "🍽️" to colors.plans
        CardKind.SHOPPING -> "🛒" to colors.stock
        CardKind.SPENDS -> "💸" to colors.money
        CardKind.WATCH -> "🎬" to colors.vault
        CardKind.TOMORROW -> "📅" to colors.plans
    }
    fun go(route: String): () -> Unit = { dismiss(); onOpen(route) }
    var later = "Not now"
    val main: Pair<String, () -> Unit>? = when (card.kind) {
        CardKind.MEAL -> "Let's cook" to go("stock/RECIPES")
        CardKind.EAT_OUT -> "See places" to go("plans")
        CardKind.SHOPPING -> "Open the list" to go("stock/SHOPPING")
        CardKind.SPENDS -> { later = "Nothing today"; "Yes, add it" to onAdd }
        CardKind.WATCH -> "Open lists" to go("plans")
        CardKind.TOMORROW -> { later = ""; "Got it" to dismiss }
        CardKind.THING -> {
            later = "Not today"
            when (row) {
                is TodayRow.Task -> when (handoffOf(row.task, s.uid)) {
                    Handoff.ASKED_ME, Handoff.CAME_BACK -> null
                    else -> "Done ✓" to { complete(row) }
                }
                // Bills and renewals open where they're paid; anything else is just to know.
                is TodayRow.Alert -> if (row.item.id.startsWith(BILL_PREFIX) || row.item.id.startsWith(RENEWAL_PREFIX)) "Open" to { openRow(row) } else { later = ""; "Got it" to dismiss }
                null -> { later = ""; "Got it" to dismiss }
            }
        }
    }
    DeckCard(card.eyebrow, position, card.title, card.detail, accent, emoji, if (emoji.isEmpty()) areaIcon(area ?: Area.PLANS) else null, row?.let { r -> { openRow(r) } }) {
        if (main == null && row is TodayRow.Task) HandoffButtons(row.task, s.uid, answers)
        else if (main != null) DeckButtons(main.first, main.second, accent, later, dismiss)
    }
}
