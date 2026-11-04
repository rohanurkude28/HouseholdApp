package com.houseaffairs.app.feature.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.CheckIn
import com.houseaffairs.app.core.data.Habit
import com.houseaffairs.app.core.data.Pillar
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.data.WeekReview
import com.houseaffairs.app.core.data.WeeklyRepository
import com.houseaffairs.app.core.data.Wheel
import com.houseaffairs.app.core.data.isoWeek
import com.houseaffairs.app.core.ui.Avatar
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.SectionTitle
import com.houseaffairs.app.core.util.formatMinor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.TextButton
import com.houseaffairs.app.feature.add.EntryType


val PILLAR_COLORS = mapOf(
    Pillar.MOVE to Color(0xFFE8745A),
    Pillar.OUTLOOK to Color(0xFFA4237A),
    Pillar.EAT to Color(0xFF6BAA45),
    Pillar.CONNECT to Color(0xFFE2BE1E),
)

/** Clockwise from the top-left, like the classic wheel. */
private val ORDER = listOf(Pillar.MOVE, Pillar.OUTLOOK, Pillar.EAT, Pillar.CONNECT)

data class ReviewUi(val loading: Boolean = true, val name: String = "", val review: WeekReview? = null)

@HiltViewModel
class WeeklyReviewViewModel @Inject constructor(
    private val session: SessionManager,
    private val weekly: WeeklyRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUi())
    val state = _state.asStateFlow()

    /** The Monday of the week on screen; arrows step back through earlier weeks. */
    private var weekStart = com.houseaffairs.app.core.data.mondayOf(LocalDate.now())

    init {
        refresh()
        viewModelScope.launch { settings.markReviewSeen(isoWeek(LocalDate.now())) }
    }

    fun refresh() {
        val r = session.state.value as? SessionState.Ready ?: return
        val week = weekStart
        viewModelScope.launch {
            val review = runCatching { weekly.load(r.household.id, r.me, week) }
                .onFailure { android.util.Log.e("WeeklyReview", "Couldn't read the week", it) }
                .getOrNull()
            if (week == weekStart) _state.value = ReviewUi(false, r.me.displayName.substringBefore(' '), review)
        }
    }

    fun showWeek(offset: Long) {
        val next = weekStart.plusWeeks(offset)
        if (next.isAfter(LocalDate.now())) return
        weekStart = next
        _state.value = _state.value.copy(loading = true)
        refresh()
    }

    /** Saves the day's diary line and mood, showing it at once. */
    fun saveDiary(day: LocalDate, note: String, mood: String?) {
        val r = session.state.value as? SessionState.Ready ?: return
        weekly.saveDiary(r.household.id, r.user.uid, day, note, mood)
        _state.value.review?.let { rev ->
            val diary = rev.diary.map { if (it.date == day) it.copy(note = note.trim().ifEmpty { null }, mood = mood) else it }
            val week = rev.week.copy(diaryDays = diary.count { it.note != null })
            _state.value = _state.value.copy(review = rev.copy(diary = diary, week = week, wheel = com.houseaffairs.app.core.data.liveWell(week, rev.plan)))
        }
    }

    /** Sets this week's count for one of the person's own targets, updating the screen at once. */
    fun countTarget(name: String, count: Int) {
        val r = session.state.value as? SessionState.Ready ?: return
        weekly.bumpTarget(r.household.id, r.user.uid, name, count, weekStart)
        _state.value.review?.let { rev -> _state.value = _state.value.copy(review = rev.copy(targetCounts = rev.targetCounts + (name to count.coerceAtLeast(0)))) }
    }

    fun checkIn(c: CheckIn) {
        val r = session.state.value as? SessionState.Ready ?: return
        viewModelScope.launch {
            runCatching { weekly.saveCheckIn(r.household.id, r.user.uid, c, weekStart) }
            refresh()
        }
    }
}

