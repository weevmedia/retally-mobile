/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.home

import com.retally.shared.presentation.home.HomeContract
import com.retally.app.R
import androidx.navigation.fragment.findNavController
import androidx.navigation.NavOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.app.ui.main.MainViewModel
import com.retally.shared.presentation.home.HomeContract.Action
import com.retally.shared.presentation.home.HomeContract.SideEffect
import com.retally.shared.presentation.home.HomeContract.SideEffect.LaunchScanner
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenNotifications
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenBudget
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenDuplicates
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenSignIn
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenReceipt
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenReceipts
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenScanReview
import com.retally.shared.presentation.home.HomeContract.SideEffect.ShowMessage
import com.retally.shared.presentation.home.HomeContract.State
import com.retally.shared.ui.home.userMessage
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : BaseComposeFragment<State, Action, SideEffect, HomeViewModel>() {

    override val viewModel: HomeViewModel by viewModel()
    private val mainViewModel: MainViewModel by activityViewModel()

    @Composable
    override fun ScreenContent(state: State) {
        val shell by mainViewModel.store.observeState().collectAsStateWithLifecycle()
        HomeScreen(
            state = state,
            dispatch = ::dispatchViewAction,
            adsEnabled = shell.adsEnabled,
            bannerAdUnitId = mainViewModel.homeBannerUnitId,
            snackbarHostState = snackbarHostState,
        )
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            // Custom scanner (CameraX): capture or gallery upload, straight to review.
            LaunchScanner -> navigate(HomeFragmentDirections.actionHomeToCustomScanner())
            is OpenScanReview -> navigate(
                HomeFragmentDirections.actionHomeToScanReview(
                    recognizedText = effect.lines.joinToString("\n"),
                    imagePath = effect.imagePath,
                ),
            )
            is OpenReceipt -> navigate(HomeFragmentDirections.actionHomeToScanReview(receiptId = effect.id))
            OpenDuplicates -> navigate(HomeFragmentDirections.actionHomeToDuplicates())
            OpenBudget -> navigate(HomeFragmentDirections.actionHomeToBudget())
            OpenNotifications -> navigate(HomeFragmentDirections.actionHomeToNotifications())
            // Home lists only the newest few; "See all" hands over to the Receipts tab.
            // Selecting the tab in MainStore only moves the highlight; Android also has to
            // navigate (same options as a tab-bar tap so the back stack stays per tab).
            OpenReceipts -> findNavController().navigate(
                R.id.receiptsFragment,
                null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(R.id.homeFragment, inclusive = false, saveState = true)
                    .build(),
            )
            // Guest banner: the same sign-in flow as More › Log in and the Smart List.
            OpenSignIn -> navigate(HomeFragmentDirections.actionHomeToAuth())
            // A nudge pointing at a store's Smart List (the store is already queued).
            HomeContract.SideEffect.OpenShoppingList -> findNavController().navigate(
                R.id.aiListFragment,
                null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(R.id.homeFragment, inclusive = false, saveState = true)
                    .build(),
            )
            SideEffect.OpenHowItWorks -> navigate(HomeFragmentDirections.actionHomeToHowItWorks())
            is ShowMessage -> effect.userMessage()?.let { snackbarHostState.showSnackbar(it) }
        }
    }
}
