package com.retally.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/**
 * Swift registers a factory at launch that builds a loading `GADBannerView` for a unit id and
 * format, and reports when a creative arrives. Keeping the Google Mobile Ads SDK on the Swift
 * side avoids a cinterop binding and lets the iOS app pull the SDK through SPM.
 */
object IosAdBannerFactory {
    /**
     * Returns a `UIView` already loading an ad, or `null` to render nothing. `onLoaded(true)`
     * fires when a creative is ready to show, `onLoaded(false)` when the request failed.
     * Called on the main thread.
     */
    var create: ((adUnitId: String, format: AdFormat, onLoaded: (Boolean) -> Unit) -> UIView?)? = null
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformAdBanner(
    adUnitId: String,
    format: AdFormat,
    modifier: Modifier,
    onAdSettled: () -> Unit,
) {
    val factory = IosAdBannerFactory.create ?: return
    var loaded by remember(adUnitId, format) { mutableStateOf(false) }
    val view = remember(adUnitId, format) {
        factory(adUnitId, format) { ok ->
            loaded = ok
            onAdSettled()
        }
    } ?: return

    Box(modifier, contentAlignment = Alignment.Center) {
        if (loaded) {
            // Size the interop view to the creative itself and center it. Stretching it wider
            // leaves UIKit-owned pixels either side, which the interop layer paints black.
            UIKitView(
                factory = { view },
                modifier = Modifier.width(format.widthDp.dp).height(format.heightDp.dp),
            )
        } else {
            // The native view is kept out of the hierarchy until a creative exists: an empty
            // interop "hole" renders black, not the theme colour. Reserve the space instead,
            // transparent so it matches whatever is behind the ad.
            Box(
                Modifier
                    .width(format.widthDp.dp)
                    .height(format.heightDp.dp),
            )
        }
    }
}
