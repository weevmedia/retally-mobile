/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.core.log

/**
 * Diagnostics switch for the shared module. Off by default; the apps turn it on for debug
 * builds only (Android: `BuildConfig.DEBUG` in ReTallyApplication; iOS: `debug` in
 * `IosApp.start`), so release builds write nothing to the device log — no store names, scan
 * contents or backend statuses.
 */
object DebugLog {
    var enabled: Boolean = false
}

/** `[tag] message` to the console in debug builds; the message isn't even built in release. */
inline fun debugLog(tag: String, message: () -> String) {
    if (DebugLog.enabled) println("[$tag] ${message()}")
}
