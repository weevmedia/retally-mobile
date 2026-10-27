package com.retally.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retally.shared.ui.navigation.ScanFabAnchor

/**
 * Gap between a pinned banner ad and the top of the tab bar's scan FAB (Android). One value for
 * every screen (Home, Receipts, Smart List) so the ad sits at the same height everywhere.
 */
val BannerGapAboveScanFab: Dp = 6.dp

/**
 * Full-size overlay that pins [content] at the bottom centre, [gap] above the tab bar's scan
 * FAB. Uses the FAB's measured position ([ScanFabAnchor]), so it is exact on Android whatever
 * the insets. When no Compose FAB is on screen (iOS, or before the bar is laid out) it falls
 * back to [fallbackBottom] from this box's bottom edge.
 */
@Composable
fun AboveScanFab(
    fallbackBottom: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = BannerGapAboveScanFab,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    var boxBottomPx by remember { mutableStateOf<Float?>(null) }
    val fabTopPx = ScanFabAnchor.topInWindowPx
    val boxBottom = boxBottomPx
    val bottom = if (fabTopPx != null && boxBottom != null) {
        with(density) { (boxBottom - fabTopPx).toDp() } + gap
    } else {
        fallbackBottom
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { boxBottomPx = it.positionInWindow().y + it.size.height },
    ) {
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom.coerceAtLeast(0.dp)),
            content = content,
        )
    }
}
