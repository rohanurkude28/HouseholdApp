package com.houseaffairs.app.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.TextButton
import com.houseaffairs.app.core.data.PlaceItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Dream
import com.houseaffairs.app.core.data.EventItem
import com.houseaffairs.app.core.data.dayLabel
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.TickCircle
import com.houseaffairs.app.core.ui.opensEdit

// Plans' rows, the same on both phones: goals with their slider, list items with ❤️ and Idea →
// Planned, dreams in lists, dates, the add box and hints.

/** A dream in a list ("⭐ Bali trip · ₹12,000 of ₹60,000 saved"); tapping opens the same dream as the vision board. */
@Composable
fun DreamListRow(dream: Dream, progress: com.houseaffairs.app.core.data.DreamProgress?, money: (Long) -> String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(dream.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            Text(dream.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    "Dream on the vision board",
                    progress?.let { "${money(it.savedMinor + it.creditsMinor + it.investedMinor)} of ${money(dream.targetMinor)} saved" }
                        ?: dream.targetMinor.takeIf { it > 0 }?.let(money),
                    dream.targetDate?.let { dayLabel(it, systemToday()) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("⭐", style = MaterialTheme.typography.titleMedium)
    }
}


/** "＋ Add a…": the same rounded box as Tasks' add box, so every tab adds things the same way. */
@Composable
fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
fun ListHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp))
}


