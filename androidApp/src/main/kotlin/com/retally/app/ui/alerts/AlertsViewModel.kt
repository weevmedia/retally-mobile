package com.retally.app.ui.alerts

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.recalls.RecallsContract.Action
import com.retally.shared.presentation.recalls.RecallsContract.SideEffect
import com.retally.shared.presentation.recalls.RecallsContract.State
import com.retally.shared.presentation.recalls.RecallsContract.Store

/** Android ViewModel for the food recall alerts screen. Owns the shared [Store]. */
class AlertsViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
