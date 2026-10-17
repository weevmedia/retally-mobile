package com.retally.shared.ui.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The "something's cooking" loading state for receipt parsing.
 *
 * A receipt with an emerald scan line sweeping down it, steam wisps rising
 * (it's cooking, after all), and rotating witty status messages.
 * Pure Compose Canvas — no image dependencies, works on both platforms.
 *
 * @param isAiMode true when the Gemini fallback is reading a torn receipt —
 *   the copy switches to "calling in the expert" flavor.
 */
@Composable
fun ScanCookingAnimation(
    modifier: Modifier = Modifier,
    isAiMode: Boolean = false,
) {
    val messages = remember(isAiMode) {
        if (isAiMode) {
            listOf(
                "This one's tricky...",
                "Calling in the AI expert...",
                "Reading between the tears...",
                "Piecing it together...",
                "The AI is squinting at this one...",
            )
        } else {
            listOf(
                "Unfolding your receipt...",
                "Reading every line...",
                "Adding up the damage...",
                "Hunting for double charges...",
                "Decoding the fine print...",
            )
        }
    }
    var messageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(isAiMode) {
        while (true) {
            delay(2200)
            messageIndex = (messageIndex + 1) % messages.size
        }
    }

    // Scan line sweeping top → bottom.
    val scanTransition = rememberInfiniteTransition(label = "scan")
    val scanProgress by scanTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanProgress",
    )
    // Steam wisps drifting upward.
    val steamTransition = rememberInfiniteTransition(label = "steam")
    val steamProgress by steamTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "steamProgress",
    )

    val emerald = Color(0xFF10B981)
    val emeraldDark = Color(0xFF0B8A5F)

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2

                // Soft emerald glow circle behind the receipt to make it pop.
                drawCircle(
                    emerald.copy(alpha = 0.10f),
                    radius = w * 0.42f,
                    center = Offset(cx, h * 0.48f),
                )
                drawCircle(
                    emerald.copy(alpha = 0.06f),
                    radius = w * 0.48f,
                    center = Offset(cx, h * 0.48f),
                )

                val receiptW = w * 0.52f
                val receiptH = h * 0.62f
                val left = (w - receiptW) / 2
                val top = h * 0.14f

                // Drop shadow for depth.
                val shadowOffset = 8f
                val shadowPath = Path().apply {
                    moveTo(left + shadowOffset, top + shadowOffset)
                    lineTo(left + receiptW + shadowOffset, top + shadowOffset)
                    lineTo(left + receiptW + shadowOffset, top + receiptH - 14f + shadowOffset)
                    val teeth = 8
                    val toothW = receiptW / teeth
                    for (i in 0 until teeth) {
                        val x = left + receiptW - i * toothW + shadowOffset
                        lineTo(x - toothW / 2, top + receiptH + shadowOffset)
                        lineTo(x - toothW, top + receiptH - 14f + shadowOffset)
                    }
                    close()
                }
                drawPath(shadowPath, Color(0x20000000))

                // Receipt paper with zigzag bottom edge.
                val paper = Path().apply {
                    moveTo(left, top)
                    lineTo(left + receiptW, top)
                    lineTo(left + receiptW, top + receiptH - 14f)
                    val teeth = 8
                    val toothW = receiptW / teeth
                    for (i in 0 until teeth) {
                        val x = left + receiptW - i * toothW
                        lineTo(x - toothW / 2, top + receiptH)
                        lineTo(x - toothW, top + receiptH - 14f)
                    }
                    close()
                }
                drawPath(paper, Color.White)
                // Thin border to separate from the background.
                drawPath(paper, Color(0xFFE5E7EB))

                // Skeleton text lines (the "content" being read) — darker for visibility.
                val lineColor = Color(0xFFD1D5DB)
                val lineLeft = left + receiptW * 0.14f
                val lineW = receiptW * 0.72f
                for (i in 0 until 6) {
                    val y = top + receiptH * 0.12f + i * receiptH * 0.11f
                    val widthFrac = if (i == 5) 0.45f else 0.85f + (i % 2) * 0.1f
                    drawLine(
                        lineColor,
                        Offset(lineLeft, y),
                        Offset(lineLeft + lineW * widthFrac.coerceAtMost(1f), y),
                        strokeWidth = 10f,
                    )
                }

                // Emerald scan line — thicker with a strong glow.
                val scanY = top + scanProgress * receiptH * 0.85f
                // Outer glow.
                drawLine(
                    emerald.copy(alpha = 0.30f),
                    Offset(left + 4f, scanY),
                    Offset(left + receiptW - 4f, scanY),
                    strokeWidth = 22f,
                )
                // Core line.
                drawLine(
                    emerald,
                    Offset(left + 4f, scanY),
                    Offset(left + receiptW - 4f, scanY),
                    strokeWidth = 8f,
                )
                // Bright center.
                drawLine(
                    Color.White.copy(alpha = 0.7f),
                    Offset(left + 4f, scanY),
                    Offset(left + receiptW - 4f, scanY),
                    strokeWidth = 3f,
                )

                // Steam wisps above the receipt — thicker and more visible.
                drawSteam(this, cx - 28f, top - 8f, steamProgress, emeraldDark)
                drawSteam(this, cx + 28f, top - 8f, (steamProgress + 0.5f) % 1f, emeraldDark)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            messages[messageIndex],
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isAiMode) "Gemini is reading your receipt" else "Reading your receipt on-device",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

/** Two wavy steam lines rising and fading — thicker for visibility. */
private fun drawSteam(scope: DrawScope, cx: Float, baseY: Float, progress: Float, color: Color) {
    with(scope) {
        val alpha = (1f - progress) * 0.75f
        val y = baseY - progress * 52f
        val wobble = kotlin.math.sin(progress * 6.28f) * 8f
        // Three short segments forming a wisp.
        for (i in 0 until 3) {
            val segY = y + i * 14f
            drawLine(
                color.copy(alpha = alpha * (1f - i * 0.22f)),
                Offset(cx + wobble * (i + 1) * 0.4f - 5f, segY),
                Offset(cx + wobble * (i + 1) * 0.4f + 5f, segY - 10f),
                strokeWidth = 6f,
            )
        }
    }
}
