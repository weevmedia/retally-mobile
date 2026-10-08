package com.retally.app.ui.auth

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.auth.OtpContract.Action
import com.retally.shared.presentation.auth.OtpContract.SideEffect
import com.retally.shared.presentation.auth.OtpContract.State
import com.retally.shared.presentation.auth.OtpContract.Store

/** Android ViewModel for the OTP screen. Owns the shared [Store]. */
class OtpViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
