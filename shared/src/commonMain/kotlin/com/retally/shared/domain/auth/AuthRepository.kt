package com.retally.shared.domain.auth

import kotlinx.coroutines.flow.Flow

/**
 * Identity boundary. Receipts in the cloud live under `users/{uid}`; this is where the uid
 * comes from. Guests are real (anonymous) Firebase users, so the same uid keeps working when
 * they later link Google / Apple / email — nothing is migrated.
 */
interface AuthRepository {
    /** Signed-in uid, or null before the first [ensureSignedIn]. */
    val currentUid: String?

    /** Emits on every auth change (sign-in, link, sign-out). */
    fun observeUid(): Flow<String?>

    /** True once the user has linked a non-anonymous credential. */
    val isAnonymous: Boolean

    /** Who is signed in, for greetings and the guest banner; re-emits on every auth change. */
    fun observeAccount(): Flow<Account>

    /**
     * Returns the current uid, signing in anonymously first if needed. Idempotent; safe to
     * call before every cloud write.
     */
    suspend fun ensureSignedIn(): String

    /**
     * Signs in with a Firebase custom token (minted by OTP verification). Returns the uid.
     * The token's uid is stable per email, so the same user keeps their receipts.
     */
    suspend fun signInWithCustomToken(token: String): String

    /**
     * Ends the session. Local receipts stay on this device; the next [ensureSignedIn] call
     * starts a fresh anonymous guest. Cloud data under the old uid is left untouched.
     */
    suspend fun signOut()

    /**
     * Deletes the Firebase user. Returns false when the backend refuses (e.g. the login
     * is too old and Firebase demands a re-authentication first). Callers still wipe
     * local data themselves.
     */
    suspend fun deleteAccount(): Boolean

    /**
     * Firebase ID token for authenticated backend calls. Null when not signed in.
     * Used for the Authorization header on Cloud Function calls. [forceRefresh] mints a new
     * token instead of the cached one (use after the server rejects a token).
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
}

/** Minimal identity for the UI. Guests (anonymous Firebase users) have no name. */
data class Account(
    val uid: String?,
    val isGuest: Boolean,
    /** First name / display name once a credential is linked; null for guests. */
    val displayName: String? = null,
    /** Email once a credential is linked; null for guests. */
    val email: String? = null,
) {
    /** First word of the display name, or null for guests (the UI substitutes its localized "Guest"). */
    val firstName: String? get() = displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }

    companion object {
        val Guest = Account(uid = null, isGuest = true)
    }
}
