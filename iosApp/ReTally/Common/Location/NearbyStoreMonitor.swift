import CoreLocation
import Foundation
import Shared
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
        guard let completion = permissionCompletion else { return }
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
            stopAll()
            return
        }
        let status = manager.authorizationStatus
        guard status == .authorizedAlways || status == .authorizedWhenInUse else { return }
        repo.refreshLocations { [weak self] _ in
            guard let self else { return }
            let locations = (repo.locations.value as? [StoreLocation]) ?? []
            self.syncRegions(locations: locations)
        }
    }

    private func syncRegions(locations: [StoreLocation]) {
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
        }
    }

    private func stopAll() {
        for region in manager.monitoredRegions {
            manager.stopMonitoring(for: region)
        }
    }

    // MARK: - Region events

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        let repo = IosDependencies.shared.getStoreLocationRepository()
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        // 24h per-store cooldown lives in the shared repository.
        if repo.shouldNotifyAndMark(locationId: region.identifier, nowEpochMillis: nowMs) {
            let name = repo.locationName(locationId: region.identifier) ?? ""
            postNotification(storeName: name)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Region monitoring failures (e.g. no network for a fix) — the OS retries.
        print("[NearbyStoreMonitor] location error: \(error.localizedDescription)")
    }

    func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?, withError error: Error) {
        print("[NearbyStoreMonitor] monitoring failed for \(region?.identifier ?? "?"): \(error.localizedDescription)")
    }

    // MARK: - Local notification

    private func postNotification(storeName: String) {
        let content = UNMutableNotificationContent()
        // The shared string uses %1$s (Java/Compose style). Apple's
        // String(format:) treats %s as a C string and garbles Swift strings
        // ("You're near aÙú"), so substitute directly instead of formatting.
        content.title = NSLocalizedString("nearby_notify_title", comment: "")
            .replacingOccurrences(of: "%1$s", with: storeName)
        content.body = NSLocalizedString("nearby_notify_body", comment: "")
        let request = UNNotificationRequest(
            identifier: "nearby-\(storeName)-\(Date().timeIntervalSince1970)",
            content: content,
            trigger: nil, // deliver immediately
        )
        center.add(request)
    }
}
