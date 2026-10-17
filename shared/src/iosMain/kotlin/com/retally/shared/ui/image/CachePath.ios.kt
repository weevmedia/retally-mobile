package com.retally.shared.ui.image

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringByAppendingPathComponent

@OptIn(ExperimentalForeignApi::class)
actual fun getCachePath(filename: String): String? {
    val base = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory, NSUserDomainMask, true
    ).firstOrNull() as? String ?: return null
    val dir = (base as NSString).stringByAppendingPathComponent("receipts")
    return (dir as NSString).stringByAppendingPathComponent(filename)
}
