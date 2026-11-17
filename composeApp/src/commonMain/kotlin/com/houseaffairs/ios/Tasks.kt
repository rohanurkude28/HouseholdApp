package com.houseaffairs.ios

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.houseaffairs.app.core.data.FinishedItem
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.data.TaskItem
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.time.localDate
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.ui.TaskAnswers
import com.houseaffairs.app.feature.plans.ChoresSheet
import com.houseaffairs.app.feature.plans.TaskActions
import com.houseaffairs.app.feature.plans.TasksView
import com.houseaffairs.app.feature.plans.tasksPart
import com.houseaffairs.app.feature.quickadd.QuickAddDraft
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val tasksRepo = HomeRepository()
private val finishedRepo = ThingsRepository()

/** What the iPhone's To do needs from the composition: what's open or typed, and finished tasks. */
internal class TasksHolder(val view: TasksView, val finished: List<FinishedItem>, val openChores: () -> Unit)

/**
 * The iPhone's Plans → To do: Android's (from :ui), saved through the shared HomeRepository. Call
 * from the Plans tab's composable, then [tasksOn] inside its list.
 */
@Composable
internal fun rememberTasks(s: SessionState.Ready, tasks: List<TaskItem>, actions: Actions): TasksHolder {
    var everyone by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var buckets by remember { mutableStateOf(emptySet<String>()) }
    var showDone by remember { mutableStateOf(false) }
    var chores by remember { mutableStateOf(false) }
    val finished by remember(s.household.id, s.uid) { finishedRepo.observeFinished(s.household.id, s.uid) }.collectAsState(emptyList())
    val hid = s.household.id
    if (chores) ChoresSheet(
        chores = tasks.filter { it.chore }.sortedBy { it.dueDate?.let { d -> d.toString() } ?: "~" },
        today = systemToday(),
        onAdd = { title, every -> actions.run("${title.trim()} · a family chore, ${every.lowercase()}") { val u = tasksRepo.addTask(hid, s.uid, title.trim(), repeat = every, source = "chore", assignee = null); ({ u() }) } },
        onRemove = { t -> actions.run("${t.title} · off the chores list") { val u = tasksRepo.removeTask(hid, t.id); ({ u() }) } },
        onRepeat = { t, r -> actions.run("${t.title} · ${r.lowercase()}") { val u = tasksRepo.setRepeat(hid, t.id, r, t.repeat); ({ u() }) } },
        onDismiss = { chores = false },
    )
    return TasksHolder(TasksView(everyone, { everyone = it }, text, { text = it }, buckets, { buckets = it }, showDone, { showDone = it }), finished, { chores = true })
}

internal fun LazyListScope.tasksOn(s: SessionState.Ready, tasks: List<TaskItem>, holder: TasksHolder, names: Map<String, String>, actions: Actions, onAdd: () -> Unit, onEdit: (String, String) -> Unit) {
    val hid = s.household.id
    val today = systemToday()
    val zone = TimeZone.currentSystemDefault()
    val doneToday = holder.finished.count { f ->
        f.collection == "tasks" && f.at?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date }?.let { localDate(it.year, it.monthNumber, it.dayOfMonth) } == today
    }
    tasksPart(
        tasks = tasks, finished = holder.finished, doneToday = doneToday,
        me = s.uid, nameOf = { uid -> if (uid == s.uid) "You" else names[uid] ?: "Someone" }, today = today,
        view = holder.view,
        actions = TaskActions(
            complete = { t -> actions.run("${t.title} · done") { val u = tasksRepo.completeTask(hid, s.uid, t); ({ u() }) } },
            reschedule = { t, date, label -> actions.run("${t.title} · ${label.lowercase()}") { val u = tasksRepo.moveTask(hid, t.id, date, t.dueDate); ({ u() }) } },
            open = { onEdit("tasks", it.id) },
            quickAdd = { line ->
                val typed = line.trim()
                if (typed.isEmpty()) false else {
                    // Through the parser, so "call mom at 5 pm tomorrow" keeps its date and time.
                    val draft = parser.parse(typed) as? QuickAddDraft.Task
                    val title = draft?.title?.takeIf { it.isNotBlank() } ?: typed.replaceFirstChar { it.uppercase() }
                    actions.run(title) { val u = tasksRepo.addTask(hid, s.uid, title, due = draft?.due ?: today, time = draft?.time, repeat = draft?.repeat, source = "tasks"); ({ u() }) }
                    true
                }
            },
            more = onAdd,
            openChores = holder.openChores,
            reopen = { f -> actions.say("${f.title} · back on the list") { finishedRepo.reopen(hid, f) } },
            answers = TaskAnswers(
                accept = { t -> actions.run("You'll do “${t.title}”") { val u = tasksRepo.answerTask(hid, s.uid, t, accept = true); ({ u() }) } },
                decline = { t, note -> actions.run("Sent “${t.title}” back") { val u = tasksRepo.answerTask(hid, s.uid, t, accept = false, note = note); ({ u() }) } },
                keep = { t -> actions.say("You'll do “${t.title}”") { tasksRepo.keepTask(hid, t) } },
                reassign = { onEdit("tasks", it.id) },
            ),
        ),
    )
}
