package com.houseaffairs.app.feature.money

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Cycle
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.Transfer
import com.houseaffairs.app.core.data.cyclesSince
import com.houseaffairs.app.core.data.cardOf
import com.houseaffairs.app.core.data.findExpenses
import com.houseaffairs.app.core.data.pocketLabel
import com.houseaffairs.app.core.data.savingsBreakdown
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.categoryIcon
import com.houseaffairs.app.feature.add.formatDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Your gullaks: the main one (every month's unspent budget lands here) and one per goal, each
 * with what's in it, how close it is, and a button to add money from the main gullak.
 */
@Composable
internal fun SavingsCard(
    m: MoneyState,
    money: (Long) -> String,
    onMove: () -> Unit,
    onFill: (com.houseaffairs.app.core.data.Dream) -> Unit,
    onNew: () -> Unit,
) {
    val colors = LocalHouseColors.current
    val s = savingsBreakdown(m)
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = colors.vault.container) {
        Text("Saved in all", style = MaterialTheme.typography.labelLarge, color = colors.vault.color)
        Text(money(s.totalMinor), style = MaterialTheme.typography.headlineMedium)
        Text(
            "${money(s.comingMinor)} more joins the main gullak on ${formatDate(m.summary.cycle.endExclusive)} if the month goes to plan",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        // The main gullak.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🐷", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text("Main gullak", style = MaterialTheme.typography.titleSmall)
                Text("Free to use · what you didn't spend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(s.gullakMinor), style = MaterialTheme.typography.titleMedium)
        }
        if (s.gullakMinor > 0) {
            TextButton(onClick = onMove, modifier = Modifier.padding(start = 32.dp)) { Text("Move to a goal or envelope") }
        }
        // One gullak per goal.
        s.dreams.forEach { (d, amt) ->
            val p = m.dreams.firstOrNull { it.dream.id == d.id }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onFill(d) }, verticalAlignment = Alignment.CenterVertically) {
                Text(d.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (d.targetMinor > 0) {
                        val f = (amt.toFloat() / d.targetMinor).coerceIn(0f, 1f)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            if (f > 0f) Box(Modifier.weight(f).height(6.dp).background(colors.vault.color))
                            if (f < 1f) Spacer(Modifier.weight(1f - f))
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        listOfNotNull(
                            if (d.targetMinor > 0) "of ${money(d.targetMinor)}" else "no target",
                            p?.monthlyNeededMinor?.let { "${money(it)}/mo to make it" },
                        ).joinToString(" · ") + " · tap to add money",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(money(amt), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedButton(onClick = onNew, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
            Text("+ New gullak for a goal")
        }
        if (s.creditsMinor > 0) {
            Spacer(Modifier.height(8.dp))
            Text("Plus ${money(s.creditsMinor)} in gift cards and coupons", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Every move between the gullak, envelopes and dreams, newest first; each can be undone. */
@Composable
internal fun MovesCard(m: MoneyState, money: (Long) -> String, onUndo: (Transfer) -> Unit) {
    var all by remember { mutableStateOf(false) }
    val dreams = m.dreams.map { it.dream }
    val moves = m.moves.filter { it.from != "growth" && !it.to.startsWith("inv:") && !it.from.startsWith("inv:") }.sortedByDescending { it.date }
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        (if (all) moves else moves.take(5)).forEachIndexed { i, t ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${pocketLabel(t.from, dreams)} → ${pocketLabel(t.to, dreams)}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDate(t.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(t.amountMinor), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { onUndo(t) }) { Text("Undo") }
            }
        }
        if (moves.size > 5) TextButton(onClick = { all = !all }) { Text(if (all) "Show less" else "Show all ${moves.size}") }
    }
}

/**
 * The expenses of one [period] (the month or year the tab shows), searchable: type a word
 * ("swiggy", "cab", "Goa"). The total of what's shown is always at the top. Picking the period
 * and seeing categories happen above it, so neither is repeated here.
 */
@Composable
internal fun ExpensesSearch(m: MoneyState, money: (Long) -> String, period: Cycle, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val category: String? = null
    val month = period.start
    val found = findExpenses(m.txns, query, category, period)
    val accent = LocalHouseColors.current.money

    Column {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search: swiggy, cab, Goa…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        HaCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(found.sumOf { it.amountMinor }), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${found.size} expenses" + (category?.let { " in $it" } ?: "") + (query.takeIf { it.isNotBlank() }?.let { " matching “$it”" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.clip(CircleShape).background(accent.container).clickable(onClick = onAdd).padding(10.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add expense", tint = accent.color)
                }
            }
            if (found.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var shown by remember(query, category, month) { mutableIntStateOf(30) }
            var lastDate: LocalDate? = null
            found.take(shown).forEach { t ->
                if (t.date != lastDate) {
                    lastDate = t.date
                    Spacer(Modifier.height(14.dp))
                    Text(formatDate(t.date).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen(t.id) }, verticalAlignment = Alignment.CenterVertically) {
                    IconBlob(categoryIcon(t.category), accent, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(t.category, t.subcategory, cardOf(t, m.budget.cards)?.let { "💳 ${it.name}" }, t.trip?.let { "🧳 $it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(money(t.amountMinor), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (found.size > shown) TextButton(onClick = { shown += 30 }) { Text("Show ${(found.size - shown).coerceAtMost(30)} more") }
        }
    }
}


/** This year's totals: what's saved and where the year is heading. Month by month is the chart below it. */
@Composable
internal fun ColumnScope.YearOutlookSummary(o: com.houseaffairs.app.core.data.YearOutlook, money: (Long) -> String) {
    val accent = LocalHouseColors.current.money
    Text("Saved this year", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(money(o.savedSoFarMinor), style = MaterialTheme.typography.headlineMedium)
    Text("On track for about ${money(o.projectedMinor)} by December", style = MaterialTheme.typography.bodyMedium, color = accent.color)
    o.insight?.let {
        Spacer(Modifier.height(8.dp))
        Text("✨ $it", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Planned spends, trips and dreams coming up in the next year; each opens where it lives. */
@Composable
internal fun ComingUpCard(o: com.houseaffairs.app.core.data.YearOutlook, money: (Long) -> String, onOpen: (com.houseaffairs.app.core.data.BigItem) -> Unit) {
    Column {
        if (o.upcoming.isNotEmpty()) {
            Text("Coming up in the next year", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp))
            HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                o.upcoming.forEachIndexed { i, item ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen(item) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (item.kind) { "Trip" -> "🧳"; "Dream" -> "⭐"; else -> "📅" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            // Say what it is and where it lives: a "Goa trip" planned spend isn't a trip.
                            Text("${when (item.kind) { "Planned" -> "Planned spend"; "Dream" -> "Dream · vision board"; else -> item.kind }} · ${com.houseaffairs.app.feature.add.formatDate(item.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item.amountMinor?.let { Text(money(it), style = MaterialTheme.typography.titleSmall) }
                    }
                }
            }
        }
    }
}