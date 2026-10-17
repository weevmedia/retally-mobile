import UIKit

/// Saves a scanned or gallery-picked receipt image (downscaled to ~1600 px) into
/// Application Support/receipts and returns its filename for the shared parser/review flow.
///
/// Only the filename is stored (not the absolute path) because iOS changes the app's
/// sandbox path on reinstall, which would invalidate stored absolute paths.
enum ScanImageFiles {
    static func keep(_ image: UIImage) -> String? {
        let maxSide: CGFloat = 1600
        let scale = min(1, maxSide / max(image.size.width, image.size.height))
        let target = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: target)
        let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: target)) }
        guard let data = resized.jpegData(compressionQuality: 0.85) else { return nil }
        do {
            let dir = try receiptsDirectory()
            let filename = "\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
            let file = dir.appendingPathComponent(filename)
            try data.write(to: file, options: .atomic)
            return filename
        } catch {
            return nil
        }
    }

    /// Resolves a stored image reference (filename or legacy absolute path) to the
    /// current full file path, or nil if the file doesn't exist.
    static func resolve(_ stored: String?) -> String? {
        guard let stored, !stored.isEmpty else { return nil }
        // Legacy: absolute path from before the filename-only change.
        if stored.hasPrefix("/"), FileManager.default.fileExists(atPath: stored) {
            return stored
        }
        // Filename (or legacy absolute path whose sandbox changed): resolve in receipts dir.
        let filename = (stored as NSString).lastPathComponent
        guard let dir = try? receiptsDirectory() else { return nil }
        let path = dir.appendingPathComponent(filename).path
        return FileManager.default.fileExists(atPath: path) ? path : nil
    }

    private static func receiptsDirectory() throws -> URL {
        let base = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let dir = base.appendingPathComponent("receipts", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
}
