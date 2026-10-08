package com.retally.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.onboarding.OnboardingContract.Action
import com.retally.shared.presentation.onboarding.OnboardingContract.SideEffect
import com.retally.shared.presentation.onboarding.OnboardingContract.SideEffect.NavigateToMain
import com.retally.shared.presentation.onboarding.OnboardingContract.State
import org.koin.androidx.viewmodel.ext.android.viewModel

/** First launch only: value-prop pager between splash and sign-in. The tab bar stays hidden. */
class OnboardingFragment : BaseComposeFragment<State, Action, SideEffect, OnboardingViewModel>() {

    override val viewModel: OnboardingViewModel by viewModel()

    @Composable
    override fun ScreenContent(state: State) {
        OnboardingScreen(dispatch = ::dispatchViewAction)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            is NavigateToMain -> {
                // Safe Args; the graph pops onboarding so Back from Home exits the app.
                // First launch continues into sign-in; the auth screen enters Home.
                navigate(OnboardingFragmentDirections.actionOnboardingToAuth())
            }
        }
    }
}
