package com.retally.shared.core

/** Small facts about the host the shared layer occasionally needs. */
interface Platform {
    /** "android" or "ios". Used for analytics and the cloud payload. */
    val name: String

    /** Marketing version, e.g. "1.0.0". */
    val appVersion: String

    /** Hardware model for the admin device list, e.g. "Google Pixel 8" or "iPhone15,2". */
    val deviceModel: String get() = "unknown"

    /** OS version, e.g. "15" (Android) or "18.1" (iOS). */
    val osVersion: String get() = "unknown"

    /** Opens [url] in the system browser. Returns false if nothing could handle it. */
    fun openUrl(url: String): Boolean
}
