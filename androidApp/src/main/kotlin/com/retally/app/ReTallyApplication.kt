/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app

import com.retally.shared.core.crash.CrashReporting
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.app.Application
import com.retally.app.ads.AndroidAdsInitializer
import com.retally.app.ads.CurrentActivityTracker
import com.retally.app.di.viewModelModule
import com.retally.app.location.NearbyStoreScheduler
import com.retally.shared.di.PlatformDependencies
import com.retally.shared.di.androidDatabaseModule
import com.retally.shared.di.initKoin
import com.retally.shared.domain.ads.AdsConfig
import com.retally.shared.domain.cloud.CloudBackend
import com.retally.shared.domain.location.NearbyTestZone
import com.retally.shared.domain.cloud.CloudConfig
import com.retally.shared.platform.AndroidPlatform
import com.russhwolf.settings.SharedPreferencesSettings
import com.retally.shared.core.strings.AndroidStringResolver
import org.koin.android.ext.koin.androidContext

class ReTallyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(CurrentActivityTracker)

        initKoin(
            PlatformDependencies(
                // Marketing version only ("1.0.0"): shown in More and registered with the backend; the
                // build number is a release-pipeline detail the shopper never needs.
                platform = AndroidPlatform(context = this, appVersion = BuildConfig.VERSION_NAME),
                settings = SharedPreferencesSettings(getSharedPreferences("retally", MODE_PRIVATE)),
                adsInitializer = AndroidAdsInitializer(this),
                adsConfig = AdsConfig(
                    enabled = true,
                    // Debug → Google's TEST unit; release → your unit (androidApp/build.gradle.kts → AdMob).
                    homeBannerUnitId = BuildConfig.HOME_BANNER_UNIT_ID,
                    splashBannerUnitId = BuildConfig.HOME_BANNER_UNIT_ID,
                ),
                cloudConfig = CloudConfig(
                    baseUrl = BuildConfig.CLOUD_BASE_URL,
                    apiKey = null,
                    // Receipts back up to Firestore under the (anonymous) Firebase uid.
                    simulate = false,
                    backend = CloudBackend.FIREBASE,
                ),
                verboseNetworkLogging = BuildConfig.DEBUG,
                stringResolver = AndroidStringResolver(this),
            ),
            extraModules = listOf(viewModelModule, androidDatabaseModule),
        ) {
            androidContext(this@ReTallyApplication)
        }

        // Crashlytics: off in debug builds (unless More → "Send test crash" was just used).
        val settings = SharedPreferencesSettings(getSharedPreferences("retally", MODE_PRIVATE))
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled =
            CrashReporting.shouldCollect(settings, BuildConfig.DEBUG)
        CrashReporting.start()

        // Re-register store geofences (no-op when the nearby toggle is off).
        // TEMP-DEBUG (remove before release): fixed "Home Store" geofence for testing.
        if (BuildConfig.DEBUG) NearbyTestZone.address = "15 Hazelwood Drive, Hingham, MA 02043"
        NearbyStoreScheduler.refresh(this)

    }
}
