import SwiftUI

struct ReceiptsView: View {
    @StateObject private var viewModel = ReceiptsViewModel()

    var body: some View {
        NavigationStack {
            ComposeHostView(controller: viewModel.screen.viewController)
                // Top only: Compose draws its own title bar under the status bar, while the
                // native TabView reserves the bottom for its bar.
                .ignoresSafeArea(edges: .top)
                .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar.
                .navigationDestination(isPresented: $viewModel.isReviewPresented) {
                    ScanReviewView(source: viewModel.reviewSource, onFinished: { _, _ in viewModel.reviewFinished() })
                }
        }
    }
}
