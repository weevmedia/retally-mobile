package com.retally.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Google's fixed banner formats. Both fill with the same test unit id; the app picks the
 * format by context — a strip when there is content to scroll, a rectangle when the screen
 * is otherwise empty and has room.
 */
enum class AdFormat(val widthDp: Int, val heightDp: Int) {
    /** Standard banner, 320×50. */
    BANNER(320, 50),
    /** Medium rectangle, 300×250 — the in-feed "Advertisement" box. */
    MEDIUM_RECTANGLE(300, 250),
}

/**
 * Reserved slot for an AdMob banner of the given [format].
 *
 * The slot always occupies the format's height once [enabled] is true so content does not
 * jump when the fill arrives. When [enabled] is false (consent not obtained, SDK unavailable,
 * ads switched off in [com.retally.shared.domain.ads.AdsConfig]) it collapses to nothing.
 *
 * Calls [onAdSettled] once the ad request finishes, whether a creative loaded or failed.
 */
@Composable
fun AdBannerSlot(
    adUnitId: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    format: AdFormat = AdFormat.BANNER,
    onAdSettled: () -> Unit = {},
) {
    if (!enabled) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(format.heightDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        PlatformAdBanner(adUnitId = adUnitId, format = format, modifier = Modifier, onAdSettled = onAdSettled)
    }
}

/**
 * Platform banner view. Android wraps `AdView`; iOS embeds a `GADBannerView` supplied by
 * Swift through [com.retally.shared.ui.components.IosAdBannerFactory].
 */
@Composable
internal expect fun PlatformAdBanner(
    adUnitId: String,
    format: AdFormat,
    modifier: Modifier,
    onAdSettled: () -> Unit = {},
)
