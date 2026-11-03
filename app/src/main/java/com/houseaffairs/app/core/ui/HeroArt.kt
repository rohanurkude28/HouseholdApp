package com.houseaffairs.app.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import java.time.LocalTime
import kotlin.math.min

enum class DayPart(val colors: List<Color>) {
    MORNING(listOf(Color(0xFFF77B63), Color(0xFFD9578A), Color(0xFF6A4DF0))),
    AFTERNOON(listOf(Color(0xFF4E8DF5), Color(0xFF5B5CE8), Color(0xFF5B3FD9))),
    EVENING(listOf(Color(0xFF2D2370), Color(0xFF1E1752), Color(0xFF140F33))),
    ;

    companion object {
        fun now(time: LocalTime = LocalTime.now()): DayPart = when (time.hour) {
            in 5..11 -> MORNING
            in 12..17 -> AFTERNOON
            else -> EVENING
        }
    }
}

/**
 * The app's signature header: a time-of-day sky with a sun or moon, rolling hills and a small
 * house. [groundColor] should match the page background so the last hill melts into the content.
 */
@Composable
fun HeroBackground(
    dayPart: DayPart,
    groundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.background(Brush.verticalGradient(dayPart.colors))) {
        Canvas(Modifier.matchParentSize()) { drawScene(dayPart, groundColor) }
        content()
    }
}

private fun DrawScope.drawScene(dayPart: DayPart, ground: Color) {
    val w = size.width
    val h = size.height
    val evening = dayPart == DayPart.EVENING

    if (evening) {
        STARS.forEach { (x, y, r) -> drawCircle(Color.White.copy(alpha = 0.7f), r * density, Offset(x * w, y * h)) }
    }

    // Sun or moon with a soft glow.
    val center = Offset(w * 0.86f, h * 0.44f)
    val radius = min(w, h) * 0.07f
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), center, radius * 3.4f),
        radius * 3.4f,
        center,
    )
    drawCircle(Color(0xFFFFF1CC), radius, center)
    if (evening) {
        drawCircle(dayPart.colors[0], radius * 0.86f, center + Offset(radius * 0.48f, -radius * 0.28f))
    }

    // Three hills, front one in the page colour.
    drawPath(hill(w, h, baseY = h * 0.80f, rise = h * 0.07f, skew = 0.35f), Color.White.copy(alpha = 0.10f))
    drawPath(hill(w, h, baseY = h * 0.86f, rise = h * 0.08f, skew = 0.70f), Color.White.copy(alpha = 0.16f))

    // A little house on the middle hill, its window lit in the evening.
    val s = h * 0.06f
    val hx = w * 0.80f
    val hy = h * 0.825f
    val wall = Color.White.copy(alpha = 0.85f)
    drawRect(wall, Offset(hx - s * 0.5f, hy - s * 0.7f), androidx.compose.ui.geometry.Size(s, s * 0.7f))
    drawPath(
        Path().apply {
            moveTo(hx - s * 0.68f, hy - s * 0.66f)
            lineTo(hx, hy - s * 1.25f)
            lineTo(hx + s * 0.68f, hy - s * 0.66f)
            close()
        },
        wall,
    )
    drawRect(
        if (evening) Color(0xFFFFD27A) else dayPart.colors[1].copy(alpha = 0.6f),
        Offset(hx - s * 0.16f, hy - s * 0.45f),
        androidx.compose.ui.geometry.Size(s * 0.32f, s * 0.28f),
    )

    drawPath(hill(w, h, baseY = h * 0.95f, rise = h * 0.05f, skew = 0.55f), ground)
}

private fun hill(w: Float, h: Float, baseY: Float, rise: Float, skew: Float) = Path().apply {
    moveTo(0f, baseY)
    cubicTo(w * skew * 0.5f, baseY - rise * 1.6f, w * (skew + 0.2f), baseY + rise * 0.5f, w, baseY - rise * 0.5f)
    lineTo(w, h)
    lineTo(0f, h)
    close()
}

private data class Star(val x: Float, val y: Float, val r: Float)

private val STARS = listOf(
    Star(0.08f, 0.18f, 1.4f), Star(0.18f, 0.34f, 1f), Star(0.31f, 0.12f, 1.6f), Star(0.44f, 0.27f, 1f),
    Star(0.56f, 0.10f, 1.2f), Star(0.63f, 0.40f, 0.9f), Star(0.93f, 0.48f, 1.1f), Star(0.72f, 0.08f, 1f),
)
