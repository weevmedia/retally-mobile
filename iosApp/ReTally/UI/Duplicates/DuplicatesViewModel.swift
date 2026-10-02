import Combine
import Foundation
import Shared

/// Owns `DuplicatesContract.Store` for the "Duplicates found" list. The Store resolves
/// Keep both / Remove one; this class handles navigation (open a receipt, pop back).
@MainActor
final class DuplicatesViewModel: ObservableObject {
    let store: DuplicatesContractStore
    let screen: ComposeScreen

    @Published var isReceiptPresented = false
    private(set) var receiptId: String = ""

    private var sideEffects: Closeable?

    init(onFinished: @escaping () -> Void) {
        let store = IosDependencies.shared.getDuplicatesStore()
        self.store = store
        screen = IosScreens.shared.duplicates(store: store)

        sideEffects = store.collectSideEffect { [weak self] effect in
            guard let self else { return }
            switch effect {
            case is DuplicatesContractSideEffect.Closed:
                onFinished()
            case let open as DuplicatesContractSideEffect.OpenReceipt:
                self.receiptId = open.id
                self.isReceiptPresented = true
            case let message as DuplicatesContractSideEffect.ShowMessage:
                self.screen.showMessage(message: message.message)
            default:
                break
            }
        }
    }

    func receiptFinished() {
        isReceiptPresented = false
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}
