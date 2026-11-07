/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.data.cloud

import com.retally.shared.core.Platform
import com.retally.shared.platform.AndroidPlatform
import org.koin.mp.KoinPlatform
import java.io.File

/**
 * The app's private receipt-image folder, `filesDir/receipts` — where the scanners save photos
 * and where downloaded cloud photos are cached. Null before Koin has started.
 */
internal fun receiptsDir(): File? {
    val platform = KoinPlatform.getKoinOrNull()?.getOrNull<Platform>() as? AndroidPlatform ?: return null
    return File(platform.appContext().filesDir, "receipts")
}
