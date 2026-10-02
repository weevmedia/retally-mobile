import SwiftUI
import VisionKit

/// VisionKit document camera. Captures one or more pages, OCRs them top-to-bottom and hands
/// the merged text lines back. Presented full-screen from `HomeView`.
struct DocumentScannerView: UIViewControllerRepresentable {

    enum Result {
        /// OCR lines plus the saved path of the first page (nil if saving failed).
        case recognized(lines: [String], imagePath: String?)
        case cancelled
        case failed(Error)
    }

    enum ScannerError: LocalizedError {
        case unsupported

        var errorDescription: String? {
            switch self {
            case .unsupported: NSLocalizedString("host_scan_unsupported", comment: "")
            }
        }
    }

    let onResult: (Result) -> Void

    static var isSupported: Bool { VNDocumentCameraViewController.isSupported }

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let controller = VNDocumentCameraViewController()
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onResult: onResult) }

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        private let onResult: (Result) -> Void

        init(onResult: @escaping (Result) -> Void) { self.onResult = onResult }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            let images = (0..<scan.pageCount).map { scan.imageOfPage(at: $0) }
            Task {
                do {
                    var lines: [String] = []
                    for image in images {
                        lines += try await ReceiptTextRecognizer.recognizeLines(in: image)
                    }
                    // Keep the first page as the "original" the shopper can show a cashier.
                    let imagePath = images.first.flatMap(ScanImageFiles.keep)
                    await MainActor.run { onResult(.recognized(lines: lines, imagePath: imagePath)) }
                } catch {
                    await MainActor.run { onResult(.failed(error)) }
                }
            }
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            onResult(.cancelled)
        }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
            onResult(.failed(error))
        }
    }
}
