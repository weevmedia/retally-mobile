package com.retally.app.ui.budget

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.budget.BudgetContract.Action
import com.retally.shared.presentation.budget.BudgetContract.SideEffect
import com.retally.shared.presentation.budget.BudgetContract.SideEffect.Closed
import com.retally.shared.presentation.budget.BudgetContract.SideEffect.ShowMessage
import com.retally.shared.presentation.budget.BudgetContract.State
import com.retally.shared.ui.budget.BudgetScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Monthly grocery budget, reached from the Home Spent tile. */
class BudgetFragment : BaseComposeFragment<State, Action, SideEffect, BudgetViewModel>() {

    override val viewModel: BudgetViewModel by viewModel()

    @Composable
    override fun ScreenContent(state: State) {
        BudgetScreen(state = state, dispatch = ::dispatchViewAction, snackbarHostState = snackbarHostState)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            Closed -> findNavController().popBackStack()
            is ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }
}
