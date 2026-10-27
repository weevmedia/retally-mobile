package com.retally.shared.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Pull-to-refresh that looks native on each platform. Use this instead of Material's
 * `PullToRefreshBox` directly.
 *
 * - Android: Material 3 — a circular progress disc slides down over the content.
 * - iOS: UIRefreshControl-style — the content itself is pulled down, revealing an iOS
 *   activity indicator whose spokes appear as you pull and spin while refreshing.
 *
 * A refresh that starts on its own (e.g. a background reload on open) shows the indicator
 * without moving the content.
 *
 * [indicator] swaps the platform spinner for a custom one (e.g. the Smart List basket). It gets
 * the pull progress (0 → 1 at the trigger point) and whether the refresh is running, and should
 * fit in [CustomRefreshIndicatorSize].
 */
@Composable
expect fun PlatformPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicator: (@Composable (progress: Float, refreshing: Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
)

/** Space a custom pull-to-refresh indicator gets. */
val CustomRefreshIndicatorSize = 44.dp
