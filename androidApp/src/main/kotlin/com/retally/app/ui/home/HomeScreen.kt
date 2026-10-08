package com.retally.app.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.retally.app.BuildConfig
import com.retally.shared.presentation.home.HomeContract.Action
import com.retally.shared.presentation.home.HomeContract.State
import com.retally.shared.ui.home.HomeScreen as SharedHomeScreen

/**
 * Android entry composable for Home. Binds Android-side inputs (ad eligibility from the shell,
 * debug flags, the fragment's snackbar host) to the shared Compose Multiplatform screen.
 */
@Composable
fun HomeScreen(
    state: State,
    dispatch: (Action) -> Unit,
    adsEnabled: Boolean,
    bannerAdUnitId: String,
    snackbarHostState: SnackbarHostState,
) {
    SharedHomeScreen(
        state = state,
        dispatch = dispatch,
        adsEnabled = adsEnabled,
        bannerAdUnitId = bannerAdUnitId,
        showDebugActions = BuildConfig.DEBUG,
        snackbarHostState = snackbarHostState,
    )
}
