package com.houseaffairs.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Words for goals ("Halfway") and for how much is left of something ("Low"). */
val PROGRESS_STEPS = listOf("Not started", "Started", "Halfway", "Almost", "Done 🎉")
val LEFT_STEPS = listOf("Empty", "Low", "Half", "Most", "Full")

/**
 * Five chunky steps instead of a thin slider: tap one, or drag across. Each says what it means,
 * so "Halfway" beats "47%". Values are 0, 25, 50, 75 and 100; others snap to the nearest.
 */
@Composable
fun LevelPicker(value: Int, labels: List<String>, color: Color, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val steps = labels.size
    var picked by remember(value) { mutableIntStateOf(((value.coerceIn(0, 100) * (steps - 1) + 50) / 100)) }
    fun set(i: Int) {
        val c = i.coerceIn(0, steps - 1)
        if (c == picked) return
        picked = c
        // A light tick per step, like a dial.
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        onChange(c * 100 / (steps - 1))
    }
    Column(modifier.fillMaxWidth().semantics { contentDescription = "${labels[picked]}, step ${picked + 1} of $steps" }) {
        Row(
            Modifier.fillMaxWidth().height(28.dp)
                .pointerInput(steps) { detectTapGestures { o -> set((o.x / size.width * steps).toInt()) } }
                .pointerInput(steps) { detectHorizontalDragGestures { change, _ -> set((change.position.x / size.width * steps).toInt()) } },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(steps) { i ->
                // The first step is "none": filled only when it's the pick, lighter.
                val on = if (i == 0) picked == 0 else i <= picked
                val fill by animateColorAsState(
                    when {
                        !on -> MaterialTheme.colorScheme.surfaceContainerHighest
                        i == 0 -> color.copy(alpha = 0.35f)
                        else -> color.copy(alpha = 0.45f + 0.55f * i / (steps - 1))
                    },
                    label = "step",
                )
                Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(8.dp)).background(fill))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == picked) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
