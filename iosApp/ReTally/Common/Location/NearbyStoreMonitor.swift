//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import CoreLocation
import Foundation
import Shared
import UIKit
import UserNotifications

/// Nearby-store reminders: one `CLCircularRegion` per store from receipt history
/// (entry only), firing a local notification — no server, no push certificates.
/// The singleton must exist from `application(didFinishLaunchingWithOptions:)` so
/// region events can wake the app after termination.
/// How the location prompts ended. `pending` and `interrupted` are not final: the caller
/// keeps its screen up and may still receive `always` later.
enum LocationPermissionOutcome {
    /// "Always": geofences can wake the app.
    case always
    /// Always is out of reach: declined ("Keep Only While Using"), or iOS will not ask again.
    case whenInUseOnly
    /// Location denied or restricted.
    case denied
    /// The Always prompt may still be on screen with no answer yet.
    case pending
    /// A prompt disappeared without an answer (e.g. the device slept).
    case interrupted

    var isFinal: Bool {
        switch self {
        case .always, .whenInUseOnly, .denied: return true
        case .pending, .interrupted: return false
        }
    }
}

final class NearbyStoreMonitor: NSObject, CLLocationManagerDelegate {
    static let shared = NearbyStoreMonitor()

    private let manager = CLLocationManager()
    private let center = UNUserNotificationCenter.current()

    /// Listener for the running location request; kept after `pending` / `interrupted` so a
    /// late answer still reaches the screen, cleared on a final outcome.
    private var listener: ((LocationPermissionOutcome) -> Void)?
    /// True between requestWhenInUseAuthorization() and its answer.
    private var whenInUseRequested = false
    /// True between requestAlwaysAuthorization() and its answer.
    private var upgradeRequested = false
    /// The app went inactive after our Always request, i.e. iOS put the prompt on screen.
    private var upgradePromptShown = false
    /// Until when an "Always" that arrives after we stopped waiting still turns alerts on.
    private var lateAlwaysUntil = Date.distantPast

    /// iOS shows "Change to Always Allow?" once per install. Once we have asked, a later
    /// request that shows nothing means "the answer stands", not "still waiting".
    private static let alwaysAskedKey = "nearby.alwaysUpgradeAsked"
    private var alwaysAskedBefore: Bool {
        get { UserDefaults.standard.bool(forKey: Self.alwaysAskedKey) }
        set { UserDefaults.standard.set(newValue, forKey: Self.alwaysAskedKey) }
    }

    private override init() {
        super.init()
        manager.delegate = self
        NotificationCenter.default.addObserver(
            self, selector: #selector(appWillResignActive),
            name: UIApplication.willResignActiveNotification, object: nil
        )
        NotificationCenter.default.addObserver(
            self, selector: #selector(appDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification, object: nil
        )
    }

    // MARK: - Location permission (Store Alerts page and the More toggle)

    /// Asks for location in two steps — While Using, then the Always upgrade — and reports
    /// how it ended. Notifications are NOT asked here: they are requested when Home loads,
    /// so nothing can cover the Always prompt.
    func requestLocationPermission(onOutcome: @escaping (LocationPermissionOutcome) -> Void) {
        listener = onOutcome
        whenInUseRequested = false
        upgradeRequested = false
        upgradePromptShown = false
        switch manager.authorizationStatus {
        case .authorizedAlways:
            report(.always)
        case .denied, .restricted:
            report(.denied)
        case .authorizedWhenInUse:
            requestAlwaysUpgrade()
        default:
            whenInUseRequested = true
            manager.requestWhenInUseAuthorization()
        }
    }

