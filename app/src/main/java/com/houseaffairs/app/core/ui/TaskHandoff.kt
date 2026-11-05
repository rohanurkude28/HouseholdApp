package com.houseaffairs.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Handoff
import com.houseaffairs.app.core.data.TaskItem
import com.houseaffairs.app.core.data.handoffOf

/** What a screen does with an answer to a task given to someone. */
class TaskAnswers(
    val accept: (TaskItem) -> Unit,
    val decline: (TaskItem, String?) -> Unit,
    /** "I'll do it" on one that came back. */
    val keep: (TaskItem) -> Unit,
    /** "Ask someone else": the task's form, to pick another person. */
    val reassign: (TaskItem) -> Unit,
)

private val REASONS = listOf("Busy that day", "Away", "Not today")

/**
 * The buttons for a task waiting on me: "Accept" / "Can't do it" when it was given to me, and
 * "I'll do it" / "Ask someone else" when someone couldn't. Nothing for anything else.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HandoffButtons(task: TaskItem, me: String, answers: TaskAnswers, modifier: Modifier = Modifier) {
    var declining by remember(task.id) { mutableStateOf(false) }
    when (handoffOf(task, me)) {
        Handoff.ASKED_ME -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { answers.accept(task) }, shape = CircleShape) { Text("Accept") }
            OutlinedButton(onClick = { declining = true }, shape = CircleShape) { Text("Can't do it") }
        }
        Handoff.CAME_BACK -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { answers.keep(task) }, shape = CircleShape) { Text("I'll do it") }
            OutlinedButton(onClick = { answers.reassign(task) }, shape = CircleShape) { Text("Ask someone else") }
        }
        else -> Unit
    }
    if (declining) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { declining = false },
            title = { Text("Can't do “${task.title}”?") },
            text = {
                Column {
                    Text("It goes back to whoever gave it, with your reason.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        REASONS.forEach { r -> androidx.compose.material3.SuggestionChip(onClick = { note = r }, label = { Text(r) }) }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("Why? (optional)") }, singleLine = true, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(onClick = { declining = false; answers.decline(task, note) }) { Text("Send it back") } },
            dismissButton = { TextButton(onClick = { declining = false }) { Text("Cancel") } },
        )
    }
}
