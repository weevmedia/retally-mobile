package com.retally.app.ui.splash

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.app.ui.main.MainViewModel
import com.retally.shared.presentation.splash.SplashContract.Action
import com.retally.shared.presentation.splash.SplashContract.SideEffect
import com.retally.shared.presentation.splash.SplashContract.SideEffect.NavigateToMain
import com.retally.shared.presentation.splash.SplashContract.State
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Start destination. Warms the ads SDK behind the brand animation, then routes to Home. */
class SplashFragment : BaseComposeFragment<State, Action, SideEffect, SplashViewModel>() {

    override val viewModel: SplashViewModel by viewModel()
    private val mainViewModel: MainViewModel by activityViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start()
    }

    @Composable
    override fun ScreenContent(state: State) {
        val shell by mainViewModel.store.observeState().collectAsStateWithLifecycle()
        SplashScreen(
            state = state,
            dispatch = ::dispatchViewAction,
            adsEnabled = shell.adsEnabled,
            bannerAdUnitId = mainViewModel.homeBannerUnitId,
        )
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            is NavigateToMain -> {
                mainViewModel.onAdsInitialized(effect.adsReady)
                // Safe Args; the graph pops splash so Back from Home exits the app.
                // First launch detours through onboarding; adsReady is already in MainViewModel.
                if (effect.isFirstLaunch) {
                    navigate(SplashFragmentDirections.actionSplashToOnboarding())
                } else {
                    navigate(SplashFragmentDirections.actionSplashToHome())
                }
            }
        }
    }
}
