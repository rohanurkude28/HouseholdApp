package com.houseaffairs.app.feature.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.CategoryBar
import com.houseaffairs.app.core.data.SpendPace
import com.houseaffairs.app.core.designsystem.LocalHouseColors

/**
 * The month as a line: spent so far climbing day by day, the budget as a dashed line, and an even
 * pace as a dotted diagonal. Anything above the budget turns red, so going overboard is plain.
 */
@Composable
internal fun PaceChart(
    p: SpendPace,
    money: (Long) -> String,
    modifier: Modifier = Modifier,
    leftMinor: Long? = null,
    note: String? = null,
    line: String = com.houseaffairs.app.core.data.paceLine(p, money),
    startLabel: String = "Day 1",
    endLabel: String = "Day ${p.days}",
) {
    val accent = LocalHouseColors.current.money.color
    val over = MaterialTheme.colorScheme.error
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    val top = maxOf(p.limitMinor, p.spentMinor, 1L) * 1.12f
    Column(modifier) {
        // This month: what's left leads, since that's what decides today's spending.
        if (leftMinor != null) {
            Text(if (leftMinor < 0) "Over budget by" else "Left this month", style = MaterialTheme.typography.labelLarge, color = guide)
            Text(money(kotlin.math.abs(leftMinor)), style = MaterialTheme.typography.displaySmall, color = if (leftMinor < 0) over else MaterialTheme.colorScheme.onSurface)
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)) }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(money(p.spentMinor), style = if (leftMinor != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, color = if (p.overFromDay != null) over else MaterialTheme.colorScheme.onSurface)
            Text(if (p.limitMinor > 0) " spent of ${money(p.limitMinor)}" else " spent", style = MaterialTheme.typography.bodyMedium, color = guide)
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = if (p.limitMinor > 0 && (p.overFromDay != null || p.aheadOfPaceMinor > 0)) over else accent)
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val w = size.width
            val h = size.height
            fun x(day: Int) = w * day / p.days.coerceAtLeast(1)
            fun y(v: Long) = h - h * (v / top)
            // No target (a category without a limit): just the line, all of it below the top.
            val limitY = if (p.limitMinor > 0) y(p.limitMinor) else -1f
            if (p.limitMinor > 0) {
                // The budget: a dashed line across.
                drawLine(guide.copy(alpha = 0.6f), Offset(0f, limitY), Offset(w, limitY), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                // An even pace: from nothing at the start to the budget at the end.
                drawLine(guide.copy(alpha = 0.35f), Offset(x(p.from - 1), h), Offset(w, limitY), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f)))
            }
            if (p.cumulative.isEmpty()) return@Canvas
            val line = Path().apply {
                moveTo(0f, h)
                p.cumulative.forEachIndexed { i, v -> lineTo(x(i + 1), y(v)) }
            }
            val area = Path().apply { addPath(line); lineTo(x(p.cumulative.size), h); close() }
            // Green below the budget, red above it.
            clipRect(top = limitY) {
                drawPath(area, accent.copy(alpha = 0.15f))
                drawPath(line, accent, style = Stroke(width = 6f, cap = StrokeCap.Round))
            }
            clipRect(bottom = limitY) {
                drawPath(area, over.copy(alpha = 0.18f))
                drawPath(line, over, style = Stroke(width = 6f, cap = StrokeCap.Round))
            }
            // Today.
            val last = Offset(x(p.cumulative.size), y(p.spentMinor))
            drawCircle(if (p.limitMinor > 0 && p.overFromDay != null) over else accent, radius = 9f, center = last)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = guide, modifier = Modifier.weight(1f))
            if (p.limitMinor > 0) Text("- - target   · · · even pace", style = MaterialTheme.typography.labelSmall, color = guide)
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = guide, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

/**
 * Each category against its target: the bar is what's spent, the tick is the target. Over
 * the target, the bar turns red. Tapping one opens its history.
 */
@Composable
internal fun CategoryBars(bars: List<CategoryBar>, money: (Long) -> String, onCategory: (String) -> Unit) {
    val accent = LocalHouseColors.current.money
    val over = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    // One scale for every bar, so a big category looks big next to a small one.
    val scale = bars.maxOfOrNull { maxOf(it.spentMinor, it.targetMinor ?: 0) }?.coerceAtLeast(1) ?: 1
    Column {
        bars.forEachIndexed { i, b ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            // Padding inside the rounded tap area, so its corners never clip the first letter.
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onCategory(b.name) }.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        money(b.spentMinor) + (b.targetMinor?.let { " of ${money(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (b.over) over else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(track)) {
                    Box(
                        Modifier.fillMaxWidth((b.spentMinor.toFloat() / scale).coerceIn(0.01f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (b.over) over else accent.color),
                    )
                }
                // The target as a tick under the bar.
                b.targetMinor?.let { target ->
                    Row(Modifier.fillMaxWidth()) {
                        val share = (target.toFloat() / scale).coerceIn(0f, 1f)
                        if (share > 0f) Spacer(Modifier.fillMaxWidth(share).height(1.dp))
                        Box(Modifier.width(2.dp).height(6.dp).background(MaterialTheme.colorScheme.onSurface))
                    }
                }
            }
        }
    }
}
