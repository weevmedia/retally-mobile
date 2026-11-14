//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import Combine
import Foundation
import Shared

/// Owns `StoreAlertsContract.Store`: runs the system prompts the screen asks for and reports
/// the exits: on to the tab bar (Enable location answered, or Skip) or back to sign-in.
@MainActor
final class StoreAlertsViewModel: ObservableObject {
    let screen: ComposeScreen

    private let store: StoreAlertsContractStore
    private var sideEffects: Closeable?

    init(onFinished: @escaping () -> Void, onBack: @escaping () -> Void) {
        let store = IosDependencies.shared.getStoreAlertsStore()
        self.store = store
        screen = IosScreens.shared.storeAlerts(store: store)

        sideEffects = store.collectSideEffect { [weak store] effect in
            switch effect {
            case is StoreAlertsContractSideEffect.RequestAlertPermissions:
                // Location only (While Using, then Always). The page stays until both location
                // prompts have a real answer; notifications are asked when Home loads.
                NearbyStoreMonitor.shared.requestLocationPermission { outcome in
                    switch outcome {
                    case .always:
                        store?.dispatch(action: StoreAlertsContractAction.PermissionsResult(granted: true))
                    case .whenInUseOnly, .denied:
                        store?.dispatch(action: StoreAlertsContractAction.PermissionsResult(granted: false))
                    case .pending, .interrupted:
                        // No answer yet (prompt still up, or it vanished): stop the spinner and
                        // stay; a late "Always" still lands here and moves on.
                        store?.dispatch(action: StoreAlertsContractAction.PermissionsInterrupted.shared)
                    }
                }
            case is StoreAlertsContractSideEffect.AlertsEnabled:
                NearbyStoreMonitor.shared.refresh()
            case is StoreAlertsContractSideEffect.Finished:
                onFinished()
            case is StoreAlertsContractSideEffect.NavigateBack:
                onBack()
            default:
                break
            }
        }
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}
