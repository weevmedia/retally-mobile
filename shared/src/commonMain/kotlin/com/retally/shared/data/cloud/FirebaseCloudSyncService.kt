package com.retally.shared.data.cloud

import com.retally.shared.core.Platform
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.domain.cloud.CloudRestoreResult
import com.retally.shared.domain.cloud.CloudSyncResult
import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/**
 * Writes receipts + line items straight to Firestore under `users/{uid}` (schema v1).
 *
 * - Guests (anonymous): cloud backup is SKIPPED. Receipts stay local only, with a
 *   30-day retention (see `pruneOldReceipts`). Images never leave the device.
 * - Signed-in users: receipts go to Firestore, images go to Firebase Storage at
 *   `users/{uid}/receipts/{receiptId}.jpg`. The storage path is stored in both
 *   Firestore (`image.storagePath`) and the local DB.
 *
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
    private val storageService: FirebaseStorageService,
    private val clock: Clock = Clock.System,
) : CloudSyncService {

    override val isConfigured: Boolean = true

    override suspend fun upload(receipts: List<Receipt>): CloudSyncResult {
        if (receipts.isEmpty()) return CloudSyncResult.Success(emptyList(), clock.now())
        // Guests: local only, no cloud backup.
        if (auth.isAnonymous) {
            return CloudSyncResult.Success(emptyList(), clock.now())
        }
        return try {
            val uid = auth.ensureSignedIn()
            ensureUserDocument(uid)

            val currency = currencyProvider()
            val deviceId = deviceIdProvider()
            val synced = mutableListOf<String>()
            for (receipt in receipts) {
                val imageStoragePath = uploadImageIfNeeded(uid, receipt)
                writeReceipt(uid, receipt, currency, deviceId, imageStoragePath)
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

    override suspend fun download(skipIds: Set<String>): CloudRestoreResult {
        if (auth.isAnonymous) return CloudRestoreResult.Success(emptyList())
        return try {
            val uid = auth.ensureSignedIn()
            val receiptsRef = firestore.collection("users").document(uid).collection("receipts")
            val restored = mutableListOf<Receipt>()
            for (doc in receiptsRef.get().documents) {
                if (doc.id in skipIds) continue
                if (doc.opt<Timestamp>("deletedAt") != null) continue
                // Line items are read only for receipts the device doesn't have yet.
                val items = receiptsRef.document(doc.id).collection("lineItems").get().documents
                    .sortedBy { it.number("position") ?: 0.0 }
                    .mapNotNull { it.toReceiptItem() }
                doc.toReceipt(items)?.let(restored::add)
            }
            CloudRestoreResult.Success(restored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("[ReTallyRestore] download failed: ${e.message}")
            CloudRestoreResult.Failure(e.message ?: "Restore failed")
        }
    }

    override suspend fun markDeleted(ids: Collection<String>) {
        if (ids.isEmpty() || auth.isAnonymous) return
        val uid = try {
            auth.ensureSignedIn()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return
        }
        val receiptsRef = firestore.collection("users").document(uid).collection("receipts")
        for (id in ids) {
            try {
                // Soft delete: rules forbid hard deletes; the purge job removes it after 30 days.
                // One write per receipt so a never-uploaded one (rejected) doesn't block the rest.
                receiptsRef.document(id).set(
                    mapOf("deletedAt" to FieldValue.serverTimestamp, "updatedAt" to FieldValue.serverTimestamp),
                    merge = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // No cloud copy, or offline: the local tombstone still keeps it from being restored.
                println("[ReTallyRestore] markDeleted($id) failed: ${e.message}")
            }
        }
    }

    // ---- Firestore → app model (inverse of FirestoreReceiptMapper) ----

    private fun DocumentSnapshot.toReceipt(items: List<ReceiptItem>): Receipt? {
        val storeName = opt<String>("storeName")?.takeIf { it.isNotBlank() } ?: return null
        val purchaseDate = opt<Timestamp>("purchaseDate")?.toInstant() ?: return null
        val createdAt = opt<Timestamp>("createdAt")?.toInstant() ?: purchaseDate
        return Receipt(
            id = id,
            storeName = storeName,
            storeAddress = opt<String>("storeAddress"),
            purchaseDate = purchaseDate,
            // Only the payment category is stored in the cloud (never card digits).
            paymentInfo = when (opt<String>("paymentMethod")) {
                "card" -> "Card"
                "cash" -> "Cash"
                "mobile" -> "Mobile pay"
                else -> null
            },
            items = items,
            subtotal = number("subtotal"),
            tax = number("tax"),
            receiptTotal = number("total"),
            barcode = opt<String>("barcode"),
            // A cloud path ("users/…"): LocalImage downloads and caches it on first view.
            imagePath = opt<String>("image.storagePath"),
            createdAt = createdAt,
            // Already in the cloud: never re-upload.
            syncedAt = opt<Timestamp>("updatedAt")?.toInstant() ?: clock.now(),
        )
    }

    private fun DocumentSnapshot.toReceiptItem(): ReceiptItem? {
        val name = opt<String>("rawDescription")?.takeIf { it.isNotBlank() } ?: return null
        return ReceiptItem(
            id = id,
            itemNumber = opt<String>("itemNumber"),
            name = name,
            price = number("unitPrice") ?: number("lineTotal") ?: 0.0,
            isDuplicate = opt<Boolean>("isDuplicate") ?: false,
            categoryId = opt<String>("category")?.takeIf { it.isNotBlank() } ?: ItemCategory.DEFAULT_ID,
        )
    }

    private fun Timestamp.toInstant(): Instant = Instant.fromEpochSeconds(seconds, nanoseconds.toLong())

    /**
     * Uploads the receipt image to Firebase Storage if the receipt has a local image.
     * Returns the storage path, or null if no image or upload failed.
     */
    private suspend fun uploadImageIfNeeded(uid: String, receipt: Receipt): String? {
        val imagePath = receipt.imagePath ?: return null
        // Skip if it's already a cloud path (already uploaded).
        if (imagePath.startsWith("users/")) return imagePath
        val localPath = resolveImagePath(imagePath) ?: return null
        return storageService.uploadReceiptImage(uid, receipt.id, localPath)
    }

    private suspend fun ensureUserDocument(uid: String) {
        val userRef = firestore.collection("users").document(uid)
        if (!userRef.get().exists) {
            userRef.set(mapper.newUserDocument(locale = localeProvider(), currency = currencyProvider()))
        }
    }

    private suspend fun writeReceipt(uid: String, receipt: Receipt, currency: String, deviceId: String, imageStoragePath: String?) {
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
                    mapper.receiptDocument(receipt, currency, deviceId, platform.appVersion, imageStoragePath),
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
            receiptRef.set(mapper.receiptDocument(receipt, currency, deviceId, platform.appVersion, imageStoragePath), merge = true)
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

/** Reads a field, or null when it is missing, null or of another type. */
private inline fun <reified T> DocumentSnapshot.opt(field: String): T? =
    runCatching { get<T?>(field) }.getOrNull()

/** Numbers come back as Double or Long depending on how they were written. */
private fun DocumentSnapshot.number(field: String): Double? =
    opt<Double>(field) ?: opt<Long>(field)?.toDouble()

