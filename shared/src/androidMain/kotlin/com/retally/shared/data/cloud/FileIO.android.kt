/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

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
    // Older Android scans stored the scanner's file:// URI; use its path if the file is still there.
    if (stored.startsWith("file://")) {
        val path = stored.removePrefix("file://")
        return path.takeIf { File(it).exists() }
    }
    // Legacy absolute path that still exists.
    val absFile = File(stored)
    if (stored.startsWith("/") && absFile.exists()) return stored
    // A bare filename (a cached cloud photo), or an absolute path from an older install whose
    // sandbox path changed: look for the file in the app's receipts folder.
    val inReceipts = receiptsDir()?.let { File(it, absFile.name) } ?: return null
    return inReceipts.absolutePath.takeIf { inReceipts.exists() }
}

actual fun deleteFile(path: String): Boolean = try {
    File(path).delete()
} catch (e: Exception) {
    false
}
