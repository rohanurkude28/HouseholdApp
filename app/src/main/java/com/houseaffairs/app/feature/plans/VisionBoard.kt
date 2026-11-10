package com.houseaffairs.app.feature.plans

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.houseaffairs.app.core.data.Dream
import com.houseaffairs.app.core.data.DreamProgress
import com.houseaffairs.app.core.data.boardOrdered
import com.houseaffairs.app.core.data.moved
import com.houseaffairs.app.core.util.formatCompact
import kotlin.math.roundToInt

/**
 * The vision board as a pinboard: every dream a picture card, two to a row. Hold a card and drag
 * it to rearrange (the order is the family's, saved for everyone); tap it to flip it over for
 * how far along it is. The last spot adds a new dream.
 */
@Composable
internal fun VisionBoard(
    dreams: List<Dream>,
    progressOf: (Dream) -> DreamProgress?,
    currency: String,
    onOpen: (Dream) -> Unit,
    onAdd: () -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    // Rebuilt when the family's saved order changes; a drag reorders this copy as it goes.
    var order by remember(dreams.map { it.id to it.boardOrder }) { mutableStateOf(boardOrdered(dreams).map { it.id }) }
    val byId = dreams.associateBy { it.id }
    var dragging by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val gap = 12.dp
        val cellW = (maxWidth - gap) / 2
        val cellH = cellW / 0.82f
        val cellWpx = with(density) { (cellW + gap).toPx() }
        val cellHpx = with(density) { (cellH + gap).toPx() }
        val slots = order.size + 1
        val rows = (slots + 1) / 2
        fun base(i: Int) = Offset((i % 2) * cellWpx, (i / 2) * cellHpx)

        Box(Modifier.fillMaxWidth().height(cellH * rows + gap * (rows - 1))) {
            order.forEachIndexed { index, id ->
                val dream = byId[id] ?: return@forEachIndexed
                val lifted = dragging == id
                // Slides to its new spot when others move; follows the finger while held.
                val x by animateFloatAsState(base(index).x, tween(220), label = "x")
                val y by animateFloatAsState(base(index).y, tween(220), label = "y")
                val pos = if (lifted) base(index) + drag else Offset(x, y)
                // A slight tilt, like pictures pinned up; straight while held.
                val tilt = if (lifted) 0f else if (index % 3 == 0) -1.5f else if (index % 3 == 1) 1.2f else -0.6f
                var flipped by remember(id) { mutableStateOf(false) }
                val turn by animateFloatAsState(if (flipped) 180f else 0f, tween(380), label = "flip")
                Box(
                    Modifier
                        .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                        .size(cellW, cellH)
                        .zIndex(if (lifted) 2f else 0f)
                        .graphicsLayer {
                            rotationZ = tilt
                            rotationY = turn
                            cameraDistance = 14 * this.density
                            val s = if (lifted) 1.06f else 1f
                            scaleX = s
                            scaleY = s
                            shadowElevation = if (lifted) 24f else 6f
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                            clip = true
                        }
                        .pointerInput(id) { detectTapGestures(onTap = { flipped = !flipped }) }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = id
                                    drag = Offset.Zero
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag += amount
                                    val from = order.indexOf(id)
                                    val at = base(from) + drag + Offset(cellWpx / 2, cellHpx / 2)
                                    val col = (at.x / cellWpx).toInt().coerceIn(0, 1)
                                    val row = (at.y / cellHpx).toInt().coerceAtLeast(0)
                                    val to = (row * 2 + col).coerceIn(0, order.lastIndex)
                                    if (to != from) {
                                        // Keep the card under the finger as its spot changes.
                                        drag += base(from) - base(to)
                                        order = moved(order, from, to)
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                },
                                onDragEnd = {
                                    dragging = null
                                    drag = Offset.Zero
                                    onReorder(order)
                                },
                                onDragCancel = {
                                    dragging = null
                                    drag = Offset.Zero
                                },
                            )
                        },
                ) {
                    if (turn <= 90f) {
                        DreamCard(dream, progressOf(dream), currency, null, onClick = null)
                    } else {
                        // Drawn mirrored back so it reads the right way round once turned.
                        Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                            DreamBack(dream, progressOf(dream), currency) { onOpen(dream) }
                        }
                    }
                }
            }
            // The last spot: a new dream.
            val addAt = base(order.size)
            Box(Modifier.offset { IntOffset(addAt.x.roundToInt(), addAt.y.roundToInt()) }.size(cellW, cellH)) {
                NewDreamTile("Add a dream", onAdd)
            }
        }
    }
}

/** The back of a dream's card: how far along it is, and a way in. */
@Composable
private fun DreamBack(dream: Dream, progress: DreamProgress?, currency: String, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brush.linearGradient(gradientFor(dream.kind))).background(Color.Black.copy(alpha = 0.35f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("${dream.emoji} ${dream.title}", style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        if (progress != null && dream.targetMinor > 0) {
            Text("${formatCompact(progress.totalMinor, currency)} of ${formatCompact(dream.targetMinor, currency)}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                val f = if (dream.achieved) 1f else progress.progress
                if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(Color.White))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
            Text(
                when {
                    progress.remainingMinor == 0L -> "Fully funded 🎉"
                    progress.monthlyNeededMinor != null -> "${formatCompact(progress.monthlyNeededMinor, currency)} a month to make it"
                    else -> "${formatCompact(progress.remainingMinor, currency)} to go"
                },
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f),
            )
        } else {
            Text(dream.targetDate?.let { "By ${com.houseaffairs.app.feature.add.formatDate(it)}" } ?: "Someday", style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
        OutlinedButton(onClick = onOpen, shape = CircleShape, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Open") }
    }
}
