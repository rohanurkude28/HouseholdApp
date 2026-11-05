package com.houseaffairs.app.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.FinishedGroup
import com.houseaffairs.app.core.data.FinishedThing
import com.houseaffairs.app.core.data.PlanMonth
import com.houseaffairs.app.core.data.planMonths
import com.houseaffairs.app.core.data.planYears
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.MonthCard
import com.houseaffairs.app.core.ui.MonthRow
import com.houseaffairs.app.core.ui.YearSheet
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private fun Long.toLocalDate(): LocalDate = java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** Plans month by month, from what the screen already has: finished tasks and list items, and goals reached. */
internal fun plansMonths(state: PlansUiState, today: LocalDate = LocalDate.now()): List<PlanMonth> {
    val tasks = state.finished.filter { it.collection == "tasks" }.mapNotNull { f -> f.at?.time?.toLocalDate()?.let { f.title to it } }
    val items = state.finished.filter { it.collection == "listItems" }.mapNotNull { f -> f.at?.time?.toLocalDate()?.let { FinishedThing(f.title, f.template, it) } }
    val goals = state.goals.filter { it.done }.mapNotNull { g -> (g.doneOn ?: g.created)?.let { g.title to it } }
    return planMonths(tasks, items, goals, today)
}

private fun monthName(m: PlanMonth) = m.month.month.getDisplayName(TextStyle.FULL, Locale.US)

private fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

/** "3 tasks done · 2 finished · 1 goal reached"; empty when nothing happened. */
private fun summaryOf(tasks: Int, finished: Int, goals: Int) = listOfNotNull(
    tasks.takeIf { it > 0 }?.let { count(it, "task") + " done" },
    finished.takeIf { it > 0 }?.let { "$it finished" },
    goals.takeIf { it > 0 }?.let { count(it, "goal") + " reached" },
).joinToString(" · ")

/** This month in Plans, at the top of Tasks, Goals and Lists; tapping opens the year. */
@Composable
internal fun PlansMonthCard(state: PlansUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val m = plansMonths(state).firstOrNull() ?: return
    MonthCard("${monthName(m)} in plans", LocalHouseColors.current.plans, onOpen, modifier) {
        Text(summaryOf(m.tasks.size, m.finishedCount, m.goals.size).ifEmpty { "Nothing ticked off yet this month." }, style = MaterialTheme.typography.bodyMedium)
        if (m.finished.isNotEmpty()) {
            Text(m.finished.joinToString("   ") { "${it.emoji} ${it.titles.size}" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The year in Plans: totals and what was finished by kind, then each month folded into one line. */
@Composable
internal fun PlansYearSheet(state: PlansUiState, onDismiss: () -> Unit) {
    val years = planYears(plansMonths(state))
    var picked by remember { mutableStateOf<Int?>(null) }
    val year = years.firstOrNull { it.year == picked } ?: years.firstOrNull()
    YearSheet(
        title = "Plans by year",
        accent = LocalHouseColors.current.plans,
        years = years.map { it.year },
        selected = year?.year,
        onSelect = { picked = it },
        onDismiss = onDismiss,
        summary = {
            if (year != null) {
                Text(if (year.year == LocalDate.now().year) "${year.year} so far" else "${year.year}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(summaryOf(year.tasks, year.finished.sumOf { it.titles.size }, year.goals).ifEmpty { "Nothing ticked off yet." }, style = MaterialTheme.typography.titleMedium)
                year.finished.takeIf { it.isNotEmpty() }?.let { groups ->
                    Text(groups.joinToString("   ") { "${it.emoji} ${it.verb.lowercase()} ${it.titles.size}" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        rows = year?.months.orEmpty().map { m ->
            MonthRow(m.month, monthName(m), summaryOf(m.tasks.size, m.finishedCount, m.goals.size).ifEmpty { "Nothing ticked off" }) { PlanMonthDetail(m) }
        },
    )
}

@Composable
private fun PlanMonthDetail(m: PlanMonth) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (m.goals.isNotEmpty()) Group("🎯", "Goals reached · ${m.goals.size}", m.goals)
        m.finished.forEach { g: FinishedGroup -> Group(g.emoji, "${g.verb} · ${g.titles.size}", g.titles) }
        if (m.tasks.isNotEmpty()) Group("✅", "Tasks done · ${m.tasks.size}", m.tasks)
        if (m.goals.isEmpty() && m.finished.isEmpty() && m.tasks.isEmpty()) {
            Text("Nothing ticked off this month yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ColumnScope.Group(emoji: String, title: String, titles: List<String>) {
    Column {
        Text("$emoji  $title", style = MaterialTheme.typography.titleSmall)
        Text(titles.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
