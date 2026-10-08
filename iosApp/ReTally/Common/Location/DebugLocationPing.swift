import CoreLocation
import Foundation
import UserNotifications

/// TEST-LOCATION-PING: after every saved scan, posts a local notification showing the
/// device's current reverse-geocoded address. Verifies the location + notification
/// plumbing end to end. Debug only — remove before release.
final class DebugLocationPing: NSObject, CLLocationManagerDelegate {
    static let enabled = true
    private static var active: DebugLocationPing?

    private let manager = CLLocationManager()
    private var done = false

    static func ping() {
        guard enabled else { return }
        // Retained until the async location + geocode finish.
        let ping = DebugLocationPing()
        active = ping
        ping.start()
    }

    private func start() {
        manager.delegate = self
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        case .denied, .restricted:
            fallthrough
        @unknown default:
            finish(address: "Location permission not granted")
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        case .denied, .restricted:
            finish(address: "Location permission denied")
        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else {
            finish(address: "No location fix")
            return
        }
        CLGeocoder().reverseGeocodeLocation(loc) { [weak self] placemarks, _ in
            let p = placemarks?.first
            var parts: [String] = []
            for part in [p?.name, p?.thoroughfare, p?.locality, p?.administrativeArea, p?.postalCode].compactMap({ $0 }) {
                if !parts.contains(part) { parts.append(part) }
            }
            if parts.isEmpty {
                parts = [String(format: "%.5f, %.5f", loc.coordinate.latitude, loc.coordinate.longitude)]
            }
            self?.finish(address: parts.joined(separator: ", "))
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finish(address: "Location failed: \(error.localizedDescription)")
    }

    private func finish(address: String) {
        guard !done else { return }
        done = true
        postNotification(address: address)
        Self.active = nil
    }

    private func postNotification(address: String) {
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .notDetermined:
                // First run: ask, then post once the answer comes back.
                center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
                    if granted { self.deliver(address: address) }
                }
            case .authorized:
                self.deliver(address: address)
            default:
                print("[DebugLocationPing] notifications not authorized")
            }
        }
    }

    private func deliver(address: String) {
        let content = UNMutableNotificationContent()
        content.title = "TEST · Current location"
        content.body = address
        let request = UNNotificationRequest(
            identifier: "debug-location-ping-\(Date().timeIntervalSince1970)",
            content: content,
            trigger: nil, // deliver immediately
        )
        UNUserNotificationCenter.current().add(request)
    }
}
