package com.houseaffairs.app.feature.money

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import com.houseaffairs.app.core.data.limitFor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import com.houseaffairs.app.core.data.InvestmentValue
import com.houseaffairs.app.core.data.revaluation
import com.houseaffairs.app.core.util.formatCompact
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Luggage
import com.houseaffairs.app.core.data.isOngoing
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.input.KeyboardType
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.Pocket
import com.houseaffairs.app.core.data.available
import com.houseaffairs.app.core.util.toMinorUnits
import com.houseaffairs.app.feature.add.SavedEntry
import com.houseaffairs.app.feature.add.EntryType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.houseaffairs.app.core.data.Fit
import com.houseaffairs.app.core.data.upcomingFit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.houseaffairs.app.core.data.BudgetSummary
import com.houseaffairs.app.core.data.CategoryStatus
import com.houseaffairs.app.core.data.Pace
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.SectionTitle
import com.houseaffairs.app.core.ui.categoryIcon
import com.houseaffairs.app.core.util.HIDDEN_AMOUNT
import com.houseaffairs.app.core.util.formatMinor
import com.houseaffairs.app.feature.add.formatDate
import java.time.format.DateTimeFormatter

/** The Money tab: what's left this budget month, where it went, and what's coming. */
/** Money's three views: the month, where savings are, and every expense. */
// Expenses live inside Month and Year (searchable, for that period), not in a tab of their own.
enum class MoneyTab(val label: String) { MONTH("Month"), YEAR("Year"), SAVINGS("Savings") }

