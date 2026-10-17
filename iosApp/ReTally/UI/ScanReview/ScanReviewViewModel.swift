import Combine
import Foundation
import Shared
import UIKit

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
            case is ScanReviewContractSideEffect.Closed:
                let fromScan: Bool
                if case .scan = source { fromScan = true } else { fromScan = false }
                onFinished(fromScan, saved)
            case let message as ScanReviewContractSideEffect.ShowMessage:
                self?.screen.showMessage(message: message.message)
            case let share as ScanReviewContractSideEffect.ShareReceipt:
                self?.shareReceipt(imagePath: share.imagePath)
            case let save as ScanReviewContractSideEffect.SaveToPhotos:
                self?.saveToPhotos(imagePath: save.imagePath)
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

    /// Shows a chooser: Save to Photos or Save to Files.
    private func saveToPhotos(imagePath: String?) {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first?.rootViewController else { return }
        var topVC = rootVC
        while let presented = topVC.presentedViewController {
            topVC = presented
        }

        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        alert.addAction(UIAlertAction(title: "Save to Photos", style: .default) { [weak self] _ in
            self?.saveImageToPhotoLibrary(imagePath: imagePath)
        })
        alert.addAction(UIAlertAction(title: "Save to Files", style: .default) { [weak self] _ in
            self?.saveImageToFiles(imagePath: imagePath)
        })
        alert.addAction(UIAlertAction(title: NSLocalizedString("action_cancel", comment: ""), style: .cancel))

        // iPad: anchor the action sheet.
        if let popover = alert.popoverPresentationController {
            popover.sourceView = topVC.view
            popover.sourceRect = CGRect(x: topVC.view.bounds.midX, y: topVC.view.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        topVC.present(alert, animated: true)
    }

    /// Saves the receipt image to the device photo library.
    private func saveImageToPhotoLibrary(imagePath: String?) {
        guard let path = ScanImageFiles.resolve(imagePath),
              let image = UIImage(contentsOfFile: path) else { return }
        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
        screen.showMessage(message: "Saved to Photos")
    }

    /// Exports the receipt image via the Files picker.
    private func saveImageToFiles(imagePath: String?) {
        guard let path = ScanImageFiles.resolve(imagePath) else { return }
        let sourceURL = URL(fileURLWithPath: path)
        // Copy to a temp file with a unique name for the exporter.
        let filename = "receipt-\(Int(Date().timeIntervalSince1970)).jpg"
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        do {
            if FileManager.default.fileExists(atPath: tempURL.path) {
                try FileManager.default.removeItem(at: tempURL)
            }
            try FileManager.default.copyItem(at: sourceURL, to: tempURL)
        } catch {
            return
        }
        let picker = UIDocumentPickerViewController(forExporting: [tempURL], asCopy: true)
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first?.rootViewController else { return }
        var topVC = rootVC
        while let presented = topVC.presentedViewController {
            topVC = presented
        }
        topVC.present(picker, animated: true)
    }

    /// Presents the iOS share sheet for the receipt image.
    private func shareReceipt(imagePath: String?) {
        var items: [Any] = []
        if let path = ScanImageFiles.resolve(imagePath),
           let image = UIImage(contentsOfFile: path) {
            items.append(image)
        }
        // Fall back to sharing the receipt text if no image.
        if items.isEmpty {
            return
        }
        let activityVC = UIActivityViewController(activityItems: items, applicationActivities: nil)
        // Find the topmost view controller to present from.
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootVC = windowScene.windows.first?.rootViewController {
            var topVC = rootVC
            while let presented = topVC.presentedViewController {
                topVC = presented
            }
            // iPad: anchor to the center.
            if let popover = activityVC.popoverPresentationController {
                popover.sourceView = topVC.view
                popover.sourceRect = CGRect(x: topVC.view.bounds.midX, y: topVC.view.bounds.midY, width: 0, height: 0)
                popover.permittedArrowDirections = []
            }
            topVC.present(activityVC, animated: true)
        }
    }
}
