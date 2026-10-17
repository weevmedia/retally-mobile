package com.retally.shared.domain.cloud

import com.retally.shared.domain.receipt.model.Receipt
import kotlin.time.Instant

/**
 * Outcome of one sync attempt. Kept as a sealed type (not exceptions) so the Store can reduce
 * it into state without try/catch at the presentation layer.
 */
sealed interface CloudSyncResult {
    data class Success(val syncedIds: List<String>, val syncedAt: Instant) : CloudSyncResult
    data class Failure(val message: String, val retryable: Boolean) : CloudSyncResult
}

/**
 * Boundary to the backend. **Placeholder** — the wire contract below is a proposal for the
 * ReTally API, not something that exists yet. Everything above this interface (stores, UI,
 * repository) is final; only the implementation swaps when the backend lands.
 */
interface CloudSyncService {
    /** Whether a backend is configured at all. False keeps the app fully offline. */
    val isConfigured: Boolean

    /** Uploads [receipts] that have not been synced yet. Never throws. */
    suspend fun upload(receipts: List<Receipt>): CloudSyncResult

    /**
     * Receipts the signed-in shopper has in the cloud that are not in [skipIds] (already on
     * this device, or deleted here). Soft-deleted cloud receipts are excluded. Never throws.
     * Backends without a download path return an empty success.
     */
    suspend fun download(skipIds: Set<String>): CloudRestoreResult = CloudRestoreResult.Success(emptyList())

    /** Soft-deletes the cloud copy (sets `deletedAt`) so other devices and restores skip it. Best-effort. */
    suspend fun markDeleted(ids: Collection<String>) {}
}

/** Outcome of [CloudSyncService.download]. */
sealed interface CloudRestoreResult {
    data class Success(val receipts: List<Receipt>) : CloudRestoreResult
    data class Failure(val message: String) : CloudRestoreResult
}

/**
 * Backend configuration. Fill [baseUrl] (and optionally [apiKey]) when the API exists; the
 * platform apps pass this in at startup so release and debug can point at different stacks.
 */
data class CloudConfig(
    val baseUrl: String = "",
    val apiKey: String? = null,
    /** True routes uploads through [SimulatedCloudSyncService] instead of the network. */
    val simulate: Boolean = false,
    /** Which real backend to use when [simulate] is false. */
    val backend: CloudBackend = CloudBackend.FIREBASE,
)

enum class CloudBackend {
    /** Firestore via the Firebase SDK; identity from anonymous Firebase Auth. */
    FIREBASE,
    /** The proposed REST contract in [com.retally.shared.data.cloud.KtorCloudSyncService]. */
    REST,
}
