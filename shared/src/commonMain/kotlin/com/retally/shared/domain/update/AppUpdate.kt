/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.update

import kotlinx.serialization.Serializable

/**
 * What the store says about this platform's app: the newest published version, the oldest
 * one still allowed to run, and where to get the update. Kept in Firestore at
 * `config/appVersion` (one map per platform) so a release — or a kill switch for a broken
 * build — needs no app change.
 */
@Serializable
data class AppVersionConfig(
    /** Newest version on the store, e.g. "1.2.0". */
    val latest: String,
    /** Oldest version that still works with the backend; older builds must update. */
    val minSupported: String,
    /** Store page for this platform (Play / App Store). */
    val storeUrl: String,
    /** Optional one-line "what's new" shown with the nudge. */
    val message: String = "",
)

/** Result of comparing the running app against [AppVersionConfig]. */
sealed interface UpdateStatus {
    data object None : UpdateStatus

    /** A newer build exists; the shopper may dismiss this one. */
    data class Available(val version: String, val message: String, val storeUrl: String) : UpdateStatus

    /** This build is below the minimum: the app is unusable until updated. */
    data class Required(val version: String, val message: String, val storeUrl: String) : UpdateStatus
}

/** Decides [UpdateStatus] for the running [current] version against [config]. */
fun evaluateUpdate(current: String, config: AppVersionConfig?): UpdateStatus {
    if (config == null || config.storeUrl.isBlank()) return UpdateStatus.None
    return when {
        config.minSupported.isNotBlank() && compareVersions(current, config.minSupported) < 0 ->
            UpdateStatus.Required(config.latest.ifBlank { config.minSupported }, config.message, config.storeUrl)
        config.latest.isNotBlank() && compareVersions(current, config.latest) < 0 ->
            UpdateStatus.Available(config.latest, config.message, config.storeUrl)
        else -> UpdateStatus.None
    }
}

/**
 * Numeric dotted comparison ("1.10.0" > "1.9.0"; "1.2" == "1.2.0"). A non-numeric part
 * (a "-debug" suffix, a build label) counts as 0, so "1.0.0-debug" == "1.0.0". Returns a
 * negative number when [a] is older than [b], 0 when equal, positive when newer.
 */
fun compareVersions(a: String, b: String): Int {
    val left = versionParts(a)
    val right = versionParts(b)
    for (i in 0 until maxOf(left.size, right.size)) {
        val diff = (left.getOrNull(i) ?: 0) - (right.getOrNull(i) ?: 0)
        if (diff != 0) return diff
    }
    return 0
}

private fun versionParts(version: String): List<Int> =
    version.trim().substringBefore('-').substringBefore('+').split('.')
        .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

/**
 * Looks up the update status for this platform. Implementations cache the config so Home can
 * decide instantly and offline; a failed read never blocks the app (it reads as [UpdateStatus.None]).
 */
interface AppUpdateRepository {
    /**
     * Current status for the running app. Refreshes the config when the cache is older than a
     * few hours ([force] skips that). [UpdateStatus.Available] is downgraded to
     * [UpdateStatus.None] once the shopper has dismissed that version.
     */
    suspend fun check(force: Boolean = false): UpdateStatus

    /** "Not now" on an [UpdateStatus.Available] nudge: hides it for 7 days, or until a newer version appears. */
    fun dismiss(version: String)
}
