package com.retally.app.ui.scan

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.retally.shared.presentation.scan.ScanReviewContract.Action
import com.retally.shared.presentation.scan.ScanReviewContract.State
import com.retally.shared.ui.scan.ScanReviewScreen as SharedScanReviewScreen

/** Android entry composable for the scan review; renders the shared Compose Multiplatform screen. */
@Composable
fun ScanReviewScreen(
    state: State,
    dispatch: (Action) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    SharedScanReviewScreen(state = state, dispatch = dispatch, snackbarHostState = snackbarHostState)
}
