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
import com.houseaffairs.app.core.data.DAY_QUESTIONS
import com.houseaffairs.app.core.data.DayCheck
import com.houseaffairs.app.core.data.Pillar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** A request to ask today's check-in now (the evening notification was tapped). */
object CheckInInbox {
    val asked = MutableStateFlow(false)
}

/** Days without a check-in, asked about on opening the app before anything else. */
@HiltViewModel
class CatchUpViewModel @Inject constructor(
    private val session: SessionManager,
    private val weekly: WeeklyRepository,
    private val settings: SettingsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    private val _days = MutableStateFlow<List<LocalDate>>(emptyList())
    val days: StateFlow<List<LocalDate>> = _days

    /** The latest missed day when the app opened: the one that must be answered. */
    var required: LocalDate? = null
        private set

    init {
        viewModelScope.launch { ask(force = false) }
        // Tapping tonight's "How was today?" notification asks straight away, even if the app was open.
        viewModelScope.launch {
            CheckInInbox.asked.collect { if (it) { CheckInInbox.asked.value = false; ask(force = true) } }
        }
    }

    /**
     * Works out the days to ask about. Asked once in the day and once from the evening on (when
     * today joins), so opening the app in the morning doesn't hide tonight's check-in. [force]
     * (from the notification) asks about today whatever the hour.
     */
    private suspend fun ask(force: Boolean) {
        val r = session.state.first { it is SessionState.Ready } as SessionState.Ready
        val today = LocalDate.now()
        val hour = LocalTime.now().hour
        val mark = askedMark(today, hour)
        if (!force && settings.diaryAskedDay.first() == mark) return
        val skipped = settings.diarySkipped.first().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        val candidates = missedDays(today, if (force) 23 else hour, emptySet(), if (force) skipped - today else skipped)
        val written = runCatching { weekly.writtenDays(r.household.id, r.user.uid, candidates) }.getOrDefault(candidates.toSet())
        _days.value = candidates.filter { it !in written }
        required = _days.value.firstOrNull()
        if (_days.value.isEmpty()) settings.markDiaryAsked(mark)
    }

    /** A day's steps from Health Connect, when this person connected it on this phone. */
    suspend fun stepsOn(day: LocalDate): Long? = weekly.stepsOn(day)

    /** "2026-11-12" by day, "2026-11-12 evening" once today's check-in is due. */
    private fun askedMark(today: LocalDate, hour: Int) = if (hour >= 20) "$today evening" else today.toString()

    fun save(day: LocalDate, mood: String, note: String, check: DayCheck) {
        val r = session.state.value as? SessionState.Ready ?: return
        weekly.saveDiary(r.household.id, r.user.uid, day, note, mood, check)
        // Answered for today: tonight's check-in reminder isn't needed.
        if (day == LocalDate.now()) com.houseaffairs.app.notify.Reminders.markCheckedIn(context, day)
        next(day)
    }

    fun skip(day: LocalDate) {
        viewModelScope.launch { settings.skipDiaryDay(day.toString()) }
        next(day)
    }

    /** Closed without answering: not again today, the days stay for tomorrow. */
    fun later() {
        _days.value = emptyList()
        viewModelScope.launch { settings.markDiaryAsked(askedMark(LocalDate.now(), LocalTime.now().hour)) }
    }

    private fun next(day: LocalDate) {
        _days.update { it - day }
        if (_days.value.isEmpty()) viewModelScope.launch { settings.markDiaryAsked(askedMark(LocalDate.now(), LocalTime.now().hour)) }
    }
}

/** Android's check-in: the shared dialog (:ui DayCheckIn.kt), one missed day at a time. */
@Composable
fun DiaryCatchUp(viewModel: CatchUpViewModel = hiltViewModel()) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val day = days.firstOrNull() ?: return
    com.houseaffairs.app.feature.review.DayCheckInDialog(
        day, required = day == viewModel.required, stepsOn = viewModel::stepsOn,
        onSave = { mood, note, check -> viewModel.save(day, mood, note, check) },
        onSkip = { viewModel.skip(day) }, onLater = viewModel::later,
    )
}
