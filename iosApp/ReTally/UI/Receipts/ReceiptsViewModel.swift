import Combine
import Foundation
import Shared

/// Owns `ReceiptsContract.Store` for the Receipts tab. The Store decides what happens; this
/// class does the iOS-specific how — here, presenting the receipt detail.
@MainActor
final class ReceiptsViewModel: ObservableObject {
    let store: ReceiptsContractStore
    let screen: ComposeScreen

    @Published var isReviewPresented = false
    private(set) var reviewSource: ScanReviewViewModel.Source = .scan(lines: [], imagePath: nil)

    private var sideEffects: Closeable?

    init() {
        let store = IosDependencies.shared.getReceiptsStore()
        self.store = store
        // Same as Home: the native TabView bar insets the page, so Compose needs no extra
        // bottom padding of its own. Ad eligibility is read live from the app-wide
        // MainContract.Store, like Home.
        screen = IosScreens.shared.receipts(
            store: store,
            mainStore: IosDependencies.shared.getMainStore()
        )
        sideEffects = store.collectSideEffect { [weak self] effect in
            self?.handle(effect)
        }
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }

    private func handle(_ effect: ReceiptsContractSideEffect) {
        switch effect {
        case let open as ReceiptsContractSideEffect.OpenReceipt:
            reviewSource = .existing(receiptId: open.id)
            isReviewPresented = true
        case let message as ReceiptsContractSideEffect.ShowMessage:
            screen.showMessage(message: message.message)
        default:
            break
        }
    }

    func reviewFinished() {
        isReviewPresented = false
    }
}
