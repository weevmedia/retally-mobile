/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.platform

import com.retally.shared.core.crash.CrashReporting
import co.touchlab.crashkios.crashlytics.setCrashlyticsUnhandledExceptionHook
import com.retally.shared.core.log.DebugLog
import com.retally.shared.core.Platform
import com.retally.shared.core.strings.IosStringResolver
import com.retally.shared.di.PlatformDependencies
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import com.retally.shared.domain.security.AppCheckTokenProvider
import com.retally.shared.di.initKoin
import com.retally.shared.domain.budget.BudgetReminderScheduler
import com.retally.shared.domain.budget.IosBudgetReminderScheduler
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
import platform.UIKit.UIDevice
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.uname
import platform.posix.utsname
import platform.UIKit.UIView
import org.koin.dsl.module

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
        /** Current Firebase App Check token (see Swift `AppCheckSetup`); completes with nil when none. */
        appCheckToken: ((onToken: (String?) -> Unit) -> Unit)? = null,
        adsEnabled: Boolean = true,
        debug: Boolean = false,
    ) {
        IosAdBannerFactory.create = bannerFactory
        // Shared-module diagnostics ([Nearby], [ReTallyScan]…) only in debug builds.
        DebugLog.enabled = debug
        // Crashlytics (FirebaseApp.configure() already ran in Swift). The hook turns an uncaught
        // Kotlin exception into a Crashlytics crash with the Kotlin stack trace.
        CrashReporting.start()
        setCrashlyticsUnhandledExceptionHook()
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
                appCheck = appCheckToken?.let { CallbackAppCheckTokenProvider(it) } ?: AppCheckTokenProvider.None,
            ),
            extraModules = listOf(
                iosDatabaseModule,
                module { single<BudgetReminderScheduler> { IosBudgetReminderScheduler() } },
            ),
        )
        IosDependencies.showDebugActions = debug
    }
}

internal class IosPlatform : Platform {
    override val name: String = "ios"

    /** Hardware identifier ("iPhone15,2"); the simulator reports its host arch instead. */
    @OptIn(ExperimentalForeignApi::class)
    override val deviceModel: String = memScoped {
        val info = alloc<utsname>()
        uname(info.ptr)
        info.machine.toKString().ifBlank { UIDevice.currentDevice.model }
    }

    override val osVersion: String = UIDevice.currentDevice.systemVersion

    /** Marketing version only ("1.0.0"); the build number stays out of the UI and the backend. */
    override val appVersion: String
        get() = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "1.0.0"

    override fun openUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(nsUrl)) return false
        app.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
        return true
    }
}

/** Bridges Swift's completion-style App Check token fetch into the shared suspend provider. */
private class CallbackAppCheckTokenProvider(
    private val fetch: (onToken: (String?) -> Unit) -> Unit,
) : AppCheckTokenProvider {
    override suspend fun token(): String? = suspendCancellableCoroutine { cont ->
        fetch { token -> if (cont.isActive) cont.resume(token) }
    }
}
