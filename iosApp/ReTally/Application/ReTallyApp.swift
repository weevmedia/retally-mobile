import FirebaseCore
import SwiftUI
import Shared
import UIKit
import UserNotifications

/// Ensures the nearby-store location monitor exists from the earliest launch moment
/// so geofence entries can wake the app even after termination.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        _ = NearbyStoreMonitor.shared
        NearbyStoreMonitor.shared.refresh()
        // Show local-notification banners even while the app is in the foreground
        // (iOS suppresses them by default) — needed for the TEST-LOCATION-PING.
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void,
    ) {
        completionHandler([.banner, .sound])
    }
}

@main
struct ReTallyApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        // Firebase must be configured before any Kotlin code touches Auth/Firestore
        // (GoogleService-Info.plist from the Firebase console lives next to Info.plist).
        FirebaseApp.configure()
        // Boot the shared Kotlin graph exactly once. Everything SDK-specific (Google Mobile
        // Ads, consent) is handed over as closures so the Kotlin framework stays SDK-free.
        IosApp.shared.start(
            initializeAds: { completion in AdsBridge.shared.initialize(completion: completion) },
            bannerFactory: { adUnitId, format, onLoaded in AdsBridge.shared.makeBanner(adUnitId: adUnitId, format: format, onLoaded: onLoaded) },
            homeBannerUnitId: AppConfig.homeBannerUnitId,
            splashBannerUnitId: AppConfig.splashBannerUnitId,
            cloudBaseUrl: AppConfig.cloudBaseUrl,
            cloudApiKey: nil,
            simulateCloud: false,
            adsEnabled: true,
            debug: AppConfig.isDebug
        )
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                // One colour theme (the design system's light palette): keep SwiftUI chrome —
                // tab bar, sheets, alerts — from switching to dark alongside the OS.
                .preferredColorScheme(.light)
        }
    }
}
