package com.retally.shared.presentation.auth

import com.retally.shared.core.Effect
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.StringResolver
import com.retally.shared.core.strings.getString
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.auth.OtpException
import com.retally.shared.domain.auth.OtpService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Email OTP verification: six numeric boxes, auto-advance, auto-verify once six digits
 * are entered (VERIFY button kept as a fallback; disabled while verifying),
 * "That code didn't match — try again." on mismatch, 2-minute resend cooldown.
 *
 * The host dispatches [Action.Started] with the validated email once; the store sends
 * the code on entry UNLESS the auth screen already sent it ([Action.Started.codeAlreadySent],
 * from the email-first flow where requestOtp sends the code after the email/name steps)
 * and owns the cooldown. [OtpService] is the backend seam — the Cloud Functions
 * (`lookupAccount` / `requestOtp` / `verifyOtp`) check the account, email the code and
 * mint a Firebase custom token on success, which the store exchanges for a real
 * Firebase session.
 *
 * Post-verification branch: the account-lookup result ([isNewUser]/[accountFirstName])
 * is stored but never affects the pre-verification UI. After the code is verified and
 * the custom token is signed in, existing accounts get the "Welcome back" greeting
 * (with the account's first name when on file); new accounts go straight Home — their
 * name was collected on the auth name form and traveled to verifyOtp as [displayName].
 */
interface OtpContract {

    data class State(
        val email: String = "",
        /** "First Last" from the auth name form (new users); stored on the Firebase user. */
        val displayName: String = "",
        /** From the account lookup — drives the post-verification branch only. */
        val isNewUser: Boolean = false,
        /** From the account lookup — shown only after verification. */
        val accountFirstName: String? = null,
        val code: String = "",
        /** True once the first code request has gone out; guards against double-sends. */
        val codeRequested: Boolean = false,
        val isVerifying: Boolean = false,
        /** Mismatch — boxes turn red and the error line shows; cleared on next edit. */
        val showError: Boolean = false,
        val isSending: Boolean = false,
        val resendCooldownSeconds: Int = 0,
    ) : com.retally.shared.core.State {
        val canVerify: Boolean
            get() = code.length == CODE_LENGTH && !isVerifying
        val canResend: Boolean
            get() = resendCooldownSeconds <= 0 && !isSending && codeRequested
    }

    sealed class Action : com.retally.shared.core.Action {
        /**
         * Host entry point: the validated email from the sign-in screen, plus the
         * "First Last" name for new users (goes to verifyOtp). [isNewUser]/
         * [accountFirstName] come from the account lookup and drive the
         * post-verification branch only — they must not affect the pre-verification
         * UI. [codeAlreadySent] is true when the auth screen already sent the code
         * via requestCode (email-first flow) — the store must not send it again.
         */
        data class Started(
            val email: String,
            val displayName: String,
            val isNewUser: Boolean,
            val accountFirstName: String?,
            val codeAlreadySent: Boolean = false,
        ) : Action()
        /** Raw text-field value; the store keeps digits only, max [CODE_LENGTH]. */
        data class CodeChanged(val value: String) : Action()
        data object VerifyClicked : Action()
        data object ResendClicked : Action()
        data object BackClicked : Action()
    }

    sealed class SideEffect : Effect {
        /** Code verified and signed in — enter the main shell. */
        data object NavigateToMain : SideEffect()
        /** Back to the sign-in screen (e.g. to fix the email). */
        data object NavigateBack : SideEffect()
        data class ShowMessage(val message: String) : SideEffect()
    }

    class Store(
        private val otpService: OtpService,
        private val authRepository: AuthRepository,
        private val strings: StringResolver,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {
        override fun initState(): State = State()

        private var cooldownJob: Job? = null

        override fun dispatch(action: Action) {
            when (action) {
                is Action.Started -> onStarted(action)
                is Action.CodeChanged -> {
                    val filtered = action.value.filter(Char::isDigit).take(CODE_LENGTH)
                    if (filtered != currentState.code) {
                        currentState = currentState.copy(code = filtered, showError = false)
                        // Auto-verify the moment the sixth digit lands; onVerify()
                        // funnels through canVerify so a second fire is impossible.
                        if (filtered.length == CODE_LENGTH) onVerify()
                    }
                }
                Action.VerifyClicked -> onVerify()
                Action.ResendClicked -> onResend()
                Action.BackClicked -> sideEffect(SideEffect.NavigateBack)
            }
        }

        private fun onStarted(action: Action.Started) {
            val normalized = action.email.trim()
            if (normalized.isBlank()) return
            val isRepeat = currentState.codeRequested && currentState.email == normalized
            if (isRepeat) return
            currentState = currentState.copy(
                email = normalized,
                displayName = action.displayName.trim(),
                isNewUser = action.isNewUser,
                accountFirstName = action.accountFirstName?.takeIf { it.isNotBlank() },
                codeRequested = true,
            )
            if (action.codeAlreadySent) {
                // The auth screen just sent a code for this email — start the cooldown
                // so RESEND can't fire instantly, and tick it down from here.
                startCooldown()
            } else {
                requestCode()
            }
        }

        private fun requestCode() {
            val email = currentState.email
            if (email.isBlank() || currentState.isSending) return
            currentState = currentState.copy(isSending = true)
            launch {
                runCatching { otpService.requestCode(email) }
                    .onSuccess {
                        currentState = currentState.copy(isSending = false)
                        startCooldown()
                    }
                    .onFailure { e ->
                        currentState = currentState.copy(isSending = false)
                        val message = if (e is OtpException.RateLimited) {
                            strings.getString(S.OTP_ERROR_RATE_LIMITED)
                        } else {
                            strings.getString(S.OTP_ERROR_SEND)
                        }
                        sideEffect(SideEffect.ShowMessage(message))
                    }
            }
        }

        private fun onVerify() {
            val state = currentState
            if (!state.canVerify) return
            currentState = state.copy(isVerifying = true, showError = false)
            launch {
                // TEMP-AUTH-DEBUG: remove before release.
                println("[auth-debug] otpVerify email=${state.email} isNewUser=${state.isNewUser}")
                val token = try {
                    otpService.verifyCode(state.email, state.code, state.displayName)
                } catch (e: OtpException) {
                    handleOtpFailure(e)
                    return@launch
                } catch (e: Exception) {
                    currentState = currentState.copy(isVerifying = false)
                    sideEffect(SideEffect.ShowMessage(strings.getString(S.OTP_ERROR_VERIFY)))
                    return@launch
                }
                // The custom token's uid is stable per email — sign in for real.
                runCatching { authRepository.signInWithCustomToken(token) }
                    .onSuccess {
                        // Email ownership proven: now it's safe to branch. Existing
                        // accounts get the "Welcome back" greeting; new accounts go
                        // straight Home (their name is already on the Firebase user).
                        if (!currentState.isNewUser) {
                            val welcomeBack = currentState.accountFirstName
                                ?.let { strings.getString(S.AUTH_WELCOME_BACK_NAME, it) }
                                ?: strings.getString(S.AUTH_WELCOME_BACK)
                            sideEffect(SideEffect.ShowMessage(welcomeBack))
                        }
                        sideEffect(SideEffect.NavigateToMain)
                    }
                    .onFailure {
                        currentState = currentState.copy(isVerifying = false)
                        sideEffect(SideEffect.ShowMessage(strings.getString(S.OTP_ERROR_VERIFY)))
                    }
            }
        }

        /** Maps a typed OTP failure to the matching UI state; mismatch keeps the red boxes. */
        private fun handleOtpFailure(e: OtpException) {
            when (e) {
                OtpException.CodeMismatch -> {
                    currentState = currentState.copy(isVerifying = false, showError = true, code = "")
                }
                OtpException.CodeExpired -> {
                    currentState = currentState.copy(isVerifying = false, code = "")
                    sideEffect(SideEffect.ShowMessage(strings.getString(S.OTP_ERROR_EXPIRED)))
                }
                OtpException.TooManyAttempts, OtpException.RateLimited -> {
                    currentState = currentState.copy(isVerifying = false)
                    sideEffect(SideEffect.ShowMessage(strings.getString(S.OTP_ERROR_RATE_LIMITED)))
                }
                OtpException.SendFailed -> {
                    currentState = currentState.copy(isVerifying = false)
                    sideEffect(SideEffect.ShowMessage(strings.getString(S.OTP_ERROR_VERIFY)))
                }
            }
        }

        private fun onResend() {
            if (!currentState.canResend) return
            currentState = currentState.copy(code = "", showError = false)
            requestCode()
        }

        private fun startCooldown() {
            cooldownJob?.cancel()
            currentState = currentState.copy(resendCooldownSeconds = RESEND_COOLDOWN_SECONDS)
            cooldownJob = launch {
                while (currentState.resendCooldownSeconds > 0) {
                    delay(1_000)
                    val remaining = (currentState.resendCooldownSeconds - 1).coerceAtLeast(0)
                    currentState = currentState.copy(resendCooldownSeconds = remaining)
                }
            }
        }
    }

    companion object {
        const val CODE_LENGTH = 6
        const val RESEND_COOLDOWN_SECONDS = 120
    }
}
