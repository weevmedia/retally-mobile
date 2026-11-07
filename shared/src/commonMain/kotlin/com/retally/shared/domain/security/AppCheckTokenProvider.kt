/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.security

/**
 * Supplies the Firebase App Check token that proves a request comes from the genuine ReTally
 * app (Play Integrity on Android, App Attest on iOS). Every backend call — Cloud Functions,
 * and the Firestore / Storage SDKs on their own — carries it; the backend rejects calls
 * without one, which is what keeps scripts, bots and repackaged builds out.
 *
 * The platform owns the SDK; the shared code only asks for the current token. The SDK
 * caches it and refreshes before expiry, so [token] is cheap after the first call.
 */
fun interface AppCheckTokenProvider {
    /** The current token, or null when App Check isn't available (the backend will refuse). */
    suspend fun token(): String?

    companion object {
        /** No App Check (tests, previews). */
        val None = AppCheckTokenProvider { null }
    }
}

/** Header the Firebase backends read the token from. */
const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
