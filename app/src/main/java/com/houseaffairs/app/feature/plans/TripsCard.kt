package com.houseaffairs.app.feature.plans

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TripsCard(
    trips: List<TripSummary>,
    money: (Long) -> String,
    onAdd: () -> Unit,
    onDone: ((TripSummary) -> Unit)? = null,
    /** Add an expense to this trip, filed under [TripSpend] when one of the spend buttons was tapped. */
    onAddExpense: ((TripSummary, com.houseaffairs.app.core.data.TripSpend?) -> Unit)? = null,
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
                // Costs hidden from this person (Settings → Privacy): the trip shows, its money doesn't.
                if (!com.houseaffairs.app.core.ui.CostPrivacy.hidden) Column(horizontalAlignment = Alignment.End) {
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
            // Spent against the budget: amber once it's nearly used up, red past it (allowed, never blocked).
            com.houseaffairs.app.core.data.tripBudget(t.totalMinor, t.trip.budgetMinor, money)?.takeIf { !com.houseaffairs.app.core.ui.CostPrivacy.hidden }?.let { b ->
                val color = when (b.level) {
                    com.houseaffairs.app.core.data.BudgetLevel.OVER -> MaterialTheme.colorScheme.error
                    com.houseaffairs.app.core.data.BudgetLevel.NEAR -> LocalHouseColors.current.coral
                    com.houseaffairs.app.core.data.BudgetLevel.FINE -> accent.color
                }
                Column(Modifier.padding(start = 52.dp, top = 8.dp)) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(b.share.coerceIn(0.02f, 1f)).height(8.dp).clip(CircleShape).background(color))
                    }
                    Text(b.line, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(top = 4.dp))
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
                    // One tap per kind of trip spending: the expense opens already under this trip and that category.
                    if (onAddExpense != null) {
                        Text("Add what you spent on", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.houseaffairs.app.core.data.TRIP_SPENDS.forEach { kind ->
                                androidx.compose.material3.SuggestionChip(onClick = { onAddExpense(t, kind) }, label = { Text("${kind.emoji} ${kind.label}") }, shape = CircleShape)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
 * The plan: each day's places in the order they're visited. A place is looked up when it's added
 * (a photo and what it is, from Wikipedia), keeps a note of what to do there, and opens that exact
 * spot in Maps; the day's route goes through them in order.
 */
@Composable
private fun TripPlan(trip: com.houseaffairs.app.core.data.Trip, places: List<String>, onStops: (List<com.houseaffairs.app.core.data.Stop>) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalHouseColors.current.money
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var adding by remember { mutableStateOf<Int?>(null) }
    // The latest stops, so a lookup that finishes later updates the right list.
    val latest = androidx.compose.runtime.rememberUpdatedState(trip.stops)
    fun open(url: String) { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }
    val days = trip.dayCount()
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🗺️  The plan", style = MaterialTheme.typography.titleSmall)
        (1..days).forEach { day ->
            if (days > 1) Text("Day $day · ${formatDate(trip.start.plusDays(day - 1L))}", style = MaterialTheme.typography.labelLarge, color = accent.color, modifier = Modifier.padding(top = 6.dp))
            val today = trip.stops.withIndex().filter { it.value.day == day }
            today.forEachIndexed { n, (i, stop) ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { com.houseaffairs.app.core.data.routeUrl(listOf(stop), trip.title)?.let(::open) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (stop.photo != null) {
                        // Wikimedia turns away image requests that don't say which app they're from.
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).data(stop.photo).addHeader("User-Agent", "HouseAffairs/0.1 (family planner)").crossfade(true).build(),
                            contentDescription = stop.title,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium),
                        )
                    } else {
                        androidx.compose.foundation.layout.Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(accent.container), contentAlignment = Alignment.Center) {
                            Text("${n + 1}", style = MaterialTheme.typography.titleMedium, color = accent.color)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stop.title + (stop.time?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
                        stop.about?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                        stop.note?.let { Text("To do: $it", style = MaterialTheme.typography.bodySmall, color = accent.color) }
                        Text("📍 Open in Maps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        androidx.compose.material3.IconButton(onClick = { onStops(com.houseaffairs.app.core.data.moveStop(trip.stops, i, later = false)) }, enabled = n > 0, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Earlier")
                        }
                        androidx.compose.material3.IconButton(onClick = { onStops(com.houseaffairs.app.core.data.moveStop(trip.stops, i, later = true)) }, enabled = n < today.lastIndex, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Later")
                        }
                        androidx.compose.material3.IconButton(onClick = { onStops(trip.stops.filterIndexed { k, _ -> k != i }) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove ${stop.title}")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { adding = day }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("+ Add a place") }
                if (today.size > 1) com.houseaffairs.app.core.data.routeUrl(today.map { it.value }, trip.title)?.let { url ->
                    TextButton(onClick = { open(url) }) { Text("Route in Maps") }
                }
            }
        }
    }
    adding?.let { day ->
        var text by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(if (days > 1) "Add a place to day $day" else "Add a place") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(text, { text = it }, label = { Text("Where") }, singleLine = true)
                    androidx.compose.material3.OutlinedTextField(note, { note = it }, label = { Text("What to do there (optional)") }, singleLine = false, minLines = 2)
                    Text("A photo and what it is are looked up for you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Saved places first, narrowed as you type.
                    places.filter { text.isBlank() || it.contains(text.trim(), ignoreCase = true) }.take(5).forEach { p ->
                        TextButton(onClick = { text = p }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("📍 $p") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val stop = com.houseaffairs.app.core.data.Stop(day, text.trim(), note = note.trim().ifEmpty { null })
                        onStops(trip.stops + stop)
                        adding = null
                        // Filled in when the lookup comes back; the place is saved straight away either way.
                        scope.launch {
                            com.houseaffairs.app.core.data.lookUpPlace(stop.title, trip.title)?.let { info ->
                                val now = latest.value
                                val at = now.indexOfLast { it.day == stop.day && it.title == stop.title && it.about == null }
                                if (at >= 0) onStops(now.toMutableList().also { it[at] = it[at].copy(about = info.about, photo = info.photo) })
                            }
                        }
                    },
                    enabled = text.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }
}
