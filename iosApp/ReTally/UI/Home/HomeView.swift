import SwiftUI

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()

    var body: some View {
        NavigationStack {
            ComposeHostView(controller: viewModel.screen.viewController)
                // Full bleed: content extends under the native tab bar for the
                // translucent effect, matching AI List and More.
                .ignoresSafeArea()
                .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar.
                .navigationDestination(isPresented: $viewModel.isReviewPresented) {
                    ScanReviewView(source: viewModel.reviewSource, onFinished: viewModel.reviewFinished(fromScan:saved:))
                }
                .navigationDestination(isPresented: $viewModel.isDuplicatesPresented) {
                    DuplicatesView(onFinished: viewModel.duplicatesFinished)
                }
                .navigationDestination(isPresented: $viewModel.isBudgetPresented) {
                    BudgetView(onFinished: viewModel.budgetFinished)
                }
                .navigationDestination(isPresented: $viewModel.isAlertsPresented) {
                    AlertsView(onFinished: viewModel.alertsFinished)
                }
                .fullScreenCover(isPresented: $viewModel.isScannerPresented) {
                    ReceiptScannerView(onResult: viewModel.scannerFinished)
                        .ignoresSafeArea()
                        // The review push waits for this: pushing while the cover is
                        // still dismissing is dropped by SwiftUI.
                        .onDisappear { viewModel.scannerCoverDismissed() }
                }
        }
    }
}
