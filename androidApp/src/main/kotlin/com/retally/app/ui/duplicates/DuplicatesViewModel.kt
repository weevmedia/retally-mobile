package com.retally.app.ui.duplicates

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.duplicates.DuplicatesContract.Action
import com.retally.shared.presentation.duplicates.DuplicatesContract.SideEffect
import com.retally.shared.presentation.duplicates.DuplicatesContract.State
import com.retally.shared.presentation.duplicates.DuplicatesContract.Store

/** Android ViewModel for the "Duplicates found" list. Owns the shared [Store]. */
class DuplicatesViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
