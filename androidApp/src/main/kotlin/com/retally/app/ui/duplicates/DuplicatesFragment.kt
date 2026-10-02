package com.retally.app.ui.duplicates

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.duplicates.DuplicatesContract.Action
import com.retally.shared.presentation.duplicates.DuplicatesContract.SideEffect
import com.retally.shared.presentation.duplicates.DuplicatesContract.SideEffect.Closed
import com.retally.shared.presentation.duplicates.DuplicatesContract.SideEffect.OpenReceipt
import com.retally.shared.presentation.duplicates.DuplicatesContract.SideEffect.ShowMessage
import com.retally.shared.presentation.duplicates.DuplicatesContract.State
import com.retally.shared.ui.duplicates.DuplicatesScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

/** "Duplicates found" list, reached from the Home stat card. */
class DuplicatesFragment : BaseComposeFragment<State, Action, SideEffect, DuplicatesViewModel>() {

    override val viewModel: DuplicatesViewModel by viewModel()

    @Composable
    override fun ScreenContent(state: State) {
        DuplicatesScreen(state = state, dispatch = ::dispatchViewAction, snackbarHostState = snackbarHostState)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            Closed -> findNavController().popBackStack()
            is OpenReceipt -> findNavController().navigate(DuplicatesFragmentDirections.actionDuplicatesToScanReview(receiptId = effect.id))
            is ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }
}
