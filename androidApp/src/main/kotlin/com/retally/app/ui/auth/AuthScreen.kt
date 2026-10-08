package com.retally.app.ui.auth

import androidx.compose.runtime.Composable
import com.retally.shared.presentation.auth.AuthContract
import com.retally.shared.ui.auth.AuthScreen as SharedAuthScreen

/**
 * Android host for the shared sign-in screen. [com.retally.app.ui.base.BaseComposeFragment]
 * already applies [com.retally.shared.ui.theme.ReTallyTheme], so this stays a thin pass-through.
 */
@Composable
fun AuthScreen(
    state: AuthContract.State,
    dispatch: (AuthContract.Action) -> Unit,
) {
    SharedAuthScreen(state = state, dispatch = dispatch)
}
