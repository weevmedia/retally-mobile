package com.retally.shared.data.cloud

import com.retally.shared.core.Platform
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.cloud.CloudSyncResult
import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.receipt.model.Receipt
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/**
 * Writes receipts + line items straight to Firestore under `users/{uid}` (schema v1).
 *
 * - Signs the user in anonymously on first use, so guests are backed up under a uid that
 *   survives a later Google/Apple/email link.
 * - One receipt = one `WriteBatch` (receipt doc + all line items), so a receipt is never
 *   half-written. Batches are capped well under Firestore's 500-op limit.
 * - `set(merge = true)` makes re-uploads idempotent: a receipt edited offline overwrites its
 *   own document; nothing is duplicated.
 */
class FirebaseCloudSyncService(
    private val auth: AuthRepository,
    private val platform: Platform,
    private val deviceIdProvider: () -> String,
    private val currencyProvider: () -> String = { "USD" },
    private val localeProvider: () -> String = { "en-US" },
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val mapper: FirestoreReceiptMapper = FirestoreReceiptMapper(),
    private val clock: Clock = Clock.System,
) : CloudSyncService {

    override val isConfigured: Boolean = true

    override suspend fun upload(receipts: List<Receipt>): CloudSyncResult {
        if (receipts.isEmpty()) return CloudSyncResult.Success(emptyList(), clock.now())
        return try {
            val uid = auth.ensureSignedIn()
            ensureUserDocument(uid)

            val currency = currencyProvider()
            val deviceId = deviceIdProvider()
            val synced = mutableListOf<String>()
            for (receipt in receipts) {
                writeReceipt(uid, receipt, currency, deviceId)
                synced += receipt.id
            }
            CloudSyncResult.Success(synced, clock.now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            CloudSyncResult.Failure(
                message = e.message ?: "Cloud backup failed",
                retryable = e.isRetryable(),
            )
        }
    }

    private suspend fun ensureUserDocument(uid: String) {
        val userRef = firestore.collection("users").document(uid)
        if (!userRef.get().exists) {
            userRef.set(mapper.newUserDocument(locale = localeProvider(), currency = currencyProvider()))
        }
    }

    private suspend fun writeReceipt(uid: String, receipt: Receipt, currency: String, deviceId: String) {
        val receiptRef = firestore
            .collection("users").document(uid)
            .collection("receipts").document(receipt.id)
        val lineItems = receiptRef.collection("lineItems")

        // 1 receipt + N items per batch; split only for absurdly long receipts.
        receipt.items.withIndex().chunked(MAX_ITEMS_PER_BATCH).forEachIndexed { chunkIndex, chunk ->
            val batch = firestore.batch()
            if (chunkIndex == 0) {
                batch.set(
                    receiptRef,
                    mapper.receiptDocument(receipt, currency, deviceId, platform.appVersion),
                    merge = true,
                )
            }
            for ((position, item) in chunk) {
                batch.set(
                    lineItems.document(item.id),
                    mapper.lineItemDocument(uid, receipt, item, position, currency),
                    merge = true,
                )
            }
            batch.commit()
        }
        if (receipt.items.isEmpty()) {
            receiptRef.set(mapper.receiptDocument(receipt, currency, deviceId, platform.appVersion), merge = true)
        }
    }

    private fun Throwable.isRetryable(): Boolean {
        val m = message?.lowercase() ?: return true
        // Rules rejections are deterministic — retrying will not help.
        return !("permission" in m || "denied" in m || "invalid" in m)
    }

    private companion object {
        const val MAX_ITEMS_PER_BATCH = 400
    }
}
