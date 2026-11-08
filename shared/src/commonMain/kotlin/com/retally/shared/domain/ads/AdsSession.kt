/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.retally.shared.domain.config.AppConfigRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * App-lifetime owner of the ads bootstrap. Consent + SDK start can take well over ten
 * seconds on a cold iOS launch, so nothing waits on it: the splash kicks it off and moves on,
 * and [MainContract.Store] observes [adsEnabled] so banners appear whenever the SDK is ready.
 */
class AdsSession(
    private val initializer: AdsInitializer,
    private val config: AdsConfig,
    /** Admin console kill switch (`config/app.features.ads`); null = always allowed. */
    private val appConfig: AppConfigRepository? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _readiness = MutableStateFlow<AdsReadiness?>(null)

    /** `null` until the platform has answered. */
    val readiness: StateFlow<AdsReadiness?> = _readiness.asStateFlow()

    /** The admin console has not switched ads off. */
    val remoteAdsAllowed: Boolean get() = appConfig?.config?.value?.features?.ads ?: true

    /**
     * True only once the SDK reported ready, ads are switched on in [AdsConfig] and the admin
     * console has not switched them off (which hides every slot live).
     */
    val adsEnabled: Flow<Boolean> = combine(
        readiness,
        appConfig?.config?.map { it.features.ads } ?: flowOf(true),
    ) { ready, allowed -> ready == AdsReadiness.READY && config.enabled && allowed }

    /** Idempotent: starts consent + SDK init once per process. */
    fun ensureStarted() {
        if (job != null || !config.enabled) {
            if (!config.enabled) _readiness.value = AdsReadiness.UNAVAILABLE
            return
        }
        job = scope.launch {
            _readiness.value = runCatching { initializer.initialize() }.getOrDefault(AdsReadiness.UNAVAILABLE)
        }
    }

    /** Waits up to [timeoutMs] for the first answer; `null` means still initializing. */
    suspend fun awaitReadiness(timeoutMs: Long): AdsReadiness? =
        withTimeoutOrNull(timeoutMs) { readiness.filterNotNull().first() }
}
