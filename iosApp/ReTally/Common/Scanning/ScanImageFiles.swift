import UIKit

/// Saves a scanned or gallery-picked receipt image (downscaled to ~1600 px) into
/// Application Support/receipts and returns its file path for the shared parser/review flow.
enum ScanImageFiles {
    static func keep(_ image: UIImage) -> String? {
        let maxSide: CGFloat = 1600
        let scale = min(1, maxSide / max(image.size.width, image.size.height))
        let target = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: target)
        let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: target)) }
        guard let data = resized.jpegData(compressionQuality: 0.85) else { return nil }
        do {
            let base = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            let dir = base.appendingPathComponent("receipts", isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let file = dir.appendingPathComponent("\(Int(Date().timeIntervalSince1970 * 1000)).jpg")
            try data.write(to: file, options: .atomic)
            return file.path
        } catch {
            return nil
        }
    }
}
