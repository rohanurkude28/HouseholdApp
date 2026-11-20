package com.houseaffairs.app.feature.add

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.USUAL_COUNT

/** One thing the + sheet offers: [key] pins and counts it, [emoji] and [label] are what people see. */
data class MenuOption(val key: String, val emoji: String, val label: String)

/** Typed together, these become several entries at once (the parser splits them). */
val SEVERAL_EXAMPLES = listOf("200 uber and 500 groceries", "add milk, curd and bread to shopping list")

/**
 * The add menu before anything is typed, the same on both phones: big tiles for what this person
 * pinned or adds most, then one "More things to add" button. Behind it is everything else, by
 * area, each option once. Holding any option pins it to the tiles; holding a tile unpins it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AddMenu(
    usual: List<MenuOption>,
    groups: List<Pair<String, List<MenuOption>>>,
    pinned: Set<String>,
    onPick: (MenuOption) -> Unit,
    onPin: (MenuOption) -> Unit,
    onExample: (String) -> Unit,
) {
    // Large text (set on the phone) gets two wide tiles a row, so words never break mid-way.
    val perRow = if (LocalDensity.current.fontScale > 1.15f) 2 else USUAL_COUNT
    usual.chunked(perRow).forEach { row ->
        // Equal heights, even when one label takes two lines.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { option ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
                        .combinedClickable(onClick = { onPick(option) }, onLongClick = { onPin(option) }, onLongClickLabel = if (option.key in pinned) "Unpin" else "Pin to top")
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    Text(option.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(option.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    // Everything else waits behind one button: a wall of options was too much to take in.
    var more by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { more = !more }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
        Text(if (more) "Fewer choices" else "More things to add")
    }
    if (!more) return
    Text(
        if (pinned.isEmpty()) "Hold any of these to pin it up top." else "Hold a tile to unpin it.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val shown = usual.map { it.key }.toSet()
    // One block per area, wrapping rather than scrolling sideways, so nothing hides off-screen.
    (groups + listOf("Several at once" to SEVERAL_EXAMPLES.map { MenuOption("EX:$it", "✍️", it) })).forEach { (name, all) ->
        val options = all.filter { it.key !in shown }
        if (options.isEmpty()) return@forEach
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val example = option.key.startsWith("EX:")
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clip(CircleShape).combinedClickable(
                            onClick = { if (example) onExample(option.label) else onPick(option) },
                            onLongClick = { if (!example) onPin(option) },
                            onLongClickLabel = "Pin to top",
                        ),
                    ) {
                        // Examples in quotes, so they read as something to say, not a kind to pick.
                        Text(if (example) "“${option.label}”" else "${option.emoji}  ${option.label}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}
