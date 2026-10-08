package com.retally.app.ui.auth

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.auth.AuthContract.Action
import com.retally.shared.presentation.auth.AuthContract.SideEffect
import com.retally.shared.presentation.auth.AuthContract.State
import com.retally.shared.presentation.auth.AuthContract.Store

/** Android ViewModel for the sign-in screen. Owns the shared [Store]. */
class AuthViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
