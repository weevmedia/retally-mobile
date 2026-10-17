import Foundation

/// Per-build configuration. Real AdMob ids and the backend URL belong here (or in an
/// .xcconfig) — never in the shared Kotlin code.
enum AppConfig {
    static var isDebug: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    // MARK: AdMob
    // Paste from https://apps.admob.com → Apps → ReTally (iOS) → Ad units. The App ID (the
    // "~" one) goes in Info.plist under GADApplicationIdentifier.
    // Google's current documented iOS demo banner unit. The older 2934735716 unit that was
    // here appears to no longer fill; the Android side's classic demo unit still does, which
    // is why test ads showed on Android but not iOS.
    private static let testBannerUnitId = "ca-app-pub-3940256099942544/2435281174"
    private static let releaseHomeBannerUnitId = "ca-app-pub-3023631555197048/7212690817"

    /// Both debug and release request the production unit (per request).
    /// `AdsBridge` registers the simulator and `testDeviceIdentifiers`, so the real unit
    /// serves creatives labelled "Test Ad" on those devices instead of counting as live
    /// impressions.
    static var homeBannerUnitId: String {
        return releaseHomeBannerUnitId.hasPrefix("ca-app-pub-") ? releaseHomeBannerUnitId : testBannerUnitId
    }
    static var splashBannerUnitId: String { homeBannerUnitId }

    /// Hashed ids of physical dev phones. On first ad request the SDK prints
    /// "To get test ads on this device, set: ... testDeviceIdentifiers = [ "<hash>" ]" —
    /// paste that hash here. Simulators are added automatically (see AdsBridge).
    /// Note: the hash resets on uninstall/reinstall — update it when it changes.
    static let testDeviceIdentifiers: [String] = ["d482edfbedb920fcb34374a8b450baf3"]

    /// Empty keeps cloud backup in placeholder mode.
    static let cloudBaseUrl = ""
}
