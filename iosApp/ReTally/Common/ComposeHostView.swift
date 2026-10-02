import SwiftUI
import UIKit

/// Embeds a Compose Multiplatform screen (built by Kotlin `IosScreens`) in SwiftUI.
/// The controller is owned by the screen's ViewModel, so SwiftUI never recreates it.
struct ComposeHostView: UIViewControllerRepresentable {
    let controller: UIViewController
    /// Colour behind the Compose content. Compose "punches a hole" for every UIKitView (the ad
    /// banners) and whatever is behind shows through any gap; default to the theme surface so
    /// nothing flashes black. The floating tab bar passes `.clear` so the page shows through.
    var background: UIColor = UIColor(red: 0xF6 / 255, green: 0xF8 / 255, blue: 0xF7 / 255, alpha: 1)

    func makeUIViewController(context: Context) -> UIViewController {
        controller.view.backgroundColor = background
        controller.view.isOpaque = background != .clear
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
