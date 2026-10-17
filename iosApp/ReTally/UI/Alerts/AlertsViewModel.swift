import Combine
import Foundation
import Shared

/// Owns `RecallsContract.Store` for the food recall alerts screen. Links open from Compose
/// (LocalUriHandler); this class handles Back and the sign-in overlay.
@MainActor
final class AlertsViewModel: ObservableObject {
    let store: RecallsContractStore
    let screen: ComposeScreen

    private var sideEffects: Closeable?

    init(onFinished: @escaping () -> Void) {
        let store = IosDependencies.shared.getRecallsStore()
        self.store = store
        screen = IosScreens.shared.alerts(store: store)

        sideEffects = store.collectSideEffect { [weak self] effect in
            guard let self else { return }
            switch effect {
            case is RecallsContractSideEffect.Closed:
                onFinished()
            case is RecallsContractSideEffect.OpenSignIn:
                NotificationCenter.default.post(name: .retallyShowAuth, object: nil)
            case let message as RecallsContractSideEffect.ShowMessage:
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
