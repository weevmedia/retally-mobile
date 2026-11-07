/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.security

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.retally.app.BuildConfig
import com.retally.shared.domain.security.AppCheckTokenProvider
import kotlinx.coroutines.tasks.await

/**
 * Firebase App Check on Android. [install] picks the provider for this build — Play Integrity
 * in release, Google's debug provider in debug builds (its token is printed to Logcat once and
 * must be registered in the Firebase console → App Check → the Android app → "Manage debug
 * tokens"; see `AppCheckProvider` in the debug/release source sets).
 *
 * Once installed, the Firestore and Storage SDKs attach the token on their own; the shared
 * Ktor client gets it through [AndroidAppCheckTokenProvider].
 */
object AndroidAppCheck {
    private const val TAG = "ReTallyAppCheck"

    fun install() {
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(AppCheckProvider.factory())
        if (BuildConfig.DEBUG) {
            // Debug builds: say which provider is active and whether a token came back. A
            // failure here right after a fresh install means the debug secret (logged just above
            // by Firebase: "Enter this debug secret into the allow list…") isn't registered yet
            // on the "ReTally Android (debug)" app in the console.
            Log.i(TAG, "installed provider: ${AppCheckProvider.factory()::class.simpleName}")
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener { Log.i(TAG, "token OK (expires ${it.expireTimeMillis})") }
                .addOnFailureListener { Log.w(TAG, "token FAILED: ${it.message}") }
        }
    }
}

/** Hands the current App Check token to the shared HTTP client; null when none can be minted. */
class AndroidAppCheckTokenProvider : AppCheckTokenProvider {
    override suspend fun token(): String? = try {
        FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
    } catch (e: Exception) {
        null
    }
}
