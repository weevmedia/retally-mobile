import SwiftUI

/// "Duplicates found" — pushed from Home's stat card. Can push a receipt on top.
struct DuplicatesView: View {
    @StateObject private var viewModel: DuplicatesViewModel

    init(onFinished: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: DuplicatesViewModel(onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar with its own Back.
            .toolbar(.hidden, for: .tabBar)
            .navigationDestination(isPresented: $viewModel.isReceiptPresented) {
                ScanReviewView(source: .existing(receiptId: viewModel.receiptId), onFinished: { _, _ in viewModel.receiptFinished() })
            }
    }
}
