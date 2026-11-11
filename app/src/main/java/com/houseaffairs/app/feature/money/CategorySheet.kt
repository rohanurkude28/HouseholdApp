package com.houseaffairs.app.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.CategoryReport
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.feature.add.formatDate
import java.time.format.DateTimeFormatter

/** One category over time: totals, 12 months as bars, where the money went, and every expense (tap to edit). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategorySheet(report: CategoryReport, money: (Long) -> String, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val accent = LocalHouseColors.current.money
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(report.category, style = MaterialTheme.typography.headlineSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOf("This month" to report.thisMonthMinor, "This year" to report.thisYearMinor, "All time" to report.allMinor).forEach { (l, v) ->
                        Column {
                            Text(money(v), style = MaterialTheme.typography.titleMedium)
                            Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // Twelve months as bars, so a busy month stands out.
            item {
                val most = report.months.maxOf { it.second }.coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    report.months.forEach { (ym, v) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(maxOf(2, (64 * v / most).toInt()).dp).clip(RoundedCornerShape(4.dp)).background(if (v > 0) accent.color else MaterialTheme.colorScheme.surfaceContainerHighest))
                            Text(ym.format(DateTimeFormatter.ofPattern("MMM")).take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (report.places.isNotEmpty()) {
                item { Text("Where it went", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(report.places.take(8)) { (name, times, total) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$times× · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(total), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(if (report.items.isEmpty()) "Nothing spent here yet" else "Every expense · ${report.items.size}", style = MaterialTheme.typography.titleMedium)
            }
            items(report.items, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).androidxClickable { onOpen(t.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title.ifEmpty { report.category }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(formatDate(t.date), t.subcategory, t.paidWith).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(money(t.amountMinor), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))
