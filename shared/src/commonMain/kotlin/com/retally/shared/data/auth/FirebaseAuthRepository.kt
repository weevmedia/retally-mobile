package com.retally.shared.data.auth

import com.retally.shared.domain.auth.Account
import com.retally.shared.domain.auth.AuthRepository
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
    private val auth: FirebaseAuth = Firebase.auth,
) : AuthRepository {

    private val signInMutex = Mutex()

    override val currentUid: String? get() = auth.currentUser?.uid

    override val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true

    override fun observeUid(): Flow<String?> = auth.authStateChanged.map { it?.uid }

    override fun observeAccount(): Flow<Account> = auth.authStateChanged.map { user ->
        Account(uid = user?.uid, isGuest = user?.isAnonymous ?: true, displayName = user?.displayName, email = user?.email)
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

    override suspend fun deleteAccount(): Boolean = try {
        auth.currentUser?.delete()
        true
    } catch (e: Exception) {
        false
    }
}
