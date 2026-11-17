package com.houseaffairs.app.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.designsystem.Accent

/*
 * One way to look back, everywhere (Kitchen, Things, Money, Plans): a card for this month at the
 * top of the list, and a year view with the year's totals first and each month folded into one
 * line that opens into the full month. This month starts open.
 */

/** One month in a year view: [key] identifies it, [summary] is its folded line, [detail] the full month. */
class MonthRow(val key: Any, val title: String, val summary: String, val detail: @Composable () -> Unit)

/** "📊 November in the kitchen ··· This year ›", then whatever the month has to say. */
@Composable
fun MonthCard(title: String, accent: Accent, onOpen: () -> Unit, modifier: Modifier = Modifier, action: String = "This year ›", content: @Composable ColumnScope.() -> Unit) {
    HaCard(modifier = modifier, onClick = onOpen, color = accent.container) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📊  $title", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(action, style = MaterialTheme.typography.labelLarge, color = accent.color)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** A chip per year, newest first; nothing when there's only one. */
@Composable
fun YearChips(years: List<Int>, selected: Int?, onSelect: (Int) -> Unit, accent: Accent, modifier: Modifier = Modifier) {
    if (years.size < 2) return
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(years.size) { i ->
            FilterChip(
                selected = years[i] == selected, onClick = { onSelect(years[i]) }, label = { Text("${years[i]}") }, shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.color, selectedLabelColor = Color.White),
            )
        }
    }
}

/** The year's totals in the accent colour: the view you get before opening any month. */
@Composable
fun YearSummaryCard(accent: Accent, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    HaCard(modifier.fillMaxWidth(), color = accent.container, content = content)
}

/** Each month folded into one line; tapping opens or folds it. [rows] newest first; the first starts open. */
@Composable
fun MonthByMonth(rows: List<MonthRow>, modifier: Modifier = Modifier) {
    // Taps flip a month from how it started, so the newest stays open until folded.
    var flipped by remember { mutableStateOf(setOf<Any>()) }
    Column(modifier.fillMaxWidth()) {
        Text("Month by month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
        rows.forEachIndexed { i, row ->
            val open = (i == 0) != (row.key in flipped)
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                Row(
                    Modifier.fillMaxWidth().clickable { flipped = if (row.key in flipped) flipped - row.key else flipped + row.key }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall)
                        Text(row.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = if (open) "Hide ${row.title}" else "Show ${row.title}")
                }
                if (open) Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) { row.detail() }
            }
        }
    }
}

/** The year view as a sheet: title, year chips, the year's totals, then month by month, then [footer]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearSheet(
    title: String,
    accent: Accent,
    years: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    summary: @Composable ColumnScope.() -> Unit,
    rows: List<MonthRow>,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            YearChips(years, selected, onSelect, accent)
            if (rows.isEmpty()) {
                Text("Nothing logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            } else {
                YearSummaryCard(accent, Modifier.padding(horizontal = 20.dp), summary)
                // Keyed by year, so switching years opens that year's newest month.
                androidx.compose.runtime.key(selected) { MonthByMonth(rows) }
            }
            footer()
        }
    }
}
