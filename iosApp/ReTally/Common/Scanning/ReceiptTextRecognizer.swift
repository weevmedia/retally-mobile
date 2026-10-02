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

    static func recognizeLines(in image: UIImage) async throws -> [String] {
        guard let cgImage = image.cgImage else { throw RecognitionError.invalidImage }

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
        return mergeRows(observations)
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
