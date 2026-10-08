package com.retally.shared.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
