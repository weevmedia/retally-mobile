/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.security

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug: Play Integrity can't attest a sideloaded / Studio build, so the debug provider mints
 * a token from a secret it prints to Logcat on first run ("Enter this debug secret into the
 * allow list in the Firebase Console for your project"). Register it once per device.
 */
object AppCheckProvider {
    fun factory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
