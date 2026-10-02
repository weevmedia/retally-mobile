package com.retally.app.ui.splash

import androidx.compose.runtime.Composable
import com.retally.shared.presentation.splash.SplashContract.Action
import com.retally.shared.presentation.splash.SplashContract.State
import com.retally.shared.ui.splash.SplashScreen as SharedSplashScreen

/**
 * Android entry composable for the splash. Renders the shared Compose Multiplatform screen;
 * Android-only chrome (BackHandler, test tags, View-based embeds) is added here.
 */
@Composable
fun SplashScreen(
    state: State,
    @Suppress("UNUSED_PARAMETER") dispatch: (Action) -> Unit,
    adsEnabled: Boolean = false,
    bannerAdUnitId: String = "",
) {
    SharedSplashScreen(progress = state.progress, adsEnabled = adsEnabled, bannerAdUnitId = bannerAdUnitId)
}
