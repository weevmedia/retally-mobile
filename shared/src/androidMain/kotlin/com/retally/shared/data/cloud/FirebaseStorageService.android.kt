package com.retally.shared.data.cloud

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

/**
 * Android implementation using the native Firebase Storage SDK directly
 * (GitLive's Data type doesn't map cleanly to ByteArray in 2.5.0).
 * The native SDK is available transitively via the GitLive dependency.
 */
actual class FirebaseStorageService actual constructor() {
    actual suspend fun uploadReceiptImage(uid: String, receiptId: String, localPath: String): String? {
        return try {
            val storagePath = "users/$uid/receipts/$receiptId.jpg"
            val ref = FirebaseStorage.getInstance().reference.child(storagePath)
            val bytes = readFileBytes(localPath) ?: return null
            // Storage rules require an image content type.
            val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
            ref.putBytes(bytes, metadata).await()
            storagePath
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun downloadReceiptImage(storagePath: String, localPath: String): Boolean {
        return try {
            val ref = FirebaseStorage.getInstance().reference.child(storagePath)
            // 10 MB max download size.
            val bytes = ref.getBytes(10 * 1024 * 1024).await()
            writeFileBytes(localPath, bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun deleteReceiptImage(storagePath: String) {
        try {
            FirebaseStorage.getInstance().reference.child(storagePath).delete().await()
        } catch (e: Exception) {
            // Best-effort; ignore.
        }
    }
}
