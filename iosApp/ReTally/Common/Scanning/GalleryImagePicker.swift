import PhotosUI
import SwiftUI

/// Photo-library picker for uploading a receipt image from the gallery. The picked image
/// goes through the same Vision OCR + shared parser as a camera scan. Presented as a sheet
/// from `HomeView`; needs no photo-library permission (PHPicker runs out-of-process).
struct GalleryImagePicker: UIViewControllerRepresentable {

    enum Result {
        case picked(UIImage)
        case cancelled
    }

    let onFinish: (Result) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let onFinish: (Result) -> Void

        init(onFinish: @escaping (Result) -> Void) { self.onFinish = onFinish }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard let item = results.first else {
                onFinish(.cancelled)
                return
            }
            item.itemProvider.loadObject(ofClass: UIImage.self) { [onFinish] object, _ in
                if let image = object as? UIImage {
                    onFinish(.picked(image))
                } else {
                    onFinish(.cancelled)
                }
            }
        }
    }
}
