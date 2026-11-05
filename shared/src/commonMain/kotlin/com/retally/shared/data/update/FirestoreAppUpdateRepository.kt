/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.data.update

import com.retally.shared.core.Platform
import com.retally.shared.domain.update.AppUpdateRepository
import com.retally.shared.domain.update.AppVersionConfig
import com.retally.shared.domain.update.UpdateStatus
import com.retally.shared.domain.update.evaluateUpdate
import com.russhwolf.settings.Settings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Reads this platform's [AppVersionConfig] from Firestore `config/appVersion`:
 *
 * ```
 * android: { latest, minSupported, storeUrl, message? }
 * ios:     { latest, minSupported, storeUrl, message? }
 * ```
 *
 * The last config is cached in [Settings] so Home decides instantly and offline, and it is
 * refreshed at most every [REFRESH_MS]. A "Not now" on a soft nudge is remembered per
 * version, so the shopper sees it once per release. Any failure reads as [UpdateStatus.None]:
 * the update check must never keep the app from opening.
 */
class FirestoreAppUpdateRepository(
    private val platform: Platform,
    private val settings: Settings,
    private val json: Json,
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AppUpdateRepository {

    override suspend fun check(force: Boolean): UpdateStatus {
        val cached = cachedConfig()
        val fetchedAt = settings.getLongOrNull(KEY_FETCHED_AT) ?: 0L
        val config = if (!force && cached != null && now() - fetchedAt < REFRESH_MS) cached else fetch() ?: cached
        return when (val status = evaluateUpdate(platform.appVersion, config)) {
            is UpdateStatus.Available ->
                if (settings.getStringOrNull(KEY_DISMISSED) == status.version) UpdateStatus.None else status
            else -> status
        }
    }

    override fun dismiss(version: String) {
        settings.putString(KEY_DISMISSED, version)
    }

    // NonCancellable: the splash stops waiting after a short bound and may be torn down, but the
    // read should still finish and land in the cache so the next launch decides instantly.
    private suspend fun fetch(): AppVersionConfig? = withContext(NonCancellable) {
        fetchNow()
    }

    private suspend fun fetchNow(): AppVersionConfig? = try {
        val doc = firestore.collection("config").document("appVersion").get()
        // Dotted paths read inside the platform's map ("android.latest"); a missing field is "".
        fun field(key: String): String =
            runCatching { doc.get<String?>("${platform.name}.$key") }.getOrNull()?.trim().orEmpty()
        val config = if (doc.exists) {
            AppVersionConfig(
                latest = field("latest"),
                minSupported = field("minSupported"),
                storeUrl = field("storeUrl"),
                message = field("message"),
            ).takeIf { it.latest.isNotEmpty() || it.minSupported.isNotEmpty() }
        } else {
            null
        }
        if (config != null) {
            settings.putString(KEY_CONFIG, json.encodeToString(AppVersionConfig.serializer(), config))
        } else {
            settings.remove(KEY_CONFIG)
        }
        settings.putLong(KEY_FETCHED_AT, now())
        log("config for ${platform.name}: $config")
        config
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("read failed: ${e.message}")
        null
    }

    private fun cachedConfig(): AppVersionConfig? =
        settings.getStringOrNull(KEY_CONFIG)?.let { raw ->
            runCatching { json.decodeFromString(AppVersionConfig.serializer(), raw) }.getOrNull()
        }

    private fun log(message: String) = println("[ReTallyUpdate] $message")

    companion object {
        private const val KEY_CONFIG = "app_update.config.v1"
        private const val KEY_FETCHED_AT = "app_update.fetched_at"
        private const val KEY_DISMISSED = "app_update.dismissed_version"
        private const val REFRESH_MS = 6 * 60 * 60 * 1000L
    }
}
