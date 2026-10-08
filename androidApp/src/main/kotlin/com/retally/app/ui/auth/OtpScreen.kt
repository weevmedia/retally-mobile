package com.retally.app.ui.auth

import androidx.compose.runtime.Composable
import com.retally.shared.presentation.auth.OtpContract
import com.retally.shared.ui.auth.OtpScreen as SharedOtpScreen

/**
 * Android host for the shared OTP screen. [com.retally.app.ui.base.BaseComposeFragment]
 * already applies [com.retally.shared.ui.theme.ReTallyTheme], so this stays a thin pass-through.
 */
@Composable
fun OtpScreen(
    state: OtpContract.State,
    dispatch: (OtpContract.Action) -> Unit,
) {
    SharedOtpScreen(state = state, dispatch = dispatch)
}
