package com.retally.shared.domain.ads

/**
 * AdMob configuration. The defaults are Google's **public test IDs** — they always fill with
 * a clearly-labelled test creative and are safe in debug. Release builds must inject real
 * unit ids from the AdMob console (see README → "Before you ship").
 */
data class AdsConfig(
    val enabled: Boolean = true,
    val homeBannerUnitId: String,
    val splashBannerUnitId: String,
) {
    companion object {
        const val ANDROID_TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
        const val IOS_TEST_BANNER = "ca-app-pub-3940256099942544/2435281174"
    }
}

/**
 * Lifecycle hooks the platform implements over the Google Mobile Ads SDK. Kept behind an
 * interface so the shared stores (and their unit tests) never touch the SDK.
 */
interface AdsInitializer {
    /**
     * Gathers consent (UMP) and initializes the SDK. Suspends until ads may be requested or
     * until the platform decides it cannot (no network, consent form failed) — callers treat
     * both the same: proceed into the app, the banner simply stays hidden.
     */
    suspend fun initialize(): AdsReadiness
}

enum class AdsReadiness { READY, UNAVAILABLE }
