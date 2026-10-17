package com.retally.shared.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.lastPathComponent
import platform.Foundation.stringByAppendingPathComponent
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageFile(path: String, maxDimension: Int): ImageBitmap? {
    val resolved = resolveImagePath(path) ?: return null
    if (!NSFileManager.defaultManager.fileExistsAtPath(resolved)) return null
    val data: NSData = NSData.dataWithContentsOfFile(resolved) ?: return null
    val length = data.length.toInt()
    if (length == 0) return null
    val bytes = ByteArray(length)
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    // Scans are saved already downscaled by the Swift side (see ScanImageFiles), so a
    // straight decode is fine here; maxDimension is honoured at save time.
    return runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

/**
 * Resolves a stored image reference to the current full path.
 * The app stores filenames only (not absolute paths) because iOS changes the sandbox
 * path on reinstall. Legacy absolute paths are handled by extracting the filename.
 */
private fun resolveImagePath(stored: String): String? {
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
