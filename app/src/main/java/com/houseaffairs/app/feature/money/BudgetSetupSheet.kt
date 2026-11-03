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
fun BudgetSetupSheet(existing: Budget?, currency: String, onSave: (Budget) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val money = LocalHouseColors.current.money
    var limit by remember { mutableStateOf(existing?.let { wholeUnits(it.limitMinor, currency) } ?: "") }
    var startDay by remember { mutableStateOf(existing?.cycleStartDay ?: 1) }
    var savings by remember { mutableStateOf(existing?.savingsGoalMinor?.takeIf { it > 0 }?.let { wholeUnits(it, currency) } ?: "") }
    val cards = remember { mutableStateListOf<Card>().apply { existing?.cards?.let(::addAll) } }
    val split = remember {
        mutableStateMapOf<String, String>().apply {
            existing?.categories?.forEach { (k, v) -> put(k, wholeUnits(v, currency)) }
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
                    IconButton(onClick = { split.remove(category) }) { Icon(Icons.Outlined.Close, contentDescription = "Remove $category", modifier = Modifier.size(18.dp)) }
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

            val unused = (SUGGESTED_SPLIT.keys + com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES).distinct().filter { it !in split.keys }
            if (unused.isNotEmpty()) {
                Text(if (split.isEmpty()) "Add a category" else "Add another", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    unused.forEach { c ->
                        FilterChip(selected = false, onClick = { split[c] = "" }, label = { Text("+ $c") }, shape = CircleShape)
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
                            cycleStartDay = startDay,
                            savingsGoalMinor = savingsMinor,
                            categories = split.mapNotNull { (k, v) -> parseMinor(v, currency)?.takeIf { it > 0 }?.let { k to it } }.toMap(),
                            cards = cards.filter { it.name.isNotBlank() },
                            paidBills = existing?.paidBills.orEmpty(),
                        ),
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
