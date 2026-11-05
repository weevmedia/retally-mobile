/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.auth

/**
 * One-time passcodes for email sign-in. The production implementation is the Firebase
 * Cloud Functions 2nd-gen pair (`requestOtp` / `verifyOtp`, see backend/functions): it
 * emails a 6-digit code via Resend and mints a Firebase custom token on success.
 */
interface OtpService {
    /**
     * Reports whether [email] already has an account WITHOUT generating or sending
     * a code. The app calls this on the email step so a code is only sent after a
     * new user completes the name form. Throws [OtpException] on failure.
     */
    suspend fun lookupAccount(email: String): CodeRequest

    /**
     * Sends a fresh 6-digit code to [email] and reports whether the email already has
     * an account. Enumeration-resistant: every call sends a real email and burns the
     * per-email rate-limit budget, so probing accounts at scale is impractical.
     * Throws [OtpException] on failure.
     */
    suspend fun requestCode(email: String): CodeRequest

    /**
     * Returns the Firebase custom token for [email] when [code] matches the latest
     * code sent. [displayName] ("First Last" from the sign-in form) is stored on the
     * Firebase user so the UI greets by name instead of "Guest". Throws [OtpException]
     * on mismatch, expiry, lockout, or transport failure.
     */
    suspend fun verifyCode(email: String, code: String, displayName: String): String

    /**
     * Exchanges a platform ID token (Apple identity token / Google ID token) for a Firebase
     * custom token. The account is the same per-email one the email code signs in to.
     * [nonce] is the raw nonce whose SHA-256 went into the Apple request (null for Google);
     * [displayName] is the name Apple hands the app on first sign-in only.
     * Throws [ProviderSignInException] on failure.
     */
    suspend fun signInWithProvider(
        provider: SignInProvider,
        idToken: String,
        nonce: String?,
        displayName: String?,
    ): ProviderSignIn
}

/** Result of [OtpService.signInWithProvider]. */
data class ProviderSignIn(
    val customToken: String,
    val isNewUser: Boolean,
    val firstName: String?,
)

/** Provider sign-in failures the UI tells apart. */
sealed class ProviderSignInException(message: String) : Exception(message) {
    /** Apple/Google didn't share an email (or it isn't verified), so there's no account to use. */
    data object NoEmail : ProviderSignInException("no-email")
    data object Rejected : ProviderSignInException("invalid-token")
    data object Failed : ProviderSignInException("failed")
}

/** Result of [OtpService.requestCode]: the code was sent, plus account info. */
data class CodeRequest(
    /** True when the email already has a Firebase account. */
    val exists: Boolean,
    /** First token of the account's display name, for the "Welcome back" greeting. */
    val firstName: String?,
)

/** Typed failures from [OtpService]; the store maps these to user-facing messages. */
sealed class OtpException(message: String) : Exception(message) {
    data object CodeMismatch : OtpException("mismatch")
    data object CodeExpired : OtpException("expired")
    data object TooManyAttempts : OtpException("too-many-attempts")
    data object RateLimited : OtpException("rate-limited")
    data object SendFailed : OtpException("send-failed")
}
