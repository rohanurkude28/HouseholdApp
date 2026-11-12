package com.houseaffairs.app.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.houseaffairs.app.core.data.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import com.houseaffairs.app.core.data.allocatedFor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Budget
import com.houseaffairs.app.core.data.SUGGESTED_SPLIT
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.categoryIcon
import com.houseaffairs.app.core.util.formatMinor
import com.houseaffairs.app.core.util.toMinorUnits
import java.math.BigDecimal
import java.util.Currency

private fun wholeUnits(minor: Long, currency: String): String {
    val digits = Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
    return BigDecimal.valueOf(minor, digits).stripTrailingZeros().toPlainString()
}

private fun parseMinor(text: String, currency: String): Long? =
    text.replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.let { toMinorUnits(it, currency) }

/** One-minute budget setup: amount, when the month starts, what to save, and a split by category. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetSetupSheet(
    existing: Budget?,
    currency: String,
    /** Dreams that a monthly amount can go to, straight from the budget. */
    dreams: List<com.houseaffairs.app.core.data.Dream> = emptyList(),
    onSave: (Budget, com.houseaffairs.app.core.data.LimitFrom) -> Unit,
    onDismiss: () -> Unit,
    onCategoryUse: (String, (Int) -> Unit) -> Unit = { _, done -> done(0) },
    onMoveCategory: (from: String, to: String?, done: () -> Unit) -> Unit = { _, _, done -> done() },
    onDeleteWithHistory: (String, () -> Unit) -> Unit = { _, done -> done() },
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val money = LocalHouseColors.current.money
    var income by remember { mutableStateOf(existing?.incomeMinor?.takeIf { it > 0 }?.let { wholeUnits(it, currency) } ?: "") }
    var limit by remember { mutableStateOf(existing?.let { wholeUnits(it.limitMinor, currency) } ?: "") }
    var startDay by remember { mutableStateOf(existing?.cycleStartDay ?: 1) }
    var savings by remember { mutableStateOf(existing?.savingsGoalMinor?.takeIf { it > 0 }?.let { wholeUnits(it, currency) } ?: "") }
    val cards = remember { mutableStateListOf<Card>().apply { existing?.cards?.let(::addAll) } }
    val split = remember {
        mutableStateMapOf<String, String>().apply {
            existing?.categories?.forEach { (k, v) -> put(k, wholeUnits(v, currency)) }
        }
    }
    // The family's own categories (cook, maid, insurance…); they stay until removed here.
    val custom = remember { mutableStateListOf<String>().apply { existing?.customCategories?.let(::addAll) } }
    var askCategory by remember { mutableStateOf(false) }
    // The family's own category being renamed or deleted.
    var managing by remember { mutableStateOf<String?>(null) }
    var limitFrom by remember { mutableStateOf(com.houseaffairs.app.core.data.LimitFrom.THIS_MONTH) }
    // This month's amount per dream, as typed.
    val monthStart = com.houseaffairs.app.core.data.cycleFor(java.time.LocalDate.now(), existing?.cycleStartDay ?: 1).start
    val toDreams = remember {
        mutableStateMapOf<String, String>().apply {
            existing?.allocatedFor(monthStart)?.forEach { (id, m) -> put(id, wholeUnits(m, currency)) }
        }
    }
    val limitMinor = parseMinor(limit, currency)
    val savingsMinor = parseMinor(savings, currency) ?: 0
    val assigned = split.values.sumOf { parseMinor(it, currency) ?: 0 }
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = money.container, selectedLabelColor = money.color)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(if (existing == null) "Set your monthly budget" else "Edit budget", style = MaterialTheme.typography.headlineSmall)

            // Optional: with income, one tap sets the savings and leaves the rest as the budget.
            Column {
                OutlinedTextField(
                    value = income,
                    onValueChange = { income = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monthly income (optional)") },
                    prefix = { Text("$currency  ") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                parseMinor(income, currency)?.takeIf { it > 0 }?.let { incomeMinor ->
                    Spacer(Modifier.height(8.dp))
                    Text("How much to save?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30, 40).forEach { pct ->
                            val save = incomeMinor * pct / 100
                            FilterChip(
                                selected = parseMinor(savings, currency) == save && parseMinor(limit, currency) == incomeMinor - save,
                                onClick = {
                                    savings = wholeUnits(save, currency)
                                    limit = wholeUnits(incomeMinor - save, currency)
                                },
                                label = { Text("$pct%") },
                                shape = CircleShape,
                                colors = chipColors,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = limit,
                onValueChange = { limit = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Monthly budget") },
                prefix = { Text("$currency  ") },
                placeholder = { Text("100000") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Salary day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Your budget month runs from one salary to the next. Credit cards have their own cycle, below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 7, 10, 15, 25, 28).forEach { day ->
                        FilterChip(
                            selected = startDay == day,
                            onClick = { startDay = day },
                            label = { Text(ordinal(day)) },
                            shape = CircleShape,
                            colors = chipColors,
                        )
                    }
                }
            }

            Column {
                OutlinedTextField(
                    value = savings,
                    onValueChange = { savings = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Savings goal this month") },
                    prefix = { Text("$currency  ") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (limitMinor != null && limitMinor > 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(10, 20, 30).forEach { pct ->
                            FilterChip(
                                selected = savingsMinor == limitMinor * pct / 100,
                                onClick = { savings = wholeUnits(limitMinor * pct / 100, currency) },
                                label = { Text("$pct%") },
                                shape = CircleShape,
                                colors = chipColors,
                            )
                        }
                    }
                }
            }

            // A new monthly amount: from when? Past months keep theirs unless it was a mistake.
            if (existing != null && limitMinor != null && limitMinor != existing.limitMinor) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("The new amount applies", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            com.houseaffairs.app.core.data.LimitFrom.THIS_MONTH to "From this month",
                            com.houseaffairs.app.core.data.LimitFrom.NEXT_MONTH to "From next month",
                            com.houseaffairs.app.core.data.LimitFrom.ALWAYS to "To every month",
                        ).forEach { (f, label) ->
                            FilterChip(selected = limitFrom == f, onClick = { limitFrom = f }, label = { Text(label) }, shape = CircleShape, colors = chipColors)
                        }
                    }
                    Text(
                        when (limitFrom) {
                            com.houseaffairs.app.core.data.LimitFrom.THIS_MONTH -> "Past months keep what they had, so the gullak stays as it is."
                            com.houseaffairs.app.core.data.LimitFrom.NEXT_MONTH -> "This month stays at ${formatMinor(existing.limitMinor, currency)}; the new amount starts next month."
                            com.houseaffairs.app.core.data.LimitFrom.ALWAYS -> "For fixing a wrong amount: every month since you started is counted again, and the gullak changes to match."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Split by category", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (limitMinor == null) "Optional" else "${formatMinor(assigned, currency)} of ${formatMinor(limitMinor, currency)} assigned",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (limitMinor != null && assigned > limitMinor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    enabled = limitMinor != null && limitMinor > 0,
                    onClick = {
                        split.clear()
                        SUGGESTED_SPLIT.forEach { (category, pct) -> split[category] = wholeUnits(limitMinor!! * pct / 100, currency) }
                    },
                ) { Text("Suggest a split") }
            }

            // Only the categories in use get a row; the rest are one tap away, so the list stays short.
            split.keys.toList().forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBlob(categoryIcon(category), money, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(category, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (category in custom) {
                        IconButton(onClick = { managing = category }) { Icon(Icons.Outlined.Edit, contentDescription = "Rename or delete $category", modifier = Modifier.size(18.dp)) }
                    }
                    IconButton(onClick = { split.remove(category); custom.remove(category) }) { Icon(Icons.Outlined.Close, contentDescription = "Remove $category", modifier = Modifier.size(18.dp)) }
                    OutlinedTextField(
                        value = split[category] ?: "",
                        onValueChange = { v -> split[category] = v.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = { Text("—") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(132.dp),
                    )
                }
            }

            val unused = (SUGGESTED_SPLIT.keys + com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES + custom).distinct().filter { it !in split.keys && it != "Other" }
            Text(if (split.isEmpty()) "Add a category" else "Add another", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                unused.forEach { c ->
                    FilterChip(selected = false, onClick = { split[c] = "" }, label = { Text("+ $c") }, shape = CircleShape)
                }
                FilterChip(selected = askCategory, onClick = { askCategory = !askCategory }, label = { Text("+ Other") }, shape = CircleShape, colors = chipColors)
            }
            // "Other" opens a box right here to name it: cook, maid, insurance…
            if (askCategory) {
                var name by remember { mutableStateOf("") }
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                fun addIt() {
                    val n = name.trim().replaceFirstChar { it.uppercaseChar() }
                    val known = (com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES + custom).firstOrNull { it.equals(n, ignoreCase = true) }
                    if (n.isNotEmpty()) {
                        if (known == null) custom.add(n)
                        if (split[known ?: n] == null) split[known ?: n] = ""
                    }
                    name = ""
                    askCategory = false
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text("What is it?") },
                        placeholder = { Text("Cook, maid, insurance…") },
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addIt() }),
                        modifier = Modifier.weight(1f).then(Modifier.focusRequester(focus)),
                    )
                    Button(onClick = ::addIt, enabled = name.isNotBlank(), shape = CircleShape) { Text("Add") }
                }
                androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
            }

            // Your own categories, also ones without a limit, can be renamed or deleted here.
            val unlisted = custom.filter { it !in split.keys }
            if (unlisted.isNotEmpty()) {
                Text("Your categories", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    unlisted.forEach { c -> androidx.compose.material3.AssistChip(onClick = { managing = c }, label = { Text(c) }, trailingIcon = { Icon(Icons.Outlined.Edit, contentDescription = "Rename or delete $c", modifier = Modifier.size(16.dp)) }, shape = CircleShape) }
                }
            }
            managing?.let { name ->
                ManageCategoryDialog(
                    name, custom, onCategoryUse,
                    onRename = { to ->
                        onMoveCategory(name, to) {}
                        // Renamed onto one that exists: they merge, limits added up.
                        if (custom.any { it.equals(to, ignoreCase = true) }) custom.remove(name) else custom[custom.indexOf(name)] = to
                        split.remove(name)?.let { moved -> split[to] = ((split[to]?.toDoubleOrNull() ?: 0.0) + (moved.toDoubleOrNull() ?: 0.0)).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } }
                        managing = null
                    },
                    onDelete = { withHistory ->
                        val done = { custom.remove(name); split.remove(name); managing = null }
                        if (withHistory) onDeleteWithHistory(name) { done() } else onMoveCategory(name, null) { done() }
                    },
                    onDismiss = { managing = null },
                )
            }

            // Dreams get their share first, every month: it isn't spent and never reaches the gullak.
            if (dreams.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save toward dreams each month", style = MaterialTheme.typography.titleMedium)
                    Text("Set aside from the budget every month, from this one on. You can still move money back if you need it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    dreams.filter { !it.achieved }.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${d.emoji} ${d.title}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                value = toDreams[d.id] ?: "",
                                onValueChange = { v -> toDreams[d.id] = v.filter { c -> c.isDigit() || c == '.' } },
                                placeholder = { Text("—") }, suffix = { Text("/mo") }, singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(150.dp),
                            )
                        }
                    }
                }
            }

            // Credit cards: each has its own statement cycle, separate from the salary month.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Credit cards", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Spends count in your budget the day you make them. The card cycle shows how big each bill is and when it's due.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cards.forEachIndexed { i, c ->
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = c.name,
                                onValueChange = { v -> cards[i] = c.copy(name = v) },
                                label = { Text("Card name") },
                                placeholder = { Text("HDFC Regalia") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { cards.removeAt(i) }) { Icon(Icons.Outlined.Close, contentDescription = "Remove card") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DayField("Statement from", c.statementDay, Modifier.weight(1f)) { cards[i] = c.copy(statementDay = it) }
                            DayField("Bill due on", c.dueDay, Modifier.weight(1f)) { cards[i] = c.copy(dueDay = it) }
                        }
                        Text(
                            "Cycle: ${ordinal(c.statementDay)} to ${if (c.statementDay == 1) "month end" else ordinal(c.statementDay - 1)} · bill due on the ${ordinal(c.dueDay)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                androidx.compose.material3.OutlinedButton(onClick = { cards.add(Card("", 1, 20)) }, shape = CircleShape) { Text("+ Add a credit card") }
            }

            Button(
                onClick = {
                    onSave(
                        Budget(
                            limitMinor = limitMinor ?: 0,
                            currency = currency,
                            incomeMinor = parseMinor(income, currency) ?: 0,
                            cycleStartDay = startDay,
                            savingsGoalMinor = savingsMinor,
                            categories = split.mapNotNull { (k, v) -> parseMinor(v, currency)?.takeIf { it > 0 }?.let { k to it } }.toMap(),
                            cards = cards.filter { it.name.isNotBlank() },
                            paidBills = existing?.paidBills.orEmpty(),
                            customCategories = custom.toList(),
                            limitHistory = existing?.limitHistory.orEmpty(),
                            dreamAllocations = com.houseaffairs.app.core.data.allocationsAfter(
                                existing?.dreamAllocations.orEmpty(),
                                toDreams.mapNotNull { (id, v) -> parseMinor(v, currency)?.takeIf { it > 0 }?.let { id to it } }.toMap(),
                                monthStart,
                            ),
                        ),
                        limitFrom,
                    )
                },
                enabled = limitMinor != null && limitMinor > 0,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save budget", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

internal fun ordinal(day: Int): String = day.toString() + when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

/** A day of the month (1–31) typed as a number. */
@Composable
private fun DayField(label: String, day: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember(day) { mutableStateOf(day.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v.filter { it.isDigit() }.take(2); text.toIntOrNull()?.takeIf { it in 1..31 }?.let(onChange) },
        label = { Text(label) },
        suffix = { Text(ordinal(text.toIntOrNull()?.coerceIn(1, 31) ?: day).takeLast(2)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/**
 * Rename or delete one of the family's categories. Deleting one nothing is filed under is
 * immediate; otherwise it asks what happens to those expenses: moved to Other, or deleted too.
 */
@Composable
private fun ManageCategoryDialog(
    name: String,
    custom: List<String>,
    onCategoryUse: (String, (Int) -> Unit) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (withHistory: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(name) }
    var used by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(name) { onCategoryUse(name) { used = it } }
    val newName = text.trim().replaceFirstChar { it.uppercaseChar() }
    val clash = newName.isNotEmpty() && !newName.equals(name, ignoreCase = true) && custom.any { it.equals(newName, ignoreCase = true) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deleting) "Delete $name?" else name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val count = used
                when {
                    count == null -> Text("Checking what's filed under it…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    deleting && count > 0 -> Text("$count expense${if (count == 1) " is" else "s are"} filed under $name. Keep them under Other, or delete them too? You can also rename the category instead.")
                    deleting -> Text("Nothing is filed under it, so nothing else changes.")
                    else -> Text(if (count == 0) "Nothing is filed under it yet." else "$count expense${if (count == 1) "" else "s"} filed under it; renaming moves them along.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!deleting) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") },
                        isError = clash, supportingText = if (clash) ({ Text("You already have $newName; its expenses would be combined.") }) else null,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
        },
        confirmButton = {
            val count = used
            when {
                deleting && count != null && count > 0 -> Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onDelete(false) }) { Text("Keep them under Other") }
                    TextButton(onClick = { onDelete(true) }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete them too") }
                }
                deleting -> TextButton(onClick = { onDelete(false) }, enabled = count != null) { Text("Delete") }
                else -> TextButton(onClick = { onRename(newName) }, enabled = newName.isNotEmpty() && !newName.equals(name, ignoreCase = false)) { Text("Rename") }
            }
        },
        dismissButton = {
            if (deleting) TextButton(onClick = { deleting = false }) { Text("Rename instead") }
            else TextButton(onClick = { deleting = true }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete…") }
        },
    )
}
