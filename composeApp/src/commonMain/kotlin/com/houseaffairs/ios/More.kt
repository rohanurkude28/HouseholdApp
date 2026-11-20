package com.houseaffairs.ios

import com.houseaffairs.app.core.data.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.BudgetSummary
import com.houseaffairs.app.core.data.CHORE_IDEAS
import com.houseaffairs.app.core.data.REPEAT_CHOICES
import com.houseaffairs.app.core.data.spendLine
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.dayOfMonth
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.core.time.localDate
import com.houseaffairs.app.core.time.monthValue
import com.houseaffairs.app.core.time.year
import com.houseaffairs.app.core.util.decimalOf
import com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Which saved entry is open for editing. */
data class Editing(val collection: String, val id: String)

private sealed interface Field {
    val key: String
    val label: String
    data class Words(override val key: String, override val label: String) : Field
    data class Amount(override val key: String, override val label: String) : Field
    data class Day(override val key: String, override val label: String) : Field
    data class Pick(override val key: String, override val label: String, val options: List<String>) : Field
}

/** The fields worth changing for each kind, under the names Android saves them. */
private fun fieldsFor(collection: String): List<Field> = when (collection) {
    "transactions" -> listOf(Field.Words("merchant", "Where or who"), Field.Amount("amountMinor", "Amount"), Field.Pick("category", "Category", EXPENSE_CATEGORIES), Field.Day("date", "When"), Field.Words("note", "Note"))
    "shopping" -> listOf(Field.Words("title", "What"), Field.Words("note", "Note"))
    "stock", "medicines" -> listOf(Field.Words("title", "What"), Field.Day("expiry", "Use by"), Field.Words("keptAt", "Kept in"), Field.Words("note", "Note"))
    "tasks" -> listOf(Field.Words("title", "What"), Field.Day("due", "When"), Field.Words("time", "At (like 5:30 pm)"), Field.Pick("repeat", "Repeats", REPEAT_CHOICES), Field.Words("note", "Note"))
    "events" -> listOf(Field.Words("title", "What"), Field.Day("date", "Date"), Field.Words("note", "Note"))
    "keptAt" -> listOf(Field.Words("title", "What"), Field.Words("location", "Where it's kept"), Field.Words("note", "Note"))
    else -> listOf(Field.Words("title", "What"), Field.Words("note", "Note"))
}

/** Change or delete anything saved; delete waits for Undo before it really goes. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditSheet(editing: Editing, store: FamilyStore, onSave: (String, suspend () -> Unit) -> Unit, onDelete: (String) -> Unit, onDone: () -> Unit) {
    val fields = remember(editing) { fieldsFor(editing.collection) }
    val text = remember(editing) { mutableStateMapOf<String, String>() }
    val days = remember(editing) { mutableStateMapOf<String, LocalDate?>() }
    var loaded by remember(editing) { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(editing) {
        runCatching { store.load(editing.collection, editing.id) }.getOrNull()?.let { d ->
            fields.forEach { f ->
                when (f) {
                    is Field.Amount -> text[f.key] = d.long(f.key)?.let { m -> if (m % 100 == 0L) (m / 100).toString() else "${m / 100}.${(m % 100).toString().padStart(2, '0')}" }.orEmpty()
                    is Field.Day -> days[f.key] = d.date(f.key)
                    else -> text[f.key] = d.str(f.key).orEmpty()
                }
            }
        }
        loaded = true
    }
    val title = text["title"]?.ifBlank { null } ?: text["merchant"]?.ifBlank { null } ?: "This"
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Change it", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            if (!loaded) item { Text("Loading…") } else {
                items(fields, key = { it.key }) { f ->
                    when (f) {
                        is Field.Words, is Field.Amount -> OutlinedTextField(
                            text[f.key].orEmpty(), { text[f.key] = it }, label = { Text(f.label) }, modifier = Modifier.fillMaxWidth(),
                            singleLine = f !is Field.Words || f.key != "note",
                            keyboardOptions = if (f is Field.Amount) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal) else androidx.compose.foundation.text.KeyboardOptions.Default,
                        )
                        is Field.Day -> Column {
                            Text(f.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { picking = f.key }) { Text(days[f.key]?.format("EEE d MMM yyyy") ?: "Pick a day") }
                                if (days[f.key] != null) TextButton(onClick = { days[f.key] = null }) { Text("No date") }
                            }
                        }
                        is Field.Pick -> Column {
                            Text(f.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                f.options.forEach { o -> FilterChip(selected = text[f.key] == o, onClick = { text[f.key] = o }, label = { Text(o) }, shape = CircleShape) }
                            }
                        }
                    }
                }
                item {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        val changes = fields.map { f ->
                            f.key to when (f) {
                                is Field.Amount -> text[f.key]?.trim()?.let { decimalOf(it) }?.minor()
                                is Field.Day -> days[f.key]?.let(::day)
                                is Field.Pick -> text[f.key]?.takeIf { it.isNotBlank() && it != "Never" }
                                is Field.Words -> text[f.key]?.trim()?.ifBlank { null }
                            }
                        }.filter { (k, v) -> v != null || k != "title" }
                        onSave("$title · saved") { store.update(editing.collection, editing.id, changes); {} }
                        onDone()
                    }) { Text("Save") }
                }
                item {
                    TextButton(onClick = { onDelete("$title · deleted"); onDone() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    picking?.let { key ->
        val state = rememberDatePickerState(initialSelectedDateMillis = (days[key] ?: today()).let { d -> kotlinx.datetime.LocalDate(d.year, d.monthValue, d.dayOfMonth).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() })
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date.let { d -> days[key] = localDate(d.year, d.monthNumber, d.dayOfMonth) } }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** One thing found by search: what it is, and where it lives (to open it). */
data class Found(val entry: Entry, val kind: String, val collection: String)

