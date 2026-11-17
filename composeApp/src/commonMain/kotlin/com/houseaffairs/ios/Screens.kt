package com.houseaffairs.ios

import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Edit
import com.houseaffairs.app.feature.money.YearOutlookSummary
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.EventNote
import com.houseaffairs.app.core.time.plusDays
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.QuickAnswer
import com.houseaffairs.app.core.data.TRIP_RESERVE
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.data.agendaAnswer
import com.houseaffairs.app.core.data.goalStep
import com.houseaffairs.app.core.data.haveAnswer
import com.houseaffairs.app.core.data.incomeAnswer
import com.houseaffairs.app.core.data.periodDays
import com.houseaffairs.app.core.data.recipeFit
import com.houseaffairs.app.core.data.savingsBreakdown
import com.houseaffairs.app.core.data.spentOn
import com.houseaffairs.app.core.data.toBuyAnswer
import com.houseaffairs.app.core.data.whenAnswer
import com.houseaffairs.app.core.data.yearOutlook
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.feature.quickadd.ListTemplate
import com.houseaffairs.app.feature.quickadd.QuickAddDraft

// The same repositories (and rules) as Android, so the numbers and words match on both phones.
private val moneyRepo = MoneyRepository()
private val pantryRepo = PantryRepository()
private val thingsRepo = ThingsRepository()

@Composable
private fun Chips(parts: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { p ->
            FilterChip(
                selected = p == selected, onClick = { onPick(p) }, label = { Text(p) }, shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
            )
        }
    }
}

