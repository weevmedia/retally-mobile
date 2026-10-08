package com.retally.shared.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageFile(path: String, maxDimension: Int): ImageBitmap? {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    val data: NSData = NSData.dataWithContentsOfFile(path) ?: return null
    val length = data.length.toInt()
    if (length == 0) return null
    val bytes = ByteArray(length)
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    // Scans are saved already downscaled by the Swift side (see DocumentScannerView), so a
    // straight decode is fine here; maxDimension is honoured at save time.
    return runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}
