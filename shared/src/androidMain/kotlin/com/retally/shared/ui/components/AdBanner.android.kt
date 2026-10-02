package com.retally.shared.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
internal actual fun PlatformAdBanner(
    adUnitId: String,
    format: AdFormat,
    modifier: Modifier,
    onAdSettled: () -> Unit,
) {
    val context = LocalContext.current
    val adView = remember(adUnitId, format) {
        AdView(context).apply {
            setAdSize(format.toAdSize())
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("ReTallyAds", "banner loaded: $adUnitId ($format)")
                    onAdSettled()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("ReTallyAds", "banner failed: code=${error.code} ${error.message} (${error.domain})")
                    onAdSettled()
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    AndroidView(factory = { adView }, modifier = modifier)
}

private fun AdFormat.toAdSize(): AdSize = when (this) {
    AdFormat.BANNER -> AdSize.BANNER
    AdFormat.MEDIUM_RECTANGLE -> AdSize.MEDIUM_RECTANGLE
}
