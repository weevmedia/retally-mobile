/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.data.device

import com.retally.shared.core.Platform
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.preferences.AppPreferences
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class RegisterDeviceCall(val data: RegisterDeviceRequest)

@Serializable
private data class RegisterDeviceRequest(
    val installationId: String,
    val platform: String,
    val model: String,
    val osVersion: String,
    val appVersion: String,
)

/**
 * Tells the backend which device this install is, so the admin console can list a user's
 * devices (`users/{uid}/devices/{installationId}`, written by the `registerDevice` function).
 *
 * Runs for guests and signed-in users alike. Sends on every sign-in / account switch and
 * otherwise at most once a day, or sooner when the app or OS version changes. Best-effort:
 * failures are logged and retried on the next launch.
 */
class DeviceRegistrar(
    private val http: HttpClient,
    private val auth: AuthRepository,
    private val platform: Platform,
    private val preferences: AppPreferences,
    private val settings: Settings,
    private val functionUrl: String = DEFAULT_URL,
    private val clock: Clock = Clock.System,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    /** Idempotent; call once at app start. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            auth.observeUid().filterNotNull().distinctUntilChanged().collect { uid -> registerIfDue(uid) }
        }
    }

    private suspend fun registerIfDue(uid: String) {
        val day = clock.now().toEpochMilliseconds() / DAY_MS
        val stamp = "$uid|${platform.appVersion}|${platform.osVersion}|$day"
        if (settings.getStringOrNull(KEY_LAST) == stamp) return
        val token = try {
            auth.getIdToken(forceRefresh = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return
        try {
            val response = http.post(functionUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterDeviceCall(
                        RegisterDeviceRequest(
                            installationId = preferences.installationId,
                            platform = platform.name,
                            model = platform.deviceModel.take(80),
                            osVersion = platform.osVersion.take(40),
                            appVersion = platform.appVersion.take(40),
                        ),
                    ),
                )
            }
            if (response.status.isSuccess()) {
                settings.putString(KEY_LAST, stamp)
            } else {
                log("HTTP ${response.status.value}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("request failed: ${e.message}")
        }
    }

    private fun log(message: String) = println("[ReTallyDevices] $message")

    companion object {
        const val DEFAULT_URL = "https://us-east1-retally-c5212.cloudfunctions.net/registerDevice"
        private const val KEY_LAST = "device_registered_stamp"
        private const val DAY_MS = 86_400_000L
    }
}
