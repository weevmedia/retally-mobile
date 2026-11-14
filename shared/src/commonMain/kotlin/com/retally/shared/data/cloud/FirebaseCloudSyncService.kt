/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.data.cloud

import com.retally.shared.core.log.debugLog
import com.retally.shared.core.crash.CrashReporting
import com.retally.shared.core.Platform
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.domain.cloud.CloudRestoreResult
import com.retally.shared.domain.cloud.CloudSyncResult
import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import com.retally.shared.domain.receipt.receiptWindowStart
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentReference
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
                // The receipt's own currency, not the user's current one: history stays
                // right after a move or a trip.
                try {
                    writeReceipt(uid, receipt, receipt.currency, deviceId, imageStoragePath)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Firestore never says which rule refused a write; list what validReceipt()
                    // / validLineItem() would reject so the culprit shows up in logcat.
                    debugLog("ReTallySync") { "upload(${receipt.id}) failed: ${e.message}\n${rulesReport(receipt, receipt.currency)}" }
                    throw e
                }
                synced += receipt.id
            }
            CloudSyncResult.Success(synced, clock.now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            CrashReporting.record(e)
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
            // Only the last 12 months (see receiptWindowStart): older receipts are never
            // downloaded, nor their line items. A one-field range filter needs no Firestore index.
            val from = receiptWindowStart().let { Timestamp(seconds = it.epochSeconds, nanoseconds = it.nanosecondsOfSecond) }
            val recent = receiptsRef.where { "purchaseDate" greaterThanOrEqualTo from }
            val restored = mutableListOf<Receipt>()
            val docs = recent.get().documents
            debugLog("ReTallyRestore") { "uid=$uid cloud=${docs.size} alreadyLocal=${docs.count { it.id in skipIds }}" }
            for (doc in docs) {
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
            debugLog("ReTallyRestore") { "download failed: ${e.message}" }
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
                val ref = receiptsRef.document(id)
                // Never backed up: nothing to soft-delete. Writing anyway creates a bare
                // {deletedAt, updatedAt} doc, which validReceipt() rejects (PERMISSION_DENIED).
                // syncedAt can't tell us this: local edits reset it even when a cloud copy exists.
                if (!ref.get().exists) {
                    debugLog("ReTallyRestore") { "markDeleted($id) skipped: no cloud copy" }
                    continue
                }
                // Soft delete: rules forbid hard deletes; the purge job removes it after 30 days.
                // One write per receipt so one failure doesn't block the rest.
                ref.set(
                    mapOf("deletedAt" to FieldValue.serverTimestamp, "updatedAt" to FieldValue.serverTimestamp),
                    merge = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Offline or refused: the local tombstone still keeps it from being restored.
                debugLog("ReTallySync") { "markDeleted($id) failed: ${e.message}\n${cloudCopyReport(receiptsRef.document(id))}" }
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
            categoryLocked = opt<Boolean>("categoryCorrected") ?: false,
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
        // createdAt is immutable in the rules. A re-upload (after an edit) would resend it from
        // the local DB, which keeps only milliseconds, while the first upload stored micros, so
        // the update looks like a createdAt change and is refused. Leave it out on updates:
        // merge keeps the cloud value.
        val exists = receiptRef.get().exists
        val receiptDoc = mapper.receiptDocument(receipt, currency, deviceId, platform.appVersion, imageStoragePath, platform.name, platform.osVersion)
            .let { if (exists) it - "createdAt" else it }

        // 1 receipt + N items per batch; split only for absurdly long receipts.
        receipt.items.withIndex().chunked(MAX_ITEMS_PER_BATCH).forEachIndexed { chunkIndex, chunk ->
            val batch = firestore.batch()
            if (chunkIndex == 0) {
                batch.set(
                    receiptRef,
                    receiptDoc,
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
            receiptRef.set(receiptDoc, merge = true)
        }
    }

    /**
     * Debug aid: an update is checked against the whole merged document, so when a soft-delete
     * is refused, a field already stored in the cloud copy breaks validReceipt(). Lists which.
     */
    private suspend fun cloudCopyReport(ref: DocumentReference): String {
        val doc = runCatching { ref.get() }.getOrElse { return "could not read cloud copy: ${it.message}" }
        if (!doc.exists) return "no cloud copy"
        val problems = mutableListOf<String>()
        fun money(field: String) {
            val v = doc.number(field)
            if (v == null || v.isNaN() || v < -100_000 || v > 100_000) problems += "$field=$v (must be a number within ±100000)"
        }
        val store = doc.opt<String>("storeName")
        if (store.isNullOrEmpty() || store.length > 120) problems += "storeName='$store' (1–120 chars)"
        if (doc.opt<String>("merchantNameNormalized") == null) problems += "merchantNameNormalized missing"
        if (doc.opt<Timestamp>("purchaseDate") == null) problems += "purchaseDate missing / not a timestamp"
        val local = doc.opt<String>("purchaseDateLocal")
        if (local == null || !Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(local)) problems += "purchaseDateLocal='$local'"
        if (doc.opt<String>("currency")?.length != 3) problems += "currency='${doc.opt<String>("currency")}'"
        money("total"); money("subtotal"); money("tax"); money("analysis.overchargeAmount")
        val count = doc.opt<Long>("lineItemCount")
        if (count == null || count !in 0..500) problems += "lineItemCount=$count (int 0..500)"
        if (doc.opt<String>("paymentMethod") !in listOf("card", "cash", "mobile", "unknown")) problems += "paymentMethod='${doc.opt<String>("paymentMethod")}'"
        if (doc.opt<Long>("analysis.duplicateCount") == null) problems += "analysis.duplicateCount missing / not an int"
        if (doc.opt<Boolean>("ocr.piiScrubbed") != true) problems += "ocr.piiScrubbed is not true"
        val raw = doc.opt<String>("ocr.rawText")
        if (raw == null || raw.length > 20_000) problems += "ocr.rawText missing or too long"
        if (doc.opt<String>("source") !in listOf("scan", "manual", "import")) problems += "source='${doc.opt<String>("source")}'"
        if (doc.opt<Long>("schemaVersion") != 1L) problems += "schemaVersion=${doc.opt<Long>("schemaVersion")}"
        return if (problems.isEmpty()) {
            "cloud copy passes the field checks (check auth / App Check)"
        } else {
            "cloud copy breaks the rules:\n" + problems.joinToString("\n") { "  - $it" }
        }
    }

    /** Debug aid: the fields of [receipt] that firestore.rules would refuse. */
    private fun rulesReport(receipt: Receipt, currency: String): String {
        val doc = mapper.receiptDocument(receipt, currency, "-", platform.appVersion)
        val problems = mutableListOf<String>()
        fun money(name: String, v: Any?) {
            val d = (v as? Number)?.toDouble()
            if (d == null || d.isNaN() || d < -100_000 || d > 100_000) problems += "$name=$v (must be a number within ±100000)"
        }
        val store = doc["storeName"] as String
        if (store.isEmpty()) problems += "storeName is empty"
        money("total", doc["total"]); money("subtotal", doc["subtotal"]); money("tax", doc["tax"])
        money("analysis.overchargeAmount", receipt.overchargeAmount)
        if (receipt.items.size > 500) problems += "lineItemCount=${receipt.items.size} (max 500)"
        if ((doc["currency"] as String).length != 3) problems += "currency='${doc["currency"]}' (must be 3 letters)"
        val pan = Regex(".*[0-9]{4}[ -]?[0-9]{4}[ -]?[0-9]{4}[ -]?[0-9]{4}.*")
        receipt.items.forEachIndexed { i, item ->
            val name = item.name.take(200)
            if (name.isEmpty()) problems += "item[$i] name is empty"
            if (pan.matches(name)) problems += "item[$i] name looks like a card number: '$name'"
            if (item.price.isNaN() || item.price < -100_000 || item.price > 100_000) problems += "item[$i] price=${item.price} out of range"
        }
        val fields = "storeName='$store' total=${doc["total"]} subtotal=${doc["subtotal"]} tax=${doc["tax"]} " +
            "items=${receipt.items.size} createdAt=${receipt.createdAt} syncedAt=${receipt.syncedAt}"
        return if (problems.isEmpty()) {
            "$fields\nNo field problems found: likely an update that changes createdAt (cloud copy differs) or App Check / auth."
        } else {
            "$fields\n" + problems.joinToString("\n") { "  - $it" }
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

