package com.retally.app.ui.receipts

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.receipts.ReceiptsContract.Action
import com.retally.shared.presentation.receipts.ReceiptsContract.SideEffect
import com.retally.shared.presentation.receipts.ReceiptsContract.State
import com.retally.shared.presentation.receipts.ReceiptsContract.Store

/** Android ViewModel for the Receipts tab. Owns the shared [Store]. */
class ReceiptsViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
