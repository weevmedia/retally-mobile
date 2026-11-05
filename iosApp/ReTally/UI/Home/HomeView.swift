//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

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
                .navigationDestination(isPresented: $viewModel.isNotificationsPresented) {
                    NotificationsView(onFinished: viewModel.notificationsFinished, onOpenBudget: viewModel.openBudgetFromNotifications)
                }
                .navigationDestination(isPresented: $viewModel.isHowItWorksPresented) {
                    HowItWorksView(onFinished: viewModel.howItWorksFinished)
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

/// "See how it works" — pushed from the empty Home. Lives in this file so no Xcode project
/// change is needed.
struct HowItWorksView: View {
    @StateObject private var viewModel: HowItWorksViewModel

    init(onFinished: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: HowItWorksViewModel(onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar with its own Back.
            .toolbar(.hidden, for: .tabBar)
            .onAppear { NotificationCenter.default.post(name: .retallyHideScanButton, object: nil) }
            .onDisappear { NotificationCenter.default.post(name: .retallyShowScanButton, object: nil) }
    }
}
