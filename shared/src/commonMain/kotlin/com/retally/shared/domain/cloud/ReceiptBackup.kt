/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.cloud

import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.model.Receipt
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-lifetime, fire-and-forget cloud copy. Lives outside any screen's Store so the upload
 * survives the Review screen being popped the instant after "Save". Failures are silent: the
 * receipt is already safe locally and Settings → "Sync now" retries whatever is unsynced.
 *
 * Receipts scanned as a guest never upload (the cloud copy is for signed-in shoppers), so
 * they need a second chance once the account stops being a guest. Without one, receipts
 * scanned before signing in stayed on the first phone forever and a second device signed
 * into the same account restored nothing.
 *
 * - Sign-in during this run (guest → account): the shopper is ASKED first — [pendingPrompt]
 *   carries the count, Home shows the dialog, [confirmPendingBackup] uploads and
 *   [declinePendingBackup] keeps them local (remembered per account, so a relaunch doesn't
 *   quietly upload what they declined; Settings → "Sync now" still can).
 * - Launch already signed in: anything still unsynced (a save whose upload failed) is
 *   pushed by [backUpPending] without asking — cloud backup was already agreed to.
 *
 * App-lifetime singleton (created at Koin start) so the sign-in hook runs regardless of
 * which screen is up — the same shape as [ReceiptRestore].
 *
 * Also handles guest retention: [pruneGuestReceipts] deletes receipts older than 30 days
 * for anonymous users (local-only FIFO). Call it on app startup.
 */
class ReceiptBackup(
    private val cloudSync: CloudSyncService,
    private val repository: ReceiptRepository,
    private val prefs: AppPreferences,
    private val auth: AuthRepository? = null,
    private val onDeleteImageFiles: ((List<String>) -> Unit)? = null,
    private val settings: Settings? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One upload at a time, so a save and the sign-in catch-up never push the same receipt twice. */
    private val uploadMutex = Mutex()

    private val _backingUpCount = MutableStateFlow(0)
    /** Receipts the sign-in catch-up is uploading right now; 0 when idle. Home shows a strip. */
    val backingUpCount: StateFlow<Int> = _backingUpCount.asStateFlow()

    private val _outcome = MutableStateFlow<BackupOutcome?>(null)
    /** How the last catch-up ended; Home shows it once, then calls [clearOutcome]. */
    val outcome: StateFlow<BackupOutcome?> = _outcome.asStateFlow()

    fun clearOutcome() {
        _outcome.value = null
    }

    private val _pendingPrompt = MutableStateFlow(0)
    /**
     * Receipts scanned before the sign-in that just happened, waiting for the shopper to say
     * whether to back them up; 0 when there is nothing to ask. Home shows the dialog.
     */
    val pendingPrompt: StateFlow<Int> = _pendingPrompt.asStateFlow()

    /** Uid of the account signed into during this run, for [declinePendingBackup]. */
    private var promptUid: String? = null

    init {
        if (auth != null && cloudSync.isConfigured) {
            // null until the first emission: a launch already signed in is a catch-up, a
            // guest that signs in later is a question.
            var wasGuest: Boolean? = null
            auth.observeAccount()
                .map { account -> account.uid?.takeUnless { account.isGuest } }
                .distinctUntilChanged()
                .onEach { uid ->
                    val fromGuest = wasGuest == true
                    wasGuest = uid == null
                    if (uid != null) onSignedIn(uid, fromGuest)
                }
                .launchIn(scope)
        }
    }

    private fun onSignedIn(uid: String, fromGuest: Boolean) {
        if (!prefs.current.cloudBackupEnabled) return
        scope.launch {
            val pending = repository.observeReceipts().first().count { it.syncedAt == null }
            if (pending == 0) return@launch
            if (settings?.getStringOrNull(KEY_DECLINED_UID) == uid) return@launch
            if (fromGuest) {
                promptUid = uid
                _pendingPrompt.value = pending
            } else {
                backUpPending()
            }
        }
    }

    /** The shopper said yes: upload the receipts scanned before signing in. */
    fun confirmPendingBackup() {
        _pendingPrompt.value = 0
        settings?.remove(KEY_DECLINED_UID)
        backUpPending()
    }

    /** The shopper said not now: they stay on this device; Settings shows them as pending. */
    fun declinePendingBackup() {
        _pendingPrompt.value = 0
        promptUid?.let { settings?.putString(KEY_DECLINED_UID, it) }
    }

    fun backUp(receipt: Receipt) {
        if (!cloudSync.isConfigured) return
        // Master switch. Anonymous users stay local-only regardless — the upload
        // itself skips them as a second line of defense.
        if (!prefs.current.cloudBackupEnabled) return
        scope.launch { upload(listOf(receipt)) }
    }

    /**
     * Uploads every receipt that has no cloud copy yet (`syncedAt == null`): the ones scanned
     * as a guest before signing in (after the shopper agreed), and any whose save-time upload
     * failed. A no-op for guests and when backup is switched off.
     */
    fun backUpPending() {
        if (!cloudSync.isConfigured) return
        if (!prefs.current.cloudBackupEnabled) return
        scope.launch {
            val pending = repository.observeReceipts().first().filter { it.syncedAt == null }
            if (pending.isEmpty()) return@launch
            println("[ReTallyBackup] uploading ${pending.size} pending receipt(s)")
            _backingUpCount.value = pending.size
            try {
                _outcome.value = when (val result = upload(pending)) {
                    null -> null // Already synced by a save that got there first.
                    is CloudSyncResult.Success -> BackupOutcome.BackedUp(result.syncedIds.size)
                    is CloudSyncResult.Failure -> BackupOutcome.Failed(pending.size, result.message)
                }
            } finally {
                _backingUpCount.value = 0
            }
        }
    }

    /** Uploads whichever of [receipts] are still unsynced; null when none were. */
    private suspend fun upload(receipts: List<Receipt>): CloudSyncResult? = uploadMutex.withLock {
        // Re-read from the DB: a batch queued behind another upload may already be synced.
        val wanted = receipts.map { it.id }.toSet()
        val stillPending = repository.observeReceipts().first()
            .filter { it.id in wanted && it.syncedAt == null }
        if (stillPending.isEmpty()) return@withLock null
        val result = cloudSync.upload(stillPending)
        if (result is CloudSyncResult.Success) {
            repository.markSynced(result.syncedIds, result.syncedAt)
        } else if (result is CloudSyncResult.Failure) {
            println("[ReTallyBackup] upload failed: ${result.message}")
        }
        result
    }

    /**
     * Deletes receipts older than 30 days for guests (anonymous users).
     * Signed-in users keep everything (cloud backup). Fire-and-forget.
     */
    fun pruneGuestReceipts() {
        val auth = auth ?: return
        // Only prune for guests; signed-in users keep all receipts.
        if (!auth.isAnonymous) return
        scope.launch {
            val deletedPaths = repository.pruneOldReceipts(maxAgeDays = 30)
            // Delete the image files.
            onDeleteImageFiles?.invoke(deletedPaths)
        }
    }

    private companion object {
        const val KEY_DECLINED_UID = "backup.declinedPendingUid"
    }
}

/** Result of a sign-in / launch catch-up upload. */
sealed interface BackupOutcome {
    data class BackedUp(val count: Int) : BackupOutcome
    data class Failed(val count: Int, val message: String) : BackupOutcome
}
