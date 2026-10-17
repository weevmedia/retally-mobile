import Combine
import Foundation
import Shared

/// Owns `HomeContract.Store`. The Store decides *what* happens (open the scanner, show the
/// review, show a message); this class does the iOS-specific *how*: VisionKit, navigation.
@MainActor
final class HomeViewModel: ObservableObject {
    let store: HomeContractStore
    let screen: ComposeScreen

    @Published var isScannerPresented = false
    @Published var isReviewPresented = false
    @Published var isDuplicatesPresented = false
    @Published var isBudgetPresented = false
    @Published var isAlertsPresented = false
    private(set) var reviewSource: ScanReviewViewModel.Source = .scan(lines: [], imagePath: nil)
    /// Finished-scan payload waiting for the camera cover to finish dismissing.
    /// Dispatching `TextRecognized` (which pushes the review) while the dismissal
    /// animates is dropped by SwiftUI, so the dispatch waits for `scannerCoverDismissed()`.
    private var pendingScan: (lines: [String], imagePath: String?)?

    private var sideEffects: Closeable?

    init() {
        let store = IosDependencies.shared.getHomeStore()
        self.store = store
        // Ad eligibility is read live from the app-wide MainContract.Store, so the banner
        // appears as soon as the SDK finishes starting — even if that is after the splash.
        // No extra inset: the native TabView bar insets the page itself (HomeView ignores
        // only the top safe area), so Compose already stops above it.
        screen = IosScreens.shared.home(
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

    // MARK: Side effects

    private func handle(_ effect: HomeContractSideEffect) {
        switch effect {
        case is HomeContractSideEffect.LaunchScanner:
            presentScanner()
        case let open as HomeContractSideEffect.OpenScanReview:
            reviewSource = .scan(lines: open.lines, imagePath: open.imagePath)
            isReviewPresented = true
        case let open as HomeContractSideEffect.OpenReceipt:
            reviewSource = .existing(receiptId: open.id)
            isReviewPresented = true
        case is HomeContractSideEffect.OpenDuplicates:
            isDuplicatesPresented = true
        case is HomeContractSideEffect.OpenBudget:
            isBudgetPresented = true
        case is HomeContractSideEffect.OpenAlerts:
            isAlertsPresented = true
        case is HomeContractSideEffect.OpenReceipts:
            // Home lists only the newest few; "See all" hands over to the Receipts tab.
            IosDependencies.shared.getMainStore().dispatch(action: MainContractAction.SelectTab(tab: .receipts))
        case is HomeContractSideEffect.OpenSignIn:
            break // TODO(sign-in): present the account-linking flow
        case let message as HomeContractSideEffect.ShowMessage:
            screen.showMessage(message: message.message)
        default:
            break
        }
    }

    // MARK: Scanner

    /// Scan FAB entry point: straight to the receipt scanner.
    private func presentScanner() {
        if ReceiptScannerView.isSupported {
            isScannerPresented = true
        } else {
            store.dispatch(action: HomeContractAction.ScanFailed(reason: ReceiptScannerView.ScannerError.cameraUnavailable.localizedDescription))
        }
    }

    func scannerFinished(_ result: ReceiptScannerView.Result) {
        isScannerPresented = false
        switch result {
        case .cancelled:
            break
        case .failed(let error):
            store.dispatch(action: HomeContractAction.ScanFailed(reason: error.localizedDescription))
        case .recognized(let lines, let imagePath):
            // Hold the payload until the camera cover is fully dismissed; the
            // review push happens in scannerCoverDismissed().
            pendingScan = (lines, imagePath)
        }
    }

    /// The camera cover finished dismissing — now it's safe to route the scan to review.
    func scannerCoverDismissed() {
        guard let pending = pendingScan else { return }
        pendingScan = nil
        store.dispatch(action: HomeContractAction.TextRecognized(lines: pending.lines, imagePath: pending.imagePath))
    }

    func reviewFinished(fromScan: Bool, saved: Bool) {
        isReviewPresented = false
    }

    func duplicatesFinished() {
        isDuplicatesPresented = false
    }

    func budgetFinished() {
        isBudgetPresented = false
    }

    func alertsFinished() {
        isAlertsPresented = false
    }
}
