import Combine
import Foundation
import Shared

/// Owns `ScanReviewContract.Store` for the receipt view. Seeded either with the OCR lines of
/// a fresh scan (auto-saved on arrival) or with the id of an existing receipt. Closing pops
/// back to Home through `onFinished`, reporting whether the review came from a scan and
/// whether the receipt was saved (drives the "scan more?" prompt).
@MainActor
final class ScanReviewViewModel: ObservableObject {
    enum Source {
        case scan(lines: [String], imagePath: String?)
        case existing(receiptId: String)
    }

    let store: ScanReviewContractStore
    let screen: ComposeScreen

    private var sideEffects: Closeable?

    init(source: Source, onFinished: @escaping (_ fromScan: Bool, _ saved: Bool) -> Void) {
        let store = IosDependencies.shared.getScanReviewStore()
        self.store = store
        screen = IosScreens.shared.scanReview(store: store)

        var saved = false
        sideEffects = store.collectSideEffect { [weak self] effect in
            switch effect {
            case is ScanReviewContractSideEffect.Saved:
                saved = true
                // A newly saved receipt may add a new store — rebuild geofences now
                // instead of waiting for the next app launch.
                NearbyStoreMonitor.shared.refresh()
                // TEST-LOCATION-PING: debug only — remove before release.
                if case .scan = source { DebugLocationPing.ping() }
            case is ScanReviewContractSideEffect.Closed:
                let fromScan: Bool
                if case .scan = source { fromScan = true } else { fromScan = false }
                onFinished(fromScan, saved)
            case let message as ScanReviewContractSideEffect.ShowMessage:
                self?.screen.showMessage(message: message.message)
            default:
                break
            }
        }
        switch source {
        case .scan(let lines, let imagePath):
            store.dispatch(action: ScanReviewContractAction.TextRecognized(lines: lines, imagePath: imagePath))
        case .existing(let receiptId):
            store.dispatch(action: ScanReviewContractAction.LoadReceipt(id: receiptId))
        }
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}
