import SwiftUI

/// SwiftUI wrapper for ReceiptScannerViewController.
/// Runs OCR on the scanned image and reports back via Result.
struct ReceiptScannerView: UIViewControllerRepresentable {
    /// Scanner result types.
    enum Result {
        case recognized(lines: [String], imagePath: String?)
        case cancelled
        case failed(Error)
    }

    /// Scanner errors.
    enum ScannerError: LocalizedError {
        case cameraUnavailable
        case captureFailed

        var errorDescription: String? {
            switch self {
            case .cameraUnavailable: return "Camera is not available on this device."
            case .captureFailed: return "Failed to capture the receipt image."
            }
        }
    }

    /// Whether the device supports camera scanning.
    static var isSupported: Bool {
        UIImagePickerController.isSourceTypeAvailable(.camera)
    }

    let onResult: (Result) -> Void

    func makeUIViewController(context: Context) -> ReceiptScannerViewController {
        let vc = ReceiptScannerViewController()
        let coordinator = context.coordinator
        // OCR + saving start the moment the cleaned image exists, in parallel with the
        // scanner's capture animation, instead of after it.
        vc.onCaptured = { image in
            coordinator.pending = Task.detached(priority: .userInitiated) { () -> ReceiptScannerView.Result in
                do {
                    let lines = try await ReceiptTextRecognizer.recognizeLines(in: image)
                    return .recognized(lines: lines, imagePath: ScanImageFiles.keep(image))
                } catch {
                    return .failed(error)
                }
            }
        }
        vc.onScan = { _ in
            Task {
                let result: ReceiptScannerView.Result = await coordinator.pending?.value ?? .failed(ScannerError.captureFailed)
                coordinator.pending = nil
                await MainActor.run { onResult(result) }
            }
        }
        vc.onCancel = {
            onResult(.cancelled)
        }
        return vc
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    /// Holds the OCR task started at capture time until the animation hands over.
    final class Coordinator {
        var pending: Task<Result, Never>?
    }

    func updateUIViewController(_ uiViewController: ReceiptScannerViewController, context: Context) {}
}
