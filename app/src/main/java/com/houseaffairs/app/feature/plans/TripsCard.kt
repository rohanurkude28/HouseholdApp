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
import com.houseaffairs.app.core.data.dayCount
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
    /** Saves a trip's new plan (stops by day, in order). */
    onStops: ((com.houseaffairs.app.core.data.Trip, List<com.houseaffairs.app.core.data.Stop>) -> Unit)? = null,
    /** Saved places, offered when adding a stop. */
    places: List<String> = emptyList(),
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
                    onStops?.let { save -> TripPlan(t.trip, places) { save(t.trip, it) } }
                    TextButton(onClick = { onEdit(t.trip.id) }, contentPadding = PaddingValues(0.dp)) { Text("Edit trip") }
                }
            }
        }
    }
}

/**
 * The plan: each day's places in the order they're visited, moved up or down, with directions
 * through them in Google Maps. One day for an outing, start to end for a trip.
 */
@Composable
private fun TripPlan(trip: com.houseaffairs.app.core.data.Trip, places: List<String>, onStops: (List<com.houseaffairs.app.core.data.Stop>) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalHouseColors.current.money
    var adding by remember { mutableStateOf<Int?>(null) }
    val days = trip.dayCount()
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("🗺️  The plan", style = MaterialTheme.typography.titleSmall)
        (1..days).forEach { day ->
            if (days > 1) Text("Day $day · ${formatDate(trip.start.plusDays(day - 1L))}", style = MaterialTheme.typography.labelLarge, color = accent.color, modifier = Modifier.padding(top = 6.dp))
            val today = trip.stops.withIndex().filter { it.value.day == day }
            today.forEachIndexed { n, (i, stop) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${n + 1}. ${stop.title}" + (stop.time?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = { onStops(com.houseaffairs.app.core.data.moveStop(trip.stops, i, later = false)) }, enabled = n > 0, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Earlier")
                    }
                    androidx.compose.material3.IconButton(onClick = { onStops(com.houseaffairs.app.core.data.moveStop(trip.stops, i, later = true)) }, enabled = n < today.lastIndex, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Later")
                    }
                    androidx.compose.material3.IconButton(onClick = { onStops(trip.stops.filterIndexed { k, _ -> k != i }) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove ${stop.title}")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { adding = day }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("+ Add a place") }
                com.houseaffairs.app.core.data.routeUrl(today.map { it.value })?.let { url ->
                    TextButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }) {
                        Text(if (today.size > 1) "Route in Maps" else "Show in Maps")
                    }
                }
            }
        }
    }
    adding?.let { day ->
        var text by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(if (days > 1) "Add a place to day $day" else "Add a place") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(text, { text = it }, label = { Text("Where") }, singleLine = true)
                    // Saved places first, narrowed as you type.
                    places.filter { text.isBlank() || it.contains(text.trim(), ignoreCase = true) }.take(6).forEach { p ->
                        TextButton(onClick = { text = p }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("📍 $p") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (text.isNotBlank()) onStops(trip.stops + com.houseaffairs.app.core.data.Stop(day, text.trim())); adding = null }, enabled = text.isNotBlank()) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }
}
