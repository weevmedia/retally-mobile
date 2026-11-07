//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import Combine
import Foundation
import Shared

/// Owns `NotificationsContract.Store` for the Home bell's 48-hour inbox. Handles Back and
/// the hand-off to the Shopping List tab when a "you're near {store}" entry is tapped.
@MainActor
final class NotificationsViewModel: ObservableObject {
    let store: NotificationsContractStore
    let screen: ComposeScreen

    private var sideEffects: Closeable?

    init(onFinished: @escaping () -> Void, onOpenBudget: @escaping () -> Void) {
        let store = IosDependencies.shared.getNotificationsStore()
        self.store = store
        screen = IosScreens.shared.notifications(store: store)

        sideEffects = store.collectSideEffect { effect in
            switch effect {
            case is NotificationsContractSideEffect.Closed:
                onFinished()
            case is NotificationsContractSideEffect.OpenBudget:
                // Budget reminder / month-end wrap-up: pop back to Home, then push Budget.
                onFinished()
                onOpenBudget()
            case is NotificationsContractSideEffect.OpenShoppingList:
                // The store already queued the store name on ShoppingListRepository.
                onFinished()
                IosDependencies.shared.getMainStore().dispatch(action: MainContractAction.SelectTab(tab: .aiList))
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
