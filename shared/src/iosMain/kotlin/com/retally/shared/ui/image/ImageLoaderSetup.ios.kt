/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.image

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** `Library/Caches/image_cache`: survives relaunches; iOS only clears it under storage pressure. */
internal actual fun imageCacheDirectory(context: PlatformContext): Path {
    val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
        ?: return "image_cache".toPath()
    return "$caches/image_cache".toPath()
}
