package com.retally.shared.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.retally.shared.ui.theme.ReTallyTheme

/** Android: the standard Material 3 pull-to-refresh disc, tinted with the brand colour. */
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
    val thresholdPx = with(LocalDensity.current) { PullToRefreshDefaults.PositionalThreshold.toPx() }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            if (indicator != null) {
                // Custom indicator slides down with the pull, like the Material disc.
                val shown = if (isRefreshing) state.distanceFraction.coerceAtLeast(1f) else state.distanceFraction
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .size(CustomRefreshIndicatorSize)
                        .graphicsLayer {
                            translationY = shown.coerceAtMost(1.5f) * thresholdPx * 0.5f
                            alpha = (shown * 2f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    indicator(state.distanceFraction.coerceIn(0f, 1f), isRefreshing)
                }
            } else {
                PullToRefreshDefaults.Indicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = ReTallyTheme.colors.surface,
                    color = ReTallyTheme.colors.primary,
                )
            }
        },
        content = content,
    )
}