/** The live-well wheel: four pillars, three habits each, brighter the better that habit went. */
@Composable
fun LiveWellWheel(wheel: Wheel, caption: String = "this week", modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2
            val gap = 1.5f
            ORDER.forEachIndexed { i, pillar ->
                val color = PILLAR_COLORS.getValue(pillar)
                val start = -180f + i * 90f
                // Inner band: the pillar, filled by its score.
                val pillarScore = wheel.pillarScore(pillar) ?: 0f
                val innerW = outer * 0.20f
                val innerR = outer * 0.60f
                drawArc(track, start + gap, 90f - 2 * gap, false, Offset(center.x - innerR, center.y - innerR), Size(innerR * 2, innerR * 2), style = Stroke(innerW))
                drawArc(color.copy(alpha = 0.35f + 0.65f * pillarScore), start + gap, 90f - 2 * gap, false, Offset(center.x - innerR, center.y - innerR), Size(innerR * 2, innerR * 2), style = Stroke(innerW))
                // Outer band: its three habits.
                val outerR = outer * 0.86f
                val outerW = outer * 0.22f
                wheel.habits.filter { it.pillar == pillar }.forEachIndexed { j, habit ->
                    val s = start + j * 30f + gap
                    drawArc(track, s, 30f - 2 * gap, false, Offset(center.x - outerR, center.y - outerR), Size(outerR * 2, outerR * 2), style = Stroke(outerW))
                    habit.score?.let { score ->
                        drawArc(color.copy(alpha = 0.25f + 0.75f * score), s, 30f - 2 * gap, false, Offset(center.x - outerR, center.y - outerR), Size(outerR * 2, outerR * 2), style = Stroke(outerW))
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${wheel.overall}", style = MaterialTheme.typography.displayMedium)
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val MOODS = listOf("😄", "🙂", "😐", "😔", "😣")

/**
 * "My week": a private diary. Each day shows what you did (walks, meals, tasks, outings, picked
 * up from what's logged) and a line you write yourself. Above it, the live-well wheel for the
 * week; below, how each part went and the week in numbers. Arrows go back to earlier weeks.
 */
@Composable
fun WeeklyReviewScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogActivity: () -> Unit,
    onEditEntry: (EntryType, String) -> Unit = { _, _ -> },
    /** Changes when something is saved elsewhere, so the week is read again. */
    refreshKey: Int = 0,
    viewModel: WeeklyReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(refreshKey) { if (refreshKey > 0) viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val review = state.review
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
    val today = LocalDate.now()
    var planning by remember { mutableStateOf(false) }
    var openPillar by remember { mutableStateOf<Pillar?>(null) }
    if (planning) WellPlanSheet(onDismiss = { planning = false }, onSaved = viewModel::refresh)

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text("My week", style = MaterialTheme.typography.headlineMedium)
                    Text("Your diary and how the week went · only you see this", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }
        }
        item {
            val start = review?.weekStart ?: com.houseaffairs.app.core.data.mondayOf(today)
            val thisWeek = start == com.houseaffairs.app.core.data.mondayOf(today)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.showWeek(-1) }) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Earlier week") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            thisWeek -> "This week"
                            start == com.houseaffairs.app.core.data.mondayOf(today).minusWeeks(1) -> "Last week"
                            else -> "Week of ${start.format(DateTimeFormatter.ofPattern("d MMM"))}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${start.format(fmt)} – ${start.plusDays(6).format(fmt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.showWeek(1) }, enabled = !thisWeek) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Later week") }
            }
        }
        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            return@LazyColumn
        }
        if (review == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load this week. Check your connection.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    TextButton(onClick = viewModel::refresh) { Text("Try again") }
                }
            }
            return@LazyColumn
        }
        val wheel = review.wheel
        item { LiveWellWheel(wheel, if (review.isThisWeek) "so far" else "that week", Modifier.fillMaxWidth().height(260.dp).padding(12.dp)) }
        item {
            Text(
                "Inner ring: the four parts of a long, good life. Outer ring: three everyday habits in each. Brighter means it went well" +
                    if (review.isThisWeek) ", counting the days so far." else ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )
        }
        item { PillarLegend(wheel) }
        review.nudge?.let { nudge ->
            item { NudgeCard(nudge, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) }
        }

        item { SectionTitle("My diary") }
        items(review.diary.size, key = { "day-" + review.diary[it].date }) { i ->
            DiaryDayCard(review.diary[i], today, onSave = viewModel::saveDiary, onOpen = { e ->
                when (e.kind) {
                    com.houseaffairs.app.core.data.DiaryKind.ACTIVITY -> onEditEntry(EntryType.ACTIVITY, e.id)
                    com.houseaffairs.app.core.data.DiaryKind.TASK -> onEditEntry(EntryType.TASK, e.id)
                    com.houseaffairs.app.core.data.DiaryKind.EVENT -> onEditEntry(EntryType.EVENT, e.id)
                    com.houseaffairs.app.core.data.DiaryKind.TRIP -> onEditEntry(EntryType.TRIP, e.id)
                    com.houseaffairs.app.core.data.DiaryKind.MEAL -> Unit
                }
            })
        }
        if (review.isThisWeek) {
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = onLogActivity, shape = CircleShape) { Text("🚶 Log a walk or workout") }
                }
            }
        }

        if (review.week.checkIn == null) item { CheckInCard(viewModel::checkIn) }

        item { SectionTitle("My plan") }
        item {
            val plan = review.plan
            HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), onClick = { planning = true }) {
                Text(
                    if (plan.isEmpty && plan.custom.isEmpty()) "Choose a few small habits for a long, good life. Nudges will use your own words."
                    else (plan.commitments.values.flatten() + plan.custom.map { "${it.name} (${it.perWeek}×)" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(if (plan.isEmpty) "Make my plan" else "Edit my plan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item { SectionTitle("How each part went") }
        ORDER.forEach { pillar ->
            item(key = "pillar-" + pillar.name) {
                val open = openPillar == pillar
                val color = PILLAR_COLORS.getValue(pillar)
                Column(Modifier.animateContentSize()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { openPillar = if (open) null else pillar }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pillar.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                wheel.habits.filter { it.pillar == pillar }.joinToString(" · ") { h -> "${h.name} ${h.score?.let { "${(it * 100).toInt()}%" } ?: "—"}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        Text(wheel.pillarScore(pillar)?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.titleMedium, color = color)
                        Icon(
                            if (open) androidx.compose.material.icons.Icons.Outlined.KeyboardArrowUp else androidx.compose.material.icons.Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (open) "Hide" else "Show",
                        )
                    }
                    if (open) {
                        PillarCard(
                            pillar, wheel.habits.filter { it.pillar == pillar }, review.plan.commitments[pillar].orEmpty(), if (pillar == Pillar.MOVE && review.isThisWeek) onLogActivity else null,
                            targets = review.plan.custom.filter { it.pillar == pillar }.map { it to (review.targetCounts[it.name] ?: 0) },
                            onCount = viewModel::countTarget,
                            activities = if (pillar == Pillar.MOVE) review.week.activities.filter { it.id.isNotEmpty() }.sortedByDescending { it.date } else emptyList(),
                            onEditActivity = { onEditEntry(EntryType.ACTIVITY, it) },
                        )
                    }
                }
            }
        }

        if (review.isThisWeek && review.tracksPantry) {
            item { SectionTitle("Today's plate") }
            item { TodaysPlate(review.today, review.plan.macros) }
        }
        item { SectionTitle("My week in numbers") }
        item { WeekNumbers(review) }
    }
}

/** One day in the diary: what happened (from what was logged) and your own line and mood. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryDayCard(day: com.houseaffairs.app.core.data.DiaryDay, today: LocalDate, onSave: (LocalDate, String, String?) -> Unit, onOpen: (com.houseaffairs.app.core.data.DiaryEntry) -> Unit) {
    var editing by remember(day.date) { mutableStateOf(false) }
    var text by remember(day.date, day.note) { mutableStateOf(day.note.orEmpty()) }
    val label = when (day.date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.date.format(DateTimeFormatter.ofPattern("EEEE"))
    }
    val empty = day.note == null && day.entries.isEmpty() && day.mood == null
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).animateContentSize(), onClick = if (!editing) ({ editing = true }) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(day.date.format(DateTimeFormatter.ofPattern("d MMMM")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            day.mood?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
        }
        if (day.note != null && !editing) {
            Spacer(Modifier.height(8.dp))
            Text(day.note, style = MaterialTheme.typography.bodyLarge)
        }
        if (day.entries.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            day.entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .then(if (e.kind != com.houseaffairs.app.core.data.DiaryKind.MEAL) Modifier.clickable { onOpen(e) } else Modifier)
                        .padding(vertical = 3.dp),
                ) {
                    Text(e.emoji, modifier = Modifier.width(28.dp))
                    Text(e.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (editing) {
            Spacer(Modifier.height(10.dp))
            Text("How did you feel?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MOODS.forEach { m ->
                    Text(
                        m,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (day.mood == m) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onSave(day.date, text, if (day.mood == m) null else m) }
                            .padding(6.dp),
                    )
                }
            }
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("One good thing about the day…") },
                shape = MaterialTheme.shapes.medium,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { text = day.note.orEmpty(); editing = false }) { Text("Cancel") }
                Button(onClick = { onSave(day.date, text, day.mood); editing = false }, shape = CircleShape) { Text("Save") }
            }
        } else if (empty) {
            Spacer(Modifier.height(4.dp))
            Text("Nothing yet. Tap to write a line.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (day.note == null) {
            Spacer(Modifier.height(6.dp))
            Text("Tap to add a line about the day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The week in plain numbers, about you; the household's spending is labelled as such. */
@Composable
private fun WeekNumbers(review: WeekReview) {
    val w = review.week
    fun minutes(kinds: Set<String>?, not: Set<String> = emptySet()) = w.activities.filter { (kinds == null || it.kind in kinds) && it.kind !in not }.sumOf { it.minutes }
    val walk = minutes(setOf("Walk", "Run", "Cycling"))
    val chores = minutes(setOf("Gardening", "Housework"))
    val workout = minutes(null, setOf("Walk", "Run", "Cycling", "Gardening", "Housework"))
    val together = w.meals.count { it.people >= 2 }
    val lines = buildList {
        add("🚶" to if (w.activities.isEmpty()) "No walks or workouts logged yet" else listOfNotNull(
            walk.takeIf { it > 0 }?.let { "$it min walking" },
            workout.takeIf { it > 0 }?.let { "$it min workout" },
            chores.takeIf { it > 0 }?.let { "$it min chores & garden" },
        ).joinToString(" · "))
        add("🍲" to if (w.meals.isEmpty()) "No home-cooked meals logged" else (if (w.meals.size == 1) "1 home-cooked meal" else "${w.meals.size} home-cooked meals") + if (together > 0) ", $together with family" else "")
        add("✅" to when {
            w.tasksDone == 0 && w.tasksOverdue == 0 -> "No tasks of yours this week"
            w.tasksOverdue > 0 -> "${w.tasksDone} of your tasks done · ${w.tasksOverdue} overdue"
            else -> "${w.tasksDone} of your tasks done"
        })
        if (w.togetherEvents > 0) add("🎉" to if (w.togetherEvents == 1) "1 outing or family event" else "${w.togetherEvents} outings and family events")
        add("📔" to "Diary written on ${w.diaryDays} of ${w.days} days")
        review.spentMinor?.let { m -> review.currency?.let { add("💰" to "Home spent ${formatMinor(m, it)} this week") } }
        w.savedToDreamsMinor.takeIf { it > 0 }?.let { m -> review.currency?.let { add("🐷" to "${formatMinor(m, it)} put into goal gullaks") } }
        if (review.tracksPantry) add("🥬" to if (w.wasted > 0) "${w.wasted} items expired before use" else "Nothing in the pantry went to waste")
    }
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        lines.forEachIndexed { i, (emoji, line) ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row {
                Text(emoji, modifier = Modifier.width(32.dp))
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PillarCard(
    pillar: Pillar,
    habits: List<Habit>,
    mine: List<String>,
    onLog: (() -> Unit)?,
    targets: List<Pair<com.houseaffairs.app.core.data.CustomTarget, Int>> = emptyList(),
    onCount: (String, Int) -> Unit = { _, _ -> },
    activities: List<com.houseaffairs.app.core.data.ActivityLog> = emptyList(),
    onEditActivity: (String) -> Unit = {},
) {
    val color = PILLAR_COLORS.getValue(pillar)
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(pillar.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        habits.forEachIndexed { i, h ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(h.score?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.labelLarge, color = color)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                val f = h.score ?: 0f
                if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(color))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
            Spacer(Modifier.height(4.dp))
            Text(h.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        targets.forEach { (t, n) ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.titleSmall)
                    Text(if (n >= t.perWeek) "Done for the week 🎉" else "$n of ${t.perWeek} this week", style = MaterialTheme.typography.bodySmall, color = if (n >= t.perWeek) color else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onCount(t.name, n - 1) }, enabled = n > 0) { Icon(androidx.compose.material.icons.Icons.Outlined.Remove, contentDescription = "One less") }
                Button(onClick = { onCount(t.name, n + 1) }, shape = CircleShape) { Text("+1") }
            }
        }
        if (mine.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Your plan", style = MaterialTheme.typography.labelLarge, color = color)
            mine.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        if (activities.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Logged this week · tap to change", style = MaterialTheme.typography.labelLarge, color = color)
            activities.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onEditActivity(a.id) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(a.kind, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        listOfNotNull("${a.minutes} min", a.date?.let { com.houseaffairs.app.feature.add.formatDate(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (onLog != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLog, shape = CircleShape) { Text("Log a walk or workout") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckInCard(onSave: (CheckIn) -> Unit) {
    var unwind by remember { mutableIntStateOf(-1) }
    var social by remember { mutableIntStateOf(-1) }
    var gratitude by remember { mutableIntStateOf(-1) }
    HaCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
        Text("Quick check-in", style = MaterialTheme.typography.titleMedium)
        Text("Three taps, only you see the answers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        @Composable
        fun Q(text: String, value: Int, onPick: (Int) -> Unit) {
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Not really", "A little", "Yes").forEachIndexed { i, label ->
                    FilterChip(selected = value == i, onClick = { onPick(i) }, label = { Text(label) }, shape = CircleShape)
                }
            }
        }
        Q("Did you get time to unwind?", unwind) { unwind = it }
        Q("Did you spend time with friends or family?", social) { social = it }
        Q("Did you feel grateful, or pray or reflect?", gratitude) { gratitude = it }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSave(CheckIn(unwind, social, gratitude)) },
            enabled = unwind >= 0 && social >= 0 && gratitude >= 0,
            shape = CircleShape,
        ) { Text("Save check-in") }
    }
}

/** Each pillar's colour, name, score and what it covers, two to a row. */
@Composable
private fun PillarLegend(wheel: Wheel) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ORDER.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val color = PILLAR_COLORS.getValue(p)
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.medium).background(color.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(6.dp))
                            Text(p.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text(wheel.pillarScore(p)?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.labelLarge, color = color)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(p.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** "One small thing": what the week is light on, and a gentle next step. */
@Composable
fun NudgeCard(nudge: com.houseaffairs.app.core.data.Nudge, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null) {
    val color = PILLAR_COLORS.getValue(nudge.pillar)
    HaCard(modifier.fillMaxWidth(), color = color.copy(alpha = 0.13f), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("One small thing · ${nudge.pillar.label}", style = MaterialTheme.typography.labelMedium, color = color)
                Text(if (nudge.byAi) "✨ ${nudge.suggestion}" else nudge.suggestion, style = MaterialTheme.typography.titleSmall)
                Text(nudge.gap, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Close, contentDescription = "Not now", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Avatar with an Instagram-style ring: bright when this week's review is new, quiet once seen. */
@Composable
fun StoryAvatar(name: String, fresh: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            if (fresh) {
                val colors = ORDER.map { PILLAR_COLORS.getValue(it) }
                colors.forEachIndexed { i, c ->
                    drawArc(c, -180f + i * 90f + 4f, 82f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                }
            } else {
                drawCircle(Color.White.copy(alpha = 0.6f), size.minDimension / 2 - stroke / 2, style = Stroke(stroke / 2))
            }
        }
        Avatar(name, size = 34.dp)
    }
}

/** Protein, fibre, carbs and fat from today's home-cooked meals, against the plan's daily targets. */
@Composable
private fun TodaysPlate(today: com.houseaffairs.app.core.data.Macros?, targets: com.houseaffairs.app.core.data.MacroTargets) {
    val eat = PILLAR_COLORS.getValue(Pillar.EAT)
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (today == null) {
            Text("Nothing logged today", style = MaterialTheme.typography.titleSmall)
            Text(
                "When you say “made dal tadka”, the dish's protein, carbs, fat and fibre count here for everyone who ate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@HaCard
        }
        Text("${today.kcal} kcal from home-cooked meals", style = MaterialTheme.typography.titleSmall)
        listOf(
            Triple("Protein", today.protein, targets.protein),
            Triple("Fibre", today.fibre, targets.fibre),
            Triple("Carbs", today.carbs, targets.carbs),
            Triple("Fat", today.fat, targets.fat),
        ).forEach { (name, have, aim) ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth((have / aim).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(eat))
                }
                Spacer(Modifier.width(10.dp))
                Text("${have.toInt()} / $aim g", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Approximate, from one serving of each dish.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