@Composable
private fun Line(title: String, detail: String = "", end: String? = null, warn: Boolean = false, onClick: (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            end?.let { Text(it, fontWeight = FontWeight.SemiBold, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))

// ---------------------------------------------------------------------------------------------
// Money: Month, Year and Savings, as on Android

@Composable
internal fun MoneyScreen(s: SessionState.Ready, store: FamilyStore, onEdit: (String, String) -> Unit, onAdd: () -> Unit = {}, actions: Actions? = null) {
    // Without the Money module: just what they spent, as Android's My spends.
    if (!s.me.canAccess(AppModule.MONEY)) {
        val mine by remember(s.household.id, s.uid) { moneyRepo.observeMine(s.household.id, s.uid) }.collectAsState(emptyList())
        com.houseaffairs.app.feature.money.MySpendsList(mine, s.household.baseCurrency, onAdd) { onEdit("transactions", it) }
        return
    }
    val m by remember(s.household.id, s.uid) { moneyRepo.observeMoney(s.household.id, s.uid) }.collectAsState(null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var editingBudget by remember { mutableStateOf(startTab == "MONEY/Budget") }
    // Moving money: from which pocket, its name, and where it's headed if already picked.
    var moving by remember { mutableStateOf<Triple<com.houseaffairs.app.core.data.Pocket, String, com.houseaffairs.app.core.data.Pocket?>?>(null) }
    val hid = s.household.id
    val expenses by remember(store) { store.expenses() }.collectAsState(emptyList())
    // "-openTab MONEY/Year" (test runs only) opens on that part.
    var part by remember { mutableStateOf(startTab?.substringAfter("MONEY/", "")?.takeIf { it in listOf("Year", "Savings") } ?: "Month") }
    var pickedYear by remember { mutableStateOf<Int?>(null) }
    var yearCategory by remember { mutableStateOf<String?>(null) }
    var allEnvelopes by remember { mutableStateOf(false) }
    // A category opened for its whole history: "where did the grocery money go?" (as Android's).
    // "-openTab MONEY/Category:Groceries" (test runs only) opens one.
    var category by remember { mutableStateOf(startTab?.substringAfter("MONEY/Category:", "")?.ifEmpty { null }) }
    category?.let { c ->
        m?.let { money ->
            com.houseaffairs.app.feature.money.CategorySheet(
                com.houseaffairs.app.core.data.categoryReport(money.txns, c, today()),
                { formatMinor(it, money.budget.currency) },
                onOpen = { id -> category = null; onEdit("transactions", id) },
                onDismiss = { category = null },
            )
        }
    }
    val costsHidden = s.uid in s.household.hideCostsFrom
    // Android's own pieces (from :ui), which pad themselves.
    val shared = true
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = if (shared) PaddingValues(bottom = 24.dp) else PaddingValues(16.dp),
        verticalArrangement = if (shared) Arrangement.Top else Arrangement.spacedBy(10.dp),
    ) {
        val money = m
        item {
            Column(if (shared) Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp) else Modifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Money", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    if (money != null && !costsHidden) androidx.compose.material3.IconButton(onClick = { editingBudget = true }) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Edit, contentDescription = "Edit budget")
                    }
                }
                money?.summary?.let {
                    Text(
                        "${it.cycle.start.format("d MMM")} – ${it.cycle.endExclusive.plusDays(-1).format("d MMM")} · day ${it.day} of ${it.cycle.days}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (costsHidden) { item { Text("Costs are kept private in this home.", Modifier.padding(horizontal = 20.dp)) }; return@LazyColumn }
        if (money == null) {
            item { com.houseaffairs.app.feature.money.SetupPrompt(onSetUp = { editingBudget = true }) }
        } else {
            item { com.houseaffairs.app.core.ui.SubTabs(listOf("Month", "Year", "Savings"), part, { it }, { part = it }, Modifier.padding(bottom = 4.dp)) }
            when (part) {
                "Month" -> month(money, expenses, onEdit, onAdd, s) { part = "Year" }
                "Year" -> year(money, pickedYear, { pickedYear = it }, yearCategory, { yearCategory = it }, onEdit, onAdd)
                else -> savings(money, s, allEnvelopes, { allEnvelopes = !allEnvelopes }, onAdd, onEdit, onCategory = { category = it }) { from, label, to -> moving = Triple(from, label, to) }
            }
        }
    }
    // Filled again once the budget has loaded, so it never opens empty over a saved one.
    if (editingBudget) androidx.compose.runtime.key(m?.budget) {
        // Android's budget setup: amount, the month's start, savings, the split and the family's own categories.
        com.houseaffairs.app.feature.money.BudgetSetupSheet(
            existing = m?.budget, currency = m?.budget?.currency ?: s.household.baseCurrency,
            dreams = m?.dreams?.map { it.dream }.orEmpty(),
            onSave = { budget, from ->
                editingBudget = false
                actions?.say("Budget saved") { moneyRepo.saveBudget(hid, s.uid, budget, from) } ?: scope.launch { runCatching { moneyRepo.saveBudget(hid, s.uid, budget, from) } }
            },
            onDismiss = { editingBudget = false },
            onCategoryUse = { name, done -> scope.launch { done(runCatching { moneyRepo.categoryUse(hid, s.uid, name) }.getOrDefault(0)) } },
            onMoveCategory = { from, to, done -> scope.launch { runCatching { moneyRepo.moveCategory(hid, s.uid, from, to) }; done() } },
            onDeleteWithHistory = { name, done -> scope.launch { runCatching { moneyRepo.deleteCategoryWithHistory(hid, s.uid, name) }; done() } },
        )
    }
    val mv = moving
    val ms = m
    if (mv != null && ms != null) {
        com.houseaffairs.app.feature.money.MoveSheet(ms, mv.first, mv.second, ms.budget.currency, preset = mv.third, onMove = { to, toLabel, amount ->
            moving = null
            actions?.run("${formatMinor(amount, ms.budget.currency)} moved from ${mv.second} to $toLabel") {
                val saved = moneyRepo.transfer(hid, s.uid, mv.first, to, amount); ({ saved.delete() })
            }
        }, onDismiss = { moving = null })
    }
}

/** Android's Month: the budget card, add chips, planned spends against the month, and where it went. */
private fun LazyListScope.month(m: MoneyState, expenses: List<Entry>, onEdit: (String, String) -> Unit, onAdd: () -> Unit, s: SessionState.Ready, onYear: () -> Unit) {
    val cur = m.budget.currency
    val money: (Long) -> String = { formatMinor(it, cur) }
    item { com.houseaffairs.app.feature.money.BudgetHero(m.summary, money, m.envelopes.potMinor) }
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.houseaffairs.app.feature.money.ActionChip("Expense", Icons.Outlined.Add, onAdd)
            com.houseaffairs.app.feature.money.ActionChip("Plan a spend", Icons.Outlined.EventNote, onAdd)
            com.houseaffairs.app.feature.money.ActionChip("Subscription", Icons.Outlined.Autorenew, onAdd)
        }
    }
    item { com.houseaffairs.app.core.ui.SectionTitle("Planned & upcoming") }
    item {
        com.houseaffairs.app.feature.money.UpcomingCard(m.summary, m.envelopes.envelopes, money, onMarkPaid = { moneyRepo.markPaid(s.household.id, it) }) { u ->
            // A trip's reserve isn't a saved expense: it opens the trip itself.
            val trip = u.id.removePrefix(TRIP_RESERVE).takeIf { it != u.id }
            if (trip != null) onEdit("trips", trip) else onEdit(if (u.planned) "transactions" else "recurring", u.id)
        }
    }
    item { com.houseaffairs.app.core.ui.SectionTitle("Where the money went") }
    item { com.houseaffairs.app.feature.money.MonthOverviewCard(m, money, m.summary.cycle, onOpen = { onEdit("transactions", it) }, onYear = onYear) }
    item { com.houseaffairs.app.core.ui.SectionTitle("Expenses") }
    item {
        com.houseaffairs.app.core.ui.HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            if (expenses.isEmpty()) Text("Nothing spent yet this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            expenses.forEachIndexed { i, e ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clickable { onEdit("transactions", e.id) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.houseaffairs.app.core.ui.IconBlob(com.houseaffairs.app.core.ui.categoryIcon(e.detail ?: "Other"), com.houseaffairs.app.core.designsystem.LocalHouseColors.current.money, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(listOfNotNull(e.detail, e.date?.let { com.houseaffairs.app.core.data.dayLabel(it, today()) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatMinor(e.amountMinor ?: 0, e.currency ?: cur), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

private val MONTH_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Android's Year: the year's totals, where the money is going against the target, by category, and its expenses. */
private fun LazyListScope.year(m: MoneyState, pickedYear: Int?, onPick: (Int) -> Unit, yearCategory: String?, onCategory: (String?) -> Unit, onEdit: (String, String) -> Unit, onAdd: () -> Unit) {
    val cur = m.budget.currency
    val money: (Long) -> String = { formatMinor(it, cur) }
    val today = today()
    val outlook = yearOutlook(m.budget, m.startedOn, m.txns, m.dreams.map { it.dream }, m.trips.map { it.trip }, today, money)
    val years = com.houseaffairs.app.core.data.moneyYears(m.txns, m.budget, m.startedOn, today)
    val year = years.firstOrNull { it.year == pickedYear } ?: years.firstOrNull()
    item {
        val accent = com.houseaffairs.app.core.designsystem.LocalHouseColors.current.money
        com.houseaffairs.app.core.ui.YearChips(years.map { it.year }, year?.year, onPick, accent, Modifier.padding(top = 8.dp))
    }
    item {
        val accent = com.houseaffairs.app.core.designsystem.LocalHouseColors.current.money
        com.houseaffairs.app.core.ui.YearSummaryCard(accent, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (year == null || year.year == today.year) YearOutlookSummary(outlook, money)
            else {
                Text("${year.year}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${money(year.spentMinor)} spent", style = MaterialTheme.typography.headlineMedium)
                Text(
                    (year.budgetMinor - year.spentMinor).let { if (it >= 0) "${money(it)} under the budget" else "${money(-it)} over the budget" } + " · ${year.count} expenses",
                    style = MaterialTheme.typography.bodyMedium, color = accent.color,
                )
            }
        }
    }
    if (year != null) {
        item(key = "money-line-${year.year}") {
            val names = (m.txns.filter { !it.planned && it.date.year == year.year }.groupBy { it.category }.entries.sortedByDescending { e -> e.value.sumOf { it.amountMinor } }.map { it.key } +
                m.budget.categories.filterValues { it > 0 }.keys).distinct()
            com.houseaffairs.app.core.ui.HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Where the money is going", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<String?>(null) + names).forEach { c ->
                        FilterChip(selected = yearCategory == c, onClick = { onCategory(c) }, label = { Text(c ?: "All") }, shape = CircleShape)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val pace = com.houseaffairs.app.core.data.yearPace(m.txns, year.year, today, m.budget, m.startedOn, yearCategory)
                com.houseaffairs.app.feature.money.PaceChart(pace, money, line = com.houseaffairs.app.core.data.yearLine(pace, money) { MONTH_SHORT[it - 1] }, startLabel = "Jan", endLabel = "Dec")
            }
        }
        val period = com.houseaffairs.app.core.data.yearPeriod(year.year)
        val bars = com.houseaffairs.app.core.data.categoryBars(m.txns, period, m.budget.categories, months = year.months.size)
        if (bars.isNotEmpty()) {
            item { com.houseaffairs.app.core.ui.SectionTitle("By category") }
            item { com.houseaffairs.app.core.ui.HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { com.houseaffairs.app.feature.money.CategoryBars(bars, money) { onCategory(it) } } }
        }
        item { com.houseaffairs.app.core.ui.SectionTitle("Expenses") }
        item(key = "money-expenses-${year.year}") { com.houseaffairs.app.feature.money.ExpensesSearch(m, money, period, onAdd = onAdd) { onEdit("transactions", it) } }
    }
    if (year == null || year.year == today.year) item {
        com.houseaffairs.app.feature.money.ComingUpCard(outlook, money) { b -> if (b.kind != "Dream" && b.kind != "Trip") onEdit("transactions", b.id) }
    }
}

/** Android's Savings: the gullaks and dreams, envelopes per category, and gift cards. Moving money is in the + box. */
private fun LazyListScope.savings(m: MoneyState, s: SessionState.Ready, showAll: Boolean, onShowAll: () -> Unit, onAdd: () -> Unit, onEdit: (String, String) -> Unit, onCategory: (String) -> Unit, onMove: (com.houseaffairs.app.core.data.Pocket, String, com.houseaffairs.app.core.data.Pocket?) -> Unit) {
    val cur = m.budget.currency
    val money: (Long) -> String = { formatMinor(it, cur) }
    item { com.houseaffairs.app.core.ui.SectionTitle("Your gullaks") }
    item { com.houseaffairs.app.feature.money.SavingsCard(m, money, onMove = { onMove(com.houseaffairs.app.core.data.Pocket.Pot, "Gullak", null) }, onFill = { d -> onMove(com.houseaffairs.app.core.data.Pocket.Pot, "Gullak", com.houseaffairs.app.core.data.Pocket.Dream(d.id)) }, onNew = onAdd) }
    item { com.houseaffairs.app.core.ui.SectionTitle("Envelopes", action = if (m.envelopes.envelopes.size > 4) (if (showAll) "Show less" else "Show all ${m.envelopes.envelopes.size}") else null, onAction = onShowAll) }
    item {
        Text(
            "This month's limit per category, tightest first. Whatever's left drops into your gullak on ${com.houseaffairs.app.core.data.dayLabel(m.summary.cycle.endExclusive, today())}.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
    }
    item { com.houseaffairs.app.feature.money.EnvelopesCard(m, m.summary.categories, money, showAll = showAll, onCategory = onCategory) { pocket, label -> onMove(pocket, label, null) } }
    item { com.houseaffairs.app.core.ui.SectionTitle("Gift cards & coupons") }
    item { com.houseaffairs.app.feature.money.CreditsCard(m, money, onUsed = { moneyRepo.markCreditUsed(s.household.id, it) }) { onEdit("credits", it) } }
}

// ---------------------------------------------------------------------------------------------
// Plans → Trips, Goals, Lists

/** Answers a question the parser read, from what's already loaded; null when iPhone can't yet. */
@Composable
internal fun rememberAnswer(s: SessionState.Ready, data: HomeData, draft: QuickAddDraft?): QuickAnswer? {
    val ask = draft as? QuickAddDraft.Ask ?: return null
    val pantry by remember(s.household.id) { pantryRepo.observePantry(s.household.id, s.uid) }.collectAsState(emptyList())
    val m by remember(s.household.id, s.uid) { if (s.me.canAccess(AppModule.MONEY)) moneyRepo.observeMoney(s.household.id, s.uid) else kotlinx.coroutines.flow.flowOf(null) }.collectAsState(null)
    val today = today()
    val shopping = data.shopping.map { it.name }
    return when (ask.question) {
        QuickAddDraft.Question.HAVE -> haveAnswer(ask.subject.orEmpty(), pantry, shopping, today)
        QuickAddDraft.Question.WHEN -> whenAnswer(ask.subject.orEmpty(), data.events, data.renewals, data.tasks, today)
        QuickAddDraft.Question.AGENDA -> agendaAnswer(ask.period ?: "today", data.tasks, data.events, data.renewals, today)
        QuickAddDraft.Question.TO_BUY -> toBuyAnswer(shopping)
        QuickAddDraft.Question.INCOME -> incomeAnswer()
        QuickAddDraft.Question.SPENT -> m?.let { money ->
            val spent = spentOn(money.txns, ask.subject, periodDays(ask.period, today, money.budget.cycleStartDay))
            QuickAnswer(
                "${formatMinor(spent.totalMinor, money.budget.currency)} on ${spent.matched ?: ask.subject ?: "everything"}",
                listOf("${spent.count} expenses") + spent.top.map { (name, minor) -> "$name ${formatMinor(minor, money.budget.currency)}" },
            )
        }
        else -> null
    }
}

@Composable
internal fun AnswerCard(a: QuickAnswer) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            a.lines.forEach { Text(it) }
        }
    }
}

@Composable
internal fun Spinner() = CircularProgressIndicator()
