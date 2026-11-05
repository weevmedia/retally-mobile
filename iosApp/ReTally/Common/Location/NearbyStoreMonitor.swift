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
final class NearbyStoreMonitor: NSObject, CLLocationManagerDelegate {
    static let shared = NearbyStoreMonitor()

    private let manager = CLLocationManager()
    private let center = UNUserNotificationCenter.current()
    private var permissionCompletion: ((Bool) -> Void)?
    private var notificationsGranted = false

    private override init() {
        super.init()
        manager.delegate = self
    }

    // MARK: - Permissions (called from the Settings toggle flow)

    /// Requests notification + Always location authorization, then reports whether
    /// the full set needed for background geofences was granted.
    func requestPermissions(completion: @escaping (Bool) -> Void) {
        permissionCompletion = completion
        center.requestAuthorization(options: [.alert, .sound]) { [weak self] granted, _ in
            self?.notificationsGranted = granted
            DispatchQueue.main.async { [weak self] in
                self?.manager.requestAlwaysAuthorization()
            }
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        nearbyLog("ios: location permission is now \(Self.describe(manager.authorizationStatus))")
        // Changed in Settings (not from our prompt): re-sync, which stops monitoring if it was turned off.
        guard let completion = permissionCompletion else {
            refresh()
            return
        }
        switch manager.authorizationStatus {
        case .authorizedAlways:
            permissionCompletion = nil
            completion(notificationsGranted)
        case .denied, .restricted:
            permissionCompletion = nil
            completion(false)
        case .authorizedWhenInUse:
            // Geofences can't wake a terminated app without Always; the shared layer
            // turns the toggle back off and tells the user to pick "Always" in Settings.
            permissionCompletion = nil
            completion(false)
        default:
            break // .notDetermined — the dialog is still up; wait for the real answer.
        }
    }

    // MARK: - Region sync (launch, toggle change)

    func refresh() {
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
            self.syncRegions(locations: locations)
        }
    }

    private func syncRegions(locations: [StoreLocation]) {
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
            manager.requestState(for: region)
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
    NSLog("[Nearby] %@", message)
}
