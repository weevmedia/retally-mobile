package com.retally.shared.data.cloud

/**
 * Uploads and downloads receipt images to/from Firebase Storage.
 *
 * Path convention: `users/{uid}/receipts/{receiptId}.jpg`
 *
 * - Signed-in users: images are uploaded on save, the storage path is stored in
 *   Firestore (`image.storagePath`) and in the local DB. Downloads are cached locally.
 * - Guests (anonymous): images stay local only, never touch Storage.
 *
 * Platform-specific (expect/actual) because the GitLive Storage `Data` type differs
 * per platform (ByteArray on Android, NSData on iOS).
 */
expect class FirebaseStorageService() {
    /**
     * Uploads the image file at [localPath] to `users/{uid}/receipts/{receiptId}.jpg`.
     * Returns the storage path (e.g. `users/abc123/receipts/xyz.jpg`), or null on failure.
     */
    suspend fun uploadReceiptImage(uid: String, receiptId: String, localPath: String): String?

    /**
     * Downloads the image at [storagePath] and saves it to [localPath].
     * Returns true on success.
     */
    suspend fun downloadReceiptImage(storagePath: String, localPath: String): Boolean

    /**
     * Deletes the image at [storagePath] from Storage. Best-effort; failures are ignored.
     */
    suspend fun deleteReceiptImage(storagePath: String)
}
