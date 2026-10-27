package com.retally.app.ui.auth

import com.retally.shared.domain.auth.SignInProvider
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import android.os.Bundle
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
    private val args: AuthFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // "Continue as guest" only on the first-launch sign-in.
        dispatchViewAction(Action.GuestOptionVisible(args.firstLaunch))
    }

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
            is SideEffect.StartProviderSignIn -> {
                // Android offers Google only (Apple is iOS-only).
                if (effect.provider != SignInProvider.GOOGLE) {
                    dispatchViewAction(Action.ProviderSignInFailed)
                } else {
                    // Its own coroutine, so other effects (toasts) aren't held up by the sheet.
                    viewLifecycleOwner.lifecycleScope.launch {
                        when (val result = GoogleSignIn.signIn(requireActivity())) {
                            is GoogleSignInResult.Success -> dispatchViewAction(
                                Action.ProviderTokenReceived(
                                    provider = SignInProvider.GOOGLE,
                                    idToken = result.idToken,
                                    displayName = result.displayName,
                                ),
                            )
                            GoogleSignInResult.Cancelled -> dispatchViewAction(Action.ProviderSignInCancelled)
                            GoogleSignInResult.Failed -> dispatchViewAction(Action.ProviderSignInFailed)
                        }
                    }
                }
            }
            is NavigateBack -> {
                if (args.firstLaunch) {
                    // Back to onboarding: onboarding was popped when auth opened,
                    // so navigate back explicitly and pop auth off the stack.
                    navigate(AuthFragmentDirections.actionAuthToOnboarding())
                } else {
                    // Opened from inside the app (Home, More, Smart List…): just close sign-in.
                    findNavController().popBackStack()
                }
            }
        }
    }
}
