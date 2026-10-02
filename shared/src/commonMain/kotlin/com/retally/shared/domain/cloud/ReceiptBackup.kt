package com.retally.shared.domain.cloud

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
 */
class ReceiptBackup(
    private val cloudSync: CloudSyncService,
    private val repository: ReceiptRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun backUp(receipt: Receipt) {
        if (!cloudSync.isConfigured) return
        scope.launch {
            val result = cloudSync.upload(listOf(receipt))
            if (result is CloudSyncResult.Success) {
                repository.markSynced(result.syncedIds, result.syncedAt)
            }
        }
    }
}
