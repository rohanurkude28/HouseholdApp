package com.houseaffairs.app.feature.plans

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.DayProgress
import com.houseaffairs.app.core.data.TaskItem
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.TickCircle
import com.houseaffairs.app.core.ui.opensEdit
import com.houseaffairs.app.feature.add.formatDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Today at a glance: a ring that fills as things get done, the day in one line, and Mine / Everyone's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksHeader(progress: DayProgress, everyone: Boolean, onEveryone: (Boolean) -> Unit) {
    val plans = LocalHouseColors.current.plans
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clip(RoundedCornerShape(28.dp)).background(plans.container).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.share },
                modifier = Modifier.size(58.dp),
                strokeWidth = 6.dp,
                color = plans.color,
                trackColor = Color.White.copy(alpha = 0.6f),
            )
            Text(if (progress.total == 0) "✓" else "${progress.done}/${progress.total}", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Today", style = MaterialTheme.typography.titleLarge)
            Text(progress.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(selected = !everyone, onClick = { onEveryone(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { Text("Mine") }
            SegmentedButton(selected = everyone, onClick = { onEveryone(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { Text("All") }
        }
    }
}

/** Type a task and press enter. Without a date it goes on the picked day, else today. ⋯ opens the full form. */
@Composable
internal fun TaskQuickAdd(text: String, onText: (String) -> Unit, day: LocalDate?, onSubmit: () -> Unit, onMore: () -> Unit) {
    TextField(
        value = text,
        onValueChange = onText,
        placeholder = { Text(if (day == null) "Add a task… “call mom at 5 pm”" else "Add for ${formatDate(day)}…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        trailingIcon = {
            if (text.isNotBlank()) IconButton(onClick = onSubmit) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add task", tint = LocalHouseColors.current.plans.color) }
            else IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More options") }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit() }),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
    )
}

/**
 * One task in its group's card. The circle finishes it; the date opens quick choices (today,
 * tomorrow, the weekend…); the rest of the row opens it. [first] and [last] round the card.
 */
@Composable
internal fun TaskCardRow(
    task: TaskItem,
    first: Boolean,
    last: Boolean,
    today: LocalDate,
    meta: List<String>,
    waiting: Boolean,
    onDone: () -> Unit,
    onReschedule: (LocalDate?, String) -> Unit,
    onOpen: () -> Unit,
    below: @Composable () -> Unit,
) {
    val plans = LocalHouseColors.current.plans
    val shape = RoundedCornerShape(topStart = if (first) 22.dp else 0.dp, topEnd = if (first) 22.dp else 0.dp, bottomStart = if (last) 22.dp else 0.dp, bottomEnd = if (last) 22.dp else 0.dp)
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp).clip(shape).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().opensEdit(onOpen).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TickCircle(plans.color, onTick = onDone, contentDescription = "Done: ${task.title}")
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = if (waiting) plans.color else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // High priority shows as a small coloured flag, not a word.
            when (task.priority) {
                "High" -> Text("⚑", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 6.dp))
                "Medium" -> Text("⚑", color = LocalHouseColors.current.coral, modifier = Modifier.padding(end = 6.dp))
            }
            task.reach?.let { com.houseaffairs.app.core.ui.ReachButton(it, task.phone, task.title) }
            // The date is a chip: tap it to move the task, no form needed.
            Box {
                val late = task.dueDate?.isBefore(today) == true
                Text(
                    task.dueDate?.let { formatDate(it) } ?: "＋ Date",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (late) MaterialTheme.colorScheme.error else if (task.dueDate == null) MaterialTheme.colorScheme.onSurfaceVariant else plans.color,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (late) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else plans.container)
                        .clickable { picking = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                    com.houseaffairs.app.core.data.rescheduleChoices(today, task.dueDate).forEach { (label, date) ->
                        DropdownMenuItem(
                            text = { Text(label + (date?.let { "  ·  ${it.format(DateTimeFormatter.ofPattern("EEE d MMM"))}" } ?: "")) },
                            onClick = { picking = false; onReschedule(date, label) },
                        )
                    }
                }
            }
        }
        below()
        // A hairline between rows of the same card.
        if (!last) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
    }
}

/**
 * The header every Plans tab shares: a ring when there's progress to show (or an emoji), a title
 * and one line. Tasks, Goals and Events read the same way.
 */
@Composable
internal fun PlansHeader(title: String, line: String, done: Int? = null, total: Int? = null, emoji: String? = null, trailing: @Composable () -> Unit = {}) {
    val plans = LocalHouseColors.current.plans
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clip(RoundedCornerShape(28.dp)).background(plans.container).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done != null && total != null) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.size(58.dp), strokeWidth = 6.dp, color = plans.color, trackColor = Color.White.copy(alpha = 0.6f),
                )
                Text(if (total == 0) "✓" else "$done/$total", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(16.dp))
        } else if (emoji != null) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** One row of a rounded group card, as in Tasks: [first] and [last] round the corners, a hairline between. */
@Composable
internal fun CardRow(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(topStart = if (first) 22.dp else 0.dp, topEnd = if (first) 22.dp else 0.dp, bottomStart = if (last) 22.dp else 0.dp, bottomEnd = if (last) 22.dp else 0.dp)
    Column(Modifier.padding(horizontal = 20.dp).clip(shape).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(top = if (first) 6.dp else 0.dp, bottom = if (last) 6.dp else 0.dp)) {
        content()
        if (!last) Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
    }
}

/** "🧹 Family chores · 4 · 2 due": opens the list of chores the family shares. */
@Composable
internal fun ChoresRow(chores: List<TaskItem>, today: LocalDate, onOpen: () -> Unit) {
    val due = chores.count { it.dueDate?.let { d -> !d.isAfter(today) } == true }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🧹", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Family chores", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    chores.isEmpty() -> "Set up the chores the family shares; they'll be nudged when due."
                    due == 0 -> "${chores.size} ${if (chores.size == 1) "chore" else "chores"} · nothing due"
                    else -> "${chores.size} ${if (chores.size == 1) "chore" else "chores"} · $due due"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The family's chores: what's on the list (how often, when next, change or remove), ideas to add
 * in one tap, and your own. A chore is due again an interval after it's done; anyone can do it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChoresSheet(
    chores: List<TaskItem>,
    today: LocalDate,
    onAdd: (String, String) -> Unit,
    onRemove: (TaskItem) -> Unit,
    onRepeat: (TaskItem, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val repeats = com.houseaffairs.app.core.data.REPEAT_CHOICES - "Never"
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Family chores", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Each one is nudged when it's due, at the time it's usually done, and more often when it's late. Done → next one after the interval.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chores.isNotEmpty()) item { Text("On the list", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            items(chores.size) { i ->
                val c = chores[i]
                var picking by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            c.dueDate?.let { d -> if (d.isAfter(today)) "Next ${formatDate(d)}" else if (d == today) "Due today" else "Late since ${formatDate(d)}" } ?: "No date",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (c.dueDate?.isBefore(today) == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        androidx.compose.material3.TextButton(onClick = { picking = true }) { Text((c.repeat ?: "Repeat") + " ▾") }
                        androidx.compose.material3.DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                            repeats.forEach { r -> androidx.compose.material3.DropdownMenuItem(text = { Text(r) }, onClick = { picking = false; if (r != c.repeat) onRepeat(c, r) }) }
                        }
                    }
                    IconButton(onClick = { onRemove(c) }) { Icon(Icons.Outlined.Close, contentDescription = "Take ${c.title} off the list") }
                }
            }
            val ideas = com.houseaffairs.app.core.data.CHORE_IDEAS.filter { idea -> chores.none { com.houseaffairs.app.core.data.choreKey(it.title) == com.houseaffairs.app.core.data.choreKey(idea.title) } }
            if (ideas.isNotEmpty()) item { Text("Add in one tap", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
            item {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ideas.forEach { idea ->
                        androidx.compose.material3.SuggestionChip(
                            onClick = { onAdd(idea.title, idea.repeat) },
                            label = { Text("${idea.emoji} ${idea.title} · ${idea.repeat.lowercase()}") },
                            shape = CircleShape,
                        )
                    }
                }
            }
            item {
                var own by remember { mutableStateOf("") }
                var every by remember { mutableStateOf("Weekly") }
                var picking by remember { mutableStateOf(false) }
                Text("Your own", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(own, { own = it }, placeholder = { Text("Feed the fish, clean the balcony…") }, singleLine = true, modifier = Modifier.weight(1f))
                    Box {
                        androidx.compose.material3.TextButton(onClick = { picking = true }) { Text("$every ▾") }
                        androidx.compose.material3.DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                            repeats.forEach { r -> androidx.compose.material3.DropdownMenuItem(text = { Text(r) }, onClick = { picking = false; every = r }) }
                        }
                    }
                }
                androidx.compose.material3.Button(onClick = { onAdd(own, every); own = "" }, enabled = own.isNotBlank(), shape = CircleShape, modifier = Modifier.padding(top = 6.dp)) { Text("Add chore") }
            }
        }
    }
}
