package com.houseaffairs.app.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseaffairs.app.core.data.Dream
import com.houseaffairs.app.core.data.DreamProgress
import com.houseaffairs.app.core.data.TripSummary
import com.houseaffairs.app.core.data.dayLabel
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.ui.LocalHouseImages

// The vision board's tiles, the same on both phones: a gradient per kind of dream (or the
// family's own photo where the phone has it), the horizon headings, and the "add" tile.

private val DREAM_GRADIENTS = mapOf(
    "Travel" to listOf(Color(0xFF36D1DC), Color(0xFF5B86E5)),
    "Car" to listOf(Color(0xFFFF8A65), Color(0xFFE0507D)),
    "Home" to listOf(Color(0xFF2BC9A0), Color(0xFF1A7FA0)),
    "Gadget" to listOf(Color(0xFF8E6BFF), Color(0xFF5B3FD9)),
    "Education" to listOf(Color(0xFFFFB547), Color(0xFFF0723C)),
    "Wedding" to listOf(Color(0xFFFF9AC2), Color(0xFFD9578A)),
    "Experience" to listOf(Color(0xFFB06CFF), Color(0xFF7446E8)),
    "Savings" to listOf(Color(0xFFFF9AC2), Color(0xFFB06CFF)),
    "Emergency" to listOf(Color(0xFF5AC8A8), Color(0xFF2E7D6B)),
    "Festival" to listOf(Color(0xFFFFC94D), Color(0xFFFF7A45)),
)
fun gradientFor(kind: String) = DREAM_GRADIENTS[kind] ?: listOf(Color(0xFF6C7BFF), Color(0xFF5B3FD9))

val DREAM_TILE = 156.dp

/** "Short term · within a year · 2": same rhythm as the app's section titles. */
@Composable
fun HorizonHeader(title: String, detail: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            if (count > 0) "$detail · $count" else detail,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun NewDreamTile(label: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(0.82f).clip(MaterialTheme.shapes.large)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Trip, car, home…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A vision-board tile: emoji, name, and how close the family is. */
@Composable
fun DreamCard(dream: Dream, progress: DreamProgress?, money: (Long) -> String, trip: TripSummary?, onClick: (() -> Unit)?) {
    val images = LocalHouseImages.current
    // The family's own picture, when there is one, behind a dark fade so the words stay readable.
    val picture = remember(dream.photos) { dream.photos.firstOrNull()?.takeIf(images::hasPhoto) }
    Box(Modifier.fillMaxWidth().aspectRatio(0.82f).clip(MaterialTheme.shapes.large).background(Brush.linearGradient(gradientFor(dream.kind))).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
    if (picture != null) {
        images.Photo(picture, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.65f)))))
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (picture == null) Text(dream.emoji, fontSize = 40.sp)
        Spacer(Modifier.weight(1f))
        Text(dream.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val status = when {
            dream.achieved -> "Achieved 🎉"
            trip != null -> "🧳 ${trip.trip.title} · ${dayLabel(trip.trip.start, systemToday())}"
            progress == null -> dream.targetDate?.let { "By ${dayLabel(it, systemToday())}" } ?: "Someday"
            progress.remainingMinor == 0L -> "Fully funded"
            progress.affordableNow -> "You can afford this now"
            progress.monthlyNeededMinor != null -> "${money(progress.monthlyNeededMinor!!)}/mo to make it"
            else -> "${money(progress.remainingMinor)} to go"
        }
        Text(status, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
        if (progress != null && dream.targetMinor > 0) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                val f = if (dream.achieved) 1f else progress.progress
                if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(Color.White))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${money(progress.totalMinor)} of ${money(dream.targetMinor)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}
}


/** "By when": one row per horizon, same-size cards, each row ending in its own "add" tile. */
fun androidx.compose.foundation.lazy.LazyListScope.dreamsByWhen(
    byHorizon: Map<com.houseaffairs.app.core.data.Horizon, List<com.houseaffairs.app.core.data.Dream>>,
    progressOf: (com.houseaffairs.app.core.data.Dream) -> com.houseaffairs.app.core.data.DreamProgress?,
    money: (Long) -> String,
    tripOf: (com.houseaffairs.app.core.data.Dream) -> com.houseaffairs.app.core.data.TripSummary?,
    onOpen: (com.houseaffairs.app.core.data.Dream) -> Unit,
    /** The horizon's label ("Short term"), or null for someday. */
    onAdd: (String?) -> Unit,
) {
    com.houseaffairs.app.core.data.Horizon.entries.forEach { horizon ->
        val list = byHorizon[horizon].orEmpty()
        // Someday only shows when something's in it; the three time frames always do.
        if (horizon == com.houseaffairs.app.core.data.Horizon.SOMEDAY && list.isEmpty()) return@forEach
        item(key = "hz-" + horizon.name) { HorizonHeader(horizon.label, horizon.detail, list.size) }
        item(key = "row-" + horizon.name) {
            // Nothing here yet: a slim add row instead of an empty tile.
            if (list.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(MaterialTheme.shapes.large)
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                        .clickable { onAdd(horizon.label) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Add a ${horizon.label.lowercase()} dream", style = MaterialTheme.typography.titleSmall)
                }
                return@item
            }
            androidx.compose.foundation.lazy.LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list.size, key = { list[it].id }) { i ->
                    val dream = list[i]
                    Box(Modifier.width(DREAM_TILE)) { DreamCard(dream, progressOf(dream), money, tripOf(dream)) { onOpen(dream) } }
                }
                item {
                    Box(Modifier.width(DREAM_TILE)) { NewDreamTile("Add another") { onAdd(horizon.takeIf { it != com.houseaffairs.app.core.data.Horizon.SOMEDAY }?.label) } }
                }
            }
        }
    }
}
