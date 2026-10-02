package com.retally.shared.core

/** Small facts about the host the shared layer occasionally needs. */
interface Platform {
    /** "android" or "ios". Used for analytics and the cloud payload. */
    val name: String

    /** Marketing version, e.g. "1.0.0". */
    val appVersion: String

    /** Opens [url] in the system browser. Returns false if nothing could handle it. */
    fun openUrl(url: String): Boolean
}
