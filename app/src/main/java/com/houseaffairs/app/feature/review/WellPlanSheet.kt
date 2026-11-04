package com.houseaffairs.app.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.PLAN_IDEAS
import com.houseaffairs.app.core.data.Pillar
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.WeeklyRepository
import com.houseaffairs.app.core.data.WellPlan
import com.houseaffairs.app.core.data.CustomTarget
import androidx.compose.material.icons.outlined.Close
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Loads and saves the signed-in person's live-well plan. */
@HiltViewModel
class WellPlanViewModel @Inject constructor(
    private val session: SessionManager,
    private val weekly: WeeklyRepository,
) : ViewModel() {
    private val _plan = MutableStateFlow<WellPlan?>(null)
    val plan = _plan.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val r = session.state.value as? SessionState.Ready ?: return
        viewModelScope.launch { _plan.value = runCatching { weekly.loadPlan(r.household.id, r.user.uid) }.getOrDefault(WellPlan()) }
    }

    fun save(plan: WellPlan) {
        val r = session.state.value as? SessionState.Ready ?: return
        weekly.savePlan(r.household.id, r.user.uid, plan)
        _plan.value = plan
    }
}

/** One line for Settings: "4 habits · walk 150 min a week". */
fun WellPlan.summary(): String {
    val n = commitments.values.sumOf { it.size }
    return if (n == 0) "Pick a few habits for a long, happy life" else "${if (n == 1) "1 habit" else "$n habits"} · walk $walkMinutes min a week"
}

/**
 * "What I'll do for a long life": pick habits under each pillar (or write your own) and set the
 * weekly targets the wheel scores against.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WellPlanSheet(onDismiss: () -> Unit, onSaved: () -> Unit = {}, viewModel: WellPlanViewModel = hiltViewModel()) {
    val loaded by viewModel.plan.collectAsStateWithLifecycle()
    val start = loaded ?: return
    var plan by remember(start) { mutableStateOf(start) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("My plan for a long, good life", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pick a few small things you'll do. Your weekly story and daily nudges use them. Only you see this.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Pillar.entries.forEach { pillar ->
                val color = PILLAR_COLORS.getValue(pillar)
                val mine = plan.commitments[pillar].orEmpty()
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(pillar.label, style = MaterialTheme.typography.titleMedium)
                        Text(pillar.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (PLAN_IDEAS.getValue(pillar) + mine).distinct().forEach { idea ->
                        val on = idea in mine
                        FilterChip(
                            selected = on,
                            onClick = {
                                val next = if (on) mine - idea else mine + idea
                                plan = plan.copy(commitments = plan.commitments + (pillar to next))
                            },
                            label = { Text(idea) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.22f)),
                        )
                    }
                }
                var own by remember { mutableStateOf("") }
                val add = {
                    own.trim().takeIf { it.isNotEmpty() }?.let { plan = plan.copy(commitments = plan.commitments + (pillar to (mine + it).distinct())) }
                    own = ""
                }
                OutlinedTextField(
                    value = own,
                    onValueChange = { own = it },
                    placeholder = { Text("Something of your own") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = { IconButton(onClick = add) { Icon(Icons.Outlined.Add, contentDescription = "Add") } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Weekly targets", style = MaterialTheme.typography.titleMedium)
            Text("Tap a number to type it. The app counts these from what you log.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stepper("Walking (min)", plan.walkMinutes, 30) { plan = plan.copy(walkMinutes = it.coerceIn(10, 2000)) }
            Stepper("Workout, yoga or sport (min)", plan.workoutMinutes, 15) { plan = plan.copy(workoutMinutes = it.coerceIn(10, 2000)) }
            Stepper("Study or learning (min)", plan.studyMinutes, 15) { plan = plan.copy(studyMinutes = it.coerceIn(0, 2000)) }
            Stepper("Meals cooked at home", plan.homeMeals, 1) { plan = plan.copy(homeMeals = it.coerceIn(1, 21)) }
            Stepper("Meals eaten together", plan.familyMeals, 1) { plan = plan.copy(familyMeals = it.coerceIn(1, 21)) }
            // Daily targets for "Today's plate"; the defaults suit most adults.
            Stepper("Protein a day (g)", plan.macros.protein, 5) { plan = plan.copy(macros = plan.macros.copy(protein = it.coerceIn(10, 250))) }
            Stepper("Fibre a day (g)", plan.macros.fibre, 5) { plan = plan.copy(macros = plan.macros.copy(fibre = it.coerceIn(5, 80))) }
            Stepper("Carbs a day (g)", plan.macros.carbs, 10) { plan = plan.copy(macros = plan.macros.copy(carbs = it.coerceIn(50, 500))) }
            Stepper("Fat a day (g)", plan.macros.fat, 5) { plan = plan.copy(macros = plan.macros.copy(fat = it.coerceIn(20, 200))) }

            Spacer(Modifier.height(4.dp))
            Text("My own targets", style = MaterialTheme.typography.titleMedium)
            Text("Anything you want to do a few times a week. Tick them off in your weekly story.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            plan.custom.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(PILLAR_COLORS.getValue(t.pillar)))
                    Spacer(Modifier.width(8.dp))
                    Stepper(t.name, t.perWeek, 1, suffix = "× a week", modifier = Modifier.weight(1f)) { n ->
                        plan = plan.copy(custom = plan.custom.map { if (it == t) it.copy(perWeek = n.coerceIn(1, 14)) else it })
                    }
                    IconButton(onClick = { plan = plan.copy(custom = plan.custom - t) }) { Icon(Icons.Outlined.Close, contentDescription = "Remove ${t.name}") }
                }
            }
            var newName by remember { mutableStateOf("") }
            var newPillar by remember { mutableStateOf(Pillar.OUTLOOK) }
            var newTimes by remember { mutableStateOf(3) }
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("e.g. Sleep by 11, no sugar, read 20 pages") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (newName.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pillar.entries.forEach { p ->
                        FilterChip(
                            selected = newPillar == p,
                            onClick = { newPillar = p },
                            label = { Text(p.short) },
                            leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(PILLAR_COLORS.getValue(p))) },
                            shape = CircleShape,
                        )
                    }
                }
                Stepper("How often", newTimes, 1, suffix = "× a week") { newTimes = it.coerceIn(1, 14) }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        plan = plan.copy(custom = plan.custom + CustomTarget(newName.trim(), newPillar, newTimes))
                        newName = ""
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add “${newName.trim()}”") }
            }
            Button(
                onClick = { viewModel.save(plan); onSaved(); onDismiss() },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save my plan") }
        }
    }
}

/** A number you can nudge with − and + or tap and type. */
@Composable
private fun Stepper(label: String, value: Int, step: Int, modifier: Modifier = Modifier, suffix: String = "", onChange: (Int) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - step) }) { Icon(Icons.Outlined.Remove, contentDescription = "Less") }
        var text by remember(value) { mutableStateOf(value.toString()) }
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() }.take(4); text.toIntOrNull()?.let(onChange) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.width(56.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 8.dp),
        )
        if (suffix.isNotEmpty()) Text(" $suffix", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onChange(value + step) }) { Icon(Icons.Outlined.Add, contentDescription = "More") }
    }
}
