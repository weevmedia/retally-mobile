import SwiftUI

struct ScanReviewView: View {
    @StateObject private var viewModel: ScanReviewViewModel

    init(source: ScanReviewViewModel.Source, onFinished: @escaping (_ fromScan: Bool, _ saved: Bool) -> Void) {
        _viewModel = StateObject(wrappedValue: ScanReviewViewModel(source: source, onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar with its own Close.
            .toolbar(.hidden, for: .tabBar)
            .onAppear {
                // Hide the native scan FAB while the receipt page (or its image viewer) is open.
                NotificationCenter.default.post(name: .retallyHideScanButton, object: nil)
            }
            .onDisappear {
                NotificationCenter.default.post(name: .retallyShowScanButton, object: nil)
            }
    }
}
