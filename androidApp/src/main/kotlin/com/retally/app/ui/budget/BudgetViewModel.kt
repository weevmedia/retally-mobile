package com.retally.app.ui.budget

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.budget.BudgetContract.Action
import com.retally.shared.presentation.budget.BudgetContract.SideEffect
import com.retally.shared.presentation.budget.BudgetContract.State
import com.retally.shared.presentation.budget.BudgetContract.Store

/** Android ViewModel for the monthly grocery budget. Owns the shared [Store]. */
class BudgetViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
