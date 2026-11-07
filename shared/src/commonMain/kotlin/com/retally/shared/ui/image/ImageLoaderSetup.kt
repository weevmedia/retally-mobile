/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.svg.SvgDecoder
import okio.Path

/**
 * App-wide Coil setup, installed once from `initKoin`. Store logos are downloaded the first
 * time they're shown and kept in this disk cache, so later screens and launches show them
 * offline. The cache lives in the platform's caches folder (Android `cacheDir`, iOS
 * `Library/Caches`) — not iOS's temporary folder, which the system empties freely.
 */
fun installImageLoader() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            // Store logos may be SVG (the admin console accepts them); PNG/JPEG/WebP decode natively.
            .components { add(SvgDecoder.Factory()) }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDirectory(context))
                    .maxSizeBytes(IMAGE_CACHE_MAX_BYTES)
                    .build()
            }
            .build()
    }
}

/** Logos are a few KB each; 20 MB is room for every store a shopper will ever visit. */
private const val IMAGE_CACHE_MAX_BYTES = 20L * 1024 * 1024

/** Folder for Coil's disk cache inside the platform's caches directory. */
internal expect fun imageCacheDirectory(context: PlatformContext): Path
