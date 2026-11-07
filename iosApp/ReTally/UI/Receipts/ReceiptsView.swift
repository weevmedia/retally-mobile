//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

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
                // The tab stays alive between visits, so Compose's first-show hook runs once;
                // this re-checks stores marked "no logo" on every visit (throttled in Kotlin).
                .onAppear { viewModel.appeared() }
                .navigationDestination(isPresented: $viewModel.isReviewPresented) {
                    ScanReviewView(source: viewModel.reviewSource, onFinished: { _, _ in viewModel.reviewFinished() })
                }
        }
    }
}
