/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.data.auth

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import com.retally.shared.domain.auth.Account
import com.retally.shared.domain.auth.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Firebase Authentication via the GitLive multiplatform SDK. Anonymous-first: the first call
 * to [ensureSignedIn] creates the guest account; the SDK persists the credential on device so
 * later launches resume the same uid without a network round-trip.
 */
class FirebaseAuthRepository(
    /** Calls the `deleteAccount` function; the App Check header is added by the shared client. */
    private val http: HttpClient,
    private val auth: FirebaseAuth = Firebase.auth,
    private val deleteAccountUrl: String = DELETE_ACCOUNT_URL,
) : AuthRepository {

    private val signInMutex = Mutex()

    override val currentUid: String? get() = auth.currentUser?.uid

    override val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true

    override fun observeUid(): Flow<String?> = auth.authStateChanged.map { it?.uid }

    /** Bumped after a profile edit: Firebase's auth-state stream doesn't fire for those. */
    private val profileChanges = MutableStateFlow(0)

    override fun observeAccount(): Flow<Account> =
        combine(auth.authStateChanged, profileChanges) { user, _ ->
            val current = auth.currentUser?.takeIf { it.uid == user?.uid } ?: user
            // A user restored from disk (offline launch) reports empty strings for the profile
            // fields the SDK normally returns as null; the UI keys "guest" copy off null.
            Account(
                uid = current?.uid,
                isGuest = current?.isAnonymous ?: true,
                displayName = current?.displayName?.takeIf { it.isNotBlank() },
                email = current?.email?.takeIf { it.isNotBlank() },
            )
        }

    override suspend fun updateDisplayName(displayName: String) {
        val user = auth.currentUser ?: error("Not signed in")
        user.updateProfile(displayName = displayName)
        runCatching { user.reload() }
        profileChanges.value++
    }

    override suspend fun ensureSignedIn(): String {
        auth.currentUser?.uid?.let { return it }
        return signInMutex.withLock {
            auth.currentUser?.uid ?: auth.signInAnonymously().user?.uid
                ?: error("Anonymous sign-in returned no user")
        }
    }

    override suspend fun signInWithCustomToken(token: String): String {
        return signInMutex.withLock {
            auth.signInWithCustomToken(token).user?.uid
                ?: error("Custom-token sign-in returned no user")
        }
    }

    override suspend fun signOut() {
        signInMutex.withLock { auth.signOut() }
    }

    /**
     * Deletes the account on the server (`deleteAccount` callable), which removes the Firebase
     * user and, through `onUserDeleted`, all of its data. The client SDK's own `delete()` is not
     * used: it refuses unless the user signed in recently. On success the local session is
     * signed out, as the user no longer exists.
     */
    override suspend fun deleteAccount(): Boolean {
        val token = try {
            auth.currentUser?.getIdToken(false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return false
        return try {
            val response = http.post(deleteAccountUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                // Callable protocol: the payload goes under "data" (empty here).
                setBody(buildJsonObject { put("data", buildJsonObject { }) })
            }
            if (!response.status.isSuccess()) return false
            signInMutex.withLock { auth.signOut() }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? = try {
        auth.currentUser?.getIdToken(forceRefresh)
    } catch (e: Exception) {
        null
    }

    companion object {
        const val DELETE_ACCOUNT_URL = "https://us-east1-retally-c5212.cloudfunctions.net/deleteAccount"
    }
}
