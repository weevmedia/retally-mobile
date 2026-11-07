/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.image

import com.retally.shared.data.cloud.receiptsDir
import java.io.File

/**
 * Where a downloaded cloud receipt photo is kept: the app's receipts folder (the same one the
 * scanners save to), so it is read from disk next time instead of downloaded again.
 */
actual fun getCachePath(filename: String): String? {
    val dir = receiptsDir()?.apply { mkdirs() } ?: return null
    return File(dir, filename).absolutePath
}