@Composable
fun WishRow(
    title: String,
    detail: String,
    /** A cover or family photo, drawn by the phone (each loads images its own way). */
    image: (@Composable () -> Unit)?,
    doneLabel: String,
    onClick: () -> Unit,
    onDone: () -> Unit,
    votes: List<String> = emptyList(),
    myUid: String = "",
    status: String = "IDEA",
    onVote: ((Boolean) -> Unit)? = null,
    onPlan: (() -> Unit)? = null,
    /** One-tap ways out: IMDb, trailer, Goodreads, YouTube Music, the family's saved link. */
    links: List<com.houseaffairs.app.core.data.ItemLink> = emptyList(),
) {
    Row(Modifier.fillMaxWidth().opensEdit(onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (image != null) {
            Box(Modifier.size(width = 40.dp, height = 56.dp).clip(MaterialTheme.shapes.small)) { image() }
        } else {
            IconBlob(Icons.Outlined.Star, LocalHouseColors.current.vault, size = 40.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // ❤️ from each member, and Idea → Planned; ticking it off is the circle on the right.
            if (onVote != null && onPlan != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    val mine = myUid in votes
                    Text(
                        (if (mine) "❤️" else "🤍") + if (votes.isNotEmpty()) " ${votes.size}" else "",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(CircleShape).clickable(onClickLabel = if (mine) "Remove your heart" else "Heart it") { onVote(!mine) }.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    val planned = status == "PLANNED"
                    Text(
                        if (planned) "📅 Planned" else "💡 Idea",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (planned) LocalHouseColors.current.plans.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (planned) LocalHouseColors.current.plans.container else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClickLabel = if (planned) "Back to idea" else "Mark planned", onClick = onPlan)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            com.houseaffairs.app.core.ui.LinkChips(links, Modifier.padding(top = 6.dp))
        }
        // Same as everywhere: the circle ticks it off; the row (tap or hold) opens it.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TickCircle(LocalHouseColors.current.vault.color, onTick = onDone, size = 24.dp, contentDescription = "$doneLabel: $title")
            Text(doneLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
}


/**
 * A weekly goal with a bar you slide: drag (or tap) along it and it fills as you go, naming the
 * step ("Halfway") with a light buzz at each one; letting go saves it, with Undo. Earlier weeks
 * are read-only.
 */
@Composable
fun GoalRow(goal: com.houseaffairs.app.core.data.Goal, onEdit: () -> Unit, onProgress: ((Int) -> Unit)?) {
    val accent = LocalHouseColors.current.plans
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Follows the finger while dragging; the saved value otherwise.
    var live by remember(goal.id, goal.progress) { mutableStateOf(goal.progress.toFloat()) }
    val shown by androidx.compose.animation.core.animateFloatAsState(live, label = "goal")
    val step = com.houseaffairs.app.core.data.goalStep(live.toInt())
    Column(Modifier.fillMaxWidth().opensEdit(onEdit).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                goal.kind?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(if (live >= 100) "Done 🎉" else "${live.toInt()}%", style = MaterialTheme.typography.labelLarge, color = accent.color)
        }
        Spacer(Modifier.height(10.dp))
        var width by remember { mutableStateOf(1) }
        fun at(x: Float) = (x / width * 100).coerceIn(0f, 100f).let { kotlin.math.round(it / 5f) * 5f }
        fun move(to: Float) {
            // A buzz each time a named step is crossed.
            if (com.houseaffairs.app.core.data.goalStep(to.toInt()) != com.houseaffairs.app.core.data.goalStep(live.toInt())) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            }
            live = to
        }
        Box(
            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .then(
                    if (onProgress == null) Modifier else Modifier
                        .pointerInput(goal.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = { if (live.toInt() != goal.progress) onProgress(live.toInt()) },
                                onDragCancel = { live = goal.progress.toFloat() },
                            ) { change, _ -> change.consume(); move(at(change.position.x)) }
                        }
                        .pointerInput(goal.id) {
                            detectTapGestures { pos -> val to = at(pos.x); move(to); if (to.toInt() != goal.progress) onProgress(to.toInt()) }
                        },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth((shown / 100f).coerceIn(0.06f, 1f)).fillMaxHeight().clip(RoundedCornerShape(17.dp)).background(accent.color))
            Text(
                if (onProgress != null && live < 100) "$step  ·  slide →" else step,
                style = MaterialTheme.typography.labelMedium,
                color = if (shown > 45) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}


/** "High" in red, "Medium" in amber; Low (the default) shows nothing, so the list stays calm. */
@Composable
fun PriorityTag(priority: String) {
    val color = when (priority) {
        "High" -> MaterialTheme.colorScheme.error
        "Medium" -> androidx.compose.ui.graphics.Color(0xFFD39B32)
        else -> return
    }
    Text(
        priority,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(end = 8.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}


/** A birthday or date: the cake, its kind, gift ideas saved for it, and when it's next. */
@Composable
fun EventRow(event: EventItem, gifts: Int, onClick: () -> Unit, onGifts: () -> Unit) {
    Row(Modifier.fillMaxWidth().opensEdit(onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBlob(Icons.Outlined.Cake, LocalHouseColors.current.plans, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.titleSmall)
            Text(event.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (gifts > 0) {
                Text(
                    "🎁 $gifts gift idea${if (gifts > 1) "s" else ""} ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalHouseColors.current.plans.color,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onGifts).padding(vertical = 2.dp),
                )
            }
        }
        Text(dayLabel(event.next, systemToday()), style = MaterialTheme.typography.labelLarge, color = LocalHouseColors.current.plans.color)
    }
}

@Composable
fun PlaceRow(place: PlaceItem, km: Double? = null, onClick: () -> Unit = {}) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val plans = LocalHouseColors.current.plans
    Row(Modifier.fillMaxWidth().opensEdit(onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        IconBlob(placeIcon(place.kind), plans, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(place.title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(km?.let { when {
                    it < 0.05 -> "right next door"
                    it < 1 -> "${(it * 1000).toInt() / 50 * 50} m"
                    else -> "${com.houseaffairs.app.core.util.oneDecimal(it)} km"
                } }, place.kind, place.area, place.rating).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            place.goodFor?.takeIf { it.isNotBlank() }?.let { Text("Try: $it", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        TextButton(onClick = {
            // Their saved Maps link, or a search for the name and area.
            val url = place.mapLink ?: ("https://www.google.com/maps/search/?api=1&query=" + com.houseaffairs.app.core.util.urlEncode(listOfNotNull(place.title, place.area).joinToString(" ")))
            runCatching { uri.openUri(url) }
        }) { Text("Map") }
    }
}

fun placeIcon(kind: String) = when (kind) {
    "Restaurant", "Café" -> Icons.Outlined.Restaurant
    "Grocery", "Market" -> Icons.Outlined.Storefront
    "Pharmacy", "Doctor", "Hospital" -> Icons.Outlined.LocalHospital
    else -> Icons.Outlined.Place
}
