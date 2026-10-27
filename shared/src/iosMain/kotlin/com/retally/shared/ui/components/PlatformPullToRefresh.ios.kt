package com.retally.shared.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** UIActivityIndicatorView's 12 spokes. */
private const val SPOKES = 12
/** iOS `systemGray`. */
private val IosSpinnerGray = Color(0xFF8E8E93)
private val IndicatorSize = 28.dp
/** Where a refresh that started on its own shows the spinner (content stays put). */
private val BackgroundRefreshInset = 12.dp

/**
 * iOS: UIRefreshControl behaviour drawn in Compose (a native UIKit view can't sit inside a
 * Compose list). Pulling drags the content down and reveals spokes one by one; past the
 * threshold, releasing holds the content open with the spinner turning until the refresh ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    indicator: (@Composable (progress: Float, refreshing: Boolean) -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val threshold = PullToRefreshDefaults.PositionalThreshold
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val indicatorPx = with(density) { (if (indicator != null) CustomRefreshIndicatorSize else IndicatorSize).toPx() }
    val insetPx = with(density) { BackgroundRefreshInset.toPx() }

    // A refresh the user didn't pull for (e.g. the reload on open) must not shove the list down.
    var userPulled by remember { mutableStateOf(false) }
    var backgroundRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && !userPulled) backgroundRefresh = true
    }
    val fraction = state.distanceFraction
    val settled = !isRefreshing && fraction == 0f
    LaunchedEffect(settled) {
        if (settled) {
            userPulled = false
            backgroundRefresh = false
        }
    }

    Box(
        modifier.pullToRefresh(
            isRefreshing = isRefreshing,
            state = state,
            threshold = threshold,
            onRefresh = {
                userPulled = true
                onRefresh()
            },
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = if (backgroundRefresh) 0f else state.distanceFraction * thresholdPx
                },
        ) {
            content()
        }
        if (isRefreshing || fraction > 0f) {
            val placement = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = if (backgroundRefresh) {
                            insetPx
                        } else {
                            // Centred in the gap the pulled-down content leaves.
                            ((state.distanceFraction * thresholdPx - indicatorPx) / 2f).coerceAtLeast(0f)
                        }
                    }
            if (indicator != null) {
                Box(placement.size(CustomRefreshIndicatorSize), contentAlignment = Alignment.Center) {
                    indicator(fraction.coerceIn(0f, 1f), isRefreshing)
                }
            } else {
                IosActivityIndicator(progress = fraction, spinning = isRefreshing, modifier = placement)
            }
        }
    }
}

/**
 * UIActivityIndicatorView look-alike. While pulling, spokes appear clockwise from the top in
 * proportion to [progress]; while [spinning], all 12 show with a fading tail that steps round.
 */
@Composable
private fun IosActivityIndicator(progress: Float, spinning: Boolean, modifier: Modifier = Modifier) {
    val head = if (spinning) {
        val transition = rememberInfiniteTransition(label = "ios-spinner")
        val step by transition.animateFloat(
            initialValue = 0f,
            targetValue = SPOKES.toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
            label = "ios-spinner-step",
        )
        step.toInt() % SPOKES
    } else {
        0
    }
    val visible = if (spinning) SPOKES else (progress * SPOKES).toInt().coerceIn(0, SPOKES)
    Canvas(modifier.size(IndicatorSize)) {
        val radius = size.minDimension / 2f
        val inner = radius * 0.48f
        val stroke = size.minDimension * 0.085f
        for (i in 0 until visible) {
            val alpha = if (spinning) {
                // Brightest at the head, fading over the spokes behind it.
                val age = (head - i + SPOKES) % SPOKES
                1f - (age.toFloat() / SPOKES) * 0.8f
            } else {
                0.85f
            }
            rotate(degrees = i * (360f / SPOKES) - 90f, pivot = center) {
                drawLine(
                    color = IosSpinnerGray.copy(alpha = alpha),
                    start = Offset(center.x + inner, center.y),
                    end = Offset(center.x + radius - stroke / 2f, center.y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
