/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.auth

import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.domain.auth.platformSignInProviders
import com.retally.shared.domain.auth.ProviderSignInException
import com.retally.shared.domain.auth.SignInProvider
import com.retally.shared.core.Effect
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.auth.OtpService
import kotlinx.coroutines.launch
import com.retally.shared.domain.config.AppConfigRepository
import com.retally.shared.domain.config.AppLinks
import com.retally.shared.ui.web.inAppPageUrl

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
        /**
         * "Continue as guest" — only on the first-launch sign-in. Opened later from the app
         * (Home, More, Smart List, recalls) the user is already a guest, so it's hidden.
         */
        val showGuestOption: Boolean = true,
        /** The Apple / Google sign-in running (platform sheet open, then our backend call). */
        val providerInProgress: SignInProvider? = null,
        /** Social sign-ins this device offers: Apple + Google on iOS, Google on Android. */
        val providers: List<SignInProvider> = platformSignInProviders,
    ) : com.retally.shared.core.State {
        val isProviderSigningIn: Boolean get() = providerInProgress != null
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
        /** Footer "Terms & Conditions" link. */
        data object TermsClicked : Action()
        /** Footer "Privacy Policy" link. */
        data object PrivacyClicked : Action()
        /**
         * Back chevron: on the name form returns to the email step;
         * on the email step returns to the onboarding screen.
         */
        data object BackClicked : Action()
        /** Set by the host when the screen opens: true on first launch, false when opened in-app. */
        data class GuestOptionVisible(val visible: Boolean) : Action()
        /**
         * The host's native sign-in finished: [idToken] from Apple / Google, [nonce] the raw
         * nonce used for Apple (null for Google), [displayName] Apple's name on first sign-in.
         */
        data class ProviderTokenReceived(
            val provider: SignInProvider,
            val idToken: String,
            val nonce: String? = null,
            val displayName: String? = null,
        ) : Action()
        /** The user closed the Apple / Google sheet. */
        data object ProviderSignInCancelled : Action()
        /** The native sign-in failed before we got a token (no account, no network…). */
        data object ProviderSignInFailed : Action()
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
        /** Host: show Terms or Privacy in the in-app web page screen. */
        data class OpenWebPage(val title: String, val url: String) : SideEffect()
        /** Host: run the platform's sign-in sheet for [provider], then report back with a token. */
        data class StartProviderSignIn(val provider: SignInProvider) : SideEffect()
    }

    class Store(
        private val authRepository: AuthRepository,
        private val otpService: OtpService,
        private val strings: StringResolver,
        private val preferences: AppPreferences? = null,
        /** Terms / Privacy links from the admin console; null = built-in defaults. */
        private val appConfig: AppConfigRepository? = null,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {
        override fun initState(): State = State()

        private val links: AppLinks get() = appConfig?.config?.value?.links ?: AppLinks()

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
                Action.AppleClicked -> startProvider(SignInProvider.APPLE)
                Action.GoogleClicked -> startProvider(SignInProvider.GOOGLE)
                // Our hosted pages, opened in the app (no browser, no address bar).
                Action.TermsClicked -> sideEffect(
                    SideEffect.OpenWebPage(strings.getString(S.SETTINGS_TERMS), inAppPageUrl(links.terms)),
                )
                Action.PrivacyClicked -> sideEffect(
                    SideEffect.OpenWebPage(strings.getString(S.SETTINGS_PRIVACY), inAppPageUrl(links.privacyPolicy)),
                )
                is Action.ProviderTokenReceived -> onProviderToken(action)
                Action.ProviderSignInCancelled -> currentState = currentState.copy(providerInProgress = null)
                Action.ProviderSignInFailed -> {
                    currentState = currentState.copy(providerInProgress = null)
                    sideEffect(SideEffect.ShowMessage(strings.getString(S.AUTH_PROVIDER_FAILED)))
                }
                Action.GuestClicked -> onGuest()
                Action.BackClicked -> onBack()
                is Action.GuestOptionVisible -> currentState = currentState.copy(showGuestOption = action.visible)
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

        private fun startProvider(provider: SignInProvider) {
            val state = currentState
            if (state.isProviderSigningIn || state.isSubmitting || state.isCheckingAccount || state.isSendingCode) return
            // Each platform offers only its own providers.
            if (provider !in state.providers) return
            currentState = state.copy(providerInProgress = provider)
            sideEffect(SideEffect.StartProviderSignIn(provider))
        }

        /**
         * Token from Apple / Google → our backend (same per-email account as the email code)
         * → Firebase custom-token sign-in → Home. Returning users get "Welcome back".
         */
        private fun onProviderToken(action: Action.ProviderTokenReceived) {
            currentState = currentState.copy(providerInProgress = action.provider)
            launch {
                val result = try {
                    otpService.signInWithProvider(action.provider, action.idToken, action.nonce, action.displayName)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    currentState = currentState.copy(providerInProgress = null)
                    val message = if (e is ProviderSignInException.NoEmail) S.AUTH_PROVIDER_NO_EMAIL else S.AUTH_PROVIDER_FAILED
                    sideEffect(SideEffect.ShowMessage(strings.getString(message)))
                    return@launch
                }
                runCatching { authRepository.signInWithCustomToken(result.customToken) }
                    .onSuccess {
                        currentState = currentState.copy(providerInProgress = null)
                        if (!result.isNewUser) {
                            val welcomeBack = result.firstName?.let { strings.getString(S.AUTH_WELCOME_BACK_NAME, it) }
                                ?: strings.getString(S.AUTH_WELCOME_BACK)
                            sideEffect(SideEffect.ShowMessage(welcomeBack))
                        }
                        preferences?.hasLaunchedBefore = true
                        sideEffect(SideEffect.NavigateToMain)
                    }
                    .onFailure {
                        currentState = currentState.copy(providerInProgress = null)
                        sideEffect(SideEffect.ShowMessage(strings.getString(S.AUTH_PROVIDER_FAILED)))
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
                    .onSuccess {
                        preferences?.hasLaunchedBefore = true
                        sideEffect(SideEffect.NavigateToMain)
                    }
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
