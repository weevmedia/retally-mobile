package com.retally.app.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
        // 12dp between the ad and the scan FAB. The content area ends where the tab bar's
        // inset starts; the FAB's top sits 4dp below that line (offset -fabOverhang + 4dp) and
        // the banner slot has 4dp of its own bottom padding, so 12 - 4 - 4 = 4dp here.
        floatingBannerBottomOffset = BANNER_GAP_ABOVE_FAB - FAB_TOP_BELOW_INSET - BANNER_SLOT_PADDING,
    )
}

private val BANNER_GAP_ABOVE_FAB = 12.dp
private val FAB_TOP_BELOW_INSET = 4.dp
private val BANNER_SLOT_PADDING = 4.dp