/** Every word must appear in the name, line or group; "eggs" finds "Egg" too. */
fun searchFound(query: String, all: List<Found>): List<Found> {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }.map { if (it.length > 3) it.removeSuffix("s") else it }
    if (words.isEmpty()) return emptyList()
    return all.filter { f -> val hay = "${f.entry.title} ${f.entry.detail} ${f.entry.group} ${f.kind}".lowercase(); words.all { it in hay } }
        .sortedBy { f -> if (words.any { f.entry.title.lowercase().startsWith(it) }) 0 else 1 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSheet(all: List<Found>, onOpen: (Found) -> Unit, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val found = remember(query, all) { searchFound(query, all) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(query, { query = it }, placeholder = { Text("Find anything: milk, crocin, keys, DMart…") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            if (query.length > 1 && found.isEmpty()) item { Text("Nothing saved about “$query” yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(found, key = { it.collection + it.entry.id }) { f ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(f) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(f.entry.title)
                        Text(listOfNotNull(f.kind, f.entry.detail.ifBlank { null }, f.entry.date?.format("d MMM")).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The month's budget, as on Android: what's left, and what to do about it. */
@Composable
internal fun BudgetCard(summary: BudgetSummary) {
    val money = { m: Long -> formatMinor(m, summary.currency) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0E9F76))) {
        Column(Modifier.padding(20.dp)) {
            Text(if (summary.leftMinor < 0) "Over this month" else "Left this month", color = Color.White.copy(alpha = 0.85f))
            Text(money(kotlin.math.abs(summary.leftMinor)), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text("of ${money(summary.limitMinor)}", color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { ((summary.spentMinor + summary.plannedMinor).toFloat() / summary.limitMinor.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
            )
            Spacer(Modifier.height(10.dp))
            Text("Spent ${money(summary.spentMinor)} · planned ${money(summary.plannedMinor)}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(spendLine(summary, money, null), color = Color.White)
        }
    }
}

/** Plans → Chores: the family's shared chores, ideas to add in one tap, and taking one off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoresPart(chores: List<Entry>, onAdd: (String, String) -> Unit, onOpen: (Entry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Chores anyone can do. Done → it's back after its interval.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        chores.forEach { c ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(c) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(c.title)
                    Text(
                        listOfNotNull(c.repeat, c.date?.let { if (c.late) "late since " + it.format("d MMM") else "next " + it.format("EEE d MMM") }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = if (c.late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val ideas = CHORE_IDEAS.filter { i -> chores.none { it.title.equals(i.title, ignoreCase = true) } }
        if (ideas.isNotEmpty()) {
            Text("Add in one tap", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ideas.forEach { i -> FilterChip(selected = false, onClick = { onAdd(i.title, i.repeat) }, label = { Text("${i.emoji} ${i.title} · ${i.repeat.lowercase()}") }, shape = CircleShape) }
            }
        }
    }
}
