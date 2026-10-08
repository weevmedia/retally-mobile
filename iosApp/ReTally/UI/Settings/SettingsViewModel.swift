import Combine
import Foundation
import Shared
import UIKit

/// Owns `SettingsContract.Store`; opens legal links and surfaces sync messages.
@MainActor
final class SettingsViewModel: ObservableObject {
    let store: SettingsContractStore
    let screen: ComposeScreen

    private var sideEffects: Closeable?

    init() {
        let store = IosDependencies.shared.getSettingsStore()
        self.store = store
        screen = IosScreens.shared.settings(store: store)

        sideEffects = store.collectSideEffect { [weak self] effect in
            self?.handle(effect)
        }
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }

    private func handle(_ effect: SettingsContractSideEffect) {
        switch effect {
        case let open as SettingsContractSideEffect.OpenUrl:
            guard let url = URL(string: open.url), UIApplication.shared.canOpenURL(url) else {
                screen.showMessage(message: NSLocalizedString("host_link_open_failed", comment: ""))
                return
            }
            UIApplication.shared.open(url)
        case let message as SettingsContractSideEffect.ShowMessage:
            screen.showMessage(message: message.message)
        case is SettingsContractSideEffect.RequestNearbyPermissions:
            NearbyStoreMonitor.shared.requestPermissions { [weak self] granted in
                self?.store.dispatch(action: SettingsContractAction.NearbyPermissionsResult(granted: granted))
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
