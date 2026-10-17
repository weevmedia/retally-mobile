import Combine
import Foundation
import Shared

/// Owns `BudgetContract.Store` for the monthly grocery budget screen. The Store
/// computes the pace-based 3-color state; this class handles navigation (pop back).
@MainActor
final class BudgetViewModel: ObservableObject {
    let store: BudgetContractStore
    let screen: ComposeScreen

    private var sideEffects: Closeable?

    init(onFinished: @escaping () -> Void) {
        let store = IosDependencies.shared.getBudgetStore()
        self.store = store
        screen = IosScreens.shared.budget(store: store)

        sideEffects = store.collectSideEffect { [weak self] effect in
            guard let self else { return }
            switch effect {
            case is BudgetContractSideEffect.Closed:
                onFinished()
            case let message as BudgetContractSideEffect.ShowMessage:
                self.screen.showMessage(message: message.message)
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
