package com.houseaffairs.app.feature.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.TripSummary
import com.houseaffairs.app.core.data.isOngoing
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.categoryIcon
import com.houseaffairs.app.feature.add.formatDate

/** Trips and outings with their spending clubbed by category; tap one to see the split. */
@Composable
internal fun TripsCard(
    trips: List<TripSummary>,
    money: (Long) -> String,
    onAdd: () -> Unit,
    onDone: ((TripSummary) -> Unit)? = null,
    onAddExpense: ((TripSummary) -> Unit)? = null,
    onReopen: ((TripSummary) -> Unit)? = null,
    onEdit: (String) -> Unit,
) {
    val accent = LocalHouseColors.current.money
    val today = java.time.LocalDate.now()
    // Collapsed until tapped, so the dreams below stay in view.
    var open by remember { mutableStateOf<String?>(null) }
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (trips.isEmpty()) {
            Text(
                "Going on a Sunday outing or a trip? Start one and every expense that day (food, shopping, transport) is clubbed under it automatically. Say “start trip Lonavala this sunday”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAdd, shape = CircleShape) { Text("Start a trip or outing") }
            return@HaCard
        }
        trips.take(5).forEachIndexed { index, t ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            val ongoing = t.trip.isOngoing(today)
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { open = if (open == t.trip.id) null else t.trip.id },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBlob(Icons.Outlined.Luggage, accent, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.trip.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            when {
                                t.trip.done -> "Done · ${formatDate(t.trip.start)}"
                                ongoing -> "Happening now · new expenses join it"
                                else -> formatDate(t.trip.start)
                            },
                            "${t.count} expenses",
                            t.trip.dream?.let { "for $it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ongoing) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(t.totalMinor), style = MaterialTheme.typography.titleSmall)
                    t.leftMinor?.let {
                        Text(
                            if (it >= 0) "${money(it)} left" else "${money(-it)} over",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (it >= 0) accent.color else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (open == t.trip.id) {
                Column(Modifier.padding(start = 52.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    t.categories.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(categoryIcon(c.name), contentDescription = null, tint = accent.color, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(c.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text(money(c.totalMinor), style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            c.subcategories.joinToString(" · ") { (sub, amt) -> "$sub ${money(amt)}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 26.dp),
                        )
                    }
                    if (t.categories.isEmpty()) {
                        Text("No expenses yet. Add one here, or say “spent 500 on lunch” while it's on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onAddExpense != null) {
                            Button(onClick = { onAddExpense(t) }, shape = CircleShape) { Text("+ Add expense") }
                        }
                        if (onReopen != null && t.trip.done) {
                            androidx.compose.material3.OutlinedButton(onClick = { onReopen(t) }, shape = CircleShape) { Text("Reopen") }
                        }
                        if (onDone != null && !t.trip.done) {
                            androidx.compose.material3.OutlinedButton(onClick = { onDone(t) }, shape = CircleShape) { Text("Mark done") }
                        }
                    }
                    TextButton(onClick = { onEdit(t.trip.id) }, contentPadding = PaddingValues(0.dp)) { Text("Edit trip") }
                }
            }
        }
    }
}
