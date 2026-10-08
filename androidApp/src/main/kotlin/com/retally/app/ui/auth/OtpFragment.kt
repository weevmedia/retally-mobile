package com.retally.app.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.auth.OtpContract.Action
import com.retally.shared.presentation.auth.OtpContract.SideEffect
import com.retally.shared.presentation.auth.OtpContract.SideEffect.NavigateBack
import com.retally.shared.presentation.auth.OtpContract.SideEffect.NavigateToMain
import com.retally.shared.presentation.auth.OtpContract.SideEffect.ShowMessage
import com.retally.shared.presentation.auth.OtpContract.State
import org.koin.androidx.viewmodel.ext.android.viewModel

/** First launch only: 6-digit email code entry between sign-in and Home. The tab bar stays hidden. */
class OtpFragment : BaseComposeFragment<State, Action, SideEffect, OtpViewModel>() {

    override val viewModel: OtpViewModel by viewModel()

    private val args: OtpFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The store survives rotation; Started is idempotent for the same email.
        dispatchViewAction(Action.Started(args.email, args.displayName, args.isNewUser, args.accountFirstName, args.codeAlreadySent))
    }

    @Composable
    override fun ScreenContent(state: State) {
        OtpScreen(state = state, dispatch = ::dispatchViewAction)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            is NavigateToMain -> {
                // Safe Args; pops sign-in + OTP so Back from Home exits the app.
                navigate(OtpFragmentDirections.actionOtpToHome())
            }
            is NavigateBack -> {
                // Back to sign-in to fix the email; auth is still on the stack.
                findNavController().popBackStack()
            }
            is ShowMessage -> {
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
