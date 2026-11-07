/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.ui.theme.ReTallyDimensions
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * Sticky 320×50 banner pinned to the bottom of a screen, the way news apps anchor an ad
 * under a scrolling feed: content scrolls behind it, it never scrolls away. A hairline on
 * top and a slight lift separate it from the list; the bar sits above the system
 * navigation bar / home indicator.
 *
 * Meant for a Scaffold `bottomBar` so the list's content padding grows by the bar's height
 * and the last card is never hidden under it.
 */
@Composable
fun StickyBottomAd(
    adUnitId: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = c.surface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = c.divider, thickness = ReTallyDimensions.dividerThickness)
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = d.xs, bottom = d.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    str(S.GENERAL_ADVERTISEMENT),
                    style = ReTallyTheme.typography.pill,
                    color = c.muted,
                )
                Spacer(Modifier.height(2.dp))
                AdBannerSlot(adUnitId = adUnitId, enabled = true, format = AdFormat.BANNER)
            }
        }
    }
}
