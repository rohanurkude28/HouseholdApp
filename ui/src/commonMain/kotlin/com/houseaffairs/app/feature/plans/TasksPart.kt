package com.houseaffairs.app.feature.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.FinishedItem
import com.houseaffairs.app.core.data.TaskBucket
import com.houseaffairs.app.core.data.TaskItem
import com.houseaffairs.app.core.data.dayProgress
import com.houseaffairs.app.core.data.handoffLine
import com.houseaffairs.app.core.data.taskBuckets
import com.houseaffairs.app.core.data.waitingOnMe
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.ui.HandoffButtons
import com.houseaffairs.app.core.ui.TaskAnswers
import com.houseaffairs.app.feature.quickadd.ListTemplate

/** What Plans → To do asks its phone to do. */
class TaskActions(
    val complete: (TaskItem) -> Unit,
    val reschedule: (TaskItem, LocalDate?, String) -> Unit,
    val open: (TaskItem) -> Unit,
    /** Saves a typed line as a task; true when it was saved, so the box empties. */
    val quickAdd: (String) -> Boolean,
    /** ⋯ in the add box: the full form. */
    val more: () -> Unit,
    val openChores: () -> Unit,
    val reopen: (FinishedItem) -> Unit,
    val answers: TaskAnswers,
)

/** What the person has open or typed on To do; each phone keeps it across its own screens. */
class TasksView(
    val everyone: Boolean, val onEveryone: (Boolean) -> Unit,
    val text: String, val onText: (String) -> Unit,
    val openBuckets: Set<String>, val onBuckets: (Set<String>) -> Unit,
    val showDone: Boolean, val onShowDone: (Boolean) -> Unit,
)

/**
 * Plans → To do, the same on both phones: the day's ring (Mine / All), family chores, the add box,
 * then the tasks by when they're due, and Done at the bottom.
 */
fun LazyListScope.tasksPart(
    tasks: List<TaskItem>,
    finished: List<FinishedItem>,
    /** How many tasks were ticked off today. */
    doneToday: Int,
    me: String,
    nameOf: (String) -> String,
    today: LocalDate,
    view: TasksView,
    actions: TaskActions,
    /** Per task, what only one phone can do (Android's call or message button). */
    extra: @Composable (TaskItem) -> Unit = {},
) {
    val waiting = waitingOnMe(tasks, me).toSet()
    val shown = if (view.everyone) tasks else tasks.filter { it.assignee == null || it.assignee == me || it in waiting }
    // Today at a glance: a ring that fills as things get done, and whose list this is.
    item(key = "task-head") { TasksHeader(dayProgress(doneToday, shown, today), view.everyone, view.onEveryone) }
    // The family's shared chores, set up once and nudged when due.
    item(key = "chores") { ChoresRow(tasks.filter { it.chore }, today, actions.openChores) }
    // Type and press enter; "call mom at 5 tomorrow" keeps its date and time. ⋯ opens the full form.
    item(key = "task-add") {
        TaskQuickAdd(view.text, view.onText, null, onSubmit = { if (actions.quickAdd(view.text)) view.onText("") }, onMore = actions.more)
    }
    if (tasks.isEmpty()) item { Hint("Nothing to do. Type one above, or say “pay rent tomorrow”.") }
    taskBuckets(shown, today, waiting.map { it.id }.toSet()).map { (b, l) -> b.label to l }.filter { it.second.isNotEmpty() }.forEach { (label, list) ->
        val foldable = label in setOf(TaskBucket.LATER.label, TaskBucket.NO_DATE.label) && list.size > 4
        val open = !foldable || label in view.openBuckets
        item(key = "bucket-$label") {
            Row(
                Modifier.fillMaxWidth().then(if (foldable) Modifier.clickable { view.onBuckets(if (open) view.openBuckets - label else view.openBuckets + label) } else Modifier)
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = if (label == TaskBucket.OVERDUE.label) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text("  ${list.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (foldable) Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (open) items(list.size, key = { list[it].id }) { i ->
            val task = list[i]
            TaskCardRow(
                task, first = i == 0, last = i == list.size - 1, today = today,
                meta = listOfNotNull(
                    task.time?.let { "⏰ $it" },
                    task.repeat?.let { "↻ $it" },
                    handoffLine(task, me, nameOf) ?: task.assignee?.takeIf { it != me }?.let { "👤 ${nameOf(it)}" },
                ),
                waiting = task in waiting,
                onDone = { actions.complete(task) },
                onReschedule = { date, l -> actions.reschedule(task, date, l) },
                onOpen = { actions.open(task) },
                extra = { extra(task) },
            ) { HandoffButtons(task, me, actions.answers, Modifier.padding(start = 48.dp, bottom = 8.dp)) }
        }
    }
    val doneTasks = finished.filter { it.collection == "tasks" }
    if (doneTasks.isNotEmpty()) {
        item(key = "done-tasks") { FinishedSection("Done", doneTasks, view.showDone, { view.onShowDone(!view.showDone) }, "Not done", actions.reopen) }
    }
}

/** "Done · 4": tap to see finished items, each with a button that puts it back. */
@Composable
fun FinishedSection(
    title: String,
    items: List<FinishedItem>,
    open: Boolean,
    onToggle: () -> Unit,
    undoLabel: String,
    onReopen: (FinishedItem) -> Unit,
) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = onToggle) { Text(if (open) "Hide ${title.lowercase()}" else "$title · ${items.size}") }
        if (open) {
            Text(
                "Kept for your story and the year in review, then cleared a year after.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
            )
            items.take(20).forEach { f ->
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (ListTemplate.of(f.template)?.emoji?.plus(" ") ?: "✓ ") + f.title,
                        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { onReopen(f) }) { Text(undoLabel) }
                }
            }
        }
    }
}
