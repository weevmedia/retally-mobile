package com.retally.shared.domain.preferences

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Preferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Master switch for the cloud backup feature. On by default for signed-in users;
     * anonymous users are always local-only (enforced at upload, regardless of this flag).
     */
    val cloudBackupEnabled: Boolean = true,
    /** When true, receipts are flagged when the same item appears twice. Core feature. */
    val highlightDuplicates: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** Epoch millis of the last successful sync, or null. */
    val lastSyncedAtMillis: Long? = null,
) {
    val lastSyncedAt: Instant? get() = lastSyncedAtMillis?.let(Instant::fromEpochMilliseconds)
}

interface AppPreferences {
    fun observe(): Flow<Preferences>
    val current: Preferences
    fun update(block: (Preferences) -> Preferences)

    /** Stable, random, installation-scoped id. Regenerated only on reinstall. */
    val installationId: String

    /** True until the splash has completed once — used to decide if onboarding hints show. */
    var hasLaunchedBefore: Boolean
}
