package com.retally.shared.ui.scan

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * Loading state while a receipt is read ("calm orbit"): the receipt icon in a softly
 * breathing disc, a green progress ring filling around it, and three ReTally sparkles
 * orbiting — one steady, two twinkling. No text. Plain Compose, same on both platforms.
 *
 * @param isAiMode true when the AI is reading a torn receipt — the ring fills more slowly,
 *   since that read takes longer.
 */
@Composable
fun ScanCookingAnimation(
    modifier: Modifier = Modifier,
    isAiMode: Boolean = false,
) {
    val c = ReTallyTheme.colors
    val ringMs = if (isAiMode) 4_500 else 3_200
    val transition = rememberInfiniteTransition(label = "scan-orbit")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        // Fills over 85% of the cycle, then holds full briefly before starting again.
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = ringMs
                0f at 0 using RingEasing
                1f at (ringMs * 0.85f).toInt()
                1f at ringMs
            },
        ),
        label = "ring",
    )
    val breathe by transition.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(ringMs / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val orbit by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "orbit",
    )
    val twinkleA by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "twinkle-a",
    )
    val twinkleB by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(700),
        ),
        label = "twinkle-b",
    )

    Box(modifier.size(OrbitSize), contentAlignment = Alignment.Center) {
        // Track + filling progress ring.
        Canvas(Modifier.size(RingSize)) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = c.primary.copy(alpha = 0.16f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
            )
            drawArc(
                color = c.primary,
                startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // Breathing disc with the receipt icon.
        Box(
            Modifier
                .graphicsLayer { scaleX = breathe; scaleY = breathe }
                .size(DiscSize)
                .shadow(10.dp, CircleShape, clip = false)
                .background(c.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ReceiptGlyph(color = c.primary, modifier = Modifier.size(54.dp))
        }
        // Orbiting sparkles: big one steady at the top, two small ones twinkling below.
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = orbit }) {
            Sparkle(c.primary, 18.dp, Modifier.align(Alignment.TopCenter))
            Sparkle(
                c.primary, 12.dp,
                Modifier.align(Alignment.BottomStart).offset(x = 10.dp, y = (-30).dp)
                    .graphicsLayer { alpha = twinkleA; scaleX = 0.7f + 0.3f * twinkleA; scaleY = scaleX },
            )
            Sparkle(
                c.primary, 14.dp,
                Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-30).dp)
                    .graphicsLayer { alpha = twinkleB; scaleX = 0.7f + 0.3f * twinkleB; scaleY = scaleX },
            )
        }
    }
}

/** Four-point ReTally sparkle (same shape as the splash star). */
@Composable
private fun Sparkle(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val path = Path().apply {
            moveTo(12f * s, 0f)
            cubicTo(12.8f * s, 6.4f * s, 17.6f * s, 11.2f * s, 24f * s, 12f * s)
            cubicTo(17.6f * s, 12.8f * s, 12.8f * s, 17.6f * s, 12f * s, 24f * s)
            cubicTo(11.2f * s, 17.6f * s, 6.4f * s, 12.8f * s, 0f, 12f * s)
            cubicTo(6.4f * s, 11.2f * s, 11.2f * s, 6.4f * s, 12f * s, 0f)
            close()
        }
        drawPath(path, color)
    }
}

/** Outline receipt with a zig-zag bottom and three text lines (24-unit grid). */
@Composable
private fun ReceiptGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = Path().apply {
            moveTo(5f * s, 2f * s)
            lineTo(19f * s, 2f * s)
            lineTo(19f * s, 22f * s)
            lineTo(16.7f * s, 20.4f * s)
            lineTo(14.3f * s, 22f * s)
            lineTo(12f * s, 20.4f * s)
            lineTo(9.7f * s, 22f * s)
            lineTo(7.3f * s, 20.4f * s)
            lineTo(5f * s, 22f * s)
            close()
        }
        drawPath(body, color, style = stroke)
        listOf(7f to 15.5f, 11f to 15.5f, 15f to 12.5f).forEach { (y, end) ->
            drawLine(color, Offset(8.5f * s, y * s), Offset(end * s, y * s), strokeWidth = 1.8f * s, cap = StrokeCap.Round)
        }
    }
}

private val OrbitSize = 198.dp
private val RingSize = 170.dp
private val RingStroke = 6.dp
private val DiscSize = 112.dp
/** Same curve as the preview: quick start, gentle finish. */
private val RingEasing = CubicBezierEasing(0.6f, 0.1f, 0.3f, 1f)
