package com.retally.shared.ui.splash

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

internal actual fun ByteArray.decodeImageBitmap(): ImageBitmap =
    SkiaImage.makeFromEncoded(this).toComposeImageBitmap()
