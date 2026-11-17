package com.houseaffairs.app.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.houseaffairs.app.core.data.DAY_QUESTIONS
import com.houseaffairs.app.core.data.DayCheck
import com.houseaffairs.app.core.data.Pillar
import com.houseaffairs.app.core.data.movedFromSteps
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.core.time.minusDays
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.util.grouped

private val DIARY_MOODS = listOf("😄", "🙂", "😐", "😔", "😣")
private val ANSWERS = listOf("No", "A little", "Yes")

/**
 * "How was yesterday?": the live-to-100 check-in for one day, the same on both phones. Four quick
 * answers (moved, ate well, people, slowed down) and a mood are needed; a line is optional. A
 * [required] day can't be skipped or closed, since the wheel and the coaching rest on it.
 * [stepsOn] answers "Did you move?" from the phone's health app, where it has one.
 */
@Composable
fun DayCheckInDialog(
    day: LocalDate,
    required: Boolean,
    stepsOn: suspend (LocalDate) -> Long?,
    onSave: (mood: String, note: String, check: DayCheck) -> Unit,
    onSkip: () -> Unit,
    onLater: () -> Unit,
    stepsSource: String = "Health Connect",
) {
    var mood by remember(day) { mutableStateOf<String?>(null) }
    var note by remember(day) { mutableStateOf("") }
    var answers by remember(day) { mutableStateOf(mapOf<Pillar, Int>()) }
    // With a health app, "Did you move?" is answered from that day's steps; it can still be changed.
    var steps by remember(day) { mutableStateOf<Long?>(null) }
    LaunchedEffect(day) {
        runCatching { stepsOn(day) }.getOrNull()?.let { n ->
            steps = n
            if (Pillar.MOVE !in answers) answers = answers + (Pillar.MOVE to movedFromSteps(n))
        }
    }
    val today = systemToday()
    val name = when (day) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> day.format("EEEE d MMM")
    }
    val complete = mood != null && answers.size == DAY_QUESTIONS.size
    AlertDialog(
        onDismissRequest = { if (!required) onLater() },
        properties = DialogProperties(dismissOnBackPress = !required, dismissOnClickOutside = !required),
        title = { Text("How was $name?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Four taps for your live-to-100 wheel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DAY_QUESTIONS.forEach { (pillar, emoji, question) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$emoji  $question", style = MaterialTheme.typography.bodyMedium)
                        if (pillar == Pillar.MOVE) steps?.let { n ->
                            Text("${grouped(n)} steps from $stepsSource", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ANSWERS.forEachIndexed { value, label ->
                                FilterChip(selected = answers[pillar] == value, onClick = { answers = answers + (pillar to value) }, label = { Text(label) }, shape = CircleShape)
                            }
                        }
                    }
                }
                Text("And overall?", style = MaterialTheme.typography.bodyMedium)
                // Five equal tiles, so all fit on any width.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DIARY_MOODS.forEach { m ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (mood == m) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { mood = m }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(m, fontSize = 24.sp) }
                    }
                }
                OutlinedTextField(note, { note = it }, placeholder = { Text("One good thing, if you like") }, singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = mood ?: return@Button
                    fun a(p: Pillar) = answers[p] ?: 0
                    onSave(m, note, DayCheck(a(Pillar.MOVE), a(Pillar.EAT), a(Pillar.CONNECT), a(Pillar.OUTLOOK)))
                },
                enabled = complete,
                shape = CircleShape,
            ) { Text("Save") }
        },
        dismissButton = { if (!required) TextButton(onClick = onSkip) { Text("Skip this day") } },
    )
}
