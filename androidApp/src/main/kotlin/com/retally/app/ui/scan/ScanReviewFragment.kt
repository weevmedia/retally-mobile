package com.retally.app.ui.scan

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.retally.app.location.DebugLocationPing
import com.retally.app.location.NearbyStoreScheduler
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.scan.ScanReviewContract.Action
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect.Closed
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect.Saved
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect.ShowMessage
import com.retally.shared.presentation.scan.ScanReviewContract.State
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Receipt view. Reached from Home either with the recognized text of a fresh scan (saved on
 * arrival) or with the id of an existing receipt (Safe Args). Closing pops back to Home.
 */
class ScanReviewFragment : BaseComposeFragment<State, Action, SideEffect, ScanReviewViewModel>() {

    override val viewModel: ScanReviewViewModel by viewModel()
    private val args: ScanReviewFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            args.receiptId != null -> viewModel.onLoadReceipt(args.receiptId!!)
            args.recognizedText != null -> viewModel.onTextRecognized(args.recognizedText!!, args.imagePath)
        }
    }

    @Composable
    override fun ScreenContent(state: State) {
        ScanReviewScreen(state = state, dispatch = ::dispatchViewAction, snackbarHostState = snackbarHostState)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            Closed -> findNavController().popBackStack()
            is Saved -> {
                // A new store may have arrived — rebuild the geofence list (no-op if toggle is off).
                NearbyStoreScheduler.refresh(requireContext())
                // TEST-LOCATION-PING: debug only — remove before release.
                DebugLocationPing.ping(requireContext())
            }
            is ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }
}
