package com.retally.shared.data.cloud

import java.io.File

actual fun readFileBytes(path: String): ByteArray? {
    return try {
        val file = File(path)
        if (!file.exists()) null else file.readBytes()
    } catch (e: Exception) {
        null
    }
}

actual fun writeFileBytes(path: String, bytes: ByteArray): Boolean {
    return try {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        true
    } catch (e: Exception) {
        false
    }
}

actual fun resolveImagePath(stored: String): String? {
    if (stored.isEmpty()) return null
    // Legacy absolute path that still exists.
    val absFile = File(stored)
    if (stored.startsWith("/") && absFile.exists()) return stored
    // Filename: resolve in the app's files/receipts directory.
    // Note: Android stores images via app-specific storage; the exact location
    // is determined by the platform image saver. For now, try the filename as-is
    // if it's already a valid path, otherwise return null.
    // TODO: Align with Android's image storage location.
    return null
}
