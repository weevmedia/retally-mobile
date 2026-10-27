package com.retally.shared.core.crash

import co.touchlab.crashkios.crashlytics.CrashlyticsKotlin
import co.touchlab.crashkios.crashlytics.enableCrashlytics
import com.russhwolf.settings.Settings

/**
 * One door to Firebase Crashlytics for the shared code (CrashKiOS underneath, so Kotlin
 * exceptions reach Crashlytics with real Kotlin stack traces on iOS too).
 *
 * Collection is OFF in debug builds — test crashes would bury real ones — except right after
 * More → "Send test crash", which flips [KEY_COLLECT_IN_DEBUG] so the next launch uploads it.
 * Each host reads [shouldCollect] and switches the native SDK's collection on or off.
 */
object CrashReporting {
    /** Also read by the iOS host straight from NSUserDefaults — keep the key in sync. */
    const val KEY_COLLECT_IN_DEBUG = "crash.collectInDebug"

    private var started = false

    /** Call once at app start, after Firebase is configured. */
    fun start() {
        if (started) return
        started = true
        enableCrashlytics()
    }

    fun shouldCollect(settings: Settings, isDebug: Boolean): Boolean =
        !isDebug || settings.getBoolean(KEY_COLLECT_IN_DEBUG, false)

    /** A caught problem worth seeing in the dashboard (shows as a non-fatal). */
    fun record(error: Throwable) {
        if (started) CrashlyticsKotlin.sendHandledException(error)
    }

    /** Breadcrumb attached to the next crash / non-fatal. */
    fun log(message: String) {
        if (started) CrashlyticsKotlin.logMessage(message)
    }

    /** Context shown on every report (screen, account, install…). */
    fun setValue(key: String, value: String) {
        if (started) CrashlyticsKotlin.setCustomValue(key, value)
    }

    /** Debug "Send test crash": lets this debug install upload, then crashes on purpose. */
    fun crashForTest(settings: Settings): Nothing {
        settings.putBoolean(KEY_COLLECT_IN_DEBUG, true)
        throw IllegalStateException("ReTally test crash (More → Send test crash)")
    }
}
