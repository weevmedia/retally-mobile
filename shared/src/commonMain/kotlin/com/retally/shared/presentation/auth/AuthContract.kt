package com.retally.shared.presentation.auth

import com.retally.shared.core.Effect
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.auth.OtpService
import kotlinx.coroutines.launch

/**
 * Email-first sign-in ("Welcome to ReTally"):
 *
 *  1. [AuthStep.Email] — email-only prompt (+ Apple/Google/guest/legal). CONTINUE does
 *     a lightweight [OtpService.lookupAccount] (NO code sent) and branches:
 *     existing account -> code is sent, straight to the OTP screen;
 *     new account -> [AuthStep.NameForm].
 *  2. [AuthStep.NameForm] (new users only) — first/last name + the email greyed out.
 *     CONTINUE sends the code via [OtpService.requestCode] and goes to the OTP screen.
 *
 * No code is generated until the user commits: existing users get it right after the
 * email step, new users only after completing the name form (abandoned signups never
 * burn a Resend send). After the code is verified, the OTP flow branches: existing
 * users see "Welcome back", new users go straight Home (their name traveled with
 * verifyOtp and is on the Firebase user).
 *
 * Auth model is anonymous-first (see [AuthRepository]): "Continue as Guest" signs in
 * anonymously today and works end-to-end. Apple/Google remain UI-complete stubs.
 */
interface AuthContract {

    /** Which form the sign-in screen shows. */
    enum class AuthStep { Email, NameForm }

    data class State(
        val step: AuthStep = AuthStep.Email,
        val email: String = "",
        val firstName: String = "",
        val lastName: String = "",
        /** Shown after a failed Continue attempt; cleared as soon as the email edits. */
        val showEmailError: Boolean = false,
        /** Shown after a failed name-form Continue; cleared as soon as a name edits. */
        val showNameError: Boolean = false,
        /** Lookup or code request failed (transport); inline, cleared on edit. */
        val showLookupError: Boolean = false,
        val isSubmitting: Boolean = false,
        /** lookupAccount in flight on the email step. */
        val isCheckingAccount: Boolean = false,
        /** requestCode in flight (sending the code). */
        val isSendingCode: Boolean = false,
    ) : com.retally.shared.core.State {
        val isEmailValid: Boolean
            get() = EMAIL_PATTERN.matches(email.trim())
        val isNameValid: Boolean
            get() = firstName.isNotBlank() && lastName.isNotBlank()
    }

    sealed class Action : com.retally.shared.core.Action {
        data class EmailChanged(val value: String) : Action()
        data class FirstNameChanged(val value: String) : Action()
        data class LastNameChanged(val value: String) : Action()
        /** Email step CONTINUE: lookup only, no code sent yet. */
        data object EmailContinueClicked : Action()
        /** Name form CONTINUE (new users): validate, send the code, go to OTP. */
        data object NameContinueClicked : Action()
        data object AppleClicked : Action()
        data object GoogleClicked : Action()
        data object GuestClicked : Action()
        /** Terms / Privacy links — TODO(auth): open the real hosted documents. */
        data object LegalClicked : Action()
        /**
         * Back chevron: on the name form returns to the email step;
         * on the email step returns to the onboarding screen.
         */
        data object BackClicked : Action()
    }

    sealed class SideEffect : Effect {
        /** Auth resolved (guest or linked) — enter the main shell. */
        data object NavigateToMain : SideEffect()
        /**
         * Go to the OTP screen. The code was already sent by requestCode, so
         * [codeAlreadySent] is always true here and the OTP screen must not send it
         * again. [displayName] ("First Last") travels to verifyOtp for new users so
         * the Firebase user is created with a name; [isNewUser]/[firstName] drive
         * the post-verification branch ("Welcome back" vs straight Home).
         */
        data class NavigateToOtp(
            val email: String,
            val displayName: String,
            val isNewUser: Boolean,
            val firstName: String?,
            val codeAlreadySent: Boolean,
        ) : SideEffect()
        /** Back to the onboarding screen. */
        data object NavigateBack : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
    }

