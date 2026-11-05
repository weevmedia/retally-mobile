/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** What the Google sheet returned. */
sealed interface GoogleSignInResult {
    data class Success(val idToken: String, val displayName: String?) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object Failed : GoogleSignInResult
}

/**
 * Sign in with Google via Credential Manager (the "Sign in with Google" sheet). Returns a
 * Google ID token for the backend's `signInWithProvider`, which turns it into the shopper's
 * per-email ReTally account.
 *
 * The token's audience is the OAuth *Web* client ID. The Google Services plugin generates it as
 * `R.string.default_web_client_id` once Google sign-in is enabled in Firebase and the updated
 * google-services.json is in the app; it's looked up by name so the app still builds before then.
 */
object GoogleSignIn {
    private const val TAG = "ReTallyGoogleSignIn"

    suspend fun signIn(activity: Activity): GoogleSignInResult {
        val resId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (resId == 0) {
            Log.w(TAG, "default_web_client_id missing: enable Google sign-in in Firebase and update google-services.json")
            return GoogleSignInResult.Failed
        }
        val option = GetSignInWithGoogleOption.Builder(activity.getString(resId)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Success(google.idToken, google.displayName)
            } else {
                Log.w(TAG, "Unexpected credential type: ${credential.type}")
                GoogleSignInResult.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in failed: ${e.type}", e)
            GoogleSignInResult.Failed
        } catch (e: Exception) {
            Log.w(TAG, "Google sign-in failed", e)
            GoogleSignInResult.Failed
        }
    }
}
