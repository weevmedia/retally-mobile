//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import Combine
import Foundation
import Shared
import UIKit

/// Owns `SettingsContract.Store`; opens legal links and surfaces sync messages.
@MainActor
final class SettingsViewModel: ObservableObject {
    let store: SettingsContractStore
    let screen: ComposeScreen

    /// More › Food recalls.
    @Published var isRecallsPresented = false
    /// More › Privacy / Terms / Help: our own page inside the app.
    @Published var isWebPagePresented = false
    private(set) var webPage: (title: String, url: String) = ("", "")

    private var sideEffects: Closeable?
    /// Set when we sent the user to the Settings app for store alerts; re-checked on return.
    private var awaitingSettingsReturn = false
    private var becameActive: AnyCancellable?

    init() {
        let store = IosDependencies.shared.getSettingsStore()
        self.store = store
        screen = IosScreens.shared.settings(store: store, mainStore: IosDependencies.shared.getMainStore())

        sideEffects = store.collectSideEffect { [weak self] effect in
            self?.handle(effect)
        }
        becameActive = NotificationCenter.default
            .publisher(for: UIApplication.didBecomeActiveNotification)
            .sink { [weak self] _ in self?.recheckAfterSettings() }
    }

    /// Back from the Settings app: if store alerts can work now, turn the toggle on.
    private func recheckAfterSettings() {
        guard awaitingSettingsReturn else { return }
        awaitingSettingsReturn = false
        NearbyStoreMonitor.shared.hasAllPermissions { [weak self] granted in
            guard granted else { return }
            self?.store.dispatch(action: SettingsContractAction.NearbyPermissionsResult(granted: true))
        }
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }

    func recallsFinished() {
        isRecallsPresented = false
    }

    func webPageFinished() {
        isWebPagePresented = false
    }

    private func handle(_ effect: SettingsContractSideEffect) {
        switch effect {
        case let open as SettingsContractSideEffect.OpenUrl:
            guard let url = URL(string: open.url), UIApplication.shared.canOpenURL(url) else {
                screen.showMessage(message: NSLocalizedString("host_link_open_failed", comment: ""))
                return
            }
            UIApplication.shared.open(url)
        case let page as SettingsContractSideEffect.OpenWebPage:
            webPage = (page.title, page.url)
            isWebPagePresented = true
        case is SettingsContractSideEffect.OpenRecalls:
            isRecallsPresented = true
        case let message as SettingsContractSideEffect.ShowMessage:
            screen.showMessage(message: message.message)
        case is SettingsContractSideEffect.RequestNearbyPermissions:
            // Location first (While Using, then Always), notifications only after that, so
            // the notification prompt can never cover "Change to Always Allow?".
            NearbyStoreMonitor.shared.requestLocationPermission { [weak self] outcome in
                switch outcome {
                case .always:
                    NearbyStoreMonitor.shared.requestNotifications { granted in
                        self?.store.dispatch(action: SettingsContractAction.NearbyPermissionsResult(granted: granted))
                    }
                case .whenInUseOnly, .denied:
                    // iOS will not ask again: the store shows the "Open Settings" dialog.
                    self?.store.dispatch(action: SettingsContractAction.NearbyPermissionsResult(granted: false))
                case .pending, .interrupted:
                    break // No answer yet; the toggle stays off until there is one.
                }
            }
        case is SettingsContractSideEffect.OpenAppSettings:
            // iOS will not prompt again: send the user to ReTally's page in Settings.
            if let url = URL(string: UIApplication.openSettingsURLString) {
                awaitingSettingsReturn = true
                UIApplication.shared.open(url)
            }
        case is SettingsContractSideEffect.NearbyMonitoringChanged:
            NearbyStoreMonitor.shared.refresh()
        case is SettingsContractSideEffect.OpenAuth:
            // Guest tapped Log in: reopen the sign-in overlay on top of the tab bar.
            NotificationCenter.default.post(name: .retallyShowAuth, object: nil)
        case let loggedOut as SettingsContractSideEffect.LoggedOut:
            screen.showMessage(message: loggedOut.message)
        case let deleted as SettingsContractSideEffect.AccountDeleted:
            screen.showMessage(message: deleted.message)
        default:
            break
        }
    }
}
