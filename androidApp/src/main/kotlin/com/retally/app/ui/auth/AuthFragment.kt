package com.retally.app.ui.auth

import android.widget.Toast
import androidx.compose.runtime.Composable
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.auth.AuthContract.Action
import com.retally.shared.presentation.auth.AuthContract.SideEffect
import com.retally.shared.presentation.auth.AuthContract.SideEffect.NavigateBack
import com.retally.shared.presentation.auth.AuthContract.SideEffect.NavigateToMain
import com.retally.shared.presentation.auth.AuthContract.SideEffect.NavigateToOtp
import com.retally.shared.presentation.auth.AuthContract.SideEffect.ShowMessage
import com.retally.shared.presentation.auth.AuthContract.State
import org.koin.androidx.viewmodel.ext.android.viewModel

/** First launch only: sign-in between onboarding and Home. The tab bar stays hidden. */
class AuthFragment : BaseComposeFragment<State, Action, SideEffect, AuthViewModel>() {

    override val viewModel: AuthViewModel by viewModel()

    @Composable
    override fun ScreenContent(state: State) {
        AuthScreen(state = state, dispatch = ::dispatchViewAction)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            is NavigateToMain -> {
                // Safe Args; the graph pops auth so Back from Home exits the app.
                navigate(AuthFragmentDirections.actionAuthToHome())
            }
            is NavigateToOtp -> {
                // Safe Args; auth stays on the stack so Back returns to fix the email.
                navigate(AuthFragmentDirections.actionAuthToOtp(effect.email, effect.displayName, effect.isNewUser, effect.firstName, effect.codeAlreadySent))
            }
            is ShowMessage -> {
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
            }
            is NavigateBack -> {
                // Back to onboarding: onboarding was popped when auth opened,
                // so navigate back explicitly and pop auth off the stack.
                navigate(AuthFragmentDirections.actionAuthToOnboarding())
            }
        }
    }
}
