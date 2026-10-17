package com.retally.shared.domain.cloud

import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.model.Receipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App-lifetime, fire-and-forget cloud copy. Lives outside any screen's Store so the upload
 * survives the Review screen being popped the instant after "Save". Failures are silent: the
 * receipt is already safe locally and Settings → "Sync now" retries whatever is unsynced.
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun backUp(receipt: Receipt) {
        if (!cloudSync.isConfigured) return
        // Master switch. Anonymous users stay local-only regardless — the upload
        // itself skips them as a second line of defense.
        if (!prefs.current.cloudBackupEnabled) return
        scope.launch {
            val result = cloudSync.upload(listOf(receipt))
            if (result is CloudSyncResult.Success) {
                repository.markSynced(result.syncedIds, result.syncedAt)
            }
        }
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
}
