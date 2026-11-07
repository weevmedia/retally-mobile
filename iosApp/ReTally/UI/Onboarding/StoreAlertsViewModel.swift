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
                // Location (Always) first, then notifications; the store turns alerts on only
                // when the whole set was granted.
                NearbyStoreMonitor.shared.requestPermissions { granted in
                    store?.dispatch(action: StoreAlertsContractAction.PermissionsResult(granted: granted))
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
