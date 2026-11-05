package com.houseaffairs.app.core.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Bit(val angle: Double, val speed: Float, val spin: Float, val color: Color, val w: Float, val h: Float)

private val CONFETTI = listOf(Color(0xFF5B3FD9), Color(0xFFFF8A3D), Color(0xFF2BB673), Color(0xFFFFC83D), Color(0xFFE5487A), Color(0xFF38A3F1))

/**
 * A short burst of confetti from the bottom of the screen when something gets finished. Each new
 * [trigger] fires one. Drawn only, so taps pass through; skipped when the phone has animations off.
 */
@Composable
fun CelebrationBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val context = LocalContext.current
    val animationsOff = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    if (animationsOff) return
    val progress = remember(trigger) { Animatable(0f) }
    val bits = remember(trigger) {
        val r = Random(trigger)
        List(46) {
            // Upwards, fanned out about 70° either side.
            val angle = Math.toRadians(-90.0 + (r.nextDouble() - 0.5) * 140)
            Bit(angle, 0.9f + r.nextFloat() * 0.9f, (r.nextFloat() - 0.5f) * 720f, CONFETTI[r.nextInt(CONFETTI.size)], 8f + r.nextFloat() * 8f, 5f + r.nextFloat() * 5f)
        }
    }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(1_300, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2, size.height * 0.82f)
        val reach = size.height * 0.55f
        bits.forEach { b ->
            val d = reach * b.speed * t
            // Thrown up, then falling back a little: a simple arc.
            val x = origin.x + (cos(b.angle) * d).toFloat()
            val y = origin.y + (sin(b.angle) * d).toFloat() + reach * 0.9f * t * t
            val alpha = (1f - t).coerceIn(0f, 1f)
            rotate(b.spin * t, Offset(x, y)) {
                drawRect(b.color.copy(alpha = alpha), Offset(x - b.w / 2, y - b.h / 2), Size(b.w, b.h))
            }
        }
    }
}
