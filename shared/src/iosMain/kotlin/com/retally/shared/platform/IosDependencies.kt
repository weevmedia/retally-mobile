package com.retally.shared.platform

import com.retally.shared.domain.ads.AdsConfig
import com.retally.shared.domain.location.StoreLocationRepository
import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.presentation.home.HomeContract
import com.retally.shared.presentation.main.MainContract
import com.retally.shared.presentation.duplicates.DuplicatesContract
import com.retally.shared.presentation.receipts.ReceiptsContract
import com.retally.shared.presentation.scan.ScanReviewContract
import com.retally.shared.presentation.settings.SettingsContract
import com.retally.shared.presentation.splash.SplashContract
import com.retally.shared.presentation.onboarding.OnboardingContract
import com.retally.shared.presentation.auth.AuthContract
import com.retally.shared.presentation.auth.OtpContract
import org.koin.mp.KoinPlatform

/**
 * Swift's window into the Koin graph (Sidearm's `IosDependencies` shape). Each Swift
 * ViewModel asks for its store here, owns it for the screen's lifetime and calls
 * `store.clear()` in `deinit`. Stores registered as `factory` are fresh per call;
 * [getMainStore] is the app-wide single.
 */
object IosDependencies {
    /** Set from `IosApp.start(debug:)`; surfaces developer affordances in the UI. */
    var showDebugActions: Boolean = false
        internal set

    fun getSplashStore(): SplashContract.Store = KoinPlatform.getKoin().get()
    fun getOnboardingStore(): OnboardingContract.Store = KoinPlatform.getKoin().get()
    fun getAuthStore(): AuthContract.Store = KoinPlatform.getKoin().get()
    fun getOtpStore(): OtpContract.Store = KoinPlatform.getKoin().get()
    fun getMainStore(): MainContract.Store = KoinPlatform.getKoin().get()
    fun getHomeStore(): HomeContract.Store = KoinPlatform.getKoin().get()
    fun getSettingsStore(): SettingsContract.Store = KoinPlatform.getKoin().get()
    fun getScanReviewStore(): ScanReviewContract.Store = KoinPlatform.getKoin().get()
    fun getDuplicatesStore(): DuplicatesContract.Store = KoinPlatform.getKoin().get()
    fun getReceiptsStore(): ReceiptsContract.Store = KoinPlatform.getKoin().get()

    fun getAdsConfig(): AdsConfig = KoinPlatform.getKoin().get()
    fun getPreferences(): AppPreferences = KoinPlatform.getKoin().get()
    fun getStoreLocationRepository(): StoreLocationRepository = KoinPlatform.getKoin().get()
}
