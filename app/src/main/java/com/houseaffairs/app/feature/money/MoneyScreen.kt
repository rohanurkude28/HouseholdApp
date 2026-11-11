package com.houseaffairs.app.feature.money

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
enum class MoneyTab(val label: String) { MONTH("This month"), YEAR("This year"), SAVINGS("Savings"), EXPENSES("Expenses") }

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
    var allEnvelopes by remember { mutableStateOf(false) }
    LaunchedEffect(requestedTab) {
        MoneyTab.entries.firstOrNull { it.name == requestedTab }?.let { tab = it; onTabShown() }
    }
    val summary = state.summary
    val money: (Long) -> String = { if (state.hideAmounts) HIDDEN_AMOUNT else formatMinor(it, state.currency) }

    val listState = com.houseaffairs.app.core.ui.rememberTopListState(tab)
    // "Planned spend" in Coming up goes to This month's "Planned & upcoming": the header, the tab
    // chips, the budget and the add chips come before it, so it's the fifth item.
    var showPlanned by remember { mutableStateOf(0) }
    LaunchedEffect(showPlanned) { if (showPlanned > 0) listState.animateScrollToItem(4) }
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
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        items(MoneyTab.entries.size) { i ->
                            val t = MoneyTab.entries[i]
                            FilterChip(
                                selected = tab == t,
                                onClick = { tab = t },
                                label = { Text(t.label) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                            )
                        }
                    }
                }
                val m = state.money
                when (tab) {
                    MoneyTab.MONTH -> {
                        item { BudgetHero(summary, money) }
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
                                onEdit(if (u.planned) EntryType.EXPENSE else EntryType.SUBSCRIPTION, u.id)
                            }
                        }
                        if (m != null) {
                            item { SectionTitle("Where the money went", action = "All expenses", onAction = { tab = MoneyTab.EXPENSES }) }
                            item { MonthOverviewCard(m, money, onOpen = { onEdit(EntryType.EXPENSE, it) }, onYear = { tab = MoneyTab.YEAR }) }
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
                    MoneyTab.EXPENSES -> if (m != null) {
                        item { ExpensesSearch(m, money, onAdd = onAddExpense) { onEdit(EntryType.EXPENSE, it) } }
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
                                    MonthBars(year.months.reversed().map { Triple(it.overview.cycle.start.format(java.time.format.DateTimeFormatter.ofPattern("MMM")), it.overview.totalMinor, it.budgetMinor) })
                                }
                            }
                        }
                        if (year != null) item(key = "money-months-${year.year}") {
                            com.houseaffairs.app.core.ui.MonthByMonth(
                                year.months.mapIndexed { i, mm ->
                                    com.houseaffairs.app.core.ui.MonthRow(
                                        mm.overview.cycle.start, monthLabel(mm.overview.cycle),
                                        "${money(mm.overview.totalMinor)} of ${money(mm.budgetMinor)}" + " · ${mm.overview.count} expenses" +
                                            if (mm.overview.totalMinor > mm.budgetMinor) " · over" else "",
                                    ) { MonthOverviewBody(mm.overview, money, current = i == 0 && year.year == java.time.LocalDate.now().year) { onEdit(EntryType.EXPENSE, it) } }
                                },
                            )
                        }
                        if (year == null || year.year == java.time.LocalDate.now().year) item {
                            // Each goes to where it lives: planned spends to This month, dreams and trips to the vision board.
                            ComingUpCard(outlook, money) { b ->
                                when (b.kind) {
                                    "Dream" -> onOpen("plans/DREAM:${b.id}")
                                    "Trip" -> onOpen("plans/DREAMS")
                                    else -> { tab = MoneyTab.MONTH; showPlanned++ }
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
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

@Composable
private fun SetupPrompt(onSetUp: () -> Unit) {
    val money = LocalHouseColors.current.money
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), color = money.container) {
        IconBlob(Icons.Outlined.AccountBalanceWallet, Accent(Color.White, money.color), size = 52.dp)
        Spacer(Modifier.height(16.dp))
        Text("Set your monthly budget", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Say ₹1,00,000 a month, starting on salary day. Every expense, planned spend and subscription counts against it, so you always know what's left.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSetUp, shape = CircleShape) { Text("Set budget") }
    }
}

/** Big "left this month" number over a stacked bar of spent / planned / due / left. */
@Composable
private fun BudgetHero(summary: BudgetSummary, money: (Long) -> String) {
    val gradient = Brush.linearGradient(listOf(Color(0xFF0E9F76), Color(0xFF1A7FA0)))
    val over = summary.leftMinor < 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(gradient)
            .padding(24.dp),
    ) {
        Text(if (over) "Over budget by" else "Left this month", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
        Text(money(kotlin.math.abs(summary.leftMinor)), style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text("of ${money(summary.limitMinor)}", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(18.dp))

        val total = summary.limitMinor.coerceAtLeast(1).toFloat()
        val parts = listOf(
            summary.spentMinor to Color.White,
            summary.plannedMinor to Color.White.copy(alpha = 0.6f),
            summary.committedMinor to Color.White.copy(alpha = 0.35f),
        )
        Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            parts.forEach { (amount, color) ->
                if (amount > 0) Box(Modifier.weight((amount / total).coerceIn(0.01f, 1f)).fillMaxHeight().background(color))
            }
            val rest = 1f - parts.sumOf { it.first.toDouble() }.toFloat() / total
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("Spent", money(summary.spentMinor), Color.White)
            Legend("Planned", money(summary.plannedMinor), Color.White.copy(alpha = 0.6f))
            Legend("Due", money(summary.committedMinor), Color.White.copy(alpha = 0.35f))
        }
        Spacer(Modifier.height(16.dp))
        // Whole currency units per day: "₹1,698 a day" reads better than "₹1,698.21".
        val perDay = (summary.safeToSpendMinor / summary.daysLeft.coerceAtLeast(1)).coerceAtLeast(0).let { it - it % 100 }
        val line = when {
            over -> "Spending is above the budget. Check categories below."
            summary.safeToSpendMinor < 0 -> "On budget, but ${money(-summary.safeToSpendMinor)} short of your savings goal."
            summary.pace == Pace.AHEAD -> "Spending faster than planned. ${money(perDay)} a day keeps you on track."
            else -> "On track. ${money(perDay)} a day for ${summary.daysLeft} days" +
                if (summary.savingsGoalMinor > 0) " keeps ${money(summary.savingsGoalMinor)} saved." else "."
        }
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Legend(label: String, value: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

/** Planned spends and bills in date order, each marked by whether the month can still carry it. */
@Composable
private fun UpcomingCard(
    summary: BudgetSummary,
    envelopes: List<com.houseaffairs.app.core.data.Envelope>,
    money: (Long) -> String,
    onMarkPaid: (String) -> Unit,
    onOpen: (com.houseaffairs.app.core.data.UpcomingSpend) -> Unit,
) {
    val accent = LocalHouseColors.current.money
    val coral = LocalHouseColors.current.coral
    val error = MaterialTheme.colorScheme.error
    val fits = upcomingFit(summary, envelopes)
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (fits.isEmpty()) {
            Text(
                "Nothing planned. Use “Plan a spend” for trips, gifts or big buys and see if they fit before they happen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@HaCard
        }
        // The verdict for the whole list first.
        val last = fits.last()
        val (headline, color) = when (last.fit) {
            Fit.FITS -> "All of it fits. ${money(last.leftAfterMinor)} left after, savings goal kept." to accent.color
            Fit.EATS_SAVINGS -> "Fits, but leaves ${money(last.leftAfterMinor)}: ${money(summary.savingsGoalMinor - last.leftAfterMinor)} short of the savings goal." to coral
            Fit.OVER -> "${money(-last.leftAfterMinor)} more than this month's budget." to error
        }
        Text(headline, style = MaterialTheme.typography.titleSmall, color = color)
        Spacer(Modifier.height(12.dp))
        fits.forEachIndexed { index, f ->
            val u = f.spend
            if (index > 0) Spacer(Modifier.height(14.dp))
            val rowColor = when (f.fit) { Fit.FITS -> accent.color; Fit.EATS_SAVINGS -> coral; Fit.OVER -> error }
            Row(Modifier.clip(MaterialTheme.shapes.small).clickable { onOpen(u) }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 4.dp, height = 40.dp).clip(CircleShape).background(rowColor))
                Spacer(Modifier.width(10.dp))
                IconBlob(if (u.planned) Icons.Outlined.EventNote else Icons.Outlined.Autorenew, accent, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Not paid yet: say when, and whether the budget can carry it. No tick, which read as "paid".
                    Text(
                        listOfNotNull(u.category.takeIf { u.planned }, (if (u.planned) "Planned for " else "Renews ") + formatDate(u.date)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        when (f.fit) {
                            Fit.FITS -> "Within budget"
                            Fit.EATS_SAVINGS -> "Dips into savings"
                            Fit.OVER -> "Over budget"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = rowColor,
                        modifier = Modifier.padding(top = 3.dp).clip(CircleShape).background(rowColor.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    f.envelopeShortMinor?.let {
                        Text("${money(it)} more than the ${u.category} envelope has", style = MaterialTheme.typography.bodySmall, color = coral)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(u.amountMinor), style = MaterialTheme.typography.titleSmall)
                    if (u.planned) TextButton(onClick = { onMarkPaid(u.id) }) { Text("Mark paid") }
                }
            }
        }
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

@Composable
private fun EnvelopesCard(m: MoneyState, categories: List<CategoryStatus>, money: (Long) -> String, showAll: Boolean = true, onCategory: (String) -> Unit = {}, onMove: (Pocket, String) -> Unit) {
    val accent = LocalHouseColors.current.money
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Tightest first, so the ones that need attention are in the first few.
        val list = m.envelopes.envelopes.sortedBy { it.availableMinor.toDouble() / it.limitMinor.coerceAtLeast(1) }
        (if (showAll) list else list.take(4)).forEachIndexed { index, e ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            val status = categories.firstOrNull { it.name == e.category }
            val total = (e.limitMinor + e.movedInMinor - e.movedOutMinor).coerceAtLeast(1)
            val negative = e.availableMinor < 0
            // Over only because of planned spends: a heads-up, not overspending.
            val overWithPlans = negative && e.availableMinor + e.plannedMinor >= 0
            val warn = if (overWithPlans) LocalHouseColors.current.coral else MaterialTheme.colorScheme.error
            // Tap for the category's whole history.
            Row(Modifier.clickable { onCategory(e.category) }, verticalAlignment = Alignment.CenterVertically) {
                IconBlob(categoryIcon(e.category), accent, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                overWithPlans -> "${money(-e.availableMinor)} over with plans"
                                negative -> "${money(-e.availableMinor)} over"
                                else -> "${money(e.availableMinor)} available"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (negative) warn else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        val used = ((e.spentMinor + e.plannedMinor).toFloat() / total).coerceIn(0f, 1f)
                        if (used > 0f) Box(Modifier.weight(used).fillMaxHeight().background(if (negative) warn else accent.color))
                        if (used < 1f) Spacer(Modifier.weight(1f - used))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            "${money(e.spentMinor)} spent of ${money(e.limitMinor)}",
                            e.plannedMinor.takeIf { it > 0 }?.let { "${money(it)} planned" },
                            (e.movedInMinor - e.movedOutMinor).takeIf { it != 0L }?.let { if (it > 0) "+${money(it)} moved in" else "−${money(-it)} moved out" },
                            e.creditsMinor.takeIf { it > 0 }?.let { "${money(it)} in gift cards" },
                            if (status?.pace == Pace.AHEAD && !negative) "going faster than the month allows" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (e.availableMinor > 0) {
                    TextButton(onClick = { onMove(Pocket.Category(e.category), e.category) }) { Text("Move") }
                }
            }
        }
        if (showAll) categories.filter { it.limitMinor == null && it.spentMinor > 0 }.forEach { c ->
            Spacer(Modifier.height(14.dp))
            Row(Modifier.clickable { onCategory(c.name) }, verticalAlignment = Alignment.CenterVertically) {
                IconBlob(categoryIcon(c.name), accent, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text("No envelope · comes out of the gullak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(c.spentMinor), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CreditsCard(m: MoneyState, money: (Long) -> String, onUsed: (String) -> Unit, onOpen: (String) -> Unit) {
    val accent = LocalHouseColors.current.money
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (m.credits.isEmpty()) {
            Text(
                "Add gift cards, card points, cashback or vouchers, and save each for a category or a dream. Say “got Amazon gift card 2000 for gifting”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        m.credits.forEachIndexed { index, c ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            Row(Modifier.clip(MaterialTheme.shapes.small).clickable { onOpen(c.id) }, verticalAlignment = Alignment.CenterVertically) {
                IconBlob(Icons.Outlined.Redeem, accent, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(c.kind, c.earmark?.let { "for $it" } ?: "for anything", c.expiry?.let { "expires ${formatDate(it)}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(c.valueMinor), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { onUsed(c.id) }) { Text("Used") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MoveSheet(m: MoneyState, from: Pocket, fromLabel: String, currency: String, preset: Pocket? = null, onMove: (Pocket, String, Long) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val available = m.available(from)
    val targets: List<Pair<Pocket, String>> = buildList {
        m.dreams.filter { !it.dream.achieved }.forEach { add(Pocket.Dream(it.dream.id) to "${it.dream.emoji} ${it.dream.title}") }
        if (from != Pocket.Pot) add(Pocket.Pot to "Gullak")
        m.envelopes.envelopes.filter { Pocket.Category(it.category) != from }.forEach { add(Pocket.Category(it.category) to it.category) }
    }
    var target by remember { mutableStateOf(targets.firstOrNull { it.first == preset } ?: targets.firstOrNull()) }
    var amount by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Move from $fromLabel", style = MaterialTheme.typography.headlineSmall)
            Text("${formatMinor(available, currency)} available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("To", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                targets.forEach { t ->
                    FilterChip(selected = target == t, onClick = { target = t }, label = { Text(t.second) }, shape = CircleShape)
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
            val t = target
            Button(
                onClick = { if (t != null && minor != null) onMove(t.first, t.second.substringAfter(' ').takeIf { t.first is Pocket.Dream || t.first is Pocket.Investment } ?: t.second, minor) },
                enabled = t != null && minor != null && minor > 0 && minor <= available,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Move") }
        }
    }
}