    private func requestAlwaysUpgrade() {
        let askedBefore = alwaysAskedBefore
        alwaysAskedBefore = true
        upgradeRequested = true
        upgradePromptShown = false
        lateAlwaysUntil = Date().addingTimeInterval(120)
        manager.requestAlwaysAuthorization()
        // iOS gives no callback when it shows nothing (already asked, or after "Allow Once").
        // The prompt can take a second or two to appear after the first one closes, so wait
        // long enough for it; if it never shows, that is the answer — move on rather than
        // leaving the user to tap Enable again. A late "Always" is still picked up below.
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) { [weak self] in
            guard let self, self.upgradeRequested, !self.upgradePromptShown else { return }
            nearbyLog("ios: Always prompt not shown (asked before: \(askedBefore)) — continuing with While Using")
            self.upgradeRequested = false
            self.report(.whenInUseOnly)
        }
    }

    @objc private func appWillResignActive() {
        if upgradeRequested { upgradePromptShown = true }
    }

    @objc private func appDidBecomeActive() {
        // Give a real answer a beat to arrive through locationManagerDidChangeAuthorization.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.checkUnansweredPrompt()
        }
    }

    /// Back in front with no answer: work out what the silence means.
    private func checkUnansweredPrompt() {
        // A prompt still on screen keeps the app inactive: nothing to decide yet.
        guard listener != nil, UIApplication.shared.applicationState == .active else { return }
        if whenInUseRequested, manager.authorizationStatus == .notDetermined {
            // The first prompt vanished unanswered (device slept / app switched away).
            whenInUseRequested = false
            nearbyLog("ios: location prompt dismissed without an answer")
            report(.interrupted)
        } else if upgradeRequested, upgradePromptShown, manager.authorizationStatus == .authorizedWhenInUse {
            // The Always prompt closed and nothing changed: "Keep Only While Using".
            upgradeRequested = false
            upgradePromptShown = false
            report(.whenInUseOnly)
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        nearbyLog("ios: location permission is now \(Self.describe(status))")
        guard listener != nil else {
            if status == .authorizedAlways, Date() < lateAlwaysUntil {
                // "Change to Always Allow" tapped after the screen had already moved on:
                // turn alerts on now so the answer is not lost.
                nearbyLog("ios: Always granted after the flow ended — enabling alerts")
                lateAlwaysUntil = .distantPast
                IosDependencies.shared.getStoreLocationRepository().setEnabled(enabled: true)
            }
            // Changed in Settings, not from our prompt: re-sync monitoring.
            refresh()
            return
        }
        switch status {
        case .authorizedAlways:
            whenInUseRequested = false
            upgradeRequested = false
            report(.always)
        case .denied, .restricted:
            whenInUseRequested = false
            upgradeRequested = false
            report(.denied)
        case .authorizedWhenInUse:
            if whenInUseRequested {
                // While Using just granted: straight on to the Always upgrade.
                whenInUseRequested = false
                requestAlwaysUpgrade()
            } else if upgradeRequested {
                upgradeRequested = false
                report(.whenInUseOnly)
            }
        default:
            break // .notDetermined: the prompt is still up.
        }
    }

    private func report(_ outcome: LocationPermissionOutcome) {
        let current = listener
        if outcome.isFinal { listener = nil }
        DispatchQueue.main.async { current?(outcome) }
    }

    // MARK: - Notifications (asked when Home loads)

    /// Shows the notification prompt if it has never been answered. Called when Home loads,
    /// after any location prompts are done.
    func requestNotificationsIfNeeded() {
        center.getNotificationSettings { [weak self] settings in
            guard settings.authorizationStatus == .notDetermined else { return }
            DispatchQueue.main.async {
                self?.center.requestAuthorization(options: [.alert, .sound]) { _, _ in }
            }
        }
    }

    /// Whether everything store alerts need is on (Always + notifications), without prompting.
    func hasAllPermissions(completion: @escaping (Bool) -> Void) {
        let always = manager.authorizationStatus == .authorizedAlways
        center.getNotificationSettings { settings in
            let notifications = settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional
            DispatchQueue.main.async { completion(always && notifications) }
        }
    }

    /// Notification permission for the More toggle: asks if never asked, else reads the answer.
    func requestNotifications(completion: @escaping (Bool) -> Void) {
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            DispatchQueue.main.async { completion(granted) }
        }
    }

    // MARK: - Region sync (launch, toggle change)

    /// Re-syncs the store zones. [checkCurrentState] asks iOS whether we are already inside
    /// each zone (and alerts if so) — right at launch, that is the point; right after a scan
    /// it is not: you are obviously at that store, so the alert popped after every scan.
    func refresh(checkCurrentState: Bool = true) {
        let repo = IosDependencies.shared.getStoreLocationRepository()
        guard repo.isEnabled else {
            nearbyLog("ios: Nearby is OFF in More — zones removed")
            stopAll()
            return
        }
        let status = manager.authorizationStatus
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            nearbyLog("ios: location permission \(Self.describe(status)) — zones removed")
            stopAll() // permission turned off in Settings: drop the regions too
            return
        }
        if status != .authorizedAlways {
            nearbyLog("ios: permission is 'While Using' — zones won't wake the app when it's closed (needs 'Always')")
        }
        nearbyLog("ios: refresh started (\(manager.monitoredRegions.count) zones currently monitored)")
        repo.refreshLocations { [weak self] _ in
            guard let self else { return }
            let locations = (repo.locations.value as? [StoreLocation]) ?? []
            self.syncRegions(locations: locations, checkCurrentState: checkCurrentState)
        }
    }

    private func syncRegions(locations: [StoreLocation], checkCurrentState: Bool) {
        nearbyLog("ios: registering \(locations.count) zones: \(locations.map { $0.storeName }.joined(separator: ", "))")
        let wanted = Set(locations.map { $0.id })
        for region in manager.monitoredRegions where !wanted.contains(region.identifier) {
            manager.stopMonitoring(for: region)
        }
        // iOS caps region monitoring at 20 — the repository already enforces this.
        for loc in locations {
            let region = CLCircularRegion(
                center: CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude),
                radius: loc.radiusMeters,
                identifier: loc.id,
            )
            region.notifyOnEntry = true
            region.notifyOnExit = false
            manager.startMonitoring(for: region)
            // didEnterRegion only fires on a crossing — opening the app while already standing
            // in the zone gives nothing. Ask for the current state; .inside alerts below.
            if checkCurrentState { manager.requestState(for: region) }
        }
    }

    private func stopAll() {
        nearbyLog("ios: removing all \(manager.monitoredRegions.count) zones")
        for region in manager.monitoredRegions {
            manager.stopMonitoring(for: region)
        }
    }

    // MARK: - Region events

    func locationManager(_ manager: CLLocationManager, didStartMonitoringFor region: CLRegion) {
        nearbyLog("ios: monitoring zone \(region.identifier)")
    }

    func locationManager(_ manager: CLLocationManager, didDetermineState state: CLRegionState, for region: CLRegion) {
        let label = state == .inside ? "inside" : state == .outside ? "outside" : "unknown"
        nearbyLog("ios: zone \(region.identifier) state = \(label)")
        // Also called right after didEnterRegion; the shared repository drops the duplicate.
        if state == .inside { handleEntry(region) }
    }

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        nearbyLog("ios: zone entered \(region.identifier) (app \(UIApplication.shared.applicationState == .background ? "in background" : "active"))")
        handleEntry(region)
    }

    private func handleEntry(_ region: CLRegion) {
        let repo = IosDependencies.shared.getStoreLocationRepository()
        // Toggle off or location permission gone: never notify, and stop monitoring.
        let status = manager.authorizationStatus
        guard repo.isEnabled, status == .authorizedAlways || status == .authorizedWhenInUse else {
            nearbyLog("ios: entry ignored — Nearby off or permission \(Self.describe(status))")
            stopAll()
            return
        }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        // Cooldown + duplicate check live in the shared repository.
        if repo.shouldNotifyAndMark(locationId: region.identifier, nowEpochMillis: nowMs) {
            let name = repo.locationName(locationId: region.identifier) ?? ""
            postNotification(storeName: name)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Region monitoring failures (e.g. no network for a fix) — the OS retries.
        nearbyLog("ios: location error: \(error.localizedDescription)")
    }

    func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?, withError error: Error) {
        nearbyLog("ios: monitoring FAILED for \(region?.identifier ?? "?"): \(error.localizedDescription)")
    }

    // MARK: - Local notification

    /// userInfo key carrying the store name to open the Shopping List on.
    static let shoppingStoreKey = "shoppingStore"

    private func postNotification(storeName: String) {
        let content = UNMutableNotificationContent()
        // The shared string uses %1$s (Java/Compose style). Apple's
        // String(format:) treats %s as a C string and garbles Swift strings
        // ("You're near aÙú"), so substitute directly instead of formatting.
        content.title = NSLocalizedString("nearby_notify_title", comment: "")
            .replacingOccurrences(of: "%1$s", with: storeName)
        // Real items from the cached AI shopping list when there is one (works offline and
        // after a background relaunch); otherwise the generic "check your list" copy.
        content.body = IosDependencies.shared.shoppingListSummary(storeName: storeName)
            ?? NSLocalizedString("nearby_notify_body", comment: "")
        // Tapping opens the Shopping List on this store (see AppDelegate.didReceive).
        content.userInfo = [NearbyStoreMonitor.shoppingStoreKey: storeName]
        content.sound = .default // system notification sound
        let request = UNNotificationRequest(
            identifier: "nearby-\(storeName)-\(Date().timeIntervalSince1970)",
            content: content,
            trigger: nil, // deliver immediately
        )
        nearbyLog("ios: posting alert for \"\(storeName)\"")
        center.add(request) { error in
            if let error { nearbyLog("ios: posting alert FAILED: \(error.localizedDescription)") }
        }
        // Keep it in the Home bell for 48 hours (or until dismissed).
        IosDependencies.shared.getNotificationInbox().recordNearbyStore(
            storeName: storeName,
            title: content.title,
            body: content.body
        )
    }

    private static func describe(_ status: CLAuthorizationStatus) -> String {
        switch status {
        case .authorizedAlways: return "Always"
        case .authorizedWhenInUse: return "While Using"
        case .denied: return "Denied"
        case .restricted: return "Restricted"
        case .notDetermined: return "Not asked yet"
        @unknown default: return "Unknown"
        }
    }
}

/// Nearby-store diagnostics, same `[Nearby]` tag as the shared code. Filter the Xcode console
/// (or Console.app, for background wake-ups) on "[Nearby]".
func nearbyLog(_ message: String) {
    #if DEBUG
    NSLog("[Nearby] %@", message)
    #endif
}
