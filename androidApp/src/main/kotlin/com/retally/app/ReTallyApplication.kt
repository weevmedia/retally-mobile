package com.retally.app

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
                platform = AndroidPlatform(context = this, appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
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

        // Re-register store geofences (no-op when the nearby toggle is off).
        NearbyStoreScheduler.refresh(this)
    }
}
