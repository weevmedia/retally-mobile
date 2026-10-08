package com.retally.shared.platform

import com.retally.shared.core.Platform
import com.retally.shared.core.strings.IosStringResolver
import com.retally.shared.di.PlatformDependencies
import com.retally.shared.di.initKoin
import com.retally.shared.di.iosDatabaseModule
import com.retally.shared.domain.ads.AdsConfig
import com.retally.shared.domain.ads.CallbackAdsInitializer
import com.retally.shared.domain.cloud.CloudConfig
import com.retally.shared.ui.components.AdFormat
import com.retally.shared.ui.components.IosAdBannerFactory
import com.russhwolf.settings.NSUserDefaultsSettings
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIView

/**
 * Swift-facing bootstrap. `@main App` calls [start] once; Swift ViewModels then pull stores
 * from [IosDependencies] and host screens built by [IosScreens].
 *
 * Everything the SDK-heavy Swift side owns is handed over as plain closures so the Kotlin
 * framework never links against Google Mobile Ads directly.
 */
object IosApp {

    /**
     * @param initializeAds Runs UMP consent + `MobileAds.shared.start`, then calls the
     *   completion with `true` when banners may load. Called once from the splash.
     * @param bannerFactory Builds a loaded `GADBannerView` for a unit id + format, or nil to hide.
     * @param cloudBaseUrl Backend URL; empty keeps cloud sync in placeholder mode.
     * @param simulateCloud Debug: fake a successful upload instead of hitting the network.
     * @param adsEnabled Kill switch for every ad slot.
     * @param debug Passes `#if DEBUG` through so Kotlin can log bodies / show dev actions.
     */
    fun start(
        initializeAds: (onComplete: (Boolean) -> Unit) -> Unit,
        bannerFactory: (adUnitId: String, format: AdFormat, onLoaded: (Boolean) -> Unit) -> UIView?,
        homeBannerUnitId: String = AdsConfig.IOS_TEST_BANNER,
        splashBannerUnitId: String = AdsConfig.IOS_TEST_BANNER,
        cloudBaseUrl: String = "",
        cloudApiKey: String? = null,
        simulateCloud: Boolean = false,
        adsEnabled: Boolean = true,
        debug: Boolean = false,
    ) {
        IosAdBannerFactory.create = bannerFactory
        initKoin(
            PlatformDependencies(
                platform = IosPlatform(),
                settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults),
                adsInitializer = CallbackAdsInitializer(initializeAds),
                adsConfig = AdsConfig(
                    enabled = adsEnabled,
                    homeBannerUnitId = homeBannerUnitId,
                    splashBannerUnitId = splashBannerUnitId,
                ),
                cloudConfig = CloudConfig(baseUrl = cloudBaseUrl, apiKey = cloudApiKey, simulate = simulateCloud),
                verboseNetworkLogging = debug,
                stringResolver = IosStringResolver,
            ),
            extraModules = listOf(iosDatabaseModule),
        )
        IosDependencies.showDebugActions = debug
    }
}

internal class IosPlatform : Platform {
    override val name: String = "ios"

    override val appVersion: String
        get() {
            val info = NSBundle.mainBundle.infoDictionary
            val short = info?.get("CFBundleShortVersionString") as? String ?: "1.0"
            val build = info?.get("CFBundleVersion") as? String
            return if (build != null) "$short ($build)" else short
        }

    override fun openUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(nsUrl)) return false
        app.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
        return true
    }
}