@Composable
fun MoneyScreen(
    onAddExpense: () -> Unit,
    onPlanSpend: () -> Unit,
    onAddSubscription: () -> Unit,
    onAddCredit: () -> Unit,
    onNewGullak: () -> Unit,
    onSaved: (SavedEntry) -> Unit,
    onEdit: (EntryType, String) -> Unit,
    requestedTab: String? = null,
    onTabShown: () -> Unit = {},
    /** Another tab, e.g. "plans/DREAM:<id>" to open a dream on the vision board. */
    onOpen: (String) -> Unit = {},
    viewModel: MoneyViewModel = hiltViewModel(),
) {
    val names by viewModel.names.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Pair<Pocket, String>?>(null) }
    // A category opened for its whole history: "where did the grocery money go?".
    var category by remember { mutableStateOf<String?>(null) }
    category?.let { c ->
        state.money?.let { m ->
            CategorySheet(
                com.houseaffairs.app.core.data.categoryReport(m.txns, c, java.time.LocalDate.now()),
                { com.houseaffairs.app.core.util.formatMinor(it, state.currency) },
                onOpen = { id -> category = null; onEdit(EntryType.EXPENSE, id) },
                onDismiss = { category = null },
            )
        }
    }
    // A goal gullak's "Add money" opens the move sheet with that goal already picked.
    var moveTarget by remember { mutableStateOf<Pocket?>(null) }
    var tab by rememberSaveable { mutableStateOf(MoneyTab.MONTH) }
    var pickedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    // Which budget month Month shows: 0 = this one, 1 = last month…
    var monthIdx by rememberSaveable { mutableStateOf(0) }
    // The category the year's line is drawn for; null is all spending.
    var yearCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var allEnvelopes by remember { mutableStateOf(false) }
    LaunchedEffect(requestedTab) {
        MoneyTab.entries.firstOrNull { it.name == requestedTab }?.let { tab = it; onTabShown() }
    }
    val summary = state.summary
    val money: (Long) -> String = { if (state.hideAmounts) HIDDEN_AMOUNT else formatMinor(it, state.currency) }

    val listState = com.houseaffairs.app.core.ui.rememberTopListState(tab)
    // "Planned spend" in Coming up goes to Month's "Planned & upcoming": the header, the tab
    // chips, the month switcher, the budget and the add chips come before it.
    var showPlanned by remember { mutableStateOf(0) }
    LaunchedEffect(showPlanned) { if (showPlanned > 0) listState.animateScrollToItem(5) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Money", style = MaterialTheme.typography.headlineLarge)
                    summary?.let {
                        val fmt = DateTimeFormatter.ofPattern("d MMM")
                        Text(
                            "${it.cycle.start.format(fmt)} – ${it.cycle.endExclusive.minusDays(1).format(fmt)} · day ${it.day} of ${it.cycle.days}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = viewModel::toggleHide) {
                    Icon(
                        if (state.hideAmounts) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (state.hideAmounts) "Show amounts" else "Hide amounts",
                    )
                }
                if (state.budget != null) {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit budget") }
                }
            }
        }

        when {
            state.loading -> Unit
            summary == null -> item { SetupPrompt(onSetUp = { editing = true }) }
            else -> {
                item {
                    com.houseaffairs.app.core.ui.SubTabs(MoneyTab.entries, tab, { it.label }, { tab = it }, Modifier.padding(bottom = 4.dp))
                }
                val m = state.money
                when (tab) {
                    MoneyTab.MONTH -> {
                        val today = java.time.LocalDate.now()
                        val months = m?.let { com.houseaffairs.app.core.data.cyclesSince(it.startedOn, today, it.budget.cycleStartDay) }.orEmpty()
                        val cycle = months.getOrNull(monthIdx) ?: summary.cycle
                        val current = monthIdx == 0
                        // Earlier months: a small "‹ October ›" switcher, only once there is an earlier month.
                        if (months.size > 1) item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { monthIdx++ }, enabled = monthIdx < months.size - 1) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Earlier month") }
                                Text(monthLabel(cycle), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                IconButton(onClick = { monthIdx-- }, enabled = monthIdx > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Later month") }
                            }
                        } else item { Spacer(Modifier.height(0.dp)) }
                        if (current) {
                            item { BudgetHero(summary, money, state.money?.envelopes?.potMinor ?: 0) { moving = Pocket.Pot to "Gullak" } }
                            item {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ActionChip("Expense", Icons.Outlined.Add, onAddExpense)
                                    ActionChip("Plan a spend", Icons.Outlined.EventNote, onPlanSpend)
                                    ActionChip("Subscription", Icons.Outlined.Autorenew, onAddSubscription)
                                    ActionChip("Gift card / coupon", Icons.Outlined.Redeem, onAddCredit)
                                }
                            }
                            item { SectionTitle("Planned & upcoming", action = "Plan", onAction = onPlanSpend) }
                            item {
                                UpcomingCard(summary, m?.envelopes?.envelopes.orEmpty(), money, viewModel::markPaid) { u ->
                                    // A trip's reserve isn't a saved expense: it opens the trip itself.
                                    val trip = u.id.removePrefix(com.houseaffairs.app.core.data.TRIP_RESERVE).takeIf { it != u.id }
                                    if (trip != null) onEdit(EntryType.TRIP, trip)
                                    else onEdit(if (u.planned) EntryType.EXPENSE else EntryType.SUBSCRIPTION, u.id)
                                }
                            }
                        }
                        if (m != null) {
                            item { SectionTitle("Where the money went") }
                            item { MonthOverviewCard(m, money, cycle, onOpen = { onEdit(EntryType.EXPENSE, it) }, onYear = { tab = MoneyTab.YEAR }) }
                            item { SectionTitle("Expenses") }
                            item { ExpensesSearch(m, money, cycle, onAdd = onAddExpense, names = names) { onEdit(EntryType.EXPENSE, it) } }
                        }
                    }
                    MoneyTab.SAVINGS -> if (m != null) {
                        item { SectionTitle("Your gullaks", action = "+ New", onAction = onNewGullak) }
                        item {
                            SavingsCard(
                                m, money,
                                onMove = { moving = Pocket.Pot to "Gullak" },
                                onFill = { dream -> moveTarget = Pocket.Dream(dream.id); moving = Pocket.Pot to "Gullak" },
                                onNew = onNewGullak,
                            )
                        }
                        item { SectionTitle("Envelopes", action = if (m.envelopes.envelopes.size > 4) (if (allEnvelopes) "Show less" else "Show all ${m.envelopes.envelopes.size}") else null, onAction = { allEnvelopes = !allEnvelopes }) }
                        item {
                            Text(
                                "This month's limit per category, tightest first. Whatever's left drops into your gullak on ${formatDate(summary.cycle.endExclusive)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                            )
                        }
                        item { EnvelopesCard(m, summary.categories, money, showAll = allEnvelopes, onCategory = { category = it }) { pocket, label -> moving = pocket to label } }
                        item { SectionTitle("Gift cards & coupons", action = "Add", onAction = onAddCredit) }
                        item { CreditsCard(m, money, viewModel::creditUsed) { onEdit(EntryType.CREDIT, it) } }
                        if (m.moves.isNotEmpty()) {
                            item { SectionTitle("Money moved") }
                            item { MovesCard(m, money) { viewModel.undoMove(it)?.let(onSaved) } }
                        }
                    }
                    MoneyTab.YEAR -> if (m != null) {
                        val outlook = com.houseaffairs.app.core.data.yearOutlook(
                            m.budget, m.startedOn, m.txns, m.dreams.map { it.dream }, m.trips.map { it.trip }, java.time.LocalDate.now(), money,
                        )
                        val years = com.houseaffairs.app.core.data.moneyYears(m.txns, m.budget, m.startedOn, java.time.LocalDate.now())
                        val year = years.firstOrNull { it.year == pickedYear } ?: years.firstOrNull()
                        item { val accent = com.houseaffairs.app.core.designsystem.LocalHouseColors.current.money; com.houseaffairs.app.core.ui.YearChips(years.map { it.year }, year?.year, { pickedYear = it }, accent, Modifier.padding(top = 8.dp)) }
                        // The same shape as every year view: the year's totals, then month by month.
                        item {
                            val accent = com.houseaffairs.app.core.designsystem.LocalHouseColors.current.money
                            com.houseaffairs.app.core.ui.YearSummaryCard(accent, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                if (year == null || year.year == java.time.LocalDate.now().year) YearOutlookSummary(outlook, money)
                                else {
                                    Text("${year.year}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${money(year.spentMinor)} spent", style = MaterialTheme.typography.headlineMedium)
                                    Text(
                                        (year.budgetMinor - year.spentMinor).let { if (it >= 0) "${money(it)} under the budget" else "${money(-it)} over the budget" } + " · ${year.count} expenses",
                                        style = MaterialTheme.typography.bodyMedium, color = accent.color,
                                    )
                                    Text("Most on " + year.topCategories().joinToString(" · ") { (c, n) -> "$c ${money(n)}" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                        if (year != null) {
                            val months = year.months.reversed()
                            // Where the year's money is going: the year as a line against its target, for
                            // all spending or one category picked from the chips.
                            item(key = "money-line-${year.year}") {
                                val names = remember(m.txns, year.year, m.budget) {
                                    (m.txns.filter { !it.planned && it.date.year == year.year }.groupBy { it.category }.entries.sortedByDescending { e -> e.value.sumOf { it.amountMinor } }.map { it.key } +
                                        m.budget.categories.filterValues { it > 0 }.keys).distinct()
                                }
                                HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                                    Text("Where the money is going", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        (listOf<String?>(null) + names).forEach { c ->
                                            FilterChip(selected = yearCategory == c, onClick = { yearCategory = c }, label = { Text(c ?: "All") }, shape = CircleShape)
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    val today = java.time.LocalDate.now()
                                    val pace = com.houseaffairs.app.core.data.yearPace(m.txns, year.year, today, m.budget, m.startedOn, yearCategory)
                                    val monthName = { n: Int -> java.time.Month.of(n).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
                                    PaceChart(pace, money, line = com.houseaffairs.app.core.data.yearLine(pace, money, monthName), startLabel = "Jan", endLabel = "Dec")
                                }
                            }
                            val period = com.houseaffairs.app.core.data.yearPeriod(year.year)
                            val bars = com.houseaffairs.app.core.data.categoryBars(m.txns, period, m.budget.categories, months = year.months.size)
                            if (bars.isNotEmpty()) {
                                item { SectionTitle("By category") }
                                item { HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { CategoryBars(bars, money) { category = it } } }
                            }
                            item { SectionTitle("Expenses") }
                            item(key = "money-expenses-${year.year}") { ExpensesSearch(m, money, period, onAdd = onAddExpense, names = names) { onEdit(EntryType.EXPENSE, it) } }
                        }
                        if (year == null || year.year == java.time.LocalDate.now().year) item {
                            // Each goes to where it lives: planned spends to This month, dreams and trips to the vision board.
                            ComingUpCard(outlook, money) { b ->
                                when (b.kind) {
                                    "Dream" -> onOpen("plans/DREAM:${b.id}")
                                    "Trip" -> onOpen("plans/DREAMS")
                                    else -> { tab = MoneyTab.MONTH; monthIdx = 0; showPlanned++ }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    moving?.let { (from, label) ->
        state.money?.let { m ->
            MoveSheet(m, from, label, state.currency, preset = moveTarget, onMove = { to, toLabel, amount ->
                viewModel.move(from, label, to, toLabel, amount)?.let(onSaved)
                moving = null
                moveTarget = null
            }, onDismiss = { moving = null; moveTarget = null })
        }
    }

    if (editing) {
        BudgetSetupSheet(
            existing = state.budget,
            dreams = state.money?.dreams?.map { it.dream }.orEmpty(),
            currency = state.currency,
            onSave = { budget, from -> viewModel.saveBudget(budget, from) { editing = false } },
            onDismiss = { editing = false },
            onCategoryUse = viewModel::categoryUse,
            onMoveCategory = viewModel::moveCategory,
            onDeleteWithHistory = { name, done -> viewModel.deleteCategoryWithHistory(name, onSaved, done) },
        )
    }
    state.error?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun PaidCard(summary: BudgetSummary, money: (Long) -> String, onOpen: (String) -> Unit) {
    val accent = LocalHouseColors.current.money
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (summary.paid.isEmpty()) {
            Text("Nothing spent yet this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        summary.paid.take(12).forEachIndexed { index, t ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            Row(Modifier.clip(MaterialTheme.shapes.small).clickable { onOpen(t.id) }, verticalAlignment = Alignment.CenterVertically) {
                IconBlob(categoryIcon(t.category), accent, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.category} · ${formatDate(t.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(t.amountMinor), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun PotCard(m: MoneyState, money: (Long) -> String, onMove: () -> Unit) {
    val colors = LocalHouseColors.current
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), color = colors.vault.container) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBlob(Icons.Outlined.Savings, Accent(Color.White, colors.vault.color), size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Gullak", style = MaterialTheme.typography.labelLarge, color = colors.vault.color)
                Text(money(m.envelopes.potMinor), style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${money(m.envelopes.savedThisYearMinor)} unused this year · this month's leftovers join on ${formatDate(m.summary.cycle.endExclusive)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (m.envelopes.potMinor > 0) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onMove, shape = CircleShape) { Text("Move to a dream or envelope") }
        }
    }
}
