//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import SwiftUI

/// Monthly grocery budget — pushed from Home's Spent tile.
struct BudgetView: View {
    @StateObject private var viewModel: BudgetViewModel

    init(onFinished: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: BudgetViewModel(onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar with its own Back.
            // No scan button on the budget page (same mechanism as the receipt page).
            .onAppear { NotificationCenter.default.post(name: .retallyHideScanButton, object: nil) }
            .onDisappear { NotificationCenter.default.post(name: .retallyShowScanButton, object: nil) }
    }
}
