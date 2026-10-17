package com.retally.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import com.retally.shared.data.cloud.FirebaseStorageService
import com.retally.shared.data.cloud.resolveImagePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Decodes an image file from the app's private storage. Returns null when the file is gone
 * (uninstall/reinstall, storage cleanup) so callers can hide the thumbnail rather than crash.
 * [maxDimension] downsamples large scans so a 12 MP page does not become a 48 MB bitmap.
 */
expect fun decodeImageFile(path: String, maxDimension: Int): ImageBitmap?

/** Composable-friendly loader: null while decoding and when the file cannot be read. */
@Composable
fun rememberLocalImage(path: String?, maxDimension: Int = 1600): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, path, maxDimension) {
        value = if (path == null) null else withContext(Dispatchers.Default) { runCatching { decodeImageFile(path, maxDimension) }.getOrNull() }
    }
    return state.value
}

/**
 * Cloud-aware image loader.
 *
 * - Local paths (filename or absolute): loads directly via [rememberLocalImage].
 * - Cloud paths (`users/...`): checks the local cache first. If not cached, downloads
 *   from Firebase Storage in the background. Returns a [CloudImageState] so callers
 *   can show a "downloading" indicator.
 */
sealed interface CloudImageState {
    data object Loading : CloudImageState
    data object Downloading : CloudImageState
    data class Loaded(val bitmap: ImageBitmap) : CloudImageState
    data object Missing : CloudImageState
}

@Composable
fun rememberReceiptImage(
    imagePath: String?,
    maxDimension: Int = 1600,
    storageService: FirebaseStorageService = koinInject(),
): CloudImageState {
    // Local path: use the existing synchronous loader.
    if (imagePath == null || !imagePath.startsWith("users/")) {
        val bitmap = rememberLocalImage(imagePath, maxDimension)
        return when {
            imagePath == null -> CloudImageState.Missing
            bitmap != null -> CloudImageState.Loaded(bitmap)
            else -> CloudImageState.Missing // Local file gone; nothing to download.
        }
    }

    // Cloud path: check local cache first, download if missing.
    val state = produceState<CloudImageState>(initialValue = CloudImageState.Loading, imagePath, maxDimension) {
        value = withContext(Dispatchers.Default) {
            // Derive a local cache filename from the storage path.
            // Storage path: users/{uid}/receipts/{receiptId}.jpg -> cache as {receiptId}.jpg
            val receiptId = imagePath.substringAfterLast("/").substringBeforeLast(".")
            val cacheFilename = "$receiptId.jpg"
            val cachedPath = resolveImagePath(cacheFilename)

            // 1. Check local cache.
            if (cachedPath != null) {
                val bitmap = runCatching { decodeImageFile(cachedPath, maxDimension) }.getOrNull()
                if (bitmap != null) {
                    return@withContext CloudImageState.Loaded(bitmap)
                }
            }

            // 2. Not cached: download from cloud.
            // Note: produceState can't easily switch to Downloading mid-flight without
            // restructuring; we set Downloading before the network call via a two-step.
            // For simplicity, the caller sees Loading until the download completes.
            // TODO: Emit Downloading state before the network call.
            val localPath = getCachePath(cacheFilename) ?: return@withContext CloudImageState.Missing
            val success = runCatching {
                storageService.downloadReceiptImage(imagePath, localPath)
            }.getOrDefault(false)
            if (!success) return@withContext CloudImageState.Missing

            val bitmap = runCatching { decodeImageFile(localPath, maxDimension) }.getOrNull()
            if (bitmap != null) CloudImageState.Loaded(bitmap) else CloudImageState.Missing
        }
    }
    return state.value
}

/**
 * Returns the full local path for caching a downloaded cloud image.
 * Platform-specific because the cache directory differs.
 */
expect fun getCachePath(filename: String): String?
