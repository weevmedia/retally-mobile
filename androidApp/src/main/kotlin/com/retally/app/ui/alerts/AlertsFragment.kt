/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.alerts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.retally.app.R
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.app.ui.main.MainViewModel
import com.retally.shared.presentation.recalls.RecallsContract.Action
import com.retally.shared.presentation.recalls.RecallsContract.SideEffect
import com.retally.shared.presentation.recalls.RecallsContract.State
import com.retally.shared.ui.recalls.RecallsScreen
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Food recalls, opened from More › Food recalls. Links open in the browser (LocalUriHandler).
 * An in-feed ad follows every 2 recalls when ads are on.
 */
class AlertsFragment : BaseComposeFragment<State, Action, SideEffect, AlertsViewModel>() {

    override val viewModel: AlertsViewModel by viewModel()
    private val mainViewModel: MainViewModel by activityViewModel()

    @Composable
    override fun ScreenContent(state: State) {
        val shell by mainViewModel.store.observeState().collectAsStateWithLifecycle()
        RecallsScreen(
            state = state,
            dispatch = ::dispatchViewAction,
            snackbarHostState = snackbarHostState,
            adsEnabled = shell.adsEnabled,
            bannerAdUnitId = mainViewModel.homeBannerUnitId,
        )
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            SideEffect.Closed -> popBack()
            SideEffect.OpenSignIn -> findNavController().navigate(R.id.action_alerts_to_auth)
            is SideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }
}
