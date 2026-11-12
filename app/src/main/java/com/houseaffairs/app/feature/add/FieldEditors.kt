package com.houseaffairs.app.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Ingredient
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.ui.Avatar
import com.houseaffairs.app.core.ui.CurrencyPickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Renders the right editor for [spec]. */
@Composable
fun FieldEditor(
    spec: FieldSpec,
    value: Any?,
    onChange: (Any?) -> Unit,
    accent: Accent,
    members: List<Member>,
    showError: Boolean,
    dynamicOptions: Map<String, List<String>> = emptyMap(),
    /** Shown inside a text field, in the accent colour ("Where or who" wears its category). */
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        if ((spec !is TextSpec || spec.isMacros) && spec !is MoneySpec) {
            Text(
                spec.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        when (spec) {
            is TextSpec -> if (spec.isMacros) MacrosEditor(value as? String, onChange) else TextEditor(spec, value as? String ?: "", onChange, showError, leading, accent)
            is MoneySpec -> MoneyEditor(spec, value as? MoneyValue ?: MoneyValue("", "USD"), onChange, showError)
            is ChoiceSpec -> {
                val extra = spec.dynamicOptions?.let { dynamicOptions[it] }.orEmpty()
                val base = if (extra.isNotEmpty() && spec.replacedByDynamic != null) spec.options - spec.replacedByDynamic else spec.options
                // "Other" stays last, after the family's own options.
                ChoiceEditor(spec.copy(options = (base + extra).distinct().sortedBy { it == "Other" }), value, onChange, accent)
            }
            is DateSpec -> DateEditor(spec, value as? LocalDate, onChange, accent)
            is QuantitySpec -> QuantityEditor(spec, value as? QuantityValue ?: QuantityValue("", spec.units.first()), onChange, accent)
            is LevelSpec -> LevelEditor(spec, value as? Int ?: 0, onChange)
            is MembersSpec -> MembersEditor(spec, (value as? List<*>)?.filterIsInstance<String>().orEmpty(), onChange, members, accent)
            is IngredientsSpec -> IngredientsEditor((value as? List<*>)?.filterIsInstance<Ingredient>().orEmpty(), onChange)
            is PhotosSpec -> com.houseaffairs.app.core.photo.PhotosEditor((value as? List<*>)?.filterIsInstance<String>().orEmpty(), onChange)
        }
        if (showError) {
            Text("Needed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TextEditor(spec: TextSpec, value: String, onChange: (Any?) -> Unit, showError: Boolean, leading: androidx.compose.ui.graphics.vector.ImageVector? = null, accent: Accent? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(spec.label) },
        placeholder = if (spec.placeholder.isNotEmpty()) ({ Text(spec.placeholder) }) else null,
        leadingIcon = when {
            spec.isLink -> ({ Icon(Icons.Outlined.Link, contentDescription = null) })
            // Crossfades, so the change is seen as it's typed.
            leading != null -> ({
                androidx.compose.animation.Crossfade(leading, label = "category") { Icon(it, contentDescription = null, tint = accent?.color ?: MaterialTheme.colorScheme.primary) }
            })
            else -> null
        },
        trailingIcon = when {
            spec.isPhone -> ({ ContactPickerButton(onChange) })
            // A saved link opens straight from the form too.
            spec.isLink && (value.startsWith("http://") || value.startsWith("https://")) -> ({
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                IconButton(onClick = { runCatching { uri.openUri(value.trim()) } }) { Icon(Icons.Outlined.Link, contentDescription = "Open link") }
            })
            else -> null
        },
        singleLine = !spec.multiline,
        minLines = if (spec.multiline) 2 else 1,
        isError = showError,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(
            capitalization = if (spec.isLink) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = when {
                spec.isLink -> KeyboardType.Uri
                spec.isPhone -> KeyboardType.Phone
                else -> KeyboardType.Text
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MoneyEditor(spec: MoneySpec, value: MoneyValue, onChange: (Any?) -> Unit, showError: Boolean) {
    var pickCurrency by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value.amount,
        onValueChange = { text -> onChange(value.copy(amount = text.filter { it.isDigit() || it == '.' || it == ',' })) },
        label = { Text(spec.label) },
        prefix = { TextButton(onClick = { pickCurrency = true }) { Text(value.currency) } },
        singleLine = true,
        isError = showError,
        textStyle = MaterialTheme.typography.titleLarge,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    if (pickCurrency) {
        CurrencyPickerDialog(
            selected = value.currency,
            onSelect = {
                onChange(value.copy(currency = it))
                pickCurrency = false
            },
            onDismiss = { pickCurrency = false },
        )
    }
}

private const val OTHER = "Other"

/** Grams of protein, carbs, fat and fibre in one serving; empty boxes count as 0, all empty clears it. */
@Composable
private fun MacrosEditor(value: String?, onChange: (Any?) -> Unit) {
    val parsed = com.houseaffairs.app.core.data.parseMacros(value)
    val start = parsed?.let { listOf(it.protein, it.carbs, it.fat, it.fibre).map { g -> if (g % 1.0 == 0.0) g.toLong().toString() else g.toString() } } ?: List(4) { "" }
    val boxes = remember { androidx.compose.runtime.mutableStateListOf(*start.toTypedArray()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Protein", "Carbs", "Fat", "Fibre").forEachIndexed { i, name ->
            OutlinedTextField(
                value = boxes[i],
                onValueChange = { v ->
                    boxes[i] = v.filter { it.isDigit() || it == '.' }.take(5)
                    onChange(if (boxes.all { it.isBlank() }) null else boxes.joinToString(" · ") { "${it.ifBlank { "0" }}g" })
                },
                label = { Text(name, maxLines = 1) },
                suffix = { Text("g") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text("For one person's serving. Leave empty if you don't know.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun chipColors(accent: Accent) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = accent.container,
    selectedLabelColor = accent.color,
    selectedLeadingIconColor = accent.color,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceEditor(spec: ChoiceSpec, value: Any?, onChange: (Any?) -> Unit, accent: Accent) {
    val selected: List<String> = when (value) {
        is List<*> -> value.filterIsInstance<String>()
        is String -> listOf(value)
        else -> emptyList()
    }
    var askCustom by remember { mutableStateOf(false) }
    val options = (spec.options + selected).distinct()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val on = option in selected
            FilterChip(
                selected = on,
                onClick = {
                    // "Other" alone says little: ask what it is ("Cook", "Pooja"), keeping "Other" if left blank.
                    if (option == OTHER && !on) {
                        askCustom = true
                        return@FilterChip
                    }
                    onChange(
                        when {
                            spec.multi -> if (on) selected - option else selected + option
                            on && !spec.required -> null
                            else -> option
                        },
                    )
                },
                label = { Text(option) },
                shape = CircleShape,
                colors = chipColors(accent),
            )
        }
        if (spec.allowCustom && OTHER !in options) {
            AssistChip(
                onClick = { askCustom = true },
                label = { Text("Other") },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = CircleShape,
            )
        }
    }
    if (askCustom && spec.isTime) {
        val now = java.time.LocalTime.now()
        val state = androidx.compose.material3.rememberTimePickerState(initialHour = (now.hour + 1) % 24, initialMinute = 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { askCustom = false },
            title = { Text(spec.label) },
            text = { androidx.compose.material3.TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(com.houseaffairs.app.core.data.formatClock(state.hour * 60 + state.minute))
                    askCustom = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { askCustom = false }) { Text("Cancel") } },
        )
    } else if (askCustom) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askCustom = false },
            title = { Text(if (OTHER in options) "What is it?" else spec.label) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = if (OTHER in options) ({ Text("Leave empty to keep “Other”") }) else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val custom = text.trim().ifEmpty { if (OTHER in options) OTHER else "" }
                    if (custom.isNotEmpty()) onChange(if (spec.multi) (selected - OTHER) + custom else custom)
                    askCustom = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { askCustom = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DateEditor(spec: DateSpec, value: LocalDate?, onChange: (Any?) -> Unit, accent: Accent) {
    val today = LocalDate.now()
    var pick by remember { mutableStateOf(false) }
    val quick = if (spec.past) {
        listOf("Today" to today, "Yesterday" to today.minusDays(1), "Last week" to today.minusWeeks(1))
    } else {
        listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        quick.forEach { (label, date) ->
            FilterChip(
                selected = value == date,
                onClick = { onChange(if (value == date) null else date) },
                label = { Text(label) },
                shape = CircleShape,
                colors = chipColors(accent),
            )
        }
        val custom = value != null && quick.none { it.second == value }
        FilterChip(
            selected = custom,
            onClick = { pick = true },
            label = { Text(if (custom) formatDate(value!!) else "Pick a date") },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
            shape = CircleShape,
            colors = chipColors(accent),
        )
    }
    if (pick) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pick = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pick = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { pick = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuantityEditor(spec: QuantitySpec, value: QuantityValue, onChange: (Any?) -> Unit, accent: Accent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value.amount,
            onValueChange = { text -> onChange(value.copy(amount = text.filter { it.isDigit() || it == '.' })) },
            placeholder = { Text("1") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            spec.units.forEach { unit ->
                FilterChip(
                    selected = value.unit == unit,
                    onClick = { onChange(value.copy(unit = unit)) },
                    label = { Text(unit) },
                    shape = CircleShape,
                    colors = chipColors(accent),
                )
            }
        }
    }
}

@Composable
private fun LevelEditor(spec: LevelSpec, value: Int, onChange: (Any?) -> Unit) {
    // "% left" is stock (Empty … Full); anything else is progress (Not started … Done).
    val labels = if ("left" in spec.suffix) com.houseaffairs.app.core.ui.LEFT_STEPS else com.houseaffairs.app.core.ui.PROGRESS_STEPS
    com.houseaffairs.app.core.ui.LevelPicker(value, labels, MaterialTheme.colorScheme.primary, onChange = { onChange(it) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MembersEditor(spec: MembersSpec, value: List<String>, onChange: (Any?) -> Unit, members: List<Member>, accent: Accent) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        members.forEach { member ->
            val on = member.uid in value
            FilterChip(
                selected = on,
                onClick = {
                    onChange(
                        when {
                            spec.multi -> if (on) value - member.uid else value + member.uid
                            on -> emptyList<String>()
                            else -> listOf(member.uid)
                        },
                    )
                },
                label = { Text(member.displayName.substringBefore(' ')) },
                leadingIcon = { Avatar(member.displayName, size = 22.dp) },
                shape = CircleShape,
                colors = chipColors(accent),
            )
        }
    }
}

private val INGREDIENT_UNITS = listOf("g", "kg", "ml", "L", "pcs", "tsp", "tbsp", "cup")

@Composable
private fun IngredientsEditor(value: List<Ingredient>, onChange: (Any?) -> Unit) {
    val rows = value.ifEmpty { listOf(Ingredient("", null, "g")) }
    fun set(index: Int, row: Ingredient) = onChange(rows.toMutableList().also { it[index] = row })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { index, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { set(index, row.copy(name = it)) },
                    placeholder = { Text("Paneer") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = row.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "",
                    onValueChange = { text -> set(index, row.copy(amount = text.filter { it.isDigit() || it == '.' }.toDoubleOrNull())) },
                    placeholder = { Text("200") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(76.dp),
                )
                var unitMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { unitMenu = true }) { Text(row.unit) }
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        INGREDIENT_UNITS.forEach { unit ->
                            DropdownMenuItem(text = { Text(unit) }, onClick = {
                                set(index, row.copy(unit = unit))
                                unitMenu = false
                            })
                        }
                    }
                }
                if (rows.size > 1) {
                    IconButton(onClick = { onChange(rows.filterIndexed { i, _ -> i != index }) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove")
                    }
                }
            }
        }
        TextButton(onClick = { onChange(rows + Ingredient("", null, rows.last().unit)) }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add ingredient")
        }
    }
}

/** Opens the contacts app to pick one number; the picked row is readable without the contacts permission. */
@Composable
private fun ContactPickerButton(onChange: (Any?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
            if (c.moveToFirst()) onChange(c.getString(0))
        }
    }
    androidx.compose.material3.IconButton(onClick = {
        launcher.launch(android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }) { Icon(Icons.Outlined.Contacts, contentDescription = "Pick from contacts") }
}
