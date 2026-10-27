package com.retally.shared.core.crash

import com.retally.shared.core.Platform
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the Crashlytics custom keys current: the Firebase uid (never the email), guest or
 * signed in, this install's id (matches the admin console's device list) and the app version.
 */
class CrashContext(
    private val auth: AuthRepository,
    private val preferences: AppPreferences,
    private val platform: Platform,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        if (started) return
        started = true
        CrashReporting.setValue("installationId", preferences.installationId)
        CrashReporting.setValue("appVersion", platform.appVersion)
        CrashReporting.setValue("device", "${platform.deviceModel} · ${platform.name} ${platform.osVersion}")
        scope.launch {
            auth.observeAccount().distinctUntilChanged().collect { account ->
                CrashReporting.setValue("uid", account.uid ?: "none")
                CrashReporting.setValue("guest", account.isGuest.toString())
            }
        }
    }
}
