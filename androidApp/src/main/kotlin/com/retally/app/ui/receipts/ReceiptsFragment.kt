package com.retally.app.ui.receipts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.app.ui.main.MainViewModel
import com.retally.shared.presentation.receipts.ReceiptsContract.Action
import com.retally.shared.presentation.receipts.ReceiptsContract.SideEffect
import com.retally.shared.presentation.receipts.ReceiptsContract.SideEffect.OpenReceipt
import com.retally.shared.presentation.receipts.ReceiptsContract.SideEffect.ShowMessage
import com.retally.shared.presentation.receipts.ReceiptsContract.State
import com.retally.shared.ui.receipts.ReceiptsScreen
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Receipts tab: the full list, month-filtered and paged.
 *
 * No bottom inset is passed — MainActivity adds the floating tab bar's height straight into
 * the nav host's window insets, so Compose already sees it (unlike iOS, see ReceiptsView).
 */
class ReceiptsFragment : BaseComposeFragment<State, Action, SideEffect, ReceiptsViewModel>() {

    override val viewModel: ReceiptsViewModel by viewModel()
    private val mainViewModel: MainViewModel by activityViewModel()

    @Composable
    override fun ScreenContent(state: State) {
        val shell by mainViewModel.store.observeState().collectAsStateWithLifecycle()
        ReceiptsScreen(
            state = state,
            dispatch = ::dispatchViewAction,
            snackbarHostState = snackbarHostState,
            adsEnabled = shell.adsEnabled,
            bannerAdUnitId = mainViewModel.homeBannerUnitId,
        )
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            is OpenReceipt -> findNavController().navigate(
                ReceiptsFragmentDirections.actionReceiptsToScanReview(receiptId = effect.id),
            )
            is ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }
}
