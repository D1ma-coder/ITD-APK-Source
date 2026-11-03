package com.itd.app.ui.screens.qr

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** Where the scanner frame is and what it means. */
sealed interface QrFrame {
    /** No code in view: a breathing frame in the upper middle. */
    data object Searching : QrFrame

    /** Following a login code. */
    data class Tracking(val rect: Rect) : QrFrame

    /** Following a code that is not an ITD login code: yellow brackets. */
    data class Foreign(val rect: Rect) : QrFrame

    /** The code is being checked. */
    data class Locked(val rect: Rect) : QrFrame

    /** A decision is on screen: the hole closes and the scrim lightens. */
    data class Covered(val rect: Rect) : QrFrame
}

private class FrameMemory {
    var settled = false
    var wasSearching = false
}

private fun Rect.scaledAroundCenter(factor: Float): Rect {
    if (factor == 1f) return this
    val w = width * factor
    val h = height * factor
    return Rect(Offset(center.x - w / 2f, center.y - h / 2f), Size(w, h))
}

/** A square a bit larger than the detected code, no larger than 90% of the screen. */
private fun Rect.squared(screen: Size): Rect {
    val side = minOf(maxOf(width, height) * 1.18f, minOf(screen.width, screen.height) * 0.9f)
    return Rect(Offset(center.x - side / 2f, center.y - side / 2f), Size(side, side))
}

/** Dark scrim with a clear hole and corner brackets around the code, animated between [QrFrame] states. */
@Composable
fun QrScannerOverlay(frame: QrFrame, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screen = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val searching = frame is QrFrame.Searching
        val locked = frame is QrFrame.Locked
        val covered = frame is QrFrame.Covered
        val target = when (frame) {
            QrFrame.Searching -> {
                val side = minOf(screen.width * 0.7f, screen.height * 0.42f)
                Rect(Offset((screen.width - side) / 2f, screen.height * 0.3f), Size(side, side))
            }
            is QrFrame.Tracking -> frame.rect.squared(screen)
            is QrFrame.Foreign -> frame.rect.squared(screen)
            is QrFrame.Locked -> frame.rect.squared(screen)
            is QrFrame.Covered -> frame.rect.squared(screen)
        }
        val memory = remember { FrameMemory() }
        if (locked || covered) memory.settled = true
        if (memory.wasSearching && (frame is QrFrame.Tracking || frame is QrFrame.Foreign)) memory.settled = false
        val spec: AnimationSpec<Float> = when {
            searching && memory.settled -> snap()
            covered -> tween(430)
            searching -> spring(0.88f, 130f)
            locked -> spring(0.85f, 195f)
            else -> spring(0.8f, 340f)
        }
        memory.wasSearching = searching
        val left by animateFloatAsState(target.left, spec, label = "qr-left")
        val top by animateFloatAsState(target.top, spec, label = "qr-top")
        val right by animateFloatAsState(target.right, spec, label = "qr-right")
        val bottom by animateFloatAsState(target.bottom, spec, label = "qr-bottom")
        val phase by rememberInfiniteTransition(label = "qr-breath").animateFloat(
            0f, 6.2831855f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "qr-breath-phase",
        )
        val breath by animateFloatAsState(if (searching) sin(phase) * 0.016f + 1f else 1f, spring(0.9f, 200f), label = "qr-breath")
        val scrim by animateFloatAsState(if (covered) 0.4f else 0.62f, tween(240), label = "qr-scrim")
        val brackets by animateFloatAsState(if (covered) 0f else 1f, tween(300), label = "qr-brackets")
        val hole by animateFloatAsState(if (covered) 0f else 1f, tween(430), label = "qr-hole")
        val pulse = remember { Animatable(0f) }
        val pulsing = locked || covered
        LaunchedEffect(pulsing) {
            if (pulsing) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(700))
            } else {
                pulse.snapTo(0f)
            }
        }
        val bracketColor by animateColorAsState(if (frame is QrFrame.Foreign) Color(0xFFFFB938) else Color.White, tween(220), label = "qr-bracket-color")
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val rect = Rect(left, top, right, bottom).scaledAroundCenter(breath)
            val corner = minOf(26.dp.toPx(), minOf(rect.width, rect.height) * 0.22f)
            drawRect(Color.Black.copy(alpha = scrim))
            if (hole > 0.01f) {
                val cut = rect.scaledAroundCenter(hole)
                drawRoundRect(Color.Transparent, cut.topLeft, cut.size, CornerRadius(hole * corner), blendMode = BlendMode.Clear)
            }
            val p = pulse.value
            if (p > 0.001f && p < 0.999f) {
                val ring = rect.scaledAroundCenter(0.38f * p + 0.92f)
                drawRoundRect(Color.White.copy(alpha = (1f - p) * 0.3f), ring.topLeft, ring.size, CornerRadius(corner))
            }
            if (brackets > 0.01f) {
                val color = bracketColor.copy(alpha = brackets * bracketColor.alpha)
                val w = rect.width
                val h = rect.height
                val arm = (minOf(minOf(w, h) * 0.22f, minOf(w, h) / 2f - corner)).coerceAtLeast(0f)
                val l = rect.left
                val t = rect.top
                val r = rect.right
                val b = rect.bottom
                val path = Path().apply {
                    moveTo(l, t + corner + arm)
                    lineTo(l, t + corner)
                    quadraticTo(l, t, l + corner, t)
                    lineTo(l + corner + arm, t)
                    moveTo(r - corner - arm, t)
                    lineTo(r - corner, t)
                    quadraticTo(r, t, r, t + corner)
                    lineTo(r, t + corner + arm)
                    moveTo(r, b - corner - arm)
                    lineTo(r, b - corner)
                    quadraticTo(r, b, r - corner, b)
                    lineTo(r - corner - arm, b)
                    moveTo(l + corner + arm, b)
                    lineTo(l + corner, b)
                    quadraticTo(l, b, l, b - corner)
                    lineTo(l, b - corner - arm)
                }
                drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
