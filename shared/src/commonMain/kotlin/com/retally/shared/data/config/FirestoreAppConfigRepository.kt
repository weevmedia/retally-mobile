/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.data.config

import com.retally.shared.core.log.debugLog
import com.retally.shared.domain.config.AppConfig
import com.retally.shared.domain.config.AppConfigRepository
import com.retally.shared.domain.config.AppFeatures
import com.retally.shared.domain.config.AppLinks
import com.russhwolf.settings.Settings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Reads Firestore `config/app` (public, edited from the admin console):
 *
 * ```
 * links:    { howItWorks, terms, privacy, support }   "" = built-in default
 * features: { ads, recalls }                          missing = on
 * ```
 *
 * The last copy is cached in [Settings] so links and switches apply from the first frame and
 * offline. A refresh starts at launch and again whenever a screen asks and the copy is older
 * than [REFRESH_MS]. A failed read keeps what we have.
 */
class FirestoreAppConfigRepository(
    private val settings: Settings,
    private val json: Json,
    private val firestore: () -> FirebaseFirestore = { Firebase.firestore },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AppConfigRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _config = MutableStateFlow(cached() ?: AppConfig())
    override val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        scope.launch { refresh(force = true) }
    }

    override suspend fun refresh(force: Boolean) {
        mutex.withLock {
            val fetchedAt = settings.getLongOrNull(KEY_FETCHED_AT) ?: 0L
            if (!force && now() - fetchedAt < REFRESH_MS) return
            fetch()?.let { fresh ->
                _config.value = fresh
                settings.putString(KEY_CONFIG, json.encodeToString(AppConfig.serializer(), fresh))
                settings.putLong(KEY_FETCHED_AT, now())
            }
        }
    }

    private suspend fun fetch(): AppConfig? = try {
        val doc = firestore().collection("config").document("app").get()
        fun link(key: String, fallback: String): String =
            runCatching { doc.get<String?>("links.$key") }.getOrNull()?.trim()
                ?.takeIf { it.startsWith("https://") } ?: fallback
        fun feature(key: String): Boolean =
            runCatching { doc.get<Boolean?>("features.$key") }.getOrNull() != false
        val defaults = AppLinks()
        val config = if (doc.exists) {
            AppConfig(
                links = AppLinks(
                    privacyPolicy = link("privacy", defaults.privacyPolicy),
                    terms = link("terms", defaults.terms),
                    support = link("support", defaults.support),
                    howItWorks = link("howItWorks", defaults.howItWorks),
                ),
                features = AppFeatures(ads = feature("ads"), recalls = feature("recalls")),
            )
        } else {
            AppConfig()
        }
        debugLog("ReTallyConfig") { "config: $config" }
        config
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        debugLog("ReTallyConfig") { "read failed: ${e.message}" }
        null
    }

    private fun cached(): AppConfig? =
        settings.getStringOrNull(KEY_CONFIG)?.let { raw ->
            runCatching { json.decodeFromString(AppConfig.serializer(), raw) }.getOrNull()
        }

    companion object {
        private const val KEY_CONFIG = "app_config.v1"
        private const val KEY_FETCHED_AT = "app_config.fetched_at"
        private const val REFRESH_MS = 15 * 60 * 1000L
    }
}
