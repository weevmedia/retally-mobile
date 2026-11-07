/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.security

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Release: Play Integrity attests the installed APK against the Play-signed build. */
object AppCheckProvider {
    fun factory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
