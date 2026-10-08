import Foundation
import GoogleMobileAds
import Shared
import UIKit
import UserMessagingPlatform

/// Owns every call into the Google Mobile Ads / UMP SDKs. Kotlin talks to it only through the
/// two closures handed to `IosApp.start`.
final class AdsBridge: NSObject, BannerViewDelegate {
    static let shared = AdsBridge()

    private var isInitialized = false

    private override init() {
        super.init()
    }

    private func log(_ message: String) {
        #if DEBUG
        print("[ReTallyAds] \(message)")
        #endif
    }

    // MARK: Consent + SDK start

    /// Consent-first bootstrap. Resolves `true` when banners may be requested. Any failure
    /// resolves `false` so the splash never blocks on an ad network.
    /// The closure type mirrors Kotlin's `(Boolean) -> Unit` as exported to Swift.
    func initialize(completion: @escaping (KotlinBoolean) -> KotlinUnit) {
        let startedAt = Date()
        let finish: (Bool) -> Void = { [weak self] ready in
            self?.log("initialize finished ready=\(ready) after \(String(format: "%.1f", Date().timeIntervalSince(startedAt)))s")
            DispatchQueue.main.async { _ = completion(KotlinBoolean(bool: ready)) }
        }
        log("initialize: requesting consent info")

        let parameters = RequestParameters()
        parameters.isTaggedForUnderAgeOfConsent = false

        ConsentInformation.shared.requestConsentInfoUpdate(with: parameters) { [weak self] updateError in
            guard let self else { return finish(false) }
            if let updateError {
                // Offline or misconfigured: fall back to what the SDK already knows.
                self.log("consent update failed: \(updateError.localizedDescription)")
                return self.startSdkIfAllowed(finish)
            }
            self.log("consent status=\(ConsentInformation.shared.consentStatus.rawValue) canRequestAds=\(ConsentInformation.shared.canRequestAds)")
            guard let host = Self.topViewController() else {
                return self.startSdkIfAllowed(finish)
            }
            ConsentForm.loadAndPresentIfRequired(from: host) { _ in
                self.startSdkIfAllowed(finish)
            }
        }
    }

    /// Test ads need no consent. Until a UMP privacy message is published in the AdMob console
    /// for the real app id, `canRequestAds` stays false and no banner would ever show; debug
    /// builds bypass that gate. Release builds always honour consent.
    private var allowWithoutConsent: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    private func startSdkIfAllowed(_ finish: @escaping (Bool) -> Void) {
        guard ConsentInformation.shared.canRequestAds || allowWithoutConsent else {
            log("consent not granted and no debug bypass; ads stay hidden")
            return finish(false)
        }
        if isInitialized { return finish(true) }
        isInitialized = true
        #if DEBUG
        // Real unit ids + registered test devices: creatives are served labelled "Test Ad" and
        // never count as live impressions. Simulators are test devices automatically; physical
        // phones are listed in AppConfig.testDeviceIdentifiers.
        MobileAds.shared.requestConfiguration.testDeviceIdentifiers = AppConfig.testDeviceIdentifiers
        log("test devices: simulator (automatic) + \(AppConfig.testDeviceIdentifiers.count) phone(s); " +
            "a physical phone prints its hash on the first request — add it to AppConfig.testDeviceIdentifiers")
        #endif
        log("MobileAds.start")
        MobileAds.shared.start { [weak self] status in
            let adapters = status.adapterStatusesByClassName.map { "\($0.key)=\($0.value.state.rawValue)" }
            self?.log("MobileAds started; adapters: \(adapters.joined(separator: ", "))")
            finish(true)
        }
    }

    // MARK: Banner

    /// Builds a banner of the requested format (320×50 or 300×250) already loading for
    /// `adUnitId`. Kotlin embeds it via `UIKitView`; a nil return hides the slot.
    /// Per-banner load callbacks; the Kotlin side keeps the view out of the layout until one fires.
    private var loadCallbacks: [ObjectIdentifier: (Bool) -> Void] = [:]

    func makeBanner(adUnitId: String, format: Shared.AdFormat, onLoaded: @escaping (KotlinBoolean) -> KotlinUnit) -> UIView? {
        // The SDK initializes itself on the first ad request if the splash's start was still
        // in flight, so a slow start no longer leaves the slot blank for the whole session.
        if !isInitialized { log("makeBanner before MobileAds.start completed; loading anyway") }
        let adSize: GoogleMobileAds.AdSize = format == Shared.AdFormat.mediumRectangle ? AdSizeMediumRectangle : AdSizeBanner
        let banner = BannerView(adSize: adSize)
        banner.adUnitID = adUnitId
        banner.rootViewController = Self.topViewController()
        banner.delegate = self
        loadCallbacks[ObjectIdentifier(banner)] = { ok in _ = onLoaded(KotlinBoolean(bool: ok)) }
        banner.translatesAutoresizingMaskIntoConstraints = true
        // Compose draws the card behind the ad; keep the view itself transparent so nothing
        // shows through as black before the creative has rendered.
        banner.backgroundColor = .clear
        banner.isOpaque = false
        log("makeBanner unit=\(adUnitId) format=\(format) rootVC=\(banner.rootViewController.map { String(describing: type(of: $0)) } ?? "nil")")
        banner.load(Request())
        return banner
    }

    // MARK: BannerViewDelegate

    func bannerViewDidReceiveAd(_ bannerView: BannerView) {
        log("banner loaded unit=\(bannerView.adUnitID ?? "?")")
        loadCallbacks[ObjectIdentifier(bannerView)]?(true)
    }

    func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
        log("banner failed: \(error.localizedDescription)")
        loadCallbacks[ObjectIdentifier(bannerView)]?(false)
    }

    // MARK: Helpers

    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap(\.windows).first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
