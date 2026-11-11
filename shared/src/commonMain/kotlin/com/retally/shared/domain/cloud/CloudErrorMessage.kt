/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.cloud

import com.retally.shared.core.strings.StringAssetIdentifiers

/**
 * Maps a raw backup / restore error (Firebase status text such as "PERMISSION_DENIED: Missing or
 * insufficient permissions.") to the string id of a friendly reason. The raw text stays in
 * logcat / Crashlytics; shoppers never see it.
 */
fun cloudErrorStringId(rawMessage: String?): String {
    val m = rawMessage?.lowercase().orEmpty()
    return when {
        listOf("unavailable", "network", "offline", "timeout", "timed out", "deadline", "unable to resolve host", "failed to connect")
            .any { it in m } -> StringAssetIdentifiers.CLOUD_ERROR_OFFLINE
        "unauthenticated" in m || "not signed in" in m || "token" in m -> StringAssetIdentifiers.CLOUD_ERROR_SIGN_IN
        "permission" in m || "denied" in m || "invalid" in m -> StringAssetIdentifiers.CLOUD_ERROR_REJECTED
        else -> StringAssetIdentifiers.CLOUD_ERROR_GENERIC
    }
}
