package com.retally.app.ui.shopping

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.shopping.ShoppingListContract.Action
import com.retally.shared.presentation.shopping.ShoppingListContract.SideEffect
import com.retally.shared.presentation.shopping.ShoppingListContract.State
import com.retally.shared.presentation.shopping.ShoppingListContract.Store

/** Android ViewModel for the AI Shopping List tab. Owns the shared [Store]. */
class ShoppingListViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
