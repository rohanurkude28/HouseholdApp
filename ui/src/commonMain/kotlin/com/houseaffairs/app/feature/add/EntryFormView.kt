package com.houseaffairs.app.feature.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.categoryIcon
import com.houseaffairs.app.feature.quickadd.ListTemplate

/** Required fields still empty, in form order. */
fun missingFields(type: EntryType, values: FormValues): List<FieldSpec> =
    type.fields.filter { it.required && it.visibleWhen(values) && !it.isFilled(values[it.key]) }

/** "Edit expense", "Add a gift idea", "Add to Books", or the kind's own title. */
fun formTitleOf(type: EntryType, values: FormValues, editing: Boolean, locked: Set<String>): String = when {
    editing -> "Edit ${type.label.lowercase()}"
    "template" in locked -> ListTemplate.of(values["template"] as? String)?.let { t -> if (t == ListTemplate.GIFTS) "Add a gift idea" else "Add to ${t.title}" } ?: type.formTitle
    else -> type.formTitle
}

/**
 * An entry's form, the same on both phones: a header with the kind's icon, the essential
 * fields (expenses lead with what, how much and who paid), "More details" for the rest, then
 * Save. Each phone brings its own extras through the slots (scan and voice in [actions],
 * "who can see" and address hints in [moreDetails], past payments in [below]).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EntryFormView(
    type: EntryType,
    values: FormValues,
    onChange: (String, Any?) -> Unit,
    members: List<Member>,
    myUid: String,
    editing: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    dynamicOptions: Map<String, List<String>> = emptyMap(),
    /** Fields set by how the form was opened (the list a wish goes on); not shown. */
    locked: Set<String> = emptySet(),
    showErrors: Boolean = false,
    showMore: Boolean? = null,
    onShowMore: ((Boolean) -> Unit)? = null,
    ongoingTrip: String? = null,
    editId: String? = null,
    note: String? = null,
    onSaveAnother: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    afterField: @Composable (FieldSpec) -> Unit = {},
    /** The family's own tags, so matching ones are suggested. */
    knownTags: List<String> = emptyList(),
    /** Asks the assistant for tags (Android with Smart help on); null where there's none. */
    onAskTags: (suspend (String) -> List<String>)? = null,
    moreDetails: @Composable ColumnScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val accent = type.accent(LocalHouseColors.current)
    var ownMore by remember { mutableStateOf(false) }
    val more = showMore ?: ownMore
    val setMore: (Boolean) -> Unit = onShowMore ?: { ownMore = it }
    val visible = type.fields.filter { spec ->
        spec.key !in locked && spec.visibleWhen(values) &&
            !(spec is ChoiceSpec && spec.hideWithoutDynamic && dynamicOptions[spec.dynamicOptions].isNullOrEmpty())
    }
    val missing = missingFields(type, values)
    val missingKeys = if (showErrors) missing.map { it.key }.toSet() else emptySet()
    // Expenses lead with what, how much and who paid (like Splitwise); the rest follows as usual.
    val headerKeys = if (type == EntryType.EXPENSE) EXPENSE_HEADER_KEYS else emptySet()
    val essential = visible.filter { it.essential && it.key !in headerKeys }
    val extra = visible.filter { !it.essential && it.key !in headerKeys }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            IconBlob(type.icon, accent, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(formTitleOf(type, values, editing, locked), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            actions()
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = accent.color) }
        if (type == EntryType.EXPENSE) ExpenseHeader(values, onChange, members, myUid, dynamicOptions, accent, missingKeys, ongoingTrip, editId)
        essential.forEach { spec ->
            // The shop field wears the category's icon, so a guess made while typing is seen at once.
            val leading = if (type == EntryType.EXPENSE && spec.key == "merchant") categoryIcon(values["category"] as? String ?: "") else null
            FieldEditor(spec, values[spec.key], { onChange(spec.key, it) }, accent, members, spec.key in missingKeys, dynamicOptions, leading)
            if (spec.key == "tags") TagHints(type, values, knownTags, onAskTags) { tag -> onChange("tags", com.houseaffairs.app.core.data.withTag(values["tags"], tag)) }
            afterField(spec)
        }
        TextButton(onClick = { setMore(!more) }) {
            Icon(if (more) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (more) "Fewer details" else "More details${extra.size.let { if (it > 0) " ($it)" else "" }}")
        }
        AnimatedVisibility(more) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                extra.forEach { spec ->
                    FieldEditor(spec, values[spec.key], { onChange(spec.key, it) }, accent, members, spec.key in missingKeys, dynamicOptions)
                    afterField(spec)
                }
                moreDetails()
            }
        }
        if (showErrors && missing.isNotEmpty()) {
            Text("Please fill: ${missing.joinToString { it.label.lowercase() }}", color = MaterialTheme.colorScheme.error)
        }
        below()
        Button(onClick = onSave, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (editing) "Save changes" else "Save", style = MaterialTheme.typography.titleMedium)
        }
        if (!editing && onSaveAnother != null) {
            OutlinedButton(onClick = onSaveAnother, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Text("Save + add another ${type.label.lowercase()}")
            }
        }
        if (editing && onDelete != null) {
            // Two taps, so a stray one doesn't delete; Undo is offered afterwards too.
            var confirming by remember { mutableStateOf(false) }
            TextButton(
                onClick = { if (confirming) onDelete() else confirming = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(if (confirming) "Tap again to delete" else "Delete ${type.label.lowercase()}") }
        }
    }
}

/** "Add: #goa #riya" under Tags, one tap each, and "✨ Suggest tags" where an assistant can help. */
@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
private fun TagHints(type: EntryType, values: FormValues, known: List<String>, ask: (suspend (String) -> List<String>)?, onAdd: (String) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var fromAi by remember(type) { mutableStateOf(emptyList<String>()) }
    var thinking by remember { mutableStateOf(false) }
    val current = com.houseaffairs.app.core.data.tagsOf(values["tags"]).toSet()
    val hints = (com.houseaffairs.app.core.data.suggestTags(values, known) + fromAi).filter { it !in current }.distinct()
    if (hints.isEmpty() && ask == null) return
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        hints.forEach { tag ->
            androidx.compose.material3.SuggestionChip(onClick = { onAdd(tag) }, label = { Text("+ #$tag") }, shape = CircleShape)
        }
        if (ask != null) {
            androidx.compose.material3.SuggestionChip(
                enabled = !thinking,
                onClick = {
                    val about = listOfNotNull(type.label, type.titleKey?.let { values[it] as? String }, values["category"] as? String, values["note"] as? String).joinToString(" · ")
                    thinking = true
                    scope.launch { fromAi = runCatching { ask(about) }.getOrDefault(emptyList()); thinking = false }
                },
                label = { Text(if (thinking) "Thinking…" else "✨ Suggest tags") }, shape = CircleShape,
            )
        }
    }
}
