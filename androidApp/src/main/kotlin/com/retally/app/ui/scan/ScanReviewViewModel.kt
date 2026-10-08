package com.retally.app.ui.scan

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.scan.ScanReviewContract.Action
import com.retally.shared.presentation.scan.ScanReviewContract.SideEffect
import com.retally.shared.presentation.scan.ScanReviewContract.State
import com.retally.shared.presentation.scan.ScanReviewContract.Store

/** Android ViewModel for the receipt view. Owns the shared [Store]; seeding is idempotent. */
class ScanReviewViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>() {

    fun onTextRecognized(text: String, imagePath: String?) = dispatch(Action.TextRecognized(text.lines(), imagePath))

    fun onLoadReceipt(id: String) = dispatch(Action.LoadReceipt(id))
}
