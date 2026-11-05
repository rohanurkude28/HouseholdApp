package com.houseaffairs.app.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.KitchenMonth
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.StockHistory
import com.houseaffairs.app.core.data.StockKind
import com.houseaffairs.app.core.data.StockYear
import com.houseaffairs.app.core.data.kitchenMonths
import com.houseaffairs.app.core.data.stockYears
import com.houseaffairs.app.core.data.usageOf
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.util.formatMinor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Two years of months, so last year's whole story is there in January. */
private const val MONTHS = 24

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UsageViewModel @Inject constructor(session: SessionManager, private val pantry: PantryRepository, money: com.houseaffairs.app.core.data.MoneyRepository) : ViewModel() {
    private val ready = session.state.filterIsInstance<SessionState.Ready>()

    private data class Who(val hid: String, val uid: String, val money: Boolean, val medicine: Boolean, val ownMedicines: Boolean)

    private val who = ready.map {
        Who(it.household.id, it.user.uid, it.me.canAccess(AppModule.MONEY), it.me.canAccess(AppModule.MEDICINE), RolePolicy.ownMedicinesOnly(it.me.role))
    }.distinctUntilChanged()

    /** Grocery spending and the Groceries budget, for those who can see money; null otherwise. */
    private val groceries = who.flatMapLatest { w ->
        if (w.money) {
            money.observeMoney(w.hid, w.uid).map { m -> m?.let { it.txns.filter { t -> t.category == "Groceries" } to it.budget.categories["Groceries"] } }
        } else {
            flowOf(null)
        }
    }

    private fun history(kind: StockKind): Flow<StockHistory> = who.flatMapLatest { w ->
        if (kind == StockKind.MEDICINES && !w.medicine) flowOf(StockHistory(emptyList(), emptyList(), emptyList()))
        else pantry.observeHistory(w.hid, w.uid, kind, ownOnly = w.ownMedicines)
    }

    private val histories = StockKind.entries.associateWith { kind ->
        history(kind).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockHistory(emptyList(), emptyList(), emptyList()))
    }

    private fun months(kind: StockKind): StateFlow<List<KitchenMonth>> =
        // Money is only for food: groceries have their own budget line.
        (if (kind == StockKind.FOOD) combine(histories.getValue(kind), groceries) { h, g -> h to g } else histories.getValue(kind).map { it to null })
            .map { (h, g) -> kitchenMonths(h.bought, h.lives, h.wasted, g?.first, LocalDate.now(), groceryBudgetMinor = g?.second, months = MONTHS) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val byKind = StockKind.entries.associateWith { months(it) }

    /** Newest month first; this month is always there. */
    fun monthsOf(kind: StockKind): StateFlow<List<KitchenMonth>> = byKind.getValue(kind)

    /** Bought, finished and wasted, for "how long things last". */
    fun historyOf(kind: StockKind): StateFlow<StockHistory> = histories.getValue(kind)

    val currency: StateFlow<String> = ready.map { it.household.baseCurrency }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "INR")
}

private fun StockKind.place() = when (this) {
    StockKind.FOOD -> "the kitchen"
    StockKind.SUPPLIES -> "supplies"
    StockKind.MEDICINES -> "medicines"
}

private fun StockKind.accent(colors: com.houseaffairs.app.core.designsystem.HouseColors): Accent = if (this == StockKind.FOOD) colors.stock else colors.vault

private fun shortMonth(m: KitchenMonth) = m.month.month.getDisplayName(TextStyle.SHORT, Locale.US)
private fun longMonth(m: KitchenMonth) = m.month.month.getDisplayName(TextStyle.FULL, Locale.US)
private fun things(n: Int) = if (n == 1) "1 thing" else "$n things"

/** "25% less than October", "same as October"; null when there's nothing to compare. */
private fun changeText(m: KitchenMonth): String? {
    val p = m.changePercent ?: return null
    val before = m.month.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.US)
    return when {
        p == 0 -> "same as $before"
        p > 0 -> "$p% more than $before"
        else -> "${-p}% less than $before"
    }
}

