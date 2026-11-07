//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import UIKit
import Vision

/// Runs Vision OCR on a receipt image and returns text lines in reading order, merging
/// fragments that sit on the same printed row (item name on the left, price on the right)
/// so the shared `ReceiptParser` sees "SOURDOUGH LOAF 5.49" as one line.
enum ReceiptTextRecognizer {

    enum RecognitionError: LocalizedError {
        case invalidImage

        var errorDescription: String? {
            switch self {
            case .invalidImage: NSLocalizedString("host_image_invalid", comment: "")
            }
        }
    }

    private static let warmUpLock = NSLock()
    private static var warmedUp = false

    /// Loads Vision's text-recognition models ahead of the first real scan. The very first
    /// `.accurate` request after install or reboot spends several seconds loading models,
    /// which otherwise lands on the user's first scan. Runs once, in the background.
    static func prewarm() {
        warmUpLock.lock()
        defer { warmUpLock.unlock() }
        guard !warmedUp else { return }
        warmedUp = true
        DispatchQueue.global(qos: .utility).async {
            let size = CGSize(width: 360, height: 80)
            let image = UIGraphicsImageRenderer(size: size).image { ctx in
                UIColor.white.setFill()
                ctx.fill(CGRect(origin: .zero, size: size))
                ("MILK 2% GAL   3.49" as NSString).draw(
                    at: CGPoint(x: 12, y: 24),
                    withAttributes: [.font: UIFont.monospacedSystemFont(ofSize: 22, weight: .regular),
                                     .foregroundColor: UIColor.black],
                )
            }
            guard let cgImage = image.cgImage else { return }
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            request.recognitionLanguages = ["en-US"]
            try? VNImageRequestHandler(cgImage: cgImage, options: [:]).perform([request])
        }
    }

    static func recognizeLines(in image: UIImage) async throws -> [String] {
        // Downscale for OCR: Vision's text accuracy plateaus around 200-300 DPI for
        // printed text — a 12MP image just takes 4x longer with no accuracy gain.
        // The full-res original is kept for the photo viewer and cloud backup;
        // only this downscaled copy goes to the OCR engine.
        let ocrImage = image.downscaledForOCR()
        guard let cgImage = ocrImage.cgImage else { throw RecognitionError.invalidImage }

        let observations: [VNRecognizedTextObservation] = try await withCheckedThrowingContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                continuation.resume(returning: (request.results as? [VNRecognizedTextObservation]) ?? [])
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            request.recognitionLanguages = ["en-US"]
            do {
                try VNImageRequestHandler(cgImage: cgImage, options: [:]).perform([request])
            } catch {
                continuation.resume(throwing: error)
            }
        }
        var lines = mergeRows(observations)
        // Barcodes: Vision's barcode detector reads the bars themselves, which text OCR
        // on the small digits under a top-of-receipt barcode often drops or misreads —
        // why Walmart/Shaw's top barcodes came and went. Each numeric payload joins as
        // its own line so the shared parser's barcode detector picks it up; appended
        // last so it never pollutes the store address at the top.
        lines += barcodes(in: cgImage)
        return lines
    }

    /// Numeric barcode payloads on the image (digits only, 12+ digits). Non-numeric
    /// payloads (QR links, alphanumeric codes) are left out: the shared parser only
    /// accepts digit lines as barcodes, so anything else would be dead weight.
    /// A failed detection just yields nothing — it never fails the scan.
    private static func barcodes(in cgImage: CGImage) -> [String] {
        var payloads: [String] = []
        let request = VNDetectBarcodesRequest { request, _ in
            for observation in (request.results as? [VNBarcodeObservation] ?? []) {
                guard let payload = observation.payloadStringValue else { continue }
                let digits = payload.filter(\.isNumber)
                let numericOnly = payload.allSatisfy { $0.isNumber || $0.isWhitespace || $0 == "-" }
                if numericOnly, digits.count >= 12 {
                    payloads.append(digits)
                }
            }
        }
        try? VNImageRequestHandler(cgImage: cgImage, options: [:]).perform([request])
        return payloads
    }

    /// Vision's origin is bottom-left: higher midY = higher on the receipt.
    private static func mergeRows(_ observations: [VNRecognizedTextObservation]) -> [String] {
        struct Fragment { let text: String; let box: CGRect }
        let fragments = observations.compactMap { obs -> Fragment? in
            guard let text = obs.topCandidates(1).first?.string.trimmingCharacters(in: .whitespacesAndNewlines),
                  !text.isEmpty else { return nil }
            return Fragment(text: text, box: obs.boundingBox)
        }.sorted { $0.box.midY > $1.box.midY }

        var rows: [[Fragment]] = []
        for fragment in fragments {
            if let last = rows.last, let anchor = last.first {
                let tolerance = max(anchor.box.height, fragment.box.height) * 0.5
                if abs(anchor.box.midY - fragment.box.midY) < tolerance {
                    rows[rows.count - 1].append(fragment)
                    continue
                }
            }
            rows.append([fragment])
        }
        return rows.map { row in
            row.sorted { $0.box.minX < $1.box.minX }.map(\.text).joined(separator: " ")
        }
    }
}

// MARK: - OCR downscale

extension UIImage {
    /// Returns a copy capped at ~2000px on the long edge (~3MP). Vision OCR is
    /// roughly linear in pixel count; this cuts processing time ~4x vs 12MP with
    /// no measurable accuracy loss on printed receipt text.
    fileprivate func downscaledForOCR(maxLongEdge: CGFloat = 2000) -> UIImage {
        let longEdge = max(size.width, size.height)
        guard longEdge > maxLongEdge else { return self }
        let scale = maxLongEdge / longEdge
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}
