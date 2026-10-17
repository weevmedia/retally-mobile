import SwiftUI
import VisionKit

/// System document scanner (VNDocumentCameraViewController) wrapped for SwiftUI.
/// This is the exact iOS system scanner UI — green document highlight, auto edge detection,
/// perspective correction. When the user finishes, we OCR the first page and go to review.
struct SystemScannerView: UIViewControllerRepresentable {
    let onResult: (CustomScannerView.Result) -> Void

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let scanner = VNDocumentCameraViewController()
        scanner.delegate = context.coordinator
        return scanner
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onResult: onResult)
    }

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let onResult: (CustomScannerView.Result) -> Void

        init(onResult: @escaping (CustomScannerView.Result) -> Void) {
            self.onResult = onResult
        }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            // Take the first page, OCR it, and go to review.
            guard scan.pageCount > 0 else {
                onResult(.failed(CustomScannerView.ScannerError.captureFailed))
                return
            }
            let image = scan.imageOfPage(at: 0)
            Task {
                do {
                    let lines = try await ReceiptTextRecognizer.recognizeLines(in: image)
                    let imagePath = ScanImageFiles.keep(image)
                    await MainActor.run {
                        onResult(.recognized(lines: lines, imagePath: imagePath))
                    }
                } catch {
                    await MainActor.run {
                        onResult(.failed(error))
                    }
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
