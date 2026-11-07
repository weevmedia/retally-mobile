/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.howitworks

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.howitworks.HowItWorksContract.Action
import com.retally.shared.presentation.howitworks.HowItWorksContract.SideEffect
import com.retally.shared.presentation.howitworks.HowItWorksContract.State
import com.retally.shared.ui.howitworks.HowItWorksScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

/** "See how it works" from the empty Home: demo built from the real receipt screens. */
class HowItWorksFragment : BaseComposeFragment<State, Action, SideEffect, HowItWorksViewModel>() {

    override val viewModel: HowItWorksViewModel by viewModel()

    @Composable
    override fun ScreenContent(state: State) {
        HowItWorksScreen(state = state, dispatch = ::dispatchViewAction, snackbarHostState = snackbarHostState)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            // "Scan a receipt" also queues a scan request that Home picks up once it's back.
            SideEffect.Closed -> popBack()
        }
    }
}
