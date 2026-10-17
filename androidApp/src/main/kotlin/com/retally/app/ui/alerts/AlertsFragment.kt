package com.retally.app.ui.alerts

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import com.retally.app.R
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.recalls.RecallsContract.Action
import com.retally.shared.presentation.recalls.RecallsContract.SideEffect
import com.retally.shared.presentation.recalls.RecallsContract.State
import com.retally.shared.ui.recalls.RecallsScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Food recall alerts, opened from the Home bell. Links open in the browser (LocalUriHandler). */
class AlertsFragment : BaseComposeFragment<State, Action, SideEffect, AlertsViewModel>() {

    override val viewModel: AlertsViewModel by viewModel()

    @Composable
    override fun ScreenContent(state: State) {
        RecallsScreen(state = state, dispatch = ::dispatchViewAction, snackbarHostState = snackbarHostState)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            SideEffect.Closed -> findNavController().popBackStack()
            SideEffect.OpenSignIn -> findNavController().navigate(R.id.action_alerts_to_auth)
            is SideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }
}
