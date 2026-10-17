package com.retally.shared.data.cloud

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.storage.storageMetadata
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual class FirebaseStorageService actual constructor() {
    actual suspend fun uploadReceiptImage(uid: String, receiptId: String, localPath: String): String? {
        return try {
            val storagePath = "users/$uid/receipts/$receiptId.jpg"
            val ref = Firebase.storage.reference(storagePath)
            // Read the file directly as NSData, wrap in GitLive Data.
            val nsData: NSData = NSData.dataWithContentsOfFile(localPath) ?: return null
            // Storage rules require an image content type.
            ref.putData(Data(nsData), storageMetadata { contentType = "image/jpeg" })
            storagePath
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun downloadReceiptImage(storagePath: String, localPath: String): Boolean {
        return try {
            val ref = Firebase.storage.reference(storagePath)
            // 10 MB max download size. Returns GitLive Data on iOS.
            val gitLiveData: Data = ref.getData(maxDownloadSizeBytes = 10 * 1024 * 1024)
            // Extract NSData from GitLive Data wrapper.
            // TODO: Verify the accessor name for GitLive iOS Data.
            val nsData: NSData = (gitLiveData as? NSData) ?: return false
            // Convert NSData to ByteArray.
            val length = nsData.length.toInt()
            if (length == 0) return false
            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
            }
            writeFileBytes(localPath, bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun deleteReceiptImage(storagePath: String) {
        try {
            Firebase.storage.reference(storagePath).delete()
        } catch (e: Exception) {
            // Best-effort; ignore.
        }
    }
}
