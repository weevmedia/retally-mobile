package com.retally.shared.data.receipt

import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.cloud.DeletedReceipts
import com.retally.shared.domain.receipt.ReceiptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wraps the local repository so deletes reach the cloud: the receipt is removed locally at
 * once, remembered as deleted (so a restore never brings it back), and soft-deleted in
 * Firestore in the background. Everything else passes straight through.
 */
internal class CloudMirroredReceiptRepository(
    private val local: ReceiptRepository,
    private val deleted: DeletedReceipts,
    private val cloudSync: () -> CloudSyncService,
) : ReceiptRepository by local {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun delete(id: String) {
        local.delete(id)
        deleted.add(listOf(id))
        scope.launch { runCatching { cloudSync().markDeleted(listOf(id)) } }
    }

    override suspend fun deleteAll() {
        val ids = local.observeReceipts().first().map { it.id }
        local.deleteAll()
        deleted.add(ids)
        scope.launch { runCatching { cloudSync().markDeleted(ids) } }
    }
}