    class Store(
        private val authRepository: AuthRepository,
        private val otpService: OtpService,
        private val strings: StringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {
        override fun initState(): State = State()

        override fun dispatch(action: Action) {
            when (action) {
                is Action.EmailChanged ->
                    currentState = currentState.copy(
                        email = action.value,
                        showEmailError = false,
                        showLookupError = false,
                    )
                is Action.FirstNameChanged ->
                    currentState = currentState.copy(
                        firstName = action.value,
                        showNameError = false,
                        showLookupError = false,
                    )
                is Action.LastNameChanged ->
                    currentState = currentState.copy(
                        lastName = action.value,
                        showNameError = false,
                        showLookupError = false,
                    )
                Action.EmailContinueClicked -> onEmailContinue()
                Action.NameContinueClicked -> onNameContinue()
                Action.AppleClicked,
                Action.GoogleClicked,
                Action.LegalClicked ->
                    // TODO(auth): platform OAuth + Firebase linkAccount; hosted legal docs.
                    // Not wired yet.
                    sideEffect(SideEffect.ShowMessage(strings.getString(S.GENERAL_COMING_SOON)))
                Action.GuestClicked -> onGuest()
                Action.BackClicked -> onBack()
            }
        }

        /**
         * Email step: plausibility-check the email, then LOOKUP the account (no code
         * sent). Existing -> send the code and go to OTP. New -> name form; the code
         * is only sent after the name form is completed.
         */
        private fun onEmailContinue() {
            val state = currentState
            if (state.isCheckingAccount || state.isSendingCode) return
            if (!state.isEmailValid) {
                currentState = state.copy(showEmailError = true)
                return
            }
            val email = state.email.trim()
            currentState = state.copy(isCheckingAccount = true, showLookupError = false)
            launch {
                val lookup = runCatching { otpService.lookupAccount(email) }
                    .onFailure {
                        currentState = currentState.copy(
                            isCheckingAccount = false,
                            showLookupError = true,
                        )
                    }
                    .getOrNull() ?: return@launch
                // TEMP-AUTH-DEBUG: remove before release.
                println("[auth-debug] lookupAccount email=$email exists=${lookup.exists}")
                if (lookup.exists) {
                    // Existing account: send the code now, then OTP.
                    currentState = currentState.copy(isCheckingAccount = false, isSendingCode = true)
                    runCatching { otpService.requestCode(email) }
                        .onSuccess {
                            currentState = currentState.copy(isSendingCode = false)
                            sideEffect(
                                SideEffect.NavigateToOtp(
                                    email = email,
                                    displayName = "",
                                    isNewUser = false,
                                    firstName = lookup.firstName,
                                    codeAlreadySent = true,
                                )
                            )
                        }
                        .onFailure {
                            currentState = currentState.copy(
                                isSendingCode = false,
                                showLookupError = true,
                            )
                        }
                } else {
                    // New account: name form first; the code goes out on its Continue.
                    currentState = currentState.copy(
                        isCheckingAccount = false,
                        step = AuthStep.NameForm,
                    )
                }
            }
        }

        /**
         * Name form (new users): validate, SEND the code, then go to the OTP screen.
         * The entered name travels as displayName so verifyOtp creates the Firebase
         * user with it.
         */
        private fun onNameContinue() {
            val state = currentState
            if (state.isSendingCode) return
            if (!state.isNameValid) {
                currentState = state.copy(showNameError = true)
                return
            }
            val email = state.email.trim()
            val displayName = "${state.firstName.trim()} ${state.lastName.trim()}".trim()
            currentState = state.copy(isSendingCode = true, showLookupError = false)
            launch {
                // TEMP-AUTH-DEBUG: remove before release.
                println("[auth-debug] requestCode (new user) email=$email")
                runCatching { otpService.requestCode(email) }
                    .onSuccess {
                        currentState = currentState.copy(isSendingCode = false)
                        sideEffect(
                            SideEffect.NavigateToOtp(
                                email = email,
                                displayName = displayName,
                                isNewUser = true,
                                firstName = state.firstName.trim().takeIf { it.isNotBlank() },
                                codeAlreadySent = true,
                            )
                        )
                    }
                    .onFailure {
                        currentState = currentState.copy(
                            isSendingCode = false,
                            showLookupError = true,
                        )
                    }
            }
        }

        private fun onBack() {
            if (currentState.step == AuthStep.NameForm) {
                // Back on the name form: return to the email step to fix the email.
                currentState = currentState.copy(step = AuthStep.Email)
            } else {
                sideEffect(SideEffect.NavigateBack)
            }
        }

        private fun onGuest() {
            if (currentState.isSubmitting) return
            currentState = currentState.copy(isSubmitting = true)
            launch {
                runCatching { authRepository.ensureSignedIn() }
                    .onSuccess { sideEffect(SideEffect.NavigateToMain) }
                    .onFailure {
                        currentState = currentState.copy(isSubmitting = false)
                        sideEffect(SideEffect.ShowMessage(strings.getString(S.AUTH_ERROR_GUEST)))
                    }
            }
        }
    }

    companion object {
        /** Plausibility check only — the backend owns real verification. */
        val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
