import SwiftUI

struct ReceiptsView: View {
    @StateObject private var viewModel = ReceiptsViewModel()

    var body: some View {
        NavigationStack {
            ComposeHostView(controller: viewModel.screen.viewController)
                // Full bleed: content extends under the native tab bar for the
                // translucent effect, matching AI List and More.
                .ignoresSafeArea()
                .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar.
                .navigationDestination(isPresented: $viewModel.isReviewPresented) {
                    ScanReviewView(source: viewModel.reviewSource, onFinished: { _, _ in viewModel.reviewFinished() })
                }
        }
    }
}
