package com.houseaffairs.app.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.time.LocalDate

/** The expense fields the header below shows itself; the form lists the rest as usual. */
val EXPENSE_HEADER_KEYS = setOf("merchant", "amount", "paidBy", "paymentMethod", "date", "category", "trip")

/**
 * An expense the way Splitwise takes one: what it was, how much, then one line of who paid, how,
 * when and what for, each a chip that opens its choices. Everything is pre-set (you, today, a
 * guessed category), so most spends are two fields and Save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseHeader(
    values: FormValues,
    onChange: (String, Any?) -> Unit,
    members: List<com.houseaffairs.app.core.model.Member>,
    myUid: String,
    dynamicOptions: Map<String, List<String>>,
    accent: Accent,
    missing: Set<String>,
    /** A trip happening today, offered as one tap. */
    ongoingTrip: String? = null,
    /** Which entry is open, so "you paid" is set once per entry. */
    editId: String? = null,
) {
    val specs = EntryType.EXPENSE.fields.filter { it.key in EXPENSE_HEADER_KEYS && it.visibleWhen(values) }.associateBy { it.key }
    // Whoever is adding it paid, unless they say otherwise.
    LaunchedEffect(editId, myUid) {
        if (values["paidBy"] == null && myUid.isNotEmpty()) onChange("paidBy", listOf(myUid))
    }
    var open by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        specs["merchant"]?.let { spec ->
            FieldEditor(spec, values["merchant"], { onChange("merchant", it) }, accent, members, "merchant" in missing, dynamicOptions,
                com.houseaffairs.app.core.ui.categoryIcon(values["category"] as? String ?: ""))
        }
        specs["amount"]?.let { spec -> FieldEditor(spec, values["amount"], { onChange("amount", it) }, accent, members, "amount" in missing, dynamicOptions) }

        // Who paid: everyone in the home, you first.
        if (members.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Paid by", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val paidBy = (values["paidBy"] as? List<*>)?.firstOrNull() as? String
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.sortedBy { it.uid != myUid }.forEach { m ->
                        FilterChip(
                            selected = paidBy == m.uid, onClick = { onChange("paidBy", listOf(m.uid)) },
                            label = { Text(if (m.uid == myUid) "You" else m.displayName.substringBefore(' ')) }, shape = CircleShape,
                        )
                    }
                }
            }
        }

        // How, when and what for, as one line of chips; each opens its choices underneath.
        val pay = values["paymentMethod"] as? String
        val day = values["date"] as? LocalDate
        val category = values["category"] as? String
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if ("paymentMethod" in specs) SummaryChip("💳 ${pay ?: "Paid how?"}", "paymentMethod" in missing && pay == null, open == "paymentMethod") { open = if (open == "paymentMethod") null else "paymentMethod" }
            if ("date" in specs) SummaryChip("📅 ${day?.let { formatDate(it) } ?: "When?"}", "date" in missing && day == null, open == "date") { open = if (open == "date") null else "date" }
            if ("category" in specs) SummaryChip("🏷️ ${category ?: "Category?"}", "category" in missing && category == null, open == "category") { open = if (open == "category") null else "category" }
            // A trip on today: one tap files it there; tap again to take it out.
            val trip = values["trip"] as? String
            (trip ?: ongoingTrip)?.let { name ->
                FilterChip(
                    selected = trip != null, onClick = { onChange("trip", if (trip != null) null else name) },
                    label = { Text(if (trip != null) "🧳 Part of $name" else "🧳 $name trip?") }, shape = CircleShape,
                )
            }
        }
        open?.let { key ->
            specs[key]?.let { spec ->
                FieldEditor(spec, values[key], { value ->
                    onChange(key, value)
                    // A pick closes it; typing your own keeps it open.
                    if (spec is ChoiceSpec && !spec.multi) open = null
                }, accent, members, key in missing, dynamicOptions)
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String, error: Boolean, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text + if (selected) "  ▴" else "  ▾") },
        shape = CircleShape,
        colors = if (error) AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error) else AssistChipDefaults.assistChipColors(),
    )
}
