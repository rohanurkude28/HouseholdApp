package com.houseaffairs.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.data.WeeklyRepository
import com.houseaffairs.app.core.data.missedDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Days without a diary line, asked about once a day on opening the app. */
@HiltViewModel
class CatchUpViewModel @Inject constructor(
    private val session: SessionManager,
    private val weekly: WeeklyRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _days = MutableStateFlow<List<LocalDate>>(emptyList())
    val days: StateFlow<List<LocalDate>> = _days

    init {
        viewModelScope.launch {
            val r = session.state.first { it is SessionState.Ready } as SessionState.Ready
            val today = LocalDate.now()
            if (settings.diaryAskedDay.first() == today.toString()) return@launch
            val skipped = settings.diarySkipped.first().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
            val candidates = missedDays(today, LocalTime.now().hour, emptySet(), skipped)
            val written = runCatching { weekly.writtenDays(r.household.id, r.user.uid, candidates) }.getOrDefault(candidates.toSet())
            _days.value = candidates.filter { it !in written }
            if (_days.value.isEmpty()) settings.markDiaryAsked(today.toString())
        }
    }

    fun save(day: LocalDate, mood: String, note: String) {
        val r = session.state.value as? SessionState.Ready ?: return
        weekly.saveDiary(r.household.id, r.user.uid, day, note, mood)
        next(day)
    }

    fun skip(day: LocalDate) {
        viewModelScope.launch { settings.skipDiaryDay(day.toString()) }
        next(day)
    }

    /** Closed without answering: not again today, the days stay for tomorrow. */
    fun later() {
        _days.value = emptyList()
        viewModelScope.launch { settings.markDiaryAsked(LocalDate.now().toString()) }
    }

    private fun next(day: LocalDate) {
        _days.update { it - day }
        if (_days.value.isEmpty()) viewModelScope.launch { settings.markDiaryAsked(LocalDate.now().toString()) }
    }
}

private fun Modifier.androidx_clip() = this.then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)))

private val DIARY_MOODS = listOf("😄", "🙂", "😐", "😔", "😣")

/**
 * "How was yesterday?": one missed day at a time, a mood and a line if you like. Save or skip
 * each day; closing asks again tomorrow. Feeds the diary and the weekly story.
 */
@Composable
fun DiaryCatchUp(viewModel: CatchUpViewModel = hiltViewModel()) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val day = days.firstOrNull() ?: return
    var mood by remember(day) { mutableStateOf<String?>(null) }
    var note by remember(day) { mutableStateOf("") }
    val today = LocalDate.now()
    val name = when (day) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMM"))
    }
    AlertDialog(
        onDismissRequest = viewModel::later,
        title = { Text("How was $name?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (days.size > 1) Text("${days.size} days to catch up on. A tap each is enough.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Five equal tiles, so all fit on any width.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DIARY_MOODS.forEach { m ->
                        androidx.compose.foundation.layout.Box(
                            Modifier.weight(1f).androidx_clip().then(
                                Modifier.background(if (mood == m) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                            ).clickable { mood = m }.padding(vertical = 8.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) { Text(m, fontSize = 24.sp) }
                    }
                }
                OutlinedTextField(note, { note = it }, placeholder = { Text("One line, if you like") }, singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { mood?.let { viewModel.save(day, it, note) } }, enabled = mood != null, shape = CircleShape) { Text("Save") } },
        dismissButton = { TextButton(onClick = { viewModel.skip(day) }) { Text("Skip this day") } },
    )
}
