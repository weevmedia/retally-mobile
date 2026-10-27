package com.retally.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * In-feed 300×250 square ad with an "Advertisement" caption, shown inline in long lists
 * (every 5th receipt, every 2nd food recall). It scrolls with the list like any other row.
 */
@Composable
fun InFeedSquareAd(adUnitId: String, modifier: Modifier = Modifier) {
    val d = ReTallyTheme.dimensions
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(d.cardRadius),
        color = Color.Transparent,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = str(S.GENERAL_ADVERTISEMENT),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = d.sm, bottom = d.xs),
            )
            AdBannerSlot(
                adUnitId = adUnitId,
                enabled = true,
                format = AdFormat.MEDIUM_RECTANGLE,
                modifier = Modifier.padding(bottom = d.sm),
            )
        }
    }
}
