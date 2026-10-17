package com.retally.shared.data.cloud

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.lastPathComponent
import platform.Foundation.stringByAppendingPathComponent
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun readFileBytes(path: String): ByteArray? {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    val data: NSData = NSData.dataWithContentsOfFile(path) ?: return null
    val length = data.length.toInt()
    if (length == 0) return null
    val bytes = ByteArray(length)
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
actual fun writeFileBytes(path: String, bytes: ByteArray): Boolean {
    // Use POSIX file I/O directly to avoid NSData creation issues.
    return try {
        val file = fopen(path, "wb") ?: return false
        try {
            bytes.usePinned { pinned ->
                val written = fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
                written == bytes.size.toULong()
            }
        } finally {
            fclose(file)
        }
    } catch (e: Exception) {
        false
    }
}

actual fun resolveImagePath(stored: String): String? {
    if (stored.isEmpty()) return null
    // Legacy absolute path that still exists (same install).
    if (stored.startsWith("/") && NSFileManager.defaultManager.fileExistsAtPath(stored)) {
        return stored
    }
    // Filename (or legacy path from a different sandbox): resolve in receipts dir.
    val filename = (stored as NSString).lastPathComponent
    val base = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory, NSUserDomainMask, true
    ).firstOrNull() as? String ?: return null
    val full = (base as NSString).stringByAppendingPathComponent("receipts")
        .let { (it as NSString).stringByAppendingPathComponent(filename) }
    return if (NSFileManager.defaultManager.fileExistsAtPath(full)) full else null
}
