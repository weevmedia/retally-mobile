package com.retally.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.app.ui.main.MainViewModel
import com.retally.shared.presentation.home.HomeContract.Action
import com.retally.shared.presentation.home.HomeContract.SideEffect
import com.retally.shared.presentation.home.HomeContract.SideEffect.LaunchScanner
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenAlerts
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenBudget
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenDuplicates
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenSignIn
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenReceipt
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenReceipts
import com.retally.shared.presentation.home.HomeContract.SideEffect.OpenScanReview
import com.retally.shared.presentation.home.HomeContract.SideEffect.ShowMessage
import com.retally.shared.presentation.home.HomeContract.State
import com.retally.shared.presentation.main.MainContract
import com.retally.shared.presentation.main.MainTab
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
            OpenAlerts -> navigate(HomeFragmentDirections.actionHomeToAlerts())
            // Home lists only the newest few; "See all" hands over to the Receipts tab.
            OpenReceipts -> mainViewModel.store.dispatch(MainContract.Action.SelectTab(MainTab.RECEIPTS))
            OpenSignIn -> Unit // TODO(sign-in): present the account-linking flow
            is ShowMessage -> effect.userMessage()?.let { snackbarHostState.showSnackbar(it) }
        }
    }
}