/** "🗑️ ₹57 wasted", or "🗑️ 2 went off" when nothing had a cost; null when nothing was wasted. */
private fun wasteText(count: Int, minor: Long, currency: String): String? = when {
    count == 0 -> null
    minor > 0 -> "🗑️ ${formatMinor(minor, currency)} wasted"
    else -> "🗑️ ${things(count)} wasted"
}

/**
 * This month, at the top of a list (Pantry and Shopping for food; Medicines; Supplies): for food,
 * grocery spend against last month and the budget; for all, what came home by kind and what went
 * to waste. Tapping opens the year.
 */
@Composable
fun StockMonthCard(kind: StockKind, onOpen: () -> Unit, modifier: Modifier = Modifier, viewModel: UsageViewModel = hiltViewModel()) {
    val months by viewModel.monthsOf(kind).collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val m = months.firstOrNull() ?: return
    val accent = kind.accent(LocalHouseColors.current)
    MonthCard("${longMonth(m)} in ${kind.place()}", accent, onOpen, modifier) {
        val spent = m.spentMinor
        if (spent != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatMinor(spent, currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("  on groceries", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 3.dp))
            }
            changeText(m)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            m.budgetMinor?.let { BudgetBar(spent, it, currency, accent.color, Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(10.dp))
        }
        val parts = listOfNotNull(
            if (kind == StockKind.FOOD) m.byType.take(4).joinToString("  ") { (a, n) -> "${a.emoji} $n" }.takeIf { it.isNotEmpty() }
            else m.bought.sumOf { it.second }.takeIf { it > 0 }?.let { "${things(it)} came home" },
            m.usedUp.sumOf { it.second }.takeIf { it > 0 && kind != StockKind.FOOD }?.let { "$it used up" },
            wasteText(m.wasted.size, m.wastedMinor, currency),
        )
        Text(
            parts.joinToString("   ·   ").ifEmpty { "Nothing logged yet this month." },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BudgetBar(spent: Long, budget: Long, currency: String, color: Color, modifier: Modifier = Modifier) {
    val over = spent > budget
    Column(modifier) {
        LinearProgressIndicator(
            progress = { (spent.toFloat() / budget).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = if (over) MaterialTheme.colorScheme.error else color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
        Text(
            if (over) "${formatMinor(spent - budget, currency)} over the ${formatMinor(budget, currency)} grocery budget"
            else "${formatMinor(budget - spent, currency)} left of ${formatMinor(budget, currency)}",
            style = MaterialTheme.typography.labelMedium,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The year for one kind of stock: the year's totals first (the generic view), then each month as
 * a one-line summary that opens into the full month. This month starts open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockYearSheet(kind: StockKind, onDismiss: () -> Unit, viewModel: UsageViewModel = hiltViewModel()) {
    val months by viewModel.monthsOf(kind).collectAsStateWithLifecycle()
    val history by viewModel.historyOf(kind).collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val years = stockYears(months)
    var pickedYear by remember { mutableStateOf<Int?>(null) }
    val year = years.firstOrNull { it.year == pickedYear } ?: years.firstOrNull()
    // This month starts open; taps flip a month from how it started.
    var flipped by remember { mutableStateOf(setOf<java.time.YearMonth>()) }
    val accent = kind.accent(LocalHouseColors.current)
    val today = LocalDate.now()
    YearSheet(
        title = "${kind.place().replaceFirstChar { it.uppercase() }} by year",
        accent = accent,
        years = years.map { it.year },
        selected = year?.year,
        onSelect = { pickedYear = it },
        onDismiss = onDismiss,
        summary = { if (year != null) YearSummary(year, kind, currency) },
        rows = year?.months.orEmpty().map { m ->
            MonthRow(
                m.month, longMonth(m),
                listOfNotNull(
                    m.spentMinor?.takeIf { it > 0 }?.let { formatMinor(it, currency) },
                    m.bought.sumOf { it.second }.takeIf { it > 0 }?.let { "${things(it)} bought" },
                    m.usedUp.sumOf { it.second }.takeIf { it > 0 }?.let { "$it used up" },
                    wasteText(m.wasted.size, m.wastedMinor, currency),
                ).joinToString(" · ").ifEmpty { "Nothing logged" },
            ) { MonthDetail(m, kind, currency, accent) }
        },
    ) {
        // How long packs last only makes sense for what gets finished and bought again.
        val items = usageOf(history.lives).filter { it.averageDays != null }
        if (kind != StockKind.MEDICINES && items.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("How long things last", style = MaterialTheme.typography.titleMedium)
                items.forEach { u ->
                    Column {
                        Text(u.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(
                                "lasts about ${u.averageDays} days",
                                "${u.finished} used",
                                u.daysLeft(today)?.let { if (it == 0) "due to run out" else "about $it days left" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.YearSummary(y: StockYear, kind: StockKind, currency: String) {
    run {
        Text(if (y.year == LocalDate.now().year) "${y.year} so far" else "${y.year}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        y.spentMinor?.let { Text("${formatMinor(it, currency)} on groceries", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        Text(
            listOfNotNull(
                "${things(y.bought)} bought",
                y.usedUp.takeIf { it > 0 }?.let { "$it used up" },
                wasteText(y.wasted, y.wastedMinor, currency) ?: "🎉 nothing wasted",
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
        )
        if (kind == StockKind.FOOD && y.byType.isNotEmpty()) {
            Text(y.byType.take(6).joinToString("  ") { (a, n) -> "${a.emoji} $n" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
        // Only worth saying once something has come home more than once.
        y.mostBought().filter { it.second > 1 }.takeIf { it.isNotEmpty() }?.let { top ->
            Text("Most bought: " + top.joinToString(" · ") { (t, n) -> if (n > 1) "$t ×$n" else t }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun MonthDetail(m: KitchenMonth, kind: StockKind, currency: String, accent: Accent, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        m.spentMinor?.let { spent ->
            Column {
                Text("Groceries", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMinor(spent, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                changeText(m)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                m.budgetMinor?.let { BudgetBar(spent, it, currency, accent.color, Modifier.padding(top = 8.dp)) }
            }
        }

        if (m.bought.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What came home · ${things(m.bought.sumOf { it.second })}", style = MaterialTheme.typography.titleSmall)
                // Kinds matter for food; medicines and supplies are one kind each.
                if (kind == StockKind.FOOD) {
                    val most = m.byType.first().second
                    m.byType.forEach { (aisle, n) -> TypeBar("${aisle.emoji}  ${aisle.label}", n, most, Color(aisle.argb)) }
                }
                Text(m.bought.joinToString(" · ") { (t, n) -> if (n > 1) "$t ×$n" else t }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    m.wasted.isEmpty() -> "🎉 Nothing went to waste"
                    m.wastedMinor > 0 -> "🗑️ Went to waste · ${formatMinor(m.wastedMinor, currency)}"
                    else -> "🗑️ Went to waste · ${things(m.wasted.size)}"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            m.wasted.forEach { w ->
                Row {
                    Text(w.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        listOfNotNull((if (w.thrown) "thrown " else "off ") + "${w.date.dayOfMonth} ${shortMonth(m)}", w.lostMinor?.let { formatMinor(it, currency) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Only cost and how much was left can put a price on it.
            if (m.wasted.any { it.lostMinor == null }) {
                Text("Add a cost when you add things to see what waste costs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (m.usedUp.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Used up", style = MaterialTheme.typography.titleSmall)
                Text(m.usedUp.joinToString(" · ") { (t, n) -> if (n > 1) "$t ×$n" else t }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TypeBar(label: String, count: Int, most: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(170.dp))
        Box(Modifier.weight(1f).height(10.dp)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / most)
                    .clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.75f)),
            )
        }
        Text("$count", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 10.dp).width(24.dp))
    }
}
